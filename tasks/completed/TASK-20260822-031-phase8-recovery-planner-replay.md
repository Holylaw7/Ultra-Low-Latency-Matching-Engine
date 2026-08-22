# Task Plan — TASK-20260822-031

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-031` / Recovery Planner and Replay Executor |
| Phase / ADR / Blueprint | Phase 8 / ADR-0016 / `PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md` |
| Status | Completed — Evidence Gate PASS |
| Depends on | TASK-030 Evidence Gate PASS |
| Manual Gate | No after Blueprint approval; Exception Gate remains active |
| Planned report | `tasks/reports/PHASE-8-task-031.md` |

## Goal

Implement explicit `PURE_WAL` and `SNAPSHOT_THEN_WAL` recovery planning,
strict Snapshot/WAL binding, restored-engine construction and WAL-tail replay.

## Authorized Scope After Approval

New production/tests under:

```text
src/main/java/com/ultralatency/matching/recovery/online/**
src/test/java/com/ultralatency/matching/recovery/online/**
```

The immutable Recovery result contains the recovered engine, strict WAL end,
next Command Sequence and evidence metadata; it does not start a live service.

## Non-Goals

No network listener, Pipeline start, coordinator, hot Snapshot, WAL deletion,
automatic corrupt-Snapshot fallback, client response emission or Product
Release behavior.

## Acceptance Criteria

- [x] Empty WAL produces a genesis recovery result.
- [x] `PURE_WAL` strictly replays a non-empty WAL from Sequence 1.
- [x] `SNAPSHOT_THEN_WAL` validates the selected latest Snapshot, binds its WAL
  prefix and replays only `N+1..WAL end`.
- [x] Snapshot at WAL end applies no duplicate commands.
- [x] Only the existing explicit final-torn-tail repair is permitted; TASK-031
  does not add, invoke or perform any WAL mutation or truncation.
- [x] Missing/corrupt/incompatible/mismatched/newer Snapshot, hard WAL
  corruption, poison command or sequence gap fails closed.
- [x] Both modes converge on equal complete canonical checkpoint digest and
  fixed public probe results.
- [x] For Snapshot N and WAL end M, ordered results, TradeId and EventSequence
  are equal for the common replay suffix `N+1..M`; Snapshot restore does not
  reconstruct or emit prefix results `1..N`.
- [x] Recovery replay results remain internal evidence, not client events.

## Evidence Gate

Focused recovery matrix, full `mvn verify`, Checkstyle 0, `git diff --check`,
approved/frozen-path audit, logical commit, normal push and exact-SHA CI PASS.

Evidence: focused `RecoveryPlannerTest` 8/8, full `mvn verify` 183 tests,
Checkstyle 0, frozen-path audit PASS, commit `eaed8b8` and exact-SHA CI
`32580018903` PASS. The tracked tree is clean; pre-existing `.vscode/` remains
untouched and untracked.

## Exception Gate

Stop for WAL semantic changes, unapproved truncation, Snapshot fallback,
counter synthesis without validated evidence, network/live runtime work, new
dependency or weakened corruption/equivalence rules.
