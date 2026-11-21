# PUSHdriver source patch batches

`prepare_push1.py` verifies the pinned, clean DrivenByMoss submodule, reconstructs the upstream source tree, then executes these patch batches in this fixed order:

1. `01_framework.py` — firmware inquiry, universal button edge/long-press safety, Push surface, sequencer foundations, display protocol.
2. `02_controls.py` — ribbon, note-repeat and control feedback.
3. `03_workflow.py` — Session/Marker/Master workflows, note sequencing, setup integration.
4. `04_navigation.py` — Device and Browser navigation adaptation.
5. `05_packaging.py` — Push 1 extension entry point and dependency/package trimming.
6. `06_menus.py` — Device parameter layout and physical button-row/LCD alignment.
7. `07_palette.py` — hardware color palette and compact Push display labels.

The batches execute in a shared build namespace because later patches intentionally reuse path handles and intermediate source values from earlier stages. The source-ownership checker inventories patch targets across every batch; do not add an upstream patch without updating its explicit allowlist and tests.
