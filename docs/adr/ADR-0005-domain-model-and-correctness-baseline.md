# ADR-0005: Domain Model and Correctness Baseline

## Status

Accepted with constraints

## Context

The matching engine needs a stable domain model before implementing an
order book, matching algorithm, event pipeline, or persistence layer.
Numeric representation, order lifecycle, and execution semantics must be
explicit so that later components cannot introduce incompatible meanings.

The domain model must also remain deterministic. The same input fields must
produce the same domain object, state transition, trade, and execution result.

## Decision

### Integer Domain Units

- `OrderId`, `TradeId`, `Price`, `Quantity`, and `Sequence` use positive
  `long`-backed value types.
- `Price` stores an encoded integer tick. Tick scale and external price
  conversion are responsibilities of a future protocol or application
  boundary.
- `Quantity` stores the smallest accepted trading unit.
- Floating-point values and `BigDecimal` are not part of the matching domain
  model.
- `Sequence` is a logical input-event sequence. It must not be generated from
  wall-clock time, random values, or thread scheduling.

### Order Lifecycle

`OrderStatus` is limited to:

```text
NEW
PARTIALLY_FILLED
FILLED
CANCELED
```

Allowed transitions:

```text
NEW -> PARTIALLY_FILLED
NEW -> FILLED
NEW -> CANCELED
PARTIALLY_FILLED -> FILLED
PARTIALLY_FILLED -> CANCELED
```

Cancellation is idempotent for an already canceled order and does not
constitute a new state transition. A filled order cannot be canceled, and a
terminal order cannot receive another execution.

### Order Representation

- A limit order carries a `Price`.
- A market order has no limit price.
- The order identity and input sequence remain stable.
- Remaining quantity and status can change only through controlled domain
  methods.
- `Order` equality is based on stable `OrderId`; order-id uniqueness is a
  responsibility of the future order-book owner.

### Trade and Execution

- `Trade` represents one match between a maker order and a taker order.
- `Execution` represents one order's result within a trade.
- One trade is expected to produce two executions when consumed by the
  matching layer.
- Both are deterministic value objects and receive all identifiers from the
  caller.
- They do not read system time or generate random identifiers.

### Scope Boundary

This decision does not introduce or reserve implementations for:

- `OrderBook`
- `MatchingEngine`
- Netty or other network adapters
- Disruptor or other event pipelines
- WAL, Snapshot, or Recovery
- Performance-specific memory layouts or concurrency mechanisms

## Consequences

Positive:

- Integer comparison and arithmetic avoid floating-point rounding in the
  matching core.
- Domain invariants fail at construction or controlled state-transition
  boundaries.
- State transitions and execution results can be tested without clocks,
  randomness, threads, or infrastructure.
- Later order-book and matching implementations receive stable semantics.

Trade-offs:

- Protocol and application layers must define and validate the external tick
  scale and quantity unit before constructing domain values.
- `long` range and arithmetic overflow must be handled explicitly.
- Order-id uniqueness and global sequence monotonicity require an owning
  application component; a single value object cannot enforce either global
  property.
- The baseline favors explainability and correctness over final allocation or
  cache-layout optimization.

## Required Tests

- Reject non-positive identifiers, prices, quantities, and sequences.
- Verify integer value comparison and sequence progression.
- Verify limit and market order construction.
- Verify partial fill, full fill, cancellation, repeated cancellation, and
  terminal-state rejection.
- Verify trade and execution field validation and value equality.
- Verify equal inputs produce equal domain results.

## Out of Scope

Benchmarking and profiling are not part of this decision. Any performance
claim or replacement representation requires a separate approved task with
baseline measurements.
