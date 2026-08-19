# OrderBook Architecture

## Status

Baseline design approved; implementation in progress under
[`ADR-0007-basic-orderbook-structure-and-boundaries.md`](../adr/ADR-0007-basic-orderbook-structure-and-boundaries.md).

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
`OrderBook + active OrderId index` sub-stage is complete and its report is
awaiting Human approval before structural limit matching begins.

## Aggregate

`OrderBook` owns one `BidBook`, one `AskBook`, and an active-only
`OrderId -> OrderNode` index. OrderBook-level cancellation performs direct
index lookup, delegates node removal to the owning side book, and removes the
index entry after a successful unlink. Historical or global order-id
uniqueness remains outside the aggregate.

The aggregate currently exposes add, cancel, active lookup, Best Bid/Ask and
price-level counts. Its package-private execution state-transition primitive
keeps partial and full execution synchronized with the active index for
correctness testing. It does not select counterparties, create match
fragments, allocate trade identifiers, or publish events.

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
