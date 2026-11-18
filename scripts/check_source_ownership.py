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
"de/mossgrabers/controller/ableton/push/command/trigger/PushDeviceNavigation.java",
"de/mossgrabers/controller/ableton/push/controller/Push1DisplayGlyphs.java",
"de/mossgrabers/controller/ableton/push/controller/Push1DisplayLayout.java",
"de/mossgrabers/controller/ableton/push/controller/Push1DisplayProtocol.java",
"de/mossgrabers/controller/ableton/push/controller/Push1DisplayText.java",
"de/mossgrabers/controller/ableton/push/controller/Push1DisplayValueFormatter.java",
"de/mossgrabers/controller/ableton/push/controller/Push1FirmwareVersion.java",
"de/mossgrabers/controller/ableton/push/engine/Push1PadGrid.java",
"de/mossgrabers/controller/ableton/push/engine/sequencer/SequencerEngine.java",
"de/mossgrabers/controller/ableton/push/engine/sequencer/AbstractSequencerEngine.java",
"de/mossgrabers/controller/ableton/push/engine/sequencer/ClassicSequencerEngine.java",
"de/mossgrabers/controller/ableton/push/engine/sequencer/PolySequencerEngine.java",
"de/mossgrabers/controller/ableton/push/view/PolySequencerView.java",
"de/mossgrabers/controller/ableton/push/view/PushPolySequencerObservers.java",
"de/mossgrabers/controller/ableton/push/view/SequencerView.java",
}
PATCH_TARGET_ALLOWLIST={
"de/mossgrabers/framework/daw/midi/DeviceInquiry.java",
"de/mossgrabers/controller/ableton/push/controller/PushControlSurface.java",
"de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerMode.java",
"de/mossgrabers/controller/ableton/push/mode/NoteMode.java",
"de/mossgrabers/controller/ableton/push/mode/track/AbstractTrackMode.java",
"de/mossgrabers/controller/ableton/push/mode/device/DeviceParamsMode.java",
"de/mossgrabers/controller/ableton/push/mode/device/DeviceChainsMode.java",
"de/mossgrabers/controller/ableton/push/mode/ConfigurationMode.java",
"de/mossgrabers/framework/view/sequencer/AbstractSequencerView.java",
"de/mossgrabers/framework/view/sequencer/AbstractNoteSequencerView.java",
"de/mossgrabers/framework/view/sequencer/AbstractPolySequencerView.java",
"de/mossgrabers/framework/view/sequencer/AbstractDrumView.java",
"de/mossgrabers/controller/ableton/push/controller/PushColorManager.java",
"de/mossgrabers/framework/daw/DAWColor.java",
"de/mossgrabers/framework/view/ColorView.java",
"de/mossgrabers/framework/view/PianoViewHelper.java",
"de/mossgrabers/controller/ableton/push/controller/Push1Display.java",
"de/mossgrabers/controller/ableton/push/command/pitchbend/TouchstripCommand.java",
"de/mossgrabers/framework/controller/AbstractControllerSetup.java",
"de/mossgrabers/controller/ableton/push/mode/NoteRepeatMode.java",
"de/mossgrabers/controller/ableton/push/mode/SessionMode.java",
"de/mossgrabers/controller/ableton/push/mode/MarkerMode.java",
"de/mossgrabers/controller/ableton/push/mode/track/MasterMode.java",
"de/mossgrabers/controller/ableton/push/mode/track/AddTrackMode.java",
"de/mossgrabers/controller/ableton/push/view/DrumView.java",
"de/mossgrabers/framework/view/AbstractDrum64View.java",
"de/mossgrabers/controller/ableton/push/PushControllerSetup.java",
"de/mossgrabers/controller/ableton/push/mode/NoteViewSelectMode.java",
"de/mossgrabers/controller/ableton/push/mode/device/DeviceBrowserMode.java",
"de/mossgrabers/controller/ableton/push/mode/SessionViewSelectMode.java",
"de/mossgrabers/controller/ableton/push/mode/ScalesMode.java",
"de/mossgrabers/controller/ableton/push/mode/FixedMode.java",
"de/mossgrabers/controller/ableton/push/mode/QuantizeMode.java",
"de/mossgrabers/bitwig/controller/ableton/push/Push1ControllerExtensionDefinition.java",
"de/mossgrabers/bitwig/controller/ableton/push/Push2ControllerExtensionDefinition.java",
"de/mossgrabers/controller/ableton/push/controller/Push2Display.java",
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
  string_assignment=re.match(r'^\s*(\w+)\s*=\s*"src/main/java/([^"]+)"',line)
  if string_assignment:
   current[string_assignment.group(1)]=string_assignment.group(2)
  call=re.search(r"(?:replace_once|replace_menu_fragment|replace_java_method)\s*\(\s*(\w+)",line)
  if call and call.group(1) in current:
   targets.add(current[call.group(1)])
  for name,path in current.items():
   if re.search(rf"\b{re.escape(name)}\.(?:write_text|unlink)\s*\(",line):
    targets.add(path)
  removed=re.search(r'root\s*/\s*"src/main/java/([^"]+)"\)\.unlink\s*\(',line)
  if removed:
   targets.add(removed.group(1))
 return targets
def validate_forbidden_navigation(source):
 forbidden=("selectPreviousInOutItem","selectNextInOutItem","hasPreviousInOutItem","hasNextInOutItem","navigateFilterColumn","canNavigateFilterColumn","findNavigableFilterColumn")
 return ["PUSHdriver-specific Device/Browser navigation state or methods remain."] if any(token in source for token in forbidden) else []


def validate_override_inventory(actual):
 errors=[f"Unreviewed project override: {p}" for p in sorted(actual-OVERRIDE_ALLOWLIST)]
 errors += [f"Override inventory entry is missing: {p}" for p in sorted(OVERRIDE_ALLOWLIST-actual)]
 for p in sorted(actual):
  if "/mode/" in f"/{p}": errors.append(f"Project-owned mode implementation is forbidden: {p}")
  if Path(p).name in FORBIDDEN: errors.append(f"Project-owned Device/Browser implementation is forbidden: {p}")
 return errors
def duplicate_setting_ids(source):
 entries=re.findall(r'get(?:Boolean|Enum|Integer|Range|Color|Signal|String|Number)Setting\s*\(\s*"([^"]*)"\s*,\s*(CATEGORY_[A-Z0-9_]+)',source)
 counts={}
 for name,category in entries:
  key=(category,name)
  counts[key]=counts.get(key,0)+1
 return sorted(f"{category}::{name}" for (category,name),count in counts.items() if count>1)


def duplicate_button_ids(source):
 entries=re.findall(r'\b(PUSH_BUTTON_[A-Z0-9_]+)\s*=\s*(0x[0-9a-fA-F]+|\d+)\s*;',source)
 counts={}
 for name,value in entries:
  number=int(value,0)
  counts.setdefault(number,[]).append(name)
 return sorted(", ".join(names) for names in counts.values() if len(names)>1)


def method_inventory(source):
 return sorted(re.findall(r"^\s*(?:public|protected|private)\s+(?:(?:static|final|abstract|synchronized|native)\s+)*[\w<>, ?\[\].]+\s+(\w+)\s*\(",source,re.M))


def validate_mode_method_inventory(project_source, upstream_source, path):
 project_methods=method_inventory(project_source)
 upstream_methods=method_inventory(upstream_source)
 if project_methods==upstream_methods:
  return []
 return [f"Device mode method inventory differs from upstream in {path}: added={sorted(set(project_methods)-set(upstream_methods))}, removed={sorted(set(upstream_methods)-set(project_methods))}"]


def validate_in_out_contract(setup, left, right, control, virtual, hardware, runtime, navigation):
 errors=[]
 checks=[
  (re.search(r"PUSH_BUTTON_DEVICE_LEFT\s*=\s*62",control) is not None,"Physical left/CC62 must be In."),
  (re.search(r"PUSH_BUTTON_DEVICE_RIGHT\s*=\s*63",control) is not None,"Physical right/CC63 must be Out."),
  ('this.addButton (ButtonID.PAGE_LEFT, "In"' in setup,"CC62/Page Left label must be In."),
  ('this.addButton (ButtonID.PAGE_RIGHT, "Out"' in setup,"CC63/Page Right label must be Out."),
  ("PushDeviceNavigation.enterFirstSlotChain" in left and re.search(r"cursor\\.selectSlotChain\\s*\\(\\s*selected\\s*\\)",navigation) is not None,"In must enter the first available slot chain through the serialized navigator."),
  ("PushDeviceNavigation.exitParent" in right and re.search(r"cursor\\.selectParent\\s*\\(\\s*\\)",navigation) is not None,"Out must select the parent device through the serialized navigator."),
  ("public static final int IN = 62;" in virtual and "public static final int OUT = 63;" in virtual,"Virtual Push button IDs must match hardware CC62/CC63."),
  ("In (CC 62) enters the first available slot chain" in hardware and "Out (CC 63) returns to its parent device" in hardware,"Hardware documentation must define hierarchy navigation."),
  ('requireAction ("device.selectSlotChain:Chain 1"' in runtime and 'requireAction ("cursorDevice.selectParent"' in runtime and '"cursorDevice.selectNext"' in runtime and '"cursorDevice.selectPrevious"' in runtime,"Runtime test must verify hierarchy entry/exit and nested ROW1 device selection."),
  ("contentTypeIndex () == inOutContentBefore + 1" in runtime and "contentTypeIndex () == inOutContentBefore" in runtime,"Runtime test must retain Browser content-type direction."),
 ]
 for ok,message in checks:
  if not ok: errors.append(message)
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
  try:
   errors += validate_forbidden_navigation(own.read_text(encoding="utf-8"))
   if upstream.is_file() and java_types(own)!=java_types(upstream):
    errors.append(f"Override changes upstream type identity: {rel}")
  except ValueError as e: errors.append(str(e))
 prepare=(ROOT/"scripts/prepare_push1.py").read_text(encoding="utf-8")
 patch_directory=ROOT/"scripts/push1_build/patches"
 patch_files=sorted(patch_directory.glob("*.py"))
 patch_sources=[p.read_text(encoding="utf-8") for p in patch_files]
 if len(patch_files)!=7:
  errors.append(f"Expected seven ordered source patch batches, found {len(patch_files)}.")
 targets=set()
 for source in patch_sources:
  targets.update(patch_targets(source))
 if targets!=PATCH_TARGET_ALLOWLIST:
  errors.append(f"Upstream patch inventory changed. Unreviewed={sorted(targets-PATCH_TARGET_ALLOWLIST)}; stale={sorted(PATCH_TARGET_ALLOWLIST-targets)}")
 errors += validate_forbidden_navigation(prepare)
 for source in patch_sources:
  errors += validate_forbidden_navigation(source)
 if not ASSEMBLED.exists(): errors.append("Assembled Java tree missing; run scripts/prepare_push1.py first.")
 else:
  errors += validate_unique_types(list(ASSEMBLED.rglob("*.java")))
  setup_file=ASSEMBLED/"de/mossgrabers/controller/ableton/push/PushControllerSetup.java"
  left_file=ASSEMBLED/"de/mossgrabers/controller/ableton/push/command/trigger/PageLeftCommand.java"
  right_file=ASSEMBLED/"de/mossgrabers/controller/ableton/push/command/trigger/PageRightCommand.java"
  control_file=ASSEMBLED/"de/mossgrabers/controller/ableton/push/controller/PushControlSurface.java"
  if all(p.is_file() for p in (setup_file,left_file,right_file,control_file)):
   virtual_file=ROOT/"tests/java/VirtualPush1Device.java"
   hardware_file=ROOT/"docs/PUSH1-HARDWARE.md"
   runtime_file=ROOT/"tests/java/VirtualPush1RuntimeTest.java"
   navigation_file=ASSEMBLED/"de/mossgrabers/controller/ableton/push/command/trigger/PushDeviceNavigation.java"
   errors += validate_in_out_contract(
    setup_file.read_text(encoding="utf-8"),left_file.read_text(encoding="utf-8"),
    right_file.read_text(encoding="utf-8"),control_file.read_text(encoding="utf-8"),
    virtual_file.read_text(encoding="utf-8"),hardware_file.read_text(encoding="utf-8"),
    runtime_file.read_text(encoding="utf-8"),navigation_file.read_text(encoding="utf-8"))
  config_file=ASSEMBLED/"de/mossgrabers/controller/ableton/push/PushConfiguration.java"
  surface_file=ASSEMBLED/"de/mossgrabers/controller/ableton/push/controller/PushControlSurface.java"
  if config_file.is_file():
   config_source=config_file.read_text(encoding="utf-8")
   duplicate_settings=duplicate_setting_ids(config_source)
   if duplicate_settings: errors.append(f"Duplicate settings identifiers: {duplicate_settings}")
   if not re.search(r"if\s*\(this\.isPush2\)\s*this\.activatePush2PadSettings\s*\(globalSettings\)\s*;\s*else\s*this\.activatePush1PadSettings\s*\(globalSettings\)\s*;",config_source):
    errors.append("Push 1 and Push 2 pad settings are no longer mutually exclusive.")
  if surface_file.is_file():
   duplicate_buttons=duplicate_button_ids(surface_file.read_text(encoding="utf-8"))
   if duplicate_buttons: errors.append(f"Duplicate Push button MIDI identifiers: {duplicate_buttons}")
  built=ASSEMBLED/"de/mossgrabers/controller/ableton/push/mode/device"
  upstream=UPSTREAM/"src/main/java/de/mossgrabers/controller/ableton/push/mode/device"
  if built.exists() and upstream.exists():
   expected={p.name for p in upstream.glob("*.java")}; actual={p.name for p in built.glob("*.java")}
   if actual!=expected: errors.append(f"Device class inventory differs from upstream: extra={sorted(actual-expected)}, missing={sorted(expected-actual)}")
   for name in sorted(actual & expected):
    project_file=built/name; upstream_file=upstream/name
    errors += validate_mode_method_inventory(project_file.read_text(encoding="utf-8"),upstream_file.read_text(encoding="utf-8"),name)
 if errors:
  for e in errors: print("SOURCE OWNERSHIP ERROR: "+e,file=sys.stderr)
  return 1
 print("Source ownership passed: no project Device-mode implementation; overrides and patches inventoried; assembled public Java types unique.")
 return 0
if __name__=="__main__": raise SystemExit(main())
