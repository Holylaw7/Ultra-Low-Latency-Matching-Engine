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

The planned baseline supports:

- Limit orders
- Market orders
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
`2026-08-19`; measurement isolation has completed and is pending Steady-State
Evidence Review. Optimization is governed by
[`ADR-0010-optimization-decision-after-profiling.md`](../adr/ADR-0010-optimization-decision-after-profiling.md);
production optimization and the future MatchingEngine stage remain
unauthorized.
