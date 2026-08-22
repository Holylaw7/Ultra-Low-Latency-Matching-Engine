# Architecture Overview

## Status

Phase 0 bootstrap, Phase 1 domain model, Phase 2 OrderBook and Phase 3
MatchingEngine are complete. Phase 3 is frozen at
`v0.2.0-engineering-baseline`. The complete Phase 4 Event Pipeline Blueprint
has Human approval. Its dependency-gated implementation, verification and
component benchmark evidence and Closure are approved and frozen at
`v0.3.0-engineering-baseline`. Phase 5 adds a versioned command WAL and strict
offline deterministic replay as a persistence/replay engineering baseline and
is completed, approved and frozen at `v0.4.0-engineering-baseline`. Phase 6
implementation of the approved binary Network Protocol and single-session
Netty gateway is complete and frozen at `v0.5.0-engineering-baseline`:
TASK-019 through TASK-023 are archived after passing their evidence gates,
including deterministic network verification and component/loopback benchmark
evidence. Phase 7 now has an approved ADR and Complete Blueprint for Live
Durable Command Pipeline Integration; TASK-024 contracts, TASK-025 coordinator
and TASK-026 durable Netty composition have passed their Evidence Gates, with
TASK-026 at `a978fe7` / CI `32565087793`. Human-approved TASK-027 Round 2
terminal remediation is complete at `7b9106f` / CI `32571940187`, after
baseline and prior remediation runs `32565591806`, `32566165212` and
`32570890919`; the final read-only Evidence Gate for TASK-027 is PASS. TASK-028
benchmark implementation and closure evidence are complete at `9fed6b2` / CI
`32574274905`; verifier, benchmark-reviewer and docs-auditor all PASS. Human
Phase 7 Closure Review is the next gate. Product Release remains separately
governed.

## Scope

The system is a single-node, deterministic matching engine. A symbol's order book is owned by one matching thread and is mutated sequentially.

## Overall Framework

```text
Client
  -> Netty Network Adapter / Binary Protocol [Phase 6 baseline frozen]
  -> Decoder / Validation                    [Phase 6 implemented]
  -> Ingress                                 [Phase 6 single session / one in-flight]
  -> Durable Command Coordinator             [Phase 7 execution active]
       -> Command WAL append + SYNC force    [Phase 7 execution active]
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
See [`network.md`](network.md) for the implemented transport boundary and
explicit non-goals.

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
5. Versioned command WAL and strict offline deterministic replay — **Completed
   and frozen at `v0.4.0-engineering-baseline`**.
6. Network adapter and protocol — **Completed and frozen at
   `v0.5.0-engineering-baseline`**.
7. Live durable Command WAL/Pipeline integration — **Blueprint Approved /
   Execution Active**. TASK-024 contracts, TASK-025 coordinator and TASK-026
   durable Netty composition and the approved TASK-027 Round 2 remediation are
   complete at `7b9106f` / CI `32571940187` with exact-SHA evidence; TASK-027
   verifier/docs-auditor Evidence Gate PASS; TASK-028 benchmark/docs evidence
   is complete at `9fed6b2` / CI `32574274905`, with all read-only reviewers
   PASS. Human Phase 7 Closure Review is next.
8. Snapshot and online Recovery — **Future Work; separate Blueprint required**.
9. Evidence-driven performance alternatives — **Future Work; benchmark and
   ADR required**.
