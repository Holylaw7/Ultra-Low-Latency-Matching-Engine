# ADR-0005: Domain Model and Correctness Baseline

## Status

Accepted with constraints — Phase 3 sequence revision proposed; Human approval
pending

## Revision Record

| Date | Scope | Status | Related Decision |
| --- | --- | --- | --- |
| 2026-08-20 | Separate input command sequence from output event sequence | `Proposed — Pending Human Approval` | [`ADR-0011`](ADR-0011-matching-engine-orchestration-model.md), condition D3 |

The original Phase 1 baseline remains accepted and implemented. The revision
below is a proposal only: it does not change production types or authorize
Phase 3 implementation until separately approved.

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

## Proposed Phase 3 Sequence Semantic Revision

### Problem

The Phase 1 baseline defines `Sequence` as a logical input-event sequence, but
the current `Trade` record also carries a field named `sequence` of that same
type. Phase 3 allows one input command to produce multiple matches. Reusing
one sequence namespace would therefore make command order and output order
ambiguous and would weaken deterministic WAL/replay verification.

### Proposed Semantic Contract

If approved, the Phase 3 domain model will use three distinct concepts:

| Concept | Owner | Cardinality | Meaning |
| --- | --- | --- | --- |
| `Sequence` | Upstream command/WAL adapter | One per accepted command | Contiguous input application order |
| `TradeId` | MatchingEngine | One per `MatchFragment` | Stable trade identity; monotonically allocated |
| `EventSequence` | MatchingEngine | One per emitted match-result aggregate | Contiguous output event order |

`Sequence` remains the input command sequence and retains its existing meaning
for `Order.sequence()`. It must not be used as an output-event counter.

Introduce a positive `long`-backed `EventSequence` value type. The current
Trade component:

```text
Trade.sequence : Sequence
```

is proposed to become:

```text
Trade.eventSequence : EventSequence
```

This is an explicit source-level domain refinement, including the accessor
name. It is not a silent reinterpretation of the existing `Sequence` value.

One `MatchFragment` produces one match-result aggregate with one `Trade`, one
maker `Execution` and one taker `Execution`. The aggregate and its Trade share
the same `EventSequence`. The two Executions do not receive independent event
sequences in the Phase 3 baseline because their deterministic order is fixed
inside the aggregate: maker first, taker second.

`TradeId` remains identity, not a substitute for command or event sequence,
even though the Phase 3 baseline allocates it monotonically. No equality
between command `Sequence`, `TradeId` or `EventSequence` may be assumed.

### Determinism and Recovery Consequences

- Equal initial state and equal accepted command streams must reproduce the
  same TradeIds, EventSequences, Trades and Executions.
- Command sequence is the canonical replay order described by ADR-0011.
- EventSequence orders derived match-result aggregates; it is not a second
  WAL authority.
- TradeId and EventSequence counters become future snapshot/recovery state.
- Neither sequence is derived from time, randomness or thread scheduling.

### Compatibility and Implementation Gate

This proposal deliberately creates a source-incompatible `Trade` signature
before a public protocol, persistent event format or product release exists.
If approved, the future Phase 3 implementation Task must update the Trade
type, constructors, tests, documentation and ADR-0011 together.

Until approval:

- the existing Phase 1 and frozen Phase 2 production code remains unchanged;
- `EventSequence` must not be introduced in code;
- ADR-0011 remains conditionally approved;
- Phase 3 implementation remains unauthorized.

### Revision Approval Gate

The Human Developer must explicitly approve or revise:

| ID | Proposed revision | Current state |
| --- | --- | --- |
| R1 | Reserve `Sequence` exclusively for accepted input-command order | Pending |
| R2 | Introduce positive `long`-backed `EventSequence` | Pending |
| R3 | Replace `Trade.sequence` with `Trade.eventSequence` | Pending |
| R4 | Allocate one EventSequence per match-result aggregate | Pending |
| R5 | Keep maker/taker Execution order inside the aggregate, without independent Execution sequences | Pending |
| R6 | Snapshot/replay must reproduce TradeId and EventSequence counters | Pending |

Approval of R1-R6 satisfies ADR-0011 condition D3. It still does not authorize
implementation; a separate implementation Task Plan and Human approval remain
required.

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
