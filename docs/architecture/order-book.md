# OrderBook Architecture

## Status

Baseline structure approved and implemented under
[`ADR-0007-basic-orderbook-structure-and-boundaries.md`](../adr/ADR-0007-basic-orderbook-structure-and-boundaries.md).
Structural Limit Matching is approved under
[`ADR-0008-structural-limit-matching.md`](../adr/ADR-0008-structural-limit-matching.md);
implementation and verification are complete within that approved scope. The
OrderBook baseline benchmark has also been executed and approved as
component-level experimental evidence. ADR-0009 has been approved, profiling
execution has completed and was approved as evidence collection on
`2026-08-19`. ADR-0010 was approved on `2026-08-19` and authorized a
separate measurement-isolation experiment. That experiment and its evidence
review are complete; Phase 2 Final Closure Review is pending.

## Initial Baseline

The first implementation uses:

- `TreeMap` for price-level ordering
- Intrusive FIFO order queues within each price level
- An `OrderId` index for direct cancellation lookup
- An `OrderBook` aggregate joining the bid and ask books

This is a correctness and measurement baseline, not a final performance claim.

Phase 2 production implementation is authorized only within the approved ADR
and task scope. The `OrderNode + OrderQueue + PriceLevel` sub-stage is complete;
Human Developer approved that sub-stage on `2026-08-19`, and the authorized
`BidBook / AskBook` sub-stage was approved on `2026-08-19`. The
`OrderBook + active OrderId index` sub-stage was approved by the Human
Developer on `2026-08-19`. Structural Limit Matching is now in its ADR /
Decision stage and was approved by the Human Developer on `2026-08-19`. The
detailed input contract, result boundary, traversal, maker-price, residual,
and invariant decisions are implemented constraints. Verification, the
approved baseline benchmark and documentation synchronization are complete.
ADR-0009 is approved and the profiling execution evidence is recorded in
[`PHASE-2-profiling-execution.md`](../../tasks/reports/PHASE-2-profiling-execution.md);
profiling execution was approved on `2026-08-19`. ADR-0010 is approved, and
the measurement-isolation evidence is recorded in
[`PHASE-2-measurement-isolation.md`](../../tasks/reports/PHASE-2-measurement-isolation.md).
The current gate is Phase 2 Final Closure Review; production optimization
remains unauthorized under
[`ADR-0010-optimization-decision-after-profiling.md`](../adr/ADR-0010-optimization-decision-after-profiling.md).

## Aggregate

`OrderBook` owns one `BidBook`, one `AskBook`, and an active-only
`OrderId -> OrderNode` index. OrderBook-level cancellation performs direct
index lookup, delegates node removal to the owning side book, and removes the
index entry after a successful unlink. Historical or global order-id
uniqueness remains outside the aggregate.

The aggregate exposes add, cancel, active lookup, Best Bid/Ask and
price-level counts. Its package-private execution state-transition primitive
keeps partial and full execution synchronized with the active index. The
approved `matchLimit` operation selects crossed counterparties and returns
structural fragments without allocating trade identifiers or publishing
events.

## Structural Limit Matching

ADR-0008 defines and the implementation provides a narrow
`OrderBook.matchLimit(Order)` operation returning an immutable,
traversal-ordered `List<MatchFragment>`. The operation accepts a new active
limit order, consumes crossed opposite-side levels using price-time priority,
uses the resting maker price, synchronizes maker and taker state transitions,
and rests a non-zero incoming residual once.

`MatchFragment` contains maker and taker identifiers, maker price, executed
quantity, and both post-fragment remaining quantities. It does not contain
`TradeId`, event sequence, timestamps, `Trade`, `Execution`, mutable nodes, or
publication behavior. Implementation and correctness verification are
complete. Profiling execution was approved as evidence collection on
`2026-08-19`. Measurement isolation and evidence review are complete; Phase 2
Final Closure Review is pending and production optimization remains unauthorized.

## Baseline Measurement

The approved baseline benchmark measures insertion, Best Bid/Best Ask lookup,
OrderId cancellation, empty-level cleanup, one-level matching and a
64-level multi-level sweep. It uses JMH 1.37, two forks, one thread, three
one-second warmup iterations and five one-second measurement iterations.
Results are recorded in
[`PHASE-2-benchmark-orderbook-baseline.md`](../../tasks/reports/PHASE-2-benchmark-orderbook-baseline.md).

The measurements are workload-specific evidence for this implementation. They
do not establish production P99, allocation, GC, or million-orders-per-second
performance. The profiling decision and execution evidence are recorded in
[`ADR-0009-performance-profiling-evidence.md`](../adr/ADR-0009-performance-profiling-evidence.md)
and [`PHASE-2-profiling-execution.md`](../../tasks/reports/PHASE-2-profiling-execution.md).
The isolated harness and current gate are recorded in
[`PHASE-2-measurement-isolation.md`](../../tasks/reports/PHASE-2-measurement-isolation.md).

## Invariants

- Buy levels are ordered from highest to lowest effective priority.
- Sell levels are ordered from lowest to highest effective priority.
- Orders at one price level retain sequence order.
- A resting order has exactly one queue position.
- A canceled or fully filled order is not present in a live queue.
- Every live queued order has exactly one active index entry.
- Every active index entry points to one live queued order.
- Partial execution keeps the active index and updates level quantity.
- Full execution removes the active index entry and empty price level.
- Empty price levels are removed.
- Cancel must not scan all orders.

## State Ownership

One matching thread owns all mutations for one symbol. Readers must use an explicit snapshot or event interface rather than concurrently reading mutable internals.

## Alternatives

Potential alternatives must be evaluated against the baseline:

- Custom balanced tree
- Skip list
- Radix or price array
- Flat or off-heap layout
- Different order index

Each replacement requires correctness regression tests and comparable benchmark data.
