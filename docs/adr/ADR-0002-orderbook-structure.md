# ADR-0002: OrderBook Baseline Structure

## Status

Accepted as baseline

## Context

The first implementation needs a clear correctness baseline before custom data structures and memory layouts are evaluated.

## Options

1. `TreeMap` plus standard collections.
2. Custom balanced tree.
3. Skip list.
4. Price array or radix structure.

## Decision

Start with ordered price levels, intrusive FIFO queues, and an `OrderId` index. The first price-level index may use `TreeMap` so ordering and correctness are explicit.

## Consequences

- The baseline is easy to reason about and test.
- Cancellation can avoid a full queue scan.
- Allocation and cache behavior may be insufficient for final targets.
- Any replacement must preserve the same observable semantics and include benchmark evidence.
