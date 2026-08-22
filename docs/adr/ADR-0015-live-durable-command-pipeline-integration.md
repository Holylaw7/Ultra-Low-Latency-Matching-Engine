# ADR-0015: Live Durable Command Pipeline Integration

## Status

Approved — Human Phase 7 Blueprint Approval recorded; implementation is
authorized in dependency order.

## Context

The project currently has three separately frozen engineering baselines:

- `v0.3.0-engineering-baseline`: bounded Event Pipeline;
- `v0.4.0-engineering-baseline`: versioned Command WAL and strict offline replay;
- `v0.5.0-engineering-baseline`: Protocol v1 and a single-session Netty Gateway.

The missing boundary is a live path that makes a command durable before it can
enter the matching pipeline. This Phase must compose the existing components
without changing Domain, OrderBook, MatchingEngine, WAL v1, offline Replay or
Protocol v1 semantics.

This ADR is a proposal only. It authorizes no implementation until the linked
Phase 7 Blueprint receives Human approval.

## Goal

Define an opt-in live durable command path:

```text
Protocol v1 request
    -> Gateway validation / request identity
    -> Command construction
    -> WAL append + SYNC_EACH_APPEND force
    -> bounded Event Pipeline admission
    -> MatchingEngine
    -> ordered EngineResult
    -> Gateway response
```

The result is an engineering baseline, not a production durable exchange.

## Decisions

### D1 — Additive integration boundary

Add a new durable composition layer. Existing Domain, OrderBook, Engine,
Pipeline, WAL/Recovery and legacy Gateway production files and public contracts
remain unchanged. New code may live under `integration/durable` and
`network/netty/durable` and may reuse the existing Protocol v1 codecs.

### D2 — Ownership and topology

The one Netty worker event-loop remains the only command producer. It owns
request admission, the durable coordinator call and pipeline publication. The
pipeline consumer retains exclusive MatchingEngine ownership; the WAL writer
retains exclusive file/directory ownership. No new queue, executor, batching
thread or multi-producer arbitration is introduced.

### D3 — Identity and sequence domains

The durable coordinator owns the candidate logical `Command Sequence` and the
WAL writer validates the exact next sequence. The Gateway owns the per-session
client request ID. Ring sequence, EventSequence, TradeId, WAL segment/offset and
file position remain independent infrastructure or result domains.

### D4 — WAL-before-execute

Normal admission is strictly ordered:

```text
validate request
    -> validate request ID
    -> construct command with candidate Sequence
    -> append command
    -> force(true) succeeds
    -> tryPublish command
    -> MatchingEngine applies command
    -> result is scheduled to the Gateway event loop
    -> response frames are written
```

No durable runtime path may publish or execute before successful append and
the required durability action.

### D5 — Durability, acceptance and response boundaries

- **Durable**: WAL append and `force(true)` returned successfully.
- **Live accepted**: durable append followed by pipeline `ACCEPTED`.
- **Response completed**: result encoding and the local Netty write future
  completed successfully.

Local write completion is not proof of client receipt. Protocol v1 does not
gain a separate durable-ACK frame in this Phase.

### D6 — Durability mode

The live durable runtime permits only `SYNC_EACH_APPEND`. `BUFFERED` remains an
explicitly labelled component/benchmark comparator and cannot be configured or
described as live durable acceptance. `force(true)` is a JDK/OS completion
claim, not a hardware power-loss guarantee.

### D7 — Append failure

A write, force or rotation failure prevents publication and makes the writer,
coordinator and durable Gateway terminal. The first cause is retained; later
admission is rejected. The server may best-effort send a terminal error before
closing. No in-process retry is allowed. Since complete bytes may exist after a
failed force, strict scan/reopen determines the later valid WAL boundary and the
client outcome is ambiguous.

### D8 — Durable append followed by pipeline FULL

`FULL` after a successful durable append is an integration failure, not the
retryable Phase 6 `BACKPRESSURE_FULL` result. The command/request identity is
already consumed, no later command is admitted, and the live service
fail-stops. The durable command may be ahead of live Engine state and is
recoverable only by a future restart/recovery Phase.

### D9 — Disconnect ambiguity

Before append begins, a disconnect means no command was admitted. After append
succeeds, disconnect does not cancel or remove the command; the service drains
the already durable/published operation when possible or enters terminal
failure. Reconnect, duplicate retry and idempotency are not provided.

### D10 — Lifecycle and terminal failure

The integrated lifecycle is:

```text
NEW -> RUNNING -> DRAINING -> STOPPED
                 |
                 +---------> FAILED (terminal)
```

Append, durable-then-FULL, pipeline/engine/handler, outbound-write and drain
failures retain the first cause and stop new admission. Graceful shutdown
stops reads, completes the one in-flight operation when possible, drains the
pipeline, closes the WAL writer and then closes network resources.

### D11 — Startup and replay boundary

Phase 7 starts only with a new/empty WAL and a genesis MatchingEngine. A
non-empty WAL is rejected rather than appended to an engine whose state is
unknown. Existing strict offline replay may verify a closed WAL, but service
restart, replay-to-live handoff, Snapshot restore and online Recovery remain
future work.

### D12 — Evidence and claim boundary

Evidence must prove append-before-publish ordering, failure and disconnect
semantics, strict replay equivalence and the stated lifecycle. Benchmarks may
claim only a single-session, one-in-flight, `SYNC_EACH_APPEND` engineering
baseline. No exactly-once, client-received ACK, power-loss, operational
recovery, multi-client throughput or production-readiness claim is authorized.

## Non-Goals

- Snapshot, online Recovery or restart from a non-empty WAL;
- Protocol/WAL format changes, durable result/event logs or request-ID
  persistence;
- reconnect, duplicate suppression, idempotency, multiple sessions or
  pipelining;
- `BUFFERED` live durability, batching, group commit or a new queue/executor;
- TLS, authentication, replication, HA, multi-symbol routing or deployment;
- performance optimization or Product Release.

## Exception Gate

Stop for Human review if implementation requires a frozen-file/API change,
Protocol/WAL/replay semantic change, publication before force, retryable
`BACKPRESSURE_FULL` after durable append, a second producer, a new critical
dependency, restart/reconnect behavior, a production-only test seam, weakened
acceptance criteria or any scope expansion.

## Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-22 | Architect / Sol High | Proposed | Phase 7 Blueprint prepared. |
| 2026-08-22 | Human Developer | Approved | D1-D12 and TASK-024 through TASK-028 approved through the Phase Blueprint. Execution is authorized in strict dependency order; Phase Closure, merge and `v0.6.0-engineering-baseline` remain unauthorized. |

```text
ADR-0015: Approved
Implementation: Authorized in dependency order
Next Gate: TASK-027 Evidence Gate
```
