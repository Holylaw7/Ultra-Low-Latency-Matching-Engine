# ADR-0003: Event Pipeline Boundary

## Status

Accepted as baseline

## Context

Networking, persistence, and output should not add blocking work or nondeterministic mutation to the matching critical path.

## Decision

Place an event pipeline outside the matching core. The core consumes events in sequence through a single consumer for each symbol ownership domain.

## Consequences

- Ingress and egress can evolve independently.
- Backpressure becomes an explicit system concern.
- Pipeline latency must be measured separately from pure matching latency.
- The pipeline must not reorder events for one symbol.

## Open Questions

- RingBuffer versus Disruptor configuration
- Producer model
- Capacity and backpressure policy
- Event reuse and allocation strategy
