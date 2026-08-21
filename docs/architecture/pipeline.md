# Event Pipeline Architecture

## Status

`Approved` by
[`ADR-0012`](../adr/ADR-0012-event-pipeline-execution-and-backpressure.md) and
the [`Phase 4 Blueprint`](../../tasks/blueprints/PHASE-4-event-pipeline-blueprint.md).
Human Phase Blueprint and Closure approvals are recorded. The dependency-gated
implementation and component evidence are accepted and frozen at
`v0.3.0-engineering-baseline`.

## Implemented Flow

```text
Caller / Future Ingress
    -> bounded non-blocking tryPublish(command)
    -> Disruptor ring buffer (single producer)
    -> one pipeline-owned matching consumer thread
    -> frozen synchronous MatchingEngine
    -> synchronous in-memory EngineResultHandler
```

## Ownership

- The external caller owns command creation and the authoritative Command
  Sequence. The Disruptor ring sequence is infrastructure-only.
- The implemented pipeline owns one MatchingEngine and its consumer thread
  while running; callers must not access that engine directly.
- MatchingEngine remains the only owner of TradeId and EventSequence.
- The result handler runs synchronously on the matching consumer and is limited
  to deterministic in-memory handling. Network and persistence I/O are not
  part of Phase 4.
- One accepted command is processed exactly once and ordered result
  collections remain observable behavior.

## Admission and Failure Semantics

- `tryPublish` is bounded and returns `ACCEPTED` or `FULL`.
- A full ring buffer does not drop, overwrite, block or hide a retry.
- Engine or result-handler failure moves the pipeline to terminal `FAILED`;
  later publication is rejected.
- Lifecycle is `NEW -> RUNNING -> DRAINING -> STOPPED`, with `FAILED` terminal.
- Graceful shutdown uses a bounded drain; timeout becomes `FAILED`.
- Event-slot command references are cleared after handling, including failure
  paths, to avoid retention across ring reuse.

## Component Evidence

The implemented single-producer/single-consumer boundary has a reproducible
component benchmark. [`pipeline.md`](../benchmark/pipeline.md) compares:

- direct synchronous MatchingEngine processing;
- producer-side bounded admission;
- batch publication plus verified completion;
- capacities `1024` and `65536`;
- `BLOCKING`, `YIELDING` and `BUSY_SPIN` wait modes as explicit experimental
  variables.

`BLOCKING` remains the correctness and portable default. Other wait modes
cannot become defaults or production recommendations without evidence and an
approved decision update.
Multi-producer ingress, asynchronous output rings, WAL, Replay, Network and
production tuning are deferred.

No lock-free or wait-free claim is made. The benchmark does not establish
network, durable or production throughput/latency.
