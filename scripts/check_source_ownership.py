#!/usr/bin/env python3
"""Enforce one reviewed owner for each Push mode and assembled Java type."""
from pathlib import Path
import re, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
UPSTREAM=ROOT/"upstream"
ASSEMBLED=ROOT/"src/main/java"
EXPECTED="013620c1330cdcf896e96ef8fdc8a2123e4fce6a"
OVERRIDE_ALLOWLIST={
"de/mossgrabers/controller/ableton/push/command/trigger/PageLeftCommand.java",
"de/mossgrabers/controller/ableton/push/command/trigger/PageRightCommand.java",
"de/mossgrabers/controller/ableton/push/command/trigger/PushCursorCommand.java",
"de/mossgrabers/controller/ableton/push/command/trigger/PushNewCommand.java",
"de/mossgrabers/controller/ableton/push/controller/Push1DisplayGlyphs.java",
"de/mossgrabers/controller/ableton/push/controller/Push1DisplayLayout.java",
"de/mossgrabers/controller/ableton/push/controller/Push1DisplayProtocol.java",
"de/mossgrabers/controller/ableton/push/controller/Push1DisplayText.java",
"de/mossgrabers/controller/ableton/push/controller/Push1DisplayValueFormatter.java",
"de/mossgrabers/controller/ableton/push/controller/Push1FirmwareVersion.java",
"de/mossgrabers/controller/ableton/push/view/PolySequencerView.java",
"de/mossgrabers/controller/ableton/push/view/SequencerView.java",
}
PATCH_TARGET_ALLOWLIST={
"de/mossgrabers/framework/daw/midi/DeviceInquiry.java",
"de/mossgrabers/controller/ableton/push/controller/PushControlSurface.java",
"de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerMode.java",
"de/mossgrabers/controller/ableton/push/mode/ConfigurationMode.java",
"de/mossgrabers/framework/view/sequencer/AbstractSequencerView.java",
"de/mossgrabers/framework/view/sequencer/AbstractDrumView.java",
"de/mossgrabers/controller/ableton/push/controller/PushColorManager.java",
"de/mossgrabers/framework/view/PianoViewHelper.java",
"de/mossgrabers/controller/ableton/push/controller/Push1Display.java",
"de/mossgrabers/controller/ableton/push/command/pitchbend/TouchstripCommand.java",
"de/mossgrabers/framework/controller/AbstractControllerSetup.java",
"de/mossgrabers/controller/ableton/push/mode/NoteRepeatMode.java",
"de/mossgrabers/controller/ableton/push/mode/SessionMode.java",
"de/mossgrabers/controller/ableton/push/mode/MarkerMode.java",
"de/mossgrabers/controller/ableton/push/mode/track/MasterMode.java",
"de/mossgrabers/controller/ableton/push/view/DrumView.java",
"de/mossgrabers/framework/view/AbstractDrum64View.java",
"de/mossgrabers/controller/ableton/push/PushControllerSetup.java",
"de/mossgrabers/controller/ableton/push/PushConfiguration.java",
"de/mossgrabers/controller/ableton/push/mode/NoteViewSelectMode.java",
"de/mossgrabers/controller/ableton/push/command/trigger/LayoutCommand.java",
"de/mossgrabers/framework/view/Views.java",
"de/mossgrabers/controller/ableton/push/mode/device/DeviceBrowserMode.java",
"de/mossgrabers/bitwig/controller/ableton/push/Push1ControllerExtensionDefinition.java",
"de/mossgrabers/bitwig/framework/daw/HostImpl.java",
"de/mossgrabers/controller/ableton/push/PushControllerDefinition.java",
"de/mossgrabers/controller/ableton/push/controller/PushUsbDisplay.java",
}
FORBIDDEN={"DeviceParamsMode.java","DeviceChainsMode.java","DeviceBrowserMode.java"}
def java_types(path):
 s=path.read_text(encoding="utf-8")
 p=re.search(r"^package\s+([\w.]+)\s*;",s,re.M)
 if not p: raise ValueError(f"Java package declaration missing: {path}")
 return {f"{p.group(1)}.{n}" for n in re.findall(r"^public\s+(?:(?:abstract|final|sealed)\s+)*(?:class|interface|enum|record)\s+(\w+)",s,re.M)}
def patch_targets(source):
 targets=set()
 current={}
 for line in source.splitlines():
  assignment=re.match(r'^\s*(\w+)\s*=\s*root\s*/\s*"src/main/java/([^"]+)"',line)
  if assignment:
   current[assignment.group(1)]=assignment.group(2)
  call=re.search(r"replace_once\s*\(\s*(\w+)",line)
  if call and call.group(1) in current:
   targets.add(current[call.group(1)])
  for name,path in current.items():
   if re.search(rf"\b{re.escape(name)}\.(?:write_text|unlink)\s*\(",line):
    targets.add(path)
  removed=re.search(r'root\s*/\s*"src/main/java/([^"]+)"\)\.unlink\s*\(',line)
  if removed:
   targets.add(removed.group(1))
 return targets
def validate_override_inventory(actual):
 errors=[f"Unreviewed project override: {p}" for p in sorted(actual-OVERRIDE_ALLOWLIST)]
 errors += [f"Override inventory entry is missing: {p}" for p in sorted(OVERRIDE_ALLOWLIST-actual)]
 for p in sorted(actual):
  if "/mode/" in f"/{p}": errors.append(f"Project-owned mode implementation is forbidden: {p}")
  if Path(p).name in FORBIDDEN: errors.append(f"Project-owned Device/Browser implementation is forbidden: {p}")
 return errors
def validate_unique_types(files):
 owners={}; errors=[]
 for path in files:
  try: names=java_types(path)
  except ValueError as e: errors.append(str(e)); continue
  for name in names:
   if name in owners: errors.append(f"Duplicate Java type {name}: {owners[name]} and {path}")
   else: owners[name]=path
 return errors
def main():
 errors=[]
 if not (UPSTREAM/".git").exists(): errors.append("Pinned upstream submodule missing; initialize recursively.")
 else:
  actual=subprocess.run(["git","-C",str(UPSTREAM),"rev-parse","HEAD"],check=True,capture_output=True,text=True).stdout.strip()
  if actual!=EXPECTED: errors.append(f"Unexpected upstream revision {actual}; expected {EXPECTED}.")
 root=ROOT/"overrides/java"
 actual_overrides={p.relative_to(root).as_posix() for p in root.rglob("*.java")} if root.exists() else set()
 errors += validate_override_inventory(actual_overrides)
 for rel in sorted(actual_overrides):
  own=root/rel; upstream=UPSTREAM/"src/main/java"/rel
  if upstream.is_file():
   try:
    if java_types(own)!=java_types(upstream): errors.append(f"Override changes upstream type identity: {rel}")
   except ValueError as e: errors.append(str(e))
 prepare=(ROOT/"scripts/prepare_push1.py").read_text(encoding="utf-8")
 targets=patch_targets(prepare)
 if targets!=PATCH_TARGET_ALLOWLIST:
  errors.append(f"Upstream patch inventory changed. Unreviewed={sorted(targets-PATCH_TARGET_ALLOWLIST)}; stale={sorted(PATCH_TARGET_ALLOWLIST-targets)}")
 if any(x in prepare for x in ("selectPreviousInOutItem","selectNextInOutItem","hasPreviousInOutItem","hasNextInOutItem")):
  errors.append("PUSHdriver-specific DeviceParamsMode navigation code remains.")
 if not ASSEMBLED.exists(): errors.append("Assembled Java tree missing; run scripts/prepare_push1.py first.")
 else:
  errors += validate_unique_types(list(ASSEMBLED.rglob("*.java")))
  built=ASSEMBLED/"de/mossgrabers/controller/ableton/push/mode/device"
  upstream=UPSTREAM/"src/main/java/de/mossgrabers/controller/ableton/push/mode/device"
  if built.exists() and upstream.exists():
   expected={p.name for p in upstream.glob("*.java")}; actual={p.name for p in built.glob("*.java")}
   if actual!=expected: errors.append(f"Device class inventory differs from upstream: extra={sorted(actual-expected)}, missing={sorted(expected-actual)}")
 if errors:
  for e in errors: print("SOURCE OWNERSHIP ERROR: "+e,file=sys.stderr)
  return 1
 print("Source ownership passed: no project Device-mode implementation; overrides and patches inventoried; assembled public Java types unique.")
 return 0
if __name__=="__main__": raise SystemExit(main())
