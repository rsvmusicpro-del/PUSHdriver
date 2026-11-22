#!/usr/bin/env python3
"""Deterministic Monte-Carlo timing model for Push 1 -> Bitwig -> Push feedback.

All built-in profiles are illustrative assumptions, never hardware measurements.
The simulator models electrical event detection and software/transport timing;
it does not model key travel, force, tactile click, LED brightness or LCD optics.
"""
from __future__ import annotations

import argparse
import heapq
import json
import math
import random
import statistics
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PROFILE = ROOT / "tests/temporal/push1-theoretical-profile.json"
SCENARIOS = {
    "chain-entry": [
        ("IN", 0, 80), ("ROW1-2", 35, 80), ("ROW1-4", 70, 80), ("ROW2-UP", 180, 80),
    ],
    "rapid-chain": [
        ("IN", 0, 55), ("ROW1-2", 18, 55), ("ROW1-4", 36, 55),
        ("ROW1-6", 54, 55), ("ROW2-UP", 110, 55), ("IN", 160, 55),
    ],
    "mode-burst": [
        ("DEVICE", 0, 70), ("TRACK", 45, 70), ("VOLUME", 90, 70),
        ("PAN-SEND", 135, 70), ("DEVICE", 180, 70),
    ],
    "button-repeat-hold": [
        ("ROW1-2", 0, 55), ("ROW1-2", 85, 55), ("ROW1-2", 170, 55),
        ("IN", 300, 360), ("ROW2-UP", 760, 55),
    ],
}


def sample_delay(spec: dict[str, Any], rng: random.Random) -> float:
    kind = spec.get("distribution", "fixed")
    if kind == "fixed":
        value = float(spec.get("value_ms", 0))
    elif kind == "uniform":
        value = rng.uniform(float(spec["min_ms"]), float(spec["max_ms"]))
    elif kind == "normal":
        value = rng.gauss(float(spec["mean_ms"]), float(spec["stddev_ms"]))
    elif kind == "lognormal":
        value = rng.lognormvariate(float(spec["mu"],), float(spec["sigma"]))
    else:
        raise ValueError(f"unsupported distribution: {kind}")
    return max(0.0, value)


def percentile(values: list[float], p: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    rank = max(0, math.ceil(p * len(ordered)) - 1)
    return ordered[rank]


def describe(values: list[float]) -> dict[str, float | int | None]:
    if not values:
        return {"count": 0, "p50_ms": None, "p95_ms": None, "p99_ms": None,
                "max_ms": None, "mean_ms": None, "stddev_ms": None, "jitter_p95_minus_p50_ms": None}
    return {
        "count": len(values), "p50_ms": percentile(values, .50),
        "p95_ms": percentile(values, .95), "p99_ms": percentile(values, .99),
        "max_ms": max(values), "mean_ms": statistics.fmean(values),
        "stddev_ms": statistics.pstdev(values),
        "jitter_p95_minus_p50_ms": percentile(values, .95) - percentile(values, .50),
    }


@dataclass(order=True)
class Event:
    at_ms: float
    order: int
    kind: str = field(compare=False)
    version: int = field(compare=False)
    payload: dict[str, Any] = field(compare=False, default_factory=dict)


class TemporalSimulator:
    INPUT_STAGES = ("button_scan", "usb_input", "midi_input", "bitwig_dispatch", "navigation", "cursor_publish")
    FEEDBACK_STAGES = ("midi_feedback", "usb_feedback", "led_render", "lcd_render")

    def __init__(self, profile: dict[str, Any], seed: int):
        self.profile = profile
        self.rng = random.Random(seed)
        self.order = 0

    def delay(self, stage: str) -> float:
        return sample_delay(self.profile["stages"][stage], self.rng)

    def dropped(self, stage: str) -> bool:
        probability = float(self.profile.get("loss_probability", {}).get(stage, 0.0))
        if not 0 <= probability <= 1:
            raise ValueError(f"loss_probability for {stage} must be between 0 and 1")
        return self.rng.random() < probability

    def push(self, queue: list[Event], at: float, kind: str, version: int, payload=None):
        self.order += 1
        heapq.heappush(queue, Event(at, self.order, kind, version, payload or {}))

    def run_once(self, actions: list[tuple[str, float, float]]) -> dict[str, Any]:
        queue: list[Event] = []
        command_latencies: list[float] = []
        led_latencies: list[float] = []
        lcd_latencies: list[float] = []
        press_to_cursor: list[float] = []
        stage_totals = {stage: [] for stage in self.INPUT_STAGES}
        lost = {stage: 0 for stage in self.INPUT_STAGES + self.FEEDBACK_STAGES}
        completed_commands = set()
        hold_durations: list[float] = []
        hold_classes = {"short": 0, "long": 0}
        long_press_threshold = float(self.profile.get("long_press_threshold_ms", 300.0))
        feedback_seen = {"led": [], "lcd": []}
        max_version = len(actions)
        for version, (button, start, hold) in enumerate(actions, 1):
            # Release is represented explicitly; firmware mechanics are not inferred.
            self.push(queue, start, "press", version, {"button": button, "hold_ms": hold})
            self.push(queue, start + hold, "release", version, {"button": button})
        latest_feedback_version = {"led": 0, "lcd": 0}
        feedback_reorders = {"led": 0, "lcd": 0}
        press_times = {i: start for i, (_, start, _) in enumerate(actions, 1)}
        while queue:
            event = heapq.heappop(queue)
            if event.kind == "release":
                duration = max(0.0, event.at_ms - press_times[event.version])
                hold_durations.append(duration)
                hold_classes["long" if duration >= long_press_threshold else "short"] += 1
                continue
            if event.kind == "press":
                self.push(queue, event.at_ms, self.INPUT_STAGES[0], event.version, event.payload)
                continue
            if event.kind in self.INPUT_STAGES:
                if self.dropped(event.kind):
                    lost[event.kind] += 1
                    continue
                delay = self.delay(event.kind)
                stage_totals[event.kind].append(delay)
                next_at = event.at_ms + delay
                index = self.INPUT_STAGES.index(event.kind)
                if event.kind == "navigation":
                    completed_commands.add(event.version)
                    command_latencies.append(next_at - press_times[event.version])
                if event.kind == "cursor_publish":
                    press_to_cursor.append(next_at - press_times[event.version])
                    self.push(queue, next_at, "feedback_start", event.version, event.payload)
                else:
                    self.push(queue, next_at, self.INPUT_STAGES[index + 1], event.version, event.payload)
                continue
            if event.kind == "feedback_start":
                for target in ("led", "lcd"):
                    self.push(queue, event.at_ms, target + "_midi", event.version, event.payload)
                continue
            if event.kind in ("led_midi", "lcd_midi"):
                stage = "midi_feedback"
                if self.dropped(stage):
                    lost[stage] += 1
                    continue
                self.push(queue, event.at_ms + self.delay(stage), event.kind.replace("_midi", "_usb"), event.version)
                continue
            if event.kind in ("led_usb", "lcd_usb"):
                stage = "usb_feedback"
                if self.dropped(stage):
                    lost[stage] += 1
                    continue
                target = "led" if event.kind.startswith("led") else "lcd"
                render_stage = target + "_render"
                self.push(queue, event.at_ms + self.delay(stage), render_stage, event.version)
                continue
            if event.kind in ("led_render", "lcd_render"):
                target = "led" if event.kind.startswith("led") else "lcd"
                if self.dropped(event.kind):
                    lost[event.kind] += 1
                    continue
                rendered_at = event.at_ms + self.delay(event.kind)
                feedback_seen[target].append((event.version, rendered_at))
                if event.version < latest_feedback_version[target]:
                    feedback_reorders[target] += 1
                latest_feedback_version[target] = max(latest_feedback_version[target], event.version)
                latency = rendered_at - press_times[event.version]
                (led_latencies if target == "led" else lcd_latencies).append(latency)
        return {
            "commands": command_latencies, "cursor": press_to_cursor,
            "led": led_latencies, "lcd": lcd_latencies, "stages": stage_totals,
            "lost": lost, "feedback_reorders": feedback_reorders,
            "expected_commands": max_version, "completed_commands": len(completed_commands),
            "hold_durations": hold_durations, "hold_classes": hold_classes,
        }


def aggregate(profile: dict[str, Any], scenario: str, samples: int, seed: int) -> dict[str, Any]:
    if scenario not in SCENARIOS:
        raise ValueError(f"unknown scenario: {scenario}")
    sim = TemporalSimulator(profile, seed)
    actions = SCENARIOS[scenario]
    aggregate_values = {"command": [], "cursor": [], "led": [], "lcd": []}
    stage_values = {stage: [] for stage in TemporalSimulator.INPUT_STAGES}
    loss_counts: dict[str, int] = {}
    reorder_counts = {"led": 0, "lcd": 0}
    expected = completed = 0
    hold_durations: list[float] = []
    hold_classes = {"short": 0, "long": 0}
    for _ in range(samples):
        result = sim.run_once(actions)
        for name, key in (("command", "commands"), ("cursor", "cursor"), ("led", "led"), ("lcd", "lcd")):
            aggregate_values[name].extend(result[key])
        for stage, values in result["stages"].items():
            stage_values[stage].extend(values)
        for stage, count in result["lost"].items():
            loss_counts[stage] = loss_counts.get(stage, 0) + count
        for target, count in result["feedback_reorders"].items():
            reorder_counts[target] += count
        expected += result["expected_commands"]
        completed += result["completed_commands"]
        hold_durations.extend(result["hold_durations"])
        for category, count in result["hold_classes"].items():
            hold_classes[category] += count
    return {
        "schema": "push1-temporal-simulation/v1",
        "evidence_class": profile.get("evidence_class", "theoretical-configurable"),
        "profile": profile.get("name", "unnamed"),
        "scenario": scenario, "samples": samples, "seed": seed,
        "assumptions": profile.get("assumptions", []),
        "metrics": {name: describe(values) for name, values in aggregate_values.items()},
        "input_stage_delay": {name: describe(values) for name, values in stage_values.items()},
        "button_holds": {"duration_ms": describe(hold_durations), "classes": hold_classes,
                         "long_press_threshold_ms": float(profile.get("long_press_threshold_ms", 300.0))},
        "delivery": {
            "expected_commands": expected, "completed_commands": completed,
            "command_loss_count": expected - completed, "losses_by_stage": loss_counts,
            "feedback_reorders": reorder_counts,
        },
        "limitations": [
            "Not measured Push 1 or Bitwig latency; profile values are assumptions until calibrated.",
            "No mechanical force, key travel, tactile click, firmware debounce, LED brightness or LCD optical response model.",
            "USB/MIDI/host distributions are independent unless a custom profile explicitly correlates them; this is a sensitivity model, not a bus-accurate emulator.",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", type=Path, default=DEFAULT_PROFILE)
    parser.add_argument("--scenario", choices=sorted(SCENARIOS), default="chain-entry")
    parser.add_argument("--samples", type=int, default=1000)
    parser.add_argument("--seed", type=int, default=20261122)
    parser.add_argument("--json", action="store_true", help="Print the full machine-readable JSON report.")
    args = parser.parse_args()
    if args.samples < 1 or args.samples > 1_000_000:
        parser.error("--samples must be between 1 and 1000000")
    try:
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = aggregate(profile, args.scenario, args.samples, args.seed)
    except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError) as error:
        print(f"Simulation failed: {error}", file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    print(f"Push 1 Temporal & Feedback Simulator — {result['scenario']} / {result['profile']}")
    print(f"Evidence: {result['evidence_class']} | runs: {args.samples} | seed: {args.seed}")
    print("Latency percentiles (press edge to completion; milliseconds)")
    for label, key in (("Navigation command", "command"), ("Nested cursor", "cursor"),
                       ("LED feedback", "led"), ("LCD feedback", "lcd")):
        stats = result["metrics"][key]
        print(f"  {label:20} n={stats['count']:6} P50={stats['p50_ms']!s:>9} "
              f"P95={stats['p95_ms']!s:>9} P99={stats['p99_ms']!s:>9} "
              f"jitter(P95-P50)={stats['jitter_p95_minus_p50_ms']!s}")
    delivery = result["delivery"]
    print(f"Commands: {delivery['completed_commands']}/{delivery['expected_commands']} completed; "
          f"lost={delivery['command_loss_count']}; feedback reorder LED/LCD="
          f"{delivery['feedback_reorders']['led']}/{delivery['feedback_reorders']['lcd']}")
    print("Profile is illustrative, not measured. See limitations in --json report.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
