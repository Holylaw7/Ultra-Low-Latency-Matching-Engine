# Task Plan — TASK-20260822-031

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-031` / Recovery Planner and Replay Executor |
| Phase / ADR / Blueprint | Phase 8 / ADR-0016 / `PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md` |
| Status | Proposed — Not Authorized |
| Depends on | TASK-030 Evidence Gate PASS after Blueprint approval |
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

- [ ] Empty WAL produces a genesis recovery result.
- [ ] `PURE_WAL` strictly replays a non-empty WAL from Sequence 1.
- [ ] `SNAPSHOT_THEN_WAL` validates the selected latest Snapshot, binds its WAL
  prefix and replays only `N+1..WAL end`.
- [ ] Snapshot at WAL end applies no duplicate commands.
- [ ] Only the existing explicit final-torn-tail repair is permitted.
- [ ] Missing/corrupt/incompatible/mismatched/newer Snapshot, hard WAL
  corruption, poison command or sequence gap fails closed.
- [ ] Both modes converge on equal complete canonical checkpoint digest and
  fixed public probe results.
- [ ] For Snapshot N and WAL end M, ordered results, TradeId and EventSequence
  are equal for the common replay suffix `N+1..M`; Snapshot restore does not
  reconstruct or emit prefix results `1..N`.
- [ ] Recovery replay results remain internal evidence, not client events.

## Evidence Gate

Focused recovery matrix, full `mvn verify`, Checkstyle 0, `git diff --check`,
approved/frozen-path audit, logical commit, normal push and exact-SHA CI PASS.

## Exception Gate

Stop for WAL semantic changes, unapproved truncation, Snapshot fallback,
counter synthesis without validated evidence, network/live runtime work, new
dependency or weakened corruption/equivalence rules.
