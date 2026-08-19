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
