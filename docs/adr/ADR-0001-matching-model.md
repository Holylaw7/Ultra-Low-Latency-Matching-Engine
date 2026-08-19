# ADR-0001: Single-Threaded Matching Model

## Status

Accepted

## Context

An order book requires a deterministic total order for events belonging to one symbol. Concurrent mutation introduces ordering, visibility, and contention complexity.

## Options

1. Multiple threads mutate one order book.
2. One matching thread owns one symbol order book.
3. A lock protects a multi-threaded order book.

## Decision

Use one matching thread as the sole mutator for one symbol order book. Parallelism may be introduced by partitioning different symbols across workers.

## Consequences

Positive:

- Deterministic mutation
- Simple state ownership
- No lock on the core mutation path
- Easier replay and recovery

Trade-offs:

- A single hot symbol is limited by one consumer.
- Cross-symbol coordination requires an explicit future design.

## Verification

The decision must be validated with deterministic replay, throughput, tail-latency, and contention measurements.
