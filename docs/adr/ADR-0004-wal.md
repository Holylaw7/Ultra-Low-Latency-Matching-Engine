# ADR-0004: Sequential WAL Baseline

## Status

Accepted as baseline

## Context

Recovery requires a durable, ordered representation of accepted events.

## Decision

Use a sequential WAL with monotonic sequence numbers, record length validation, and CRC validation. Flush policy and segment rotation remain configurable decisions.

## Consequences

- Replay order is explicit.
- Partial writes and corruption can be detected.
- Durability and latency depend on the selected flush policy.
- Snapshot position must be recorded with sufficient precision for replay.

## Required Tests

- Normal append and replay
- Partial final record
- Corrupted record
- Sequence gap
- Snapshot plus WAL replay
- Recovered state hash equality
