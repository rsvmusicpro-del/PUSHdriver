#!/usr/bin/env python3
"""Assemble the DrivenByMoss 19.2.2 sources required by the Push 1 extension."""
from pathlib import Path
import shutil
import subprocess

from push1_build.patching import replace_once, replace_java_method

root = Path(__file__).resolve().parents[1]
upstream = root / "upstream"
EXPECTED_UPSTREAM_COMMIT = "013620c1330cdcf896e96ef8fdc8a2123e4fce6a"
try:
    actual_upstream_commit = subprocess.run(
        ["git", "-C", str(upstream), "rev-parse", "HEAD"],
        check=True, capture_output=True, text=True,
    ).stdout.strip()
except (OSError, subprocess.CalledProcessError) as error:
    raise SystemExit(f"Cannot verify DrivenByMoss submodule revision: {error}") from error
if actual_upstream_commit != EXPECTED_UPSTREAM_COMMIT:
    raise SystemExit(
        f"Unexpected DrivenByMoss revision: {actual_upstream_commit}; "
        f"expected {EXPECTED_UPSTREAM_COMMIT}"
    )

try:
    upstream_status = subprocess.run(
        ["git", "-C", str(upstream), "status", "--porcelain"],
        check=True, capture_output=True, text=True,
    ).stdout.strip()
except (OSError, subprocess.CalledProcessError) as error:
    raise SystemExit(f"Cannot verify DrivenByMoss worktree state: {error}") from error
if upstream_status:
    raise SystemExit(
        "DrivenByMoss submodule has local changes; restore it to the pinned clean revision "
        "before assembling PUSHdriver."
    )

# Rebuild generated inputs from a clean state so repeated local builds cannot
# retain Java sources, service registrations or Maven artifacts from old runs.
shutil.rmtree(root / "src/main/java/de/mossgrabers", ignore_errors=True)
shutil.rmtree(root / "src/main/resources/META-INF/services", ignore_errors=True)
shutil.rmtree(root / "src/assembly", ignore_errors=True)
shutil.rmtree(root / "src/main/resources/devices", ignore_errors=True)
shutil.rmtree(root / "src/main/resources/images", ignore_errors=True)
(root / "src/main/resources/DrivenByMoss-Manual.pdf").unlink(missing_ok=True)
shutil.rmtree(root / "maven-local-repository", ignore_errors=True)
java = root / "src/main/java/de/mossgrabers"
resources = root / "src/main/resources/META-INF/services"
java.mkdir(parents=True, exist_ok=True)
resources.mkdir(parents=True, exist_ok=True)

def copy_tree(source: str, destination: str) -> None:
    shutil.copytree(upstream / source, root / destination, dirs_exist_ok=True)

# Copy runtime data used by HostImpl and the controller UI. In particular,
# HostImpl.readDeviceFiles() wraps these streams without a null check, so omitting
# devices/*.txt makes extension construction fail before init() is reached.
for resource_dir in ("devices", "images"):
    copy_tree(f"src/main/resources/{resource_dir}", f"src/main/resources/{resource_dir}")
shutil.copy2(
    upstream / "src/main/resources/Documentation/DrivenByMoss-Manual.pdf",
    root / "src/main/resources/DrivenByMoss-Manual.pdf",
)

copy_tree("src/main/java/de/mossgrabers/framework", "src/main/java/de/mossgrabers/framework")
copy_tree("src/main/java/de/mossgrabers/bitwig/framework", "src/main/java/de/mossgrabers/bitwig/framework")
# Push 1 uses MIDI only; the Bitwig USB-HID adapter exists for other hardware.
shutil.rmtree(root / "src/main/java/de/mossgrabers/bitwig/framework/usb", ignore_errors=True)
copy_tree("src/main/java/de/mossgrabers/bitwig/controller/ableton/push",
          "src/main/java/de/mossgrabers/bitwig/controller/ableton/push")
copy_tree("src/main/java/de/mossgrabers/controller/ableton/push",
          "src/main/java/de/mossgrabers/controller/ableton/push")

# Apply source adaptations in a stable, reviewable subsystem order.
import runpy

PATCH_BATCHES = (
    "01_framework.py",
    "02_controls.py",
    "03_workflow.py",
    "04_navigation.py",
    "05_packaging.py",
    "06_menus.py",
    "07_palette.py",
)
patch_directory = root / "scripts/push1_build/patches"
patch_context = globals().copy()
for patch_batch in PATCH_BATCHES:
    patch_file = patch_directory / patch_batch
    if not patch_file.is_file():
        raise SystemExit(f"Missing source patch batch: {patch_file}")
    patch_context.update(runpy.run_path(str(patch_file), init_globals=patch_context))
