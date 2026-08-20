# Architecture Overview

## Status

Phase 0 bootstrap and Phase 1 domain model are complete. The Phase 2 OrderBook
baseline and structural limit matching are implemented and verified. The
MatchingEngine orchestration, event pipeline, network, WAL, snapshot and
recovery layers are Future Work and are not yet authorized for implementation.

## Scope

The system is a single-node, deterministic matching engine. A symbol's order book is owned by one matching thread and is mutated sequentially.

## Overall Framework

```text
Client                                       [Future Work]
  -> Netty Network Adapter / Binary Protocol [Future Work]
  -> Decoder / Validation                    [Future Work]
  -> Ingress                                 [Future Work]
  -> RingBuffer / Disruptor Pipeline         [Future Work]
  -> MatchingEngine                          [Phase 3]
       -> sequence / orchestration
       -> OrderBook                          [Phase 2 baseline implemented]
            -> BidBook / AskBook
            -> PriceLevel / OrderQueue
            -> active OrderId index
            -> structural limit matching
       -> Trade / Execution events           [domain types implemented]
  -> WAL / Output / Metrics                  [Future Work]
  -> Snapshot / Replay / State Hash          [Future Work]
```

## Implemented Boundary

`OrderBook.matchLimit(Order)` owns deterministic price-time traversal and
in-memory lifecycle mutation. It returns ordered immutable `MatchFragment`
values. Trade identifiers, event sequences, `Trade`/`Execution` orchestration,
publication and persistence remain outside the implemented Phase 2 boundary.

See [`order-book.md`](order-book.md) and
[`matching-engine.md`](matching-engine.md) for the detailed boundary.

## Boundary Rules

- The matching core does not perform network, database, or blocking file I/O.
- Event sequence is assigned before an event enters the matching core.
- External consumers receive events after the core state transition.
- Persistence and output must not change matching order.
- Any change to event ordering requires an ADR and deterministic replay tests.

## Planned Delivery Order

1. Domain model and correctness baseline — **Completed**.
2. Basic OrderBook and structural limit matching — **Execution complete;
   Phase 2 Final Closure Review pending**.
3. MatchingEngine orchestration — **Future Work / Not Authorized**.
4. Event pipeline — **Future Work**.
5. Network adapter and protocol — **Future Work**.
6. WAL, snapshot and recovery — **Future Work**.
7. Deterministic replay and system verification — **Future Work**.
8. Evidence-driven performance alternatives — **Future Work; benchmark and
   ADR required**.
