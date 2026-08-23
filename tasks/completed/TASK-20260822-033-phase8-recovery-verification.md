# Task Plan — TASK-20260822-033

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-033` / Crash, Corruption and Determinism Verification |
| Phase / ADR / Blueprint | Phase 8 / ADR-0016 / `PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md` |
| Status | Completed — Evidence Gate PASS / Archived |
| Depends on | TASK-032 Evidence Gate PASS |
| Manual Gate | No after Blueprint approval; Exception Gate remains active |
| Planned report | `tasks/reports/PHASE-8-task-033.md` |

## Goal

Produce dynamic, public-contract evidence for Snapshot/WAL crash windows,
strict corruption behavior, repeated restart determinism and listener-last
failure semantics without expanding production design.

## Authorized Scope After Approval

Phase 8 test fixtures, recovery tests and evidence report only. Production code
is not authorized unless a verified defect remains within an already approved
file boundary; any broader need triggers the Exception Gate.

## Verification Matrix

- [x] before append, partial final append, durable-before-publish and
  apply-before-response windows;
- [x] Snapshot temp write, successful force/read-back/atomic move and
  Snapshot+tail; force/move fault-injection windows remain an explicit
  known limitation because no production-only seam is authorized;
- [x] hard WAL corruption, sequence gap, Snapshot checksum/digest/prefix
  mismatch, incompatible/newer Snapshot and duplicate/non-canonical state;
- [x] repeated pure-WAL and Snapshot-tail restart cycles;
- [x] equal complete checkpoint digest and fixed public probe, plus equal
  ordered results, TradeId and EventSequence for the common `N+1..M` transcript
  suffix;
- [x] exact first live command after recovery;
- [x] no listener bind/admission on recovery or handoff failure;
- [x] no automatic fallback, rollback, retry, reconnect or client response for
  recovery replay.

## Test Constraints

Use deterministic barriers/futures and public or Blueprint-approved composition
boundaries. No reflection, `Thread.sleep` correctness oracle, production-only
test seam or weakened assertion.

## Evidence Gate

Focused crash/recovery suites, repeated matrix, full `mvn verify`, Checkstyle 0,
`git diff --check`, approved/frozen-path audit, read-only verifier PASS, logical
commit, normal push and exact-SHA CI PASS.

Evidence checkpoint: `eff5955` / CI `32614610701` PASS. Four focused
`Phase8RecoveryVerificationTest` tests and the existing corruption, recovery,
handoff and lease suites are green. Dynamic injection of `force(true)` failure
before move and completed-move failure is not claimed; it remains a documented
limitation because adding a production-only seam is prohibited.

## Exception Gate

Stop if dynamic proof requires any unlisted API, recovery semantic expansion,
corruption fallback, hot Snapshot, new dependency or reduced acceptance rule.
