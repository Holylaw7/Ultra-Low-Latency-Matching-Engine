# ADR-0012: Event Pipeline Execution and Backpressure Model

## Status

Approved — implementation, component evidence and Phase 4 Closure accepted

## Decision Record

- Proposal date: `2026-08-21`
- Reviewer: `Human Developer`
- Decision: `Approved`
- Phase Blueprint:
  [`PHASE-4-event-pipeline-blueprint.md`](../../tasks/blueprints/PHASE-4-event-pipeline-blueprint.md)
- Related decisions:
  - [`ADR-0001-matching-model.md`](ADR-0001-matching-model.md)
  - [`ADR-0003-event-pipeline.md`](ADR-0003-event-pipeline.md)
  - [`ADR-0005-domain-model-and-correctness-baseline.md`](ADR-0005-domain-model-and-correctness-baseline.md)
  - [`ADR-0011-matching-engine-orchestration-model.md`](ADR-0011-matching-engine-orchestration-model.md)

Human Phase 4 Blueprint Approval accepted D1-D8 and the enumerated Phase 4
Tasks. Implementation remains subject to the dependency order, automated
evidence gates and the Exception Gate below.

## Context

Phase 3 is complete and frozen at `v0.2.0-engineering-baseline`. Its
`MatchingEngine` is a synchronous deterministic state machine with no queue,
thread, callback or I/O dependency. One caller owns one engine and its frozen
Phase 2 `OrderBook`.

ADR-0003 accepted the high-level boundary that an event pipeline belongs
outside the matching core and must preserve per-symbol order. It did not
resolve the queue implementation, producer model, backpressure, lifecycle,
consumer failure or result handoff semantics required for implementation.

Phase 4 is the Roadmap's Event Pipeline phase. Network, WAL, Replay, Snapshot,
Recovery and production optimization remain later phases.

## Problem

The project needs one bounded asynchronous ingress boundary that can drive the
Phase 3 engine without changing its deterministic semantics. The decision must
answer:

1. which queue implementation and dependency are used;
2. whether one or multiple producer threads may publish;
3. what successful publication means;
4. what happens when the ring is full;
5. who owns the matching thread and engine lifecycle;
6. how `EngineResult` leaves the matching consumer;
7. how command, consumer and result-handler failures are exposed;
8. which wait strategies are correctness defaults versus benchmark variables;
9. which responsibilities remain deferred.

Without these decisions, an implementation could silently reorder command
sequences, drop commands, block an ingress thread indefinitely, continue after
uncertain mutation, or leak Disruptor mechanics into `MatchingEngine`.

## Options Considered

### Option A — LMAX Disruptor, single producer and single matching consumer

Use `com.lmax:disruptor:4.0.0` with `ProducerType.SINGLE`, preallocated command
slots, one event handler and an explicit bounded `tryPublish` API.

Advantages:

- established bounded inter-thread sequencing implementation;
- explicit single-producer optimization and consumer gating;
- preallocated event slots and configurable wait strategies;
- avoids implementing a custom concurrent ring buffer in this Phase.

Costs:

- adds a critical third-party dependency;
- lifecycle and exception behavior must be wrapped behind a project boundary;
- wait-strategy choices affect CPU use and tail latency and require evidence.

**Proposed decision: select.**

### Option B — `ArrayBlockingQueue`

This provides a familiar bounded baseline and simple lifecycle, but uses locks
and condition signalling and does not match the planned RingBuffer boundary.
It remains a useful conceptual comparison, not the Phase 4 implementation.

**Proposed decision: reject for the implementation baseline.**

### Option C — custom SPSC ring buffer

This could minimize dependencies, but would require proving publication,
visibility, wrap, capacity and shutdown correctness before it adds product
value. It conflicts with the rule against unjustified lock-free complexity.

**Proposed decision: reject.**

### Option D — multi-producer Disruptor

Multiple publishers are attractive for future network ingress, but physical
publication order can diverge from upstream command sequence order. Resolving
that requires a sequencing/admission layer not present in the current system.

**Proposed decision: defer to a future ingress/network ADR.**

## Proposed Decision

### 1. Dependency Boundary

Phase 4 uses `com.lmax:disruptor:4.0.0` inside the pipeline package only. The
version is available from Maven Central, requires Java 11 or later and uses
Apache License 2.0. The project remains on Java 21.

No LMAX type appears in `domain`, `orderbook`, or `engine` public contracts.
Pipeline callers use project-owned types.

References:

- <https://lmax-exchange.github.io/disruptor/user-guide/>
- <https://github.com/LMAX-Exchange/disruptor/releases/tag/4.0.0>
- <https://central.sonatype.com/artifact/com.lmax/disruptor/4.0.0>

### 2. Ownership and Topology

The Phase 4 topology is:

```text
one external producer thread
    -> bounded Disruptor command ring
    -> one pipeline-owned matching consumer thread
    -> one pipeline-owned MatchingEngine
    -> synchronous non-blocking EngineResultHandler
```

The pipeline takes exclusive ownership of its `MatchingEngine` for its entire
running lifecycle. Callers must not invoke that engine directly while the
pipeline is running.

Only one publisher thread is supported. Producer ownership is captured and
validated by the pipeline; a different publisher thread is rejected before
claiming a ring slot. Multi-producer publication is not silently enabled.

### 3. Sequence Semantics

`EngineCommand.sequence()` remains the authoritative input command sequence.
The Disruptor cursor/slot sequence is infrastructure metadata only and must
never be copied into `Sequence`, `TradeId` or `EventSequence`.

The pipeline does not allocate, rewrite, sort, buffer-by-command-sequence or
repair commands. Single-producer publication order must equal command order.
`MatchingEngine` retains exact-next sequence validation.

### 4. Capacity and Backpressure

Ring capacity is fixed at construction, must be a power of two and must satisfy
the minimum documented capacity. Runtime resizing is not supported.

The public submission boundary is non-blocking:

```text
tryPublish(command)
    -> ACCEPTED when one slot was published
    -> FULL when no slot was available
```

`ACCEPTED` means enqueued in memory only. It does not mean applied, durable,
published downstream or recoverable. `FULL` does not consume or mutate the
command and permits the same command to be retried. The pipeline never drops,
overwrites or internally reorders a command.

No blocking `publish`, unbounded queue or hidden retry loop is part of Phase 4.

### 5. Event Slot and Allocation Boundary

The ring preallocates mutable infrastructure slots. A producer writes one
immutable `EngineCommand` reference into a claimed slot. The consumer reads
the reference, invokes the engine, then clears the slot in `finally` so the
command cannot be retained across a full ring wrap.

Phase 4 does not redesign the immutable command/result graph or claim zero
allocation. Event-slot reuse is infrastructure reuse only.

### 6. Result Handoff

After a command is successfully applied, the matching consumer invokes one
project-owned `EngineResultHandler` synchronously on the same consumer thread.
The handler contract permits deterministic in-memory handoff only; blocking
I/O, network calls, WAL writes, logging and arbitrary thread switching are
outside the approved boundary.

The callback exists in the pipeline adapter, not in `MatchingEngine`. An
asynchronous output ring and publication guarantees require a later Phase.

### 7. Lifecycle

The public pipeline lifecycle is explicit:

```text
NEW -> RUNNING -> DRAINING -> STOPPED
                   |
                   -> FAILED
RUNNING ----------> FAILED
```

- `start` is single-use and starts the matching consumer.
- publication is permitted only while `RUNNING`.
- graceful shutdown stops new publication, drains accepted commands and waits
  up to a caller-supplied timeout.
- a drain timeout halts processing and records `FAILED`; accepted but
  unprocessed commands are reported as a limitation, not silently called
  completed.
- restart of the same instance is not supported.

### 8. Failure Semantics

The following are terminal pipeline failures:

- `MatchingEngine.process` throws after a command was accepted by the ring;
- the result handler throws after engine application;
- an unexpected consumer/infrastructure exception occurs;
- graceful drain times out.

On terminal failure, the pipeline records the first cause, rejects new
publication and stops further command consumption. It must not continue from
an uncertain state. The failure is observable through project-owned pipeline
state/cause APIs.

Pre-publication errors such as null input, invalid lifecycle or producer-thread
violation do not claim a slot and do not fail the owned engine.

Phase 4 provides fail-stop behavior, not crash recovery. Recovery is deferred
until command WAL semantics are implemented.

### 9. Wait Strategy

`BLOCKING` is the correctness and portable default because it has conservative
CPU behavior. `YIELDING` and `BUSY_SPIN` are allowed only as explicit
configuration and controlled benchmark variables.

No wait strategy may be presented as a production recommendation before
reproducible benchmark and CPU-topology evidence. Correctness tests use the
blocking mode unless a test specifically verifies configuration mapping.

### 10. Public Contract

The Phase 4 public package may introduce only these concepts:

- immutable pipeline configuration;
- wait-mode enum;
- publish outcome enum;
- lifecycle state enum;
- result-handler interface;
- final pipeline lifecycle/submit facade.

Exact names are frozen by the approved Task plan. Existing `MatchingEngine`,
`EngineCommand`, `EngineResult`, Domain and OrderBook APIs remain unchanged.

## Invariants

If approved, implementation must preserve:

1. exactly one producer thread and one matching consumer thread;
2. one pipeline exclusively owns one engine while running;
3. ring order equals command publication order;
4. command `Sequence` is never derived from a ring sequence;
5. no accepted slot is silently dropped or overwritten;
6. `FULL` is non-mutating and retryable;
7. one accepted command is processed at most once by the consumer;
8. result order equals engine application order;
9. every consumed slot is cleared;
10. consumer or handler failure is terminal and observable;
11. equal command streams produce results equal to direct synchronous engine
    execution;
12. Phase 2 OrderBook and Phase 3 engine semantics remain unchanged.

## Consequences and Risks

Positive:

- the synchronous deterministic engine remains isolated from scheduling;
- bounded backpressure becomes explicit and testable;
- the project gains a measurable inter-thread handoff baseline;
- later network and persistence adapters can target a stable pipeline boundary.

Trade-offs:

- a third-party concurrency dependency enters the core module;
- one producer does not yet serve multiple network channels;
- the synchronous result handler can stall the matching thread if its contract
  is violated;
- accepted in-memory commands can be lost on process crash;
- fail-stop after applied-command handler failure requires future recovery.

No durability, exactly-once, lock-free, wait-free, throughput, latency or
production-readiness claim follows from this decision.

## Verification Plan

| Area | Required evidence |
| --- | --- |
| Configuration | capacity, power-of-two, wait-mode and null validation |
| Lifecycle | start once, publish only while running, drain, stop and timeout |
| Producer ownership | same producer accepted; different thread rejected before claim |
| Backpressure | deterministic saturation returns `FULL`; retry preserves command |
| Ordering | results preserve exact command and match-result order |
| Determinism | pipeline and direct engines produce equal ordered results |
| Failure | invalid sequence, engine failure and handler failure produce terminal state |
| Retention | consumed command references are cleared from reusable slots |
| Boundary | no modification to Domain, OrderBook or existing Engine API |
| Build | focused tests, full `mvn verify`, Checkstyle and exact-SHA CI |

Benchmark evidence compares direct synchronous engine and pipeline overhead,
capacity and wait mode. It must distinguish producer-side enqueue latency from
batch end-to-end completion and may not claim network or durable latency.

## Explicitly Deferred

- multi-producer admission and sequence arbitration;
- Netty, decoder and binary protocol;
- WAL, flush policy, Replay, Snapshot and Recovery;
- asynchronous result/egress ring;
- market orders;
- multi-symbol routing;
- thread affinity, CPU isolation or production wait-strategy selection;
- custom ring buffer, off-heap events and allocation optimization;
- Release or production deployment.

## Human Decision Gate

| ID | Proposed decision | Current state |
| --- | --- | --- |
| D1 | LMAX Disruptor 4.0.0 behind a project-owned pipeline boundary | Approved |
| D2 | Single external producer and one pipeline-owned matching consumer | Approved |
| D3 | Preserve command Sequence; ring sequence remains infrastructure-only | Approved |
| D4 | Bounded non-blocking `tryPublish` with explicit `ACCEPTED` / `FULL` | Approved |
| D5 | Synchronous deterministic in-memory result handler on consumer thread | Approved |
| D6 | Explicit single-use lifecycle, graceful drain and fail-stop errors | Approved |
| D7 | Blocking default; Yielding/BusySpin only explicit benchmark variables | Approved with condition |
| D8 | Network, WAL/Replay, output ring and production optimization deferred | Approved |

## Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-21 | Human Developer | `Proposal Authorized` | Create a complete Phase 4 Blueprint Proposal only. Implementation remains unauthorized pending Human Blueprint Approval. |
| 2026-08-21 | Human Developer | `Approved` | D1-D8 and TASK-010 through TASK-013 approved through the Phase Blueprint. v0.2.0 baseline, OrderBook and MatchingEngine production files remain immutable; `BLOCKING` remains default and cannot change automatically from benchmark results; Phase Closure requires separate Human approval. |

## Implementation Evidence

The approved decision is implemented behind the project-owned `pipeline`
boundary. TASK-010 added the Disruptor dependency and immutable contracts;
TASK-011 added the bounded single-producer lifecycle facade;
TASK-012 verified deterministic results, backpressure, failure and lifecycle
behavior; TASK-013 added the component-level JMH evidence and synchronized the
architecture documentation. Existing Domain, OrderBook and MatchingEngine
production paths remain unchanged.

The benchmark is recorded in
[`pipeline.md`](../benchmark/pipeline.md). It separates direct synchronous
processing, producer admission and batch completion, and treats capacities and
wait modes as evidence variables only. `BLOCKING` remains the default; no
production wait-strategy recommendation or end-to-end performance claim
follows from the run.
