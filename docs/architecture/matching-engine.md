# Matching Engine Architecture

## State Machine

```text
Input Event
    -> Validate
    -> Assign / Verify Sequence
    -> Match Against Opposite Side
    -> Rest Remaining Quantity
    -> Emit Execution and Trade Events
    -> Update Order State
```

## Supported Semantics

The long-term planned system supports:

- Limit orders
- Market orders (future policy; not part of the current Phase 3 proposal)
- Price-time priority
- Partial fills
- Full fills
- Cancellation
- Deterministic trade generation

## Critical-Path Constraints

The matching path must not perform:

- Network I/O
- Blocking I/O
- Database or MQ access
- Logging
- JSON serialization
- Unbounded allocation

## Determinism

The same ordered input event sequence must produce the same:

- Executions
- Trades
- Order states
- Final order book
- State hash

## OrderBook Boundary

The approved Phase 2 Structural Limit Matching decision in
[`ADR-0008-structural-limit-matching.md`](../adr/ADR-0008-structural-limit-matching.md)
places price-time traversal and in-memory order lifecycle mutation in
`OrderBook.matchLimit(Order)`. The result is an ordered
`MatchFragment` list. `Trade`, `Execution`, trade identifiers, event sequence
assignment, and event publication remain MatchingEngine responsibilities and
are not part of the Phase 2 Structural Limit Matching implementation scope.
The structural implementation and correctness verification are complete. The
OrderBook baseline benchmark and documentation synchronization were approved
on `2026-08-19`. Profiling execution was completed under
[`ADR-0009-performance-profiling-evidence.md`](../adr/ADR-0009-performance-profiling-evidence.md)
and approved as evidence collection on `2026-08-19`. ADR-0010 was approved on
`2026-08-19`; measurement isolation, evidence review and Phase 2 Final Closure
have completed. Optimization is governed by
[`ADR-0010-optimization-decision-after-profiling.md`](../adr/ADR-0010-optimization-decision-after-profiling.md);
production optimization remains unauthorized.

## Phase 3 Orchestration Gate

Phase 2 is closed and frozen at `v0.1.0-engineering-baseline`. The first Phase
3 architecture proposal is
[`ADR-0011-matching-engine-orchestration-model.md`](../adr/ADR-0011-matching-engine-orchestration-model.md).
Its current status is `Approved`. D1-D7 are finalized. The D3 condition was
satisfied by the approved sequence semantic revision in
[`ADR-0005-domain-model-and-correctness-baseline.md`](../adr/ADR-0005-domain-model-and-correctness-baseline.md).

The proposal recommends a synchronous, single-owner MatchingEngine boundary:

```text
Sequenced Command
    -> MatchingEngine
    -> OrderBook
    -> ordered MatchFragments
    -> immutable Trade/Execution result
```

It separates input command sequence, TradeId and output event sequence
ownership, and treats a future command WAL as the canonical replay input.
Disruptor/Actor scheduling, market-order policy, publication, networking,
WAL implementation, snapshot, recovery and optimization remain deferred.

`Sequence` is reserved for commands. `Trade.sequence` has been migrated to
`Trade.eventSequence`; the value type validates and orders output sequence
values while allocation remains reserved for the future MatchingEngine.
[`TASK-20260820-008-phase3-matching-engine-implementation.md`](../../tasks/active/TASK-20260820-008-phase3-matching-engine-implementation.md)
is in Stage 1 completion review. Immutable command/result boundary types have
been added, but no MatchingEngine, OrderBook integration, Trade generation or
EventSequence allocation exists. MatchingEngine Core and Determinism
Verification remain separately gated. OrderBook is an external frozen
dependency and no OrderBook file or API may change.
