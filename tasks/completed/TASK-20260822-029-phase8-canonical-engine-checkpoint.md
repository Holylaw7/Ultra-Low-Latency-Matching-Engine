# Task Plan — TASK-20260822-029

| Field | Value |
| --- | --- |
| Task | `TASK-20260822-029` / Canonical Engine Checkpoint Foundation |
| Phase / ADR / Blueprint | Phase 8 / ADR-0016 / `PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md` |
| Status | Completed / Evidence Gate PASS |
| Depends on | Human Phase 8 Blueprint Approval — satisfied |
| Manual Gate | No after Blueprint approval; Exception Gate remained clear |
| Planned report | `tasks/reports/PHASE-8-task-029.md` |

## Decision

ADR-0016 D4 and the Phase 8 Blueprint authorize only canonical checkpoint
export/restore in the listed OrderBook/MatchingEngine files. Snapshot
serialization and recovery orchestration remain later Tasks.

## Goal

Introduce immutable, canonical OrderBook and MatchingEngine checkpoint values,
deterministic export and fail-closed restore while preserving current matching
outcomes and price-time priority.

## Authorized Scope After Approval

New checkpoint types under `orderbook` and `engine`, focused tests, and narrowly
additive changes to:

```text
OrderBook.java
SideBook.java
PriceLevel.java
OrderNode.java
MatchingEngine.java
```

Restore must use existing Domain construction/lifecycle behavior. `Order.java`
and all Domain value semantics remain unchanged.

## Non-Goals

No Snapshot file codec/store, WAL scan, live startup, network, hot checkpoint,
matching algorithm change, new dependency or performance optimization.

## Acceptance Criteria

- [x] Canonical export orders BUY prices descending and SELL prices ascending,
  with FIFO preserved inside each price.
- [x] Each active order retains OrderId, Side, Price, original/remaining
  Quantity and original Command Sequence.
- [x] Engine checkpoint retains last applied Command Sequence, next TradeId and
  next EventSequence.
- [x] Export/restore/export is structurally equal and produces an equal
  canonical checkpoint digest covering sequence/counters and active state.
- [x] Fixed public probe after restore produces equal ordered EngineResult,
  TradeId and EventSequence.
- [x] Duplicate IDs, invalid values/order, counter mismatch or malformed state
  fails before mutating a live engine.
- [x] Existing constructors and Phase 2-7 behavior remain compatible.

## Evidence Gate

Focused checkpoint tests, full `mvn verify`, Checkstyle 0, `git diff --check`,
approved-file audit, no unlisted frozen changes, logical commit, normal push and
exact-SHA CI PASS. Evidence passed at `66fc9d2` / CI `32577713667`; synchronize
this plan and `PHASE-8-task-029.md`, then archive this task before TASK-030.

## Exception Gate

Stop if implementation requires modifying Domain values, matching semantics,
an unlisted existing production file, a production-only test seam, reflection,
sleep-based correctness, a new dependency or weaker checkpoint validation.
