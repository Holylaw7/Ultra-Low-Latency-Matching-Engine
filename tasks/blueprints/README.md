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
  Tag CI `32622757607` PASS. Phase 8 is frozen.
- [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](PHASE-9-system-qualification-and-long-run-reliability-blueprint.md)
  — Approved; TASK-035 through TASK-040 authorized in strict dependency order.
  TASK-036 Evidence Gate PASS at `f90e42c` / standard CI `32627744868` and Quick Lane `32627744878`; TASK-037 Limited Qualification-Only Remediation Evidence Gate PASS at `c420313` / standard CI `32645549709` and Quick Lane `32645549694`. The remediation adds bounded streaming evidence and a separately versioned `MEMORY_STEADY_STATE_V1` lane. The separately approved v2 Full Campaign is now PASS: two immutable runs,
  44 cumulative natural post-GC samples, and campaign summary SHA-256
  `5bf1b84b30226807d79f5a0a4950ae649c3a72a860d6d6b13edd9fa715e24112`.
  TASK-037 Human Evidence / Closure Approval is complete; TASK-038 Evidence
  Gate is PASS after the approved 20/10 restart/termination campaign and
  read-only review. TASK-039 implementation, full JMH matrix and
  representative GC/JFR profile are complete; verifier, benchmark-reviewer and
  docs-auditor have reported PASS; technical checkpoint `d003266` passed
  Standard CI `32707393196` and Quick Lane `32707393200`. TASK-039 is archived
  and TASK-040 Evidence Gate is PASS with current Closure Input `8e5d39d` /
  Standard CI `32709188522` / Quick Lane `32709188327`. The Closure Proposal
  Sol High review and Human Phase 9 Closure Approval are complete. Phase 9 is
  frozen at `v0.8.0-engineering-baseline` from merge `ef73f60`; Master CI
  `32711512036` and Tag CI `32711649980` passed. Phase 10 implementation and
  Product Release remain unauthorized.
- [`PHASE-10-release-candidate-runtime-assembly-blueprint.md`](PHASE-10-release-candidate-runtime-assembly-blueprint.md)
  — Approved by Human Blueprint Approval on 2026-08-24. ADR-0018 D1-D16 and
  TASK-041 through TASK-046 define release-candidate runtime assembly,
  configuration, lifecycle, operations, packaging and qualification. TASK-041
  contracts are implemented and its Evidence Gate passed at `cc9a957` with
  Standard CI `32718394177` and Quick Lane `32718394269`; TASK-042 is next and
  later tasks remain dependency locked. Full Campaign,
  merge, candidate tagging and Product Release remain separately gated.
