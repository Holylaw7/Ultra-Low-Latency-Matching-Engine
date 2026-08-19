# OrderBook Architecture

## Status

Baseline design approved; implementation in progress under
[`ADR-0007-basic-orderbook-structure-and-boundaries.md`](../adr/ADR-0007-basic-orderbook-structure-and-boundaries.md).

## Initial Baseline

The first implementation will use:

- `TreeMap` for price-level ordering
- Intrusive FIFO order queues within each price level
- An `OrderId` index for direct cancellation lookup

This is a correctness and measurement baseline, not a final performance claim.

Phase 2 production implementation is authorized only within the approved ADR
and task scope. The `OrderNode + OrderQueue + PriceLevel` sub-stage is complete;
Human Developer approved that sub-stage on `2026-08-19`, and the authorized
`BidBook / AskBook` sub-stage is complete. Its completion report is awaiting
Human approval before the next `OrderBook / active OrderId index` sub-stage.

## Invariants

- Buy levels are ordered from highest to lowest effective priority.
- Sell levels are ordered from lowest to highest effective priority.
- Orders at one price level retain sequence order.
- A resting order has exactly one queue position.
- A canceled or fully filled order is not present in a live queue.
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
