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

The Phase 2 Structural Limit Matching proposal in
[`ADR-0008-structural-limit-matching.md`](../adr/ADR-0008-structural-limit-matching.md)
places price-time traversal and in-memory order lifecycle mutation in
`OrderBook.matchLimit(Order)`. The proposed result is an ordered
`MatchFragment` list. `Trade`, `Execution`, trade identifiers, event sequence
assignment, and event publication remain MatchingEngine responsibilities and
are not part of the current Phase 2 implementation scope.
