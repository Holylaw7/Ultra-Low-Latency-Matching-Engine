# ADR-0007: Basic OrderBook Structure and Boundary Semantics

## Status

Accepted with constraints

## Decision Record

- Proposal date: `2026-08-19`
- Reviewer: Human Developer
- Decision date: `2026-08-19`
- Decision: `Approved`
- Related decisions:
  - [`ADR-0001-matching-model.md`](ADR-0001-matching-model.md)
  - [`ADR-0002-orderbook-structure.md`](ADR-0002-orderbook-structure.md)
  - [`ADR-0005-domain-model-and-correctness-baseline.md`](ADR-0005-domain-model-and-correctness-baseline.md)

## Context

Phase 1 established the domain model used by the matching core:

- `Order` supports limit and market orders with controlled lifecycle
  transitions.
- `Price`, `Quantity`, `OrderId`, and `Sequence` are positive integer-backed
  value objects.
- `Order` owns remaining quantity and status transitions.
- `Trade` and `Execution` are deterministic value objects whose identifiers
  are supplied by the caller.

The architecture baseline already identifies a single-thread-owned OrderBook,
price-time priority, intrusive FIFO queues, and an `OrderId` cancellation
index. `ADR-0002` accepts `TreeMap` as the initial price-level index, but it
does not define the complete behavior needed for the first Basic OrderBook:

- the separation between `BidBook` and `AskBook`;
- the exact `PriceLevel` and intrusive `OrderQueue` invariants;
- active-order index lifetime and cancellation idempotence;
- Best Bid and Best Ask lookup behavior;
- empty price-level removal;
- the boundary between resting, matching, and market-order handling;
- the structural match result consumed later by `MatchingEngine`.

This ADR was approved for Phase 2 implementation on `2026-08-19`. Production
implementation is authorized only within the scope and constraints recorded
below and in the linked task plan.

## Problem

The project needs one implementable and testable OrderBook baseline that:

1. preserves price-time priority and deterministic mutation;
2. supports direct add, O(1) unlink-based cancel, Best Bid, Best Ask, and
   multi-level limit-order matching;
3. keeps each live order in exactly one price-level queue;
4. removes empty price levels without leaving stale best-price state;
5. does not make the Basic OrderBook responsible for market-order policy,
   event sequencing, trade-id allocation, or external event emission;
6. remains simple enough to benchmark before any custom data structure is
   considered.

## Options Considered

### Option 1 - TreeMap, intrusive FIFO queues, active OrderId index

Use two side-specific books backed by ordered `TreeMap<Price, PriceLevel>`
indexes. Each price level owns an intrusive FIFO queue. The aggregate
`OrderBook` maintains an active `OrderId -> OrderNode` index and cached best
price levels.

Advantages:

- directly extends the existing `ADR-0002` baseline;
- ordering and empty-level behavior are explicit;
- unlinking a known node is O(1);
- best-price reads are O(1) when the cache is valid;
- correctness tests can be written before performance alternatives.

Costs:

- `TreeMap` and node objects are not expected to be the final memory layout;
- price-level insertion and removal remain O(log P), where P is the number of
  live price levels;
- the active-only index does not provide historical order-id uniqueness.

**Result: Accepted as the Phase 2 baseline with constraints.**

### Option 2 - Custom Red-Black Tree or Skip List

This may reduce allocation or improve locality after measurement, but it
introduces more balancing, traversal, and boundary-case code before a
correctness baseline exists.

**Result: Deferred.**

### Option 3 - Price Array or Radix Structure

This can make price navigation efficient when the tick range is dense and
bounded. The current domain model does not define a bounded instrument price
range, so sparse or extreme prices could create wasted memory or additional
mapping rules.

**Result: Deferred.**

## Decision

### 1. Ownership and Side Separation

Introduce an `OrderBook` aggregate with two side-specific components:

```text
OrderBook
    +-- BidBook
    +-- AskBook
    +-- active OrderId -> OrderNode index
```

One matching thread is the sole mutator for one symbol, consistent with
`ADR-0001`. Concurrent mutation and lock-based protection are out of scope.
Readers must use explicit book accessors or a future snapshot interface; they
must not inspect mutable internals concurrently.

`BidBook` accepts only buy-side limit orders. `AskBook` accepts only sell-side
limit orders. A market order has no price level and is not rested by the Basic
OrderBook.

### 2. Price Index

Each side uses a `TreeMap<Price, PriceLevel>` as the correctness baseline:

- `AskBook` orders levels from low price to high price.
- `BidBook` orders levels from high price to low price.
- the first entry is the best price for either side;
- price-level insert and remove are O(log P);
- the aggregate caches the current best price level so Best Bid and Best Ask
  reads are O(1);
- when the cached best level is removed, the next best level is recalculated
  from the ordered index.

The cache is an implementation invariant, not a separate source of truth.
The map remains authoritative.

### 3. PriceLevel and OrderQueue

Each `PriceLevel` contains:

- its immutable `Price`;
- one intrusive `OrderQueue`;
- live order count;
- total remaining quantity in domain units.

`OrderQueue` stores `OrderNode` instances with:

```text
OrderNode
    +-- Order
    +-- PriceLevel owner
    +-- prev
    +-- next
```

Appending to the tail and unlinking a known node are O(1). Orders at the same
price are consumed from the head in accepted input order. The queue must not
sort by `OrderId`, hash order, wall-clock time, or thread scheduling.

The order's `Sequence` is the deterministic time-priority value. The
OrderBook does not generate sequences. Monotonic event assignment and
validation remain responsibilities of the upstream matching input owner,
while the single matching thread preserves the accepted order of mutations.

### 4. Active Order Index and Cancellation

The aggregate maintains an active index:

```text
OrderId -> OrderNode
```

The index contains only live, queued orders. Cancellation performs:

1. lookup by `OrderId`;
2. controlled `Order.cancel()` transition;
3. O(1) intrusive unlink;
4. `PriceLevel` count and quantity update;
5. active-index removal;
6. empty-level and best-cache cleanup.

Cancel of an absent order is a no-op result. This includes a repeated cancel
after the order has already been removed, so cancellation is idempotent at the
OrderBook boundary. A filled order is also absent from the live index and
cannot be canceled.

The Basic OrderBook rejects duplicate identifiers that are currently active.
Historical/global `OrderId` uniqueness is not retained by the book and remains
the responsibility of the owning Matching Engine or event source, consistent
with ADR-0005.

### 5. Add Boundary

`add` is the resting-order primitive. It accepts an active limit order with
positive remaining quantity that is not already indexed and inserts it into
the matching side's price level.

`add` must not create a crossed externally observable book. Incoming orders
that may cross the opposite side enter through the separate `match` operation.
An active partially filled incoming order may be rested after its residual
quantity is calculated.

### 6. Match Boundary

The Basic OrderBook provides a structural limit-order match operation:

- it accepts an incoming limit order;
- it checks the opposite side's best price;
- a buy crosses when `incoming price >= best ask`;
- a sell crosses when `incoming price <= best bid`;
- it consumes the best opposite order first;
- it consumes same-price orders FIFO;
- it may sweep multiple price levels;
- it applies `Order.applyExecution()` to both orders;
- it removes filled nodes and empty levels;
- it rests any non-zero incoming limit residual;
- it restores the non-crossed state when the operation returns.

Execution price is the resting maker order's price. The operation returns
deterministic structural match fragments containing maker/taker identifiers,
execution price, executed quantity, and remaining quantities. It does not
allocate `TradeId`, assign event sequence, emit `Trade`/`Execution` events, or
perform output or persistence I/O. The future Matching Engine translates these
fragments into the Phase 1 `Trade` and `Execution` values.

Market-order matching, external trade-id allocation, event validation, and
Matching Engine orchestration are Phase 3 responsibilities.

### 7. Empty Price Levels and Invariants

After the last live order leaves a `PriceLevel` through cancellation or full
execution:

- the queue must be empty;
- count and total remaining quantity must be zero;
- the level must be removed from the side index immediately;
- the cached best level must be updated if the removed level was best.

The following invariants must hold after every public operation:

- a live order occurs in exactly one side and one queue position;
- an indexed live order has exactly one corresponding `OrderNode`;
- a queued order's side matches its book;
- a queued order is an active limit order;
- `PriceLevel.totalQuantity` equals the sum of remaining quantities in its
  queue;
- an empty price level is not present in the price index;
- Best Bid is the highest live bid price, or empty;
- Best Ask is the lowest live ask price, or empty;
- when no match operation is active, `BestBid < BestAsk` whenever both exist.

## Human Approval Record

| Date | Reviewer | Decision | Constraints / Notes |
| --- | --- | --- | --- |
| 2026-08-19 | Human Developer | `Approved` | Authorizes Phase 2 implementation of the TreeMap side books, intrusive FIFO queues, active `OrderId -> OrderNode` index, best-price cache, add/cancel, and structural limit matching within the approved scope. `OrderBookMatch` remains a structural fragment only; maker price is authoritative. Market Order, MatchingEngine, Trade/Execution creation or publication, WAL, network, Disruptor, lock-free, off-heap, custom tree, SkipList, radix, and unproven performance optimization remain out of scope. Any Phase 1 domain change requires a separate ADR-0005 review. |

## Scope Boundary

This proposal authorizes only the following after approval:

- `OrderBook`, `BidBook`, and `AskBook` baseline structure;
- `PriceLevel`, `OrderQueue`, and intrusive `OrderNode`;
- active `OrderId` index;
- add, cancel, Best Bid, Best Ask, and structural limit matching;
- correctness tests for queue, price priority, cancellation, cleanup, and
  deterministic matching;
- documentation and a baseline benchmark plan.

This decision does not authorize:

- `MatchingEngine` event orchestration;
- market-order matching policy;
- network, pipeline, WAL, Snapshot, Recovery, or Metrics integration;
- custom tree, SkipList, radix, off-heap, cache-line, or lock-free
  optimization;
- global order-id allocation or sequence assignment;
- performance claims without benchmark evidence.

## Consequences and Risks

Positive:

- The baseline is directly compatible with the Phase 1 domain model.
- Price-time priority and queue ownership are explicit.
- Known-node cancellation is O(1) for unlinking.
- Best-price reads have a defined O(1) accessor contract.
- Empty-level cleanup prevents stale prices from affecting matching.
- The future Matching Engine receives deterministic match fragments without
  coupling OrderBook to event output or persistence.

Trade-offs and risks:

- `TreeMap`, `OrderNode`, and `HashMap` allocations are a correctness baseline,
  not a final latency design.
- Price-level mutations are O(log P).
- The active-only index does not enforce historical order-id uniqueness.
- Best-level cache maintenance adds mutation edge cases.
- The structural match result is an additional boundary that must remain
  consistent with `Trade` and `Execution` construction in Phase 3.

## Verification and Benchmark Evidence Plan

Implementation verification must include:

- add orders to both sides and reject invalid side/type/status combinations;
- preserve FIFO at one price and price priority across levels;
- verify Best Bid and Best Ask after add, cancel, fill, and cleanup;
- verify repeated cancellation is a no-op;
- verify O(1) unlink behavior through node ownership/invariant tests;
- verify one-level and multi-level sweeps;
- verify partial and full fills and residual rest;
- verify maker-price execution and deterministic match fragments;
- verify empty-level removal and final non-crossed state;
- run deterministic replay of the same order sequence and compare final state.

The future benchmark must compare the same event stream and state assertions
for price-level insert, best lookup, cancel, one-level match, multi-level
match, and empty-level cleanup. It must report environment, workload,
warmup, measurement, throughput, latency, allocation, and GC. No performance
conclusion is part of this proposal.

The approved Phase 2 baseline benchmark was later executed on `2026-08-19`
with JMH 1.37, Java 21.0.12, two forks and one matching-owner thread. It
records the fixed workload and raw result location in
[`PHASE-2-benchmark-orderbook-baseline.md`](../../tasks/reports/PHASE-2-benchmark-orderbook-baseline.md).
Allocation and GC were not measured, and the result remains experimental
baseline evidence pending Human Approval.

## Relationship to ADR-0002

The review found no conflict with ADR-0002's initial `TreeMap`, intrusive FIFO,
and `OrderId` index direction. ADR-0007 refines that baseline with the missing
behavioral boundaries and invariants required for implementation. ADR-0002
remains the historical baseline; ADR-0007 is the authoritative Phase 2
implementation decision for the listed scope.
