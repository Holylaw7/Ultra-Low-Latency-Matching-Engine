# ADR-0008: Structural Limit Matching Boundary and Deterministic Fragments

## Status

Approved

## Decision Record

- Proposal date: `2026-08-19`
- Reviewer: `Human Developer`
- Decision date: `2026-08-19`
- Decision: `Approved`
- Related decisions:
  - [`ADR-0001-matching-model.md`](ADR-0001-matching-model.md)
  - [`ADR-0005-domain-model-and-correctness-baseline.md`](ADR-0005-domain-model-and-correctness-baseline.md)
  - [`ADR-0007-basic-orderbook-structure-and-boundaries.md`](ADR-0007-basic-orderbook-structure-and-boundaries.md)

## Context

Phase 2 Sub-stages 1-3 established and approved the deterministic OrderBook
state model:

- `PriceLevel` owns an intrusive FIFO queue;
- `BidBook` and `AskBook` own ordered price-level indexes;
- `OrderBook` owns both sides and an active `OrderId -> OrderNode` index;
- cancellation and execution lifecycle updates remove empty levels and stale
  active-index entries.

Human Developer approved Sub-stage 3 on `2026-08-19` and authorized the next
scope, Structural Limit Matching. ADR-0007 already defines the high-level
boundary: limit orders may cross the opposite side, matches use maker price,
residual quantity rests, and the OrderBook does not create or publish
`Trade`/`Execution` events.

This ADR freezes the detailed behavior for that sub-stage before production
implementation begins. It does not reopen the approved TreeMap, intrusive
queue, active-index, or Phase 1 domain decisions.

Human Developer approved this ADR on `2026-08-19`. Production implementation
is authorized only within the scope and constraints recorded below.

## Problem

The project needs a small, deterministic structural matching operation that:

1. consumes the best eligible opposite-side orders in price-time priority;
2. supports one-level and multi-level limit-order sweeps;
3. applies partial and full lifecycle transitions to maker and taker orders;
4. rests a non-zero incoming limit residual in the correct side book;
5. returns enough immutable information for a later MatchingEngine to create
   `Trade` and `Execution` values;
6. does not make the OrderBook responsible for trade identifiers, event
   sequencing, event publication, market-order policy, or persistence.

## Options Considered

### Option 1 - OrderBook match operation returning structural fragments

Add a `matchLimit(Order)` operation that mutates only the in-memory OrderBook
and returns an ordered `List<MatchFragment>`. Each fragment identifies the
maker and taker, maker execution price, executed quantity, and both remaining
quantities after the fragment.

Advantages:

- keeps price-time traversal beside the authoritative side-book state;
- gives the future MatchingEngine deterministic input without coupling it to
  mutable nodes;
- makes one-level, multi-level, partial-fill, full-fill, and residual
  behavior directly testable;
- does not allocate trade identifiers or emit external events.

Costs:

- a returned fragment list introduces allocation in this correctness baseline;
- a future performance stage may need a different output transport after
  benchmark evidence.

**Result: Approved.**

### Option 2 - Return mutated OrderNode or expose mutable queue traversal

This avoids a dedicated result boundary, but leaks mutable OrderBook
internals, makes later event translation fragile, and allows callers to depend
on node ownership or queue links.

**Result: Rejected.**

### Option 3 - Create `Trade` and `Execution` inside OrderBook

This would produce a convenient caller API, but it would require the
OrderBook to allocate `TradeId`, assign event sequence, define event ordering,
and own event publication responsibilities.

**Result: Rejected.** `Trade`/`Execution` construction remains a later
MatchingEngine responsibility.

### Option 4 - Add a class named `OrderBookMatch`

ADR-0007 describes a structural match boundary, but the current approved
sub-stage does not require a dedicated aggregate or event object with that
name. Introducing such a type now would blur the boundary with the future
MatchingEngine.

**Result: Deferred.** The proposed baseline uses the narrower immutable
`MatchFragment` result type.

## Decision

### 1. Public Operation and Result Boundary

Introduce:

```text
OrderBook.matchLimit(Order incoming)
    -> List<MatchFragment>
```

`MatchFragment` is an immutable structural record in the OrderBook package:

```text
MatchFragment
    makerOrderId
    takerOrderId
    price
    quantity
    makerRemainingQuantityUnits
    takerRemainingQuantityUnits
```

The returned list is ordered in the exact traversal order and is immutable to
callers. It contains no `TradeId`, event `Sequence`, timestamps, events, or
mutable `OrderNode` references.

The result type is not `Trade`, `Execution`, or a publication event. A future
MatchingEngine maps each fragment to those Phase 1 domain values and supplies
their identifiers.

### 2. Incoming Order Validation

`matchLimit` accepts only an externally submitted, active limit order in
`NEW` status with positive remaining quantity. It rejects:

- `null`;
- market orders;
- terminal orders;
- previously partially filled orders passed as a new incoming event;
- an `OrderId` currently present in the active index.

The active-only index remains the source for current duplicate detection.
Reuse of an identifier after the previous order has left the live book remains
an upstream/global-identity concern, consistent with ADR-0007 and ADR-0005.

The existing `add` operation remains the resting primitive. It continues to
accept an active limit residual, including a `PARTIALLY_FILLED` residual
created by this operation.

### 3. Crossing Rules

For an incoming buy:

```text
incoming limit price >= best ask
```

For an incoming sell:

```text
incoming limit price <= best bid
```

If the opposite side is empty or the best price is not crossed, matching
stops and the active incoming limit order is rested in its own side book.

### 4. Traversal and Priority

The operation repeatedly:

1. reads the best opposite price level;
2. checks whether that level crosses the incoming limit price;
3. takes the head `OrderNode` from that level;
4. executes the minimum of maker remaining quantity and taker remaining
   quantity;
5. emits one fragment for that maker/taker pair;
6. continues at the same queue head or next price level as lifecycle state
   requires.

Price levels are consumed in the existing side-book order. Orders at the same
price are consumed from the existing FIFO queue head. No `HashMap` iteration,
`OrderId` order, wall-clock time, or thread scheduling may affect the result.

### 5. Maker Price

Every fragment price is the resting maker order's limit price:

```text
fragment.price = maker.limitPrice
```

The incoming taker's limit price is used only for the crossing decision and is
never used as the execution price.

### 6. Lifecycle and Index Synchronization

For every fragment:

- the maker receives the executed quantity through the existing controlled
  domain transition;
- the incoming order receives the same executed quantity through the same
  domain transition;
- a fully filled maker is unlinked from its queue, its empty level is removed,
  and its active-index entry is removed;
- a partially filled maker remains at the head of its existing queue;
- a fully filled incoming order is not added to the active index;
- a non-zero incoming residual is added exactly once to its own side book
  after the sweep and becomes the tail of its price level.

After the operation returns:

- no crossed prices remain between the two sides;
- `PriceLevel.totalQuantity` equals the sum of queued order residuals;
- every live queued order has one active-index entry;
- every active-index entry points to one live queued order;
- best-price accessors and empty-level cleanup remain correct.

### 7. Single-Threaded Mutation

The operation follows ADR-0001. One matching thread owns all mutations for
one symbol. No locks, callbacks, I/O, event publication, or concurrent
iteration are introduced in this sub-stage.

## Scope Boundary

Authorized after this ADR and the associated stage plan are approved:

- `OrderBook.matchLimit(Order)`;
- immutable `MatchFragment`;
- one-level and multi-level limit traversal;
- buy/sell crossing checks;
- maker-price fragments;
- partial and full maker/taker state transitions;
- residual limit resting;
- deterministic structural matching tests and invariant checks;
- documentation and phase-report synchronization.

Not authorized:

- market-order matching or IOC/FOK/slippage policy;
- `MatchingEngine` orchestration;
- `Trade`/`Execution` creation or publication;
- `TradeId` or event sequence allocation;
- `OrderBookMatch` aggregate/event type;
- WAL, Snapshot, Recovery, network, Disruptor, Metrics, or logging;
- custom tree, SkipList, radix, price-array, off-heap, lock-free, or other
  performance alternatives;
- benchmark-based performance conclusions.

Any required change to `Order`, `OrderStatus`, `Trade`, `Execution`, or
ADR-0005 must stop this sub-stage and receive a separate domain review.

## Consequences and Risks

Positive:

- The match algorithm is colocated with the authoritative OrderBook state.
- FIFO and price traversal reuse the already approved data structures.
- The future MatchingEngine receives deterministic, inspectable fragments.
- Trade/event concerns remain outside the critical OrderBook boundary.

Trade-offs and risks:

- Returning a list may allocate per incoming order and is not a performance
  claim.
- Matching must keep the active index synchronized while removing maker nodes.
- The incoming order is not indexed during traversal and must be added only
  after its final residual is known.
- A future event layer must preserve fragment order when allocating Trade and
  Execution identifiers.

## Verification Plan

Implementation verification must cover both buy and sell directions:

| Case | Required evidence |
| --- | --- |
| Empty opposite side | Incoming limit rests unchanged |
| No crossing | Incoming limit rests and returns no fragments |
| One-level exact fill | One maker-price fragment, both lifecycle states correct |
| One-level partial fill | Maker remains at queue head with reduced quantity |
| Taker residual | Residual rests at the incoming price |
| Multi-level sweep | Best price to worse price, one fragment per consumed maker |
| Same-price FIFO | Earlier queue head is consumed before later orders |
| Maker price | Fragment price equals resting order price |
| Last maker removal | Empty level and active index entry are removed |
| Both directions | Buy-against-ask and sell-against-bid are symmetric |
| Invalid input | Market, terminal, duplicate-active-id and null inputs rejected |
| Determinism | Equal ordered inputs produce equal fragments and final state |
| Invariants | Quantity totals, active index, best prices and non-crossed state hold |

No benchmark is required for this ADR / Decision stage. A later approved
verification stage may run the baseline benchmark with recorded parameters.

## Baseline Benchmark Evidence

The Human Developer authorized the Phase 2 OrderBook baseline benchmark on
`2026-08-19`. The benchmark was executed after Structural Limit Matching
implementation and correctness verification, using the existing TreeMap,
intrusive FIFO and active OrderId index baseline.

The run used JMH 1.37, Java 21.0.12, two forks, one thread, three one-second
warmup iterations and five one-second measurement iterations. It measured
price-level insertion, Best Bid/Ask lookup, OrderId cancellation,
empty-level cleanup, one-level matching and a deterministic 64-level sweep.
The complete result table and raw JSON location are recorded in
[`PHASE-2-benchmark-orderbook-baseline.md`](../../tasks/reports/PHASE-2-benchmark-orderbook-baseline.md).

This evidence does not itself authorize profile execution or optimization and
does not establish production throughput, latency, allocation, GC or
million-orders-per-second claims. Profiling is separately proposed under
[`ADR-0009-performance-profiling-evidence.md`](ADR-0009-performance-profiling-evidence.md).

## Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | `Approved` | Structural Limit Matching is authorized within the defined scope. `MatchFragment` remains the structural result boundary; Trade/Execution, MatchingEngine, Market Order, WAL, Network and performance alternatives remain out of scope. |

## Relationship to ADR-0007

ADR-0007 remains the authoritative Phase 2 Basic OrderBook decision. This ADR
refines only the Structural Limit Matching sub-stage, specifically the
operation input contract, fragment result boundary, traversal order, maker
price, residual resting, and verification obligations. It does not alter the
approved side-book data structures or Phase 1 domain semantics.
