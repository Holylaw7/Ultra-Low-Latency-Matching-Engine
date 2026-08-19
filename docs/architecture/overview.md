# Architecture Overview

## Status

Bootstrap baseline. The runtime architecture is planned and the matching core has not been implemented.

## Scope

The system is a single-node, deterministic matching engine. A symbol's order book is owned by one matching thread and is mutated sequentially.

## Logical Layers

```text
Client
    -> Network Adapter
    -> Decoder
    -> Ingress
    -> Event Pipeline
    -> Matching Engine
    -> OrderBook
    -> Trade Event
    -> WAL / Output / Metrics
```

## Boundary Rules

- The matching core does not perform network, database, or blocking file I/O.
- Event sequence is assigned before an event enters the matching core.
- External consumers receive events after the core state transition.
- Persistence and output must not change matching order.
- Any change to event ordering requires an ADR and deterministic replay tests.

## Initial Implementation Order

1. Domain model
2. Correctness baseline
3. OrderBook
4. Matching engine
5. Event pipeline
6. WAL and recovery
7. Network adapter
8. Performance alternatives
