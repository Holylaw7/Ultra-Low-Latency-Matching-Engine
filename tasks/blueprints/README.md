# Phase Blueprints

This directory contains one complete architecture and execution package for
each new multi-task Phase.

Create Blueprints from [`../PHASE_BLUEPRINT_TEMPLATE.md`](../PHASE_BLUEPRINT_TEMPLATE.md).
All required ADR drafts and Task plans must exist before Human Phase Blueprint
Review. One recorded Blueprint approval authorizes only the ADR decisions,
Tasks, stages and boundaries explicitly enumerated in that Blueprint.

Blueprint-authorized sub-stages continue after automated evidence gates pass.
Stop for an explicit manual gate, an Exception Gate or Phase Closure.

Historical Phases completed before Phase Blueprint Mode are not retrofitted.

## Current Blueprints

- [`PHASE-4-event-pipeline-blueprint.md`](PHASE-4-event-pipeline-blueprint.md)
  — Completed / Approved / frozen at `v0.3.0-engineering-baseline`.
- [`PHASE-5-command-wal-and-replay-blueprint.md`](PHASE-5-command-wal-and-replay-blueprint.md)
  — Completed / Approved / frozen at `v0.4.0-engineering-baseline`.
- [`PHASE-6-network-protocol-blueprint.md`](PHASE-6-network-protocol-blueprint.md)
  — Completed / Approved / frozen at `v0.5.0-engineering-baseline`.
- [`PHASE-7-live-durable-command-pipeline-blueprint.md`](PHASE-7-live-durable-command-pipeline-blueprint.md)
  — Completed / Approved / frozen at `v0.6.0-engineering-baseline`.
- [`PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md`](PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md)
  — Approved; TASK-029 through TASK-034 completed / Evidence Gate PASS. Technical
  Closure input is `c59d7c0` / CI `32616802595` with 195 tests, 0 failures and
  Checkstyle 0. Human Phase 8 Closure Approval is complete; merge `87abbc1` /
  Master CI `32622722649` PASS and baseline tag `v0.7.0-engineering-baseline` /
  Tag CI `32622757607` PASS. Phase 8 is frozen; Phase 9 and Product Release
  remain unauthorized.
