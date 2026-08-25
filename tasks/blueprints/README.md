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
  Standard CI `32718394177` and Quick Lane `32718394269`; TASK-042 is complete
  at `1eba2c5` with Standard CI `32720292382` and Quick Lane `32720292393` PASS.
  TASK-043 is complete at `247d526` with Standard CI `32724123762` and
  Qualification Quick Lane `32724123745` PASS. TASK-044 is complete at
  `c3f0883` with Standard CI `32726203105` and Qualification Quick Lane
  `32726203076` PASS. TASK-045 completed at `f024aef` with Standard CI
  `32728038236` and Qualification Quick Lane `32728038263` PASS. TASK-046
  pre-campaign passed at `0a96593` with Standard `32730760419`, Quick Lane
  `32730760501` and lifecycle 30/30. Human approved exactly two assembled
  Full Runs; both passed and the immutable campaign summary records `2/2`
  qualifying runs and `campaign.result=true`. Qualification-only
  characterization then passed 30/30 empty-WAL and 30/30 Snapshot-tail
  lifecycle samples plus two fixed 10-minute management trials. The preserved
  v3 characterization summary is `6204f190...`, with 62 JFR/resource files, 62
  non-zero allocation summaries and a recursively verified 919-entry sidecar.
  Benchmark review found that the v3 trial timer included one-time Protocol
  connection setup. The qualification-only correction `7566814` passed
  Standard CI `32811578976` and Quick Lane `32811578978`; v4 characterization
  then passed 30/30 + 30/30 lifecycle samples and both fixed 10-minute trials.
  Its summary is `60608026...`, manifest `120fe39d...` and sidecar
  `da3cbb89...` (903 entries); v3 remains preserved as non-final. Fixed
  technical docs/evidence input `dfe1f7d` passed Standard CI `32813393216` and
  Quick Lane `32813393127`; docs-only status validation `b7530f6` passed
  Standard CI `32813640675` and Quick Lane `32813640754`. Final read-only
  docs/evidence sync validation `eb9a4ab` passed Standard CI `32814053468` and
  Quick Lane `32814053459`; this is external validation only. Final status
  synchronization `a6bc574` passed Standard CI `32814596881` and Quick Lane
  `32814596914`; this is also external validation only. Current docs validation
  `84b3546` passed Standard CI `32814830192` and Quick Lane `32814830164`; this
  is external validation only. Final read-only
  Evidence Gate is PASS; Sol High Evidence/Closure Review remains pending. No new 60-minute Full Run was
  authorized; merge, candidate tagging and Product Release remain gated.
