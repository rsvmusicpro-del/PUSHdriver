#!/usr/bin/env python3
"""Summarize opt-in PUSHdriver Device-navigation timing lines from Bitwig's console log."""
import argparse
import math
import re
import sys
from pathlib import Path

PATTERN = re.compile(
    r"PUSHdriver_NAV_TIMING status=(?P<status>\\w+) type=(?P<type>\\w+)"
    r" index=(?P<index>-?\\d+) queue_us=(?P<queue_us>\\d+)"
    r" host_us=(?P<host_us>\\d+) total_us=(?P<total_us>\\d+)"
    r" polls=(?P<polls>\\d+)"
)


def parse_lines(lines):
    records = []
    for line in lines:
        match = PATTERN.search(line)
        if match:
            record = match.groupdict()
            for key in ("index", "queue_us", "host_us", "total_us", "polls"):
                record[key] = int(record[key])
            records.append(record)
    return records


def percentile(values, percentile_value):
    if not values:
        raise ValueError("percentile requires at least one value")
    if not 0 < percentile_value <= 100:
        raise ValueError("percentile must be in (0, 100]")
    ordered = sorted(values)
    return ordered[max(0, math.ceil(percentile_value * len(ordered) / 100) - 1)]


def summarize(records):
    completed = [record for record in records if record["status"] == "complete"]
    if not completed:
        return None
    summary = {"count": len(completed)}
    for field in ("queue_us", "host_us", "total_us"):
        values = [record[field] for record in completed]
        summary[field] = {
            "p50": percentile(values, 50),
            "p95": percentile(values, 95),
            "p99": percentile(values, 99),
            "max": max(values),
        }
    summary["timeouts"] = sum(record["status"] == "timeout" for record in records)
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path, help="Bitwig Controller Script Console log exported to a text file")
    args = parser.parse_args()
    try:
        records = parse_lines(args.log.read_text(encoding="utf-8", errors="replace").splitlines())
    except OSError as error:
        print(f"Cannot read {args.log}: {error}", file=sys.stderr)
        return 2
    result = summarize(records)
    if result is None:
        print("No completed PUSHdriver navigation timing samples found.", file=sys.stderr)
        return 1
    print(f"Completed navigation samples: {result['count']}; timeouts: {result['timeouts']}")
    for label, field in (("Queue wait", "queue_us"), ("Bitwig cursor confirmation", "host_us"), ("Total extension navigation", "total_us")):
        stats = result[field]
        print(f"{label}: P50={stats['p50']/1000:.3f} ms  P95={stats['p95']/1000:.3f} ms  "
              f"P99={stats['p99']/1000:.3f} ms  max={stats['max']/1000:.3f} ms")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
