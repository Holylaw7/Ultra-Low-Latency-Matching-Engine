# Architecture Overview

## Status

Phase 0 bootstrap, Phase 1 domain model, Phase 2 OrderBook and Phase 3
MatchingEngine are complete. Phase 3 is frozen at
`v0.2.0-engineering-baseline`. The complete Phase 4 Event Pipeline Blueprint
has Human approval. Its dependency-gated implementation, verification and
component benchmark evidence and Closure are approved and frozen at
`v0.3.0-engineering-baseline`. Phase 5 adds a versioned command WAL and strict
offline deterministic replay as a persistence/replay engineering baseline;
Phase 5 Closure, merge and `v0.4.0-engineering-baseline` remain Human gates.

## Scope

The system is a single-node, deterministic matching engine. A symbol's order book is owned by one matching thread and is mutated sequentially.

## Overall Framework

```text
Client                                       [Future Work]
  -> Netty Network Adapter / Binary Protocol [Future Work]
  -> Decoder / Validation                    [Future Work]
  -> Ingress                                 [Future Work]
  -> RingBuffer / Disruptor Pipeline         [Phase 4 implemented / evidence recorded]
  -> MatchingEngine                          [Phase 3 baseline frozen]
       -> sequence / orchestration
       -> OrderBook                          [Phase 2 baseline implemented]
            -> BidBook / AskBook
            -> PriceLevel / OrderQueue
            -> active OrderId index
            -> structural limit matching
       -> Trade / Execution events           [Phase 3 implemented]
  -> Command WAL / Offline Replay            [Phase 5 implemented baseline]
  -> Snapshot / Online Recovery              [Future Work]
  -> Output / Metrics                        [Future Work]
```

## Implemented Boundary

`OrderBook.matchLimit(Order)` owns deterministic price-time traversal and
in-memory lifecycle mutation. It returns ordered immutable `MatchFragment`
values. Phase 3 adds synchronous command sequencing, TradeId/EventSequence
allocation and immutable `Trade`/`Execution` results without modifying the
Phase 2 OrderBook production implementation. Publication and persistence
remain outside the frozen Phase 3 baseline.

See [`order-book.md`](order-book.md) and
[`matching-engine.md`](matching-engine.md) for the detailed boundary.

## Boundary Rules

- The matching core does not perform network, database, or blocking file I/O.
- Command sequence is assigned upstream and validated by MatchingEngine.
- EventSequence and TradeId are assigned only by MatchingEngine when match
  results are produced.
- External consumers receive events after the core state transition.
- Persistence and output must not change matching order.
- Any change to event ordering requires an ADR and deterministic replay tests.

## Planned Delivery Order

1. Domain model and correctness baseline — **Completed**.
2. Basic OrderBook and structural limit matching — **Completed and frozen at
   `v0.1.0-engineering-baseline`**.
3. MatchingEngine orchestration — **Completed and frozen at
   `v0.2.0-engineering-baseline`**.
4. Event pipeline — **Completed and frozen at
   `v0.3.0-engineering-baseline`**.
5. Versioned command WAL and strict offline deterministic replay — **Phase 5
   implementation completed; Closure Review pending**.
6. Network adapter and protocol — **Future Work**.
7. Snapshot and online Recovery — **Future Work**.
8. Evidence-driven performance alternatives — **Future Work; benchmark and
   ADR required**.
