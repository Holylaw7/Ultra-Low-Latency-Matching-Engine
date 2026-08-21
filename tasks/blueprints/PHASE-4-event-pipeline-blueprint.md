# Phase 4 Blueprint — Event Pipeline

## 1. Executive Status

| Field | Value |
| --- | --- |
| Phase | `Phase 4 — Event Pipeline` |
| Blueprint Status | `Proposed` |
| Owner | Human Developer |
| Architect | Codex |
| Created | `2026-08-21` |
| Updated | `2026-08-21` |
| Product Baseline | `v0.2.0-engineering-baseline` (`9281124`) |
| Planning Baseline | `bbf909c` |
| Blueprint Branch | `docs/phase4-event-pipeline-blueprint` |
| Planned Tasks | `TASK-20260821-010` through `TASK-20260821-013` |
| Next Gate | `Human Phase Blueprint Approval` |

```text
Blueprint: Proposed
Implementation: Not Authorized
Phase 3 baseline: Frozen
```

## 2. Phase Goal

Deliver a correctness-first, bounded, single-producer event pipeline that owns
one matching consumer thread and invokes the frozen synchronous Phase 3
`MatchingEngine` without changing command, matching, Trade/Execution or
OrderBook semantics.

The Phase creates a measurable inter-thread command handoff baseline with
explicit capacity, backpressure, lifecycle, failure and result-handoff
contracts. It is the Roadmap step between the synchronous engine and future
Network/WAL adapters.

## 3. Non-Goals and Frozen Boundaries

### Explicit Non-Goals

- Netty, decoder, binary protocol or any network I/O.
- WAL, durability acknowledgement, Replay, Snapshot or Recovery.
- asynchronous output/egress ring, MQ, database or event publication.
- multi-producer ingress or multi-symbol routing.
- market, IOC, FOK or other new order semantics.
- thread affinity, CPU pinning, off-heap storage or custom lock-free code.
- modification of `MatchingEngine`, `EngineCommand`, `EngineResult`, Domain or
  OrderBook APIs/algorithms.
- production performance optimization, deployment or Release.
- claims of lock-free/wait-free behavior, end-to-end throughput, production
  latency or durability.

### Frozen Baseline Boundaries

- `v0.2.0-engineering-baseline` remains immutable.
- `src/main/java/com/ultralatency/matching/orderbook/**` has zero permitted
  modifications.
- existing `src/main/java/com/ultralatency/matching/engine/**` files have zero
  permitted modifications; Phase 4 may depend on them only.
- command `Sequence`, `TradeId` and `EventSequence` retain ADR-0005/0011
  semantics.
- `MatchingEngine` remains synchronous and contains no pipeline dependency.

Any need to cross these boundaries triggers the Exception Gate.

## 4. Current State and Dependencies

### Verified Baseline

- Phase 3 is completed, approved and frozen at `9281124`.
- 61 tests and deterministic dual-engine verification pass.
- `MatchingEngine.process(EngineCommand)` provides the synchronous application
  boundary and immutable ordered `EngineResult`.
- one engine owns one frozen Phase 2 OrderBook.
- the current engine has no thread, queue, lock, callback or I/O dependency.
- Phase 2-to-Phase 3 OrderBook production diff is zero.

### Existing Decisions

| Decision | Current effect |
| --- | --- |
| ADR-0001 | one writer owns one symbol's mutable state |
| ADR-0003 | event pipeline sits outside core and preserves per-symbol order |
| ADR-0005 | command Sequence, TradeId and EventSequence are distinct domains |
| ADR-0011 | MatchingEngine stays synchronous; upstream pipeline owns scheduling |

### Proposed Dependency

`com.lmax:disruptor:4.0.0` is proposed for the pipeline package. Official
documentation identifies Disruptor as a bounded inter-thread messaging/ring
buffer library with single/multi-producer sequencers, preallocated event slots
and configurable wait strategies. Maven Central records version 4.0.0 and its
Apache License 2.0 metadata.

- <https://lmax-exchange.github.io/disruptor/user-guide/>
- <https://github.com/LMAX-Exchange/disruptor/releases/tag/4.0.0>
- <https://central.sonatype.com/artifact/com.lmax/disruptor/4.0.0>

These are dependency facts, not project performance evidence.

## 5. ADR Set and Decision Matrix

All Phase 4 implementation decisions are proposed in
[`ADR-0012`](../../docs/adr/ADR-0012-event-pipeline-execution-and-backpressure.md).
ADR-0003 remains the accepted high-level boundary; ADR-0012 supplies the
implementation-level refinement.

| Decision ID | ADR | Proposed Decision | Scope / Constraint | Approval Result |
| --- | --- | --- | --- | --- |
| D1 | ADR-0012 | LMAX Disruptor 4.0.0 behind project types | dependency limited to pipeline internals | Pending |
| D2 | ADR-0012 | single external producer, single matching consumer | no multi-producer ingress | Pending |
| D3 | ADR-0012 | preserve command Sequence; ring sequence is infrastructure-only | no sequence rewriting/sorting | Pending |
| D4 | ADR-0012 | bounded non-blocking `tryPublish` | explicit `ACCEPTED`/`FULL`; no drop/block/overwrite | Pending |
| D5 | ADR-0012 | synchronous in-memory result handler | no I/O; handler failure is terminal | Pending |
| D6 | ADR-0012 | single-use lifecycle, graceful drain and fail-stop | no restart/recovery in this Phase | Pending |
| D7 | ADR-0012 | Blocking default; Yielding/BusySpin benchmark variables | no production recommendation without evidence | Pending |
| D8 | ADR-0012 | defer Network, WAL/Replay, output ring and optimization | Phase 4 scope lock | Pending |

Human Blueprint Approval accepts only D1-D8 and the Tasks/stages listed below.
Before implementation, approval must be synchronized into ADR-0012 and every
Task plan.

## 6. Target Architecture

```text
Single Producer Thread
    -> MatchingEnginePipeline.tryPublish(EngineCommand)
    -> bounded preallocated Disruptor ring
    -> one pipeline-owned consumer thread
    -> frozen MatchingEngine.process(command)
    -> EngineResultHandler.onResult(result)
         [deterministic in-memory handoff only]
```

### Responsibility Map

| Component | Owns | Must not own |
| --- | --- | --- |
| Producer | command creation, command Sequence and retry after `FULL` | ring cursor as business sequence |
| Pipeline facade | lifecycle, producer contract, bounded admission, failure visibility | matching semantics or durability |
| Disruptor adapter | preallocated slots and inter-thread handoff | public Domain/Engine contract |
| Matching consumer | exclusive MatchingEngine invocation and ordered result handoff | network/WAL/blocking I/O |
| MatchingEngine | sequence validation, OrderBook mutation, Trade/Execution results | thread, queue or callback |
| Result handler | deterministic in-memory result observation | blocking I/O or hidden thread switch |

### Proposed Public API Concepts

Names are frozen for Task planning and may change only through an approved
Blueprint amendment:

```text
pipeline/PipelineConfiguration
pipeline/PipelineWaitMode
pipeline/PipelinePublishOutcome
pipeline/PipelineState
pipeline/EngineResultHandler
pipeline/MatchingEnginePipeline
```

Package-private infrastructure may include a mutable command event slot,
Disruptor adapter, consumer handler and exception handler.

### Lifecycle

```text
NEW
  -> RUNNING
       -> DRAINING -> STOPPED
       -> FAILED
  -> FAILED (invalid terminal infrastructure failure)
```

`ACCEPTED` means only that an in-memory slot was published. It is not an
application, output, durability or recovery acknowledgement.

## 7. Task Decomposition

| Order | Task | Goal | Depends On | Authorized Scope after Blueprint approval | Report |
| ---: | --- | --- | --- | --- | --- |
| 1 | [`TASK-010`](../active/TASK-20260821-010-phase4-pipeline-foundation.md) | dependency and project-owned API/configuration foundation | ADR-0012 approval | POM plus new pipeline boundary types/tests | `tasks/reports/PHASE-4-event-pipeline.md` |
| 2 | [`TASK-011`](../active/TASK-20260821-011-phase4-pipeline-core.md) | bounded Disruptor adapter, lifecycle and engine consumer | TASK-010 | new pipeline implementation/tests only | same cumulative report |
| 3 | [`TASK-012`](../active/TASK-20260821-012-phase4-pipeline-verification.md) | determinism, ordering, saturation, lifecycle and fail-stop evidence | TASK-011 | pipeline tests/fixtures only | same cumulative report |
| 4 | [`TASK-013`](../active/TASK-20260821-013-phase4-pipeline-benchmark-docs.md) | benchmark baseline, documentation and closure preparation | TASK-012 | benchmark/docs/evidence only | same cumulative report plus Closure Report |

The four Tasks inherit approval only if Human Blueprint Approval explicitly
lists them. They remain `Proposed` until that approval is recorded.

## 8. Stage Authorization Matrix

| Task / Stage | Files or Modules | Deliverable | Evidence Gate | Manual Gate? |
| --- | --- | --- | --- | --- |
| TASK-010 Foundation | parent/core POM; new `pipeline` API/config types; tests | dependency and validated boundary | focused tests + `mvn verify` + diff + CI | No |
| TASK-011 Core | new `pipeline` implementation and tests | running SPSC pipeline with backpressure/lifecycle | focused integration + regression + diff + CI | No |
| TASK-012 Verification | pipeline tests and deterministic fixtures | behavioral equivalence and failure evidence | focused test matrix + repeated regression + CI | No |
| TASK-013 Benchmark | benchmark module and pipeline benchmark report | reproducible component evidence | JMH smoke/full runs + result review + CI | No |
| TASK-013 Documentation | ADR/architecture/README/context/report | synchronized Phase evidence | link/scope/diff checks + CI | No |
| Phase Closure | Closure Report only | consolidated approval request | full verify + evidence audit | **Yes** |

After Blueprint approval, Tasks 010-013 may execute continuously in order when
their evidence gates pass and no Exception Gate is triggered. Only Phase
Closure is a planned Human gate.

## 9. Phase Acceptance Criteria and Invariants

### Functional / Correctness

- [ ] a valid command can be published and processed asynchronously;
- [ ] one accepted command produces exactly the same `EngineResult` as direct
  synchronous engine execution;
- [ ] ring saturation returns `FULL` without mutation, loss or internal retry;
- [ ] retrying the same command after capacity becomes available is valid;
- [ ] start, drain, stop and terminal failure states are explicit and observable;
- [ ] every consumed event slot releases its command reference;
- [ ] pipeline failure rejects new publication and preserves the first cause.

### Determinism / Ordering

- [ ] result collection order is significant and equals command application order;
- [ ] fixed command streams produce equal direct-engine and pipeline results;
- [ ] TradeId, EventSequence, Trade and Execution fields remain equal;
- [ ] Disruptor ring sequences never enter business values or comparisons;
- [ ] no wall clock, scheduler race, hash iteration or random identity defines
  business order.

### Failure / Recovery

- [ ] null, invalid lifecycle and producer-thread violations fail before slot claim;
- [ ] sequence-invalid accepted commands cause terminal pipeline failure;
- [ ] result-handler failure after mutation causes terminal fail-stop;
- [ ] drain timeout is observable and does not claim clean completion;
- [ ] no Recovery claim is made; crash/process-loss behavior is documented as
  unsupported until a later WAL/Recovery Phase.

### Compatibility / Boundary

- [ ] Phase 2 OrderBook production diff remains zero;
- [ ] existing Phase 3 Engine and Domain production files remain unchanged;
- [ ] no LMAX type leaks into existing public packages;
- [ ] no Network, WAL, Replay, Snapshot, output ring or market-order work appears;
- [ ] dependency version, license and purpose are recorded;
- [ ] `v0.2.0-engineering-baseline` remains unchanged.

### Completion Evidence

- [ ] Tasks 010-013 completed with cumulative report checkpoints;
- [ ] focused tests, full `mvn verify`, Checkstyle and exact-SHA CI pass;
- [ ] benchmark methodology/result/limitations are committed;
- [ ] ADR-0012 and architecture documentation match implementation;
- [ ] Phase Closure Report prepared and Human approved;
- [ ] approved merge/tag actions verified.

## 10. Verification Strategy

| Layer | Required Evidence | Command / Method | Pass Condition |
| --- | --- | --- | --- |
| Unit | configuration, state and outcome value contracts | `mvn -pl core -am -Dtest=... test` | all focused tests pass |
| Integration | real consumer thread, ring, engine and result handler | pipeline integration tests with deterministic latches/barriers | no timeout/loss/reorder |
| Determinism | direct engine versus pipeline on identical fixed stream | ordered structured equality, at least 1,024 commands | all results and public observations equal |
| Backpressure | deterministic small-capacity saturation | gated consumer; no sleeps as correctness oracle | `FULL` is observed and retry succeeds |
| Failure | invalid sequence, foreign producer, handler exception, drain timeout | public API only; no reflection/test hooks | terminal state/cause and no further acceptance |
| Retention | slot clearing across wrap | weak/observable adapter contract or package-level infrastructure test | no stale command reference after consume |
| Regression | all Phase 1-3 tests | `mvn verify` | 61 existing tests plus new tests pass |
| Static / Build | Java 21, compiler and Checkstyle | `mvn verify`; `git diff --check` | reactor 3/3 SUCCESS; 0 violations |
| CI | exact commit validation | GitHub Actions push run | success for every required checkpoint |

Tests must coordinate with latches/barriers and bounded timeouts. Arbitrary
sleep is not a correctness oracle. No production test hook or reflection is
authorized.

## 11. Benchmark and Profile Strategy

This Phase adds component-level pipeline evidence; it does not establish
network or durable end-to-end performance.

### Baselines and Comparisons

- B0: direct synchronous `MatchingEngine.process` baseline under the same
  command mix;
- P1: producer-side `tryPublish` acceptance cost;
- P2: batch publication-to-consumption completion throughput;
- wait modes: `BLOCKING`, `YIELDING`, `BUSY_SPIN`;
- capacities: fixed powers of two such as 1,024 and 65,536;
- workloads: no-match, single-match and deterministic mixed stream;
- producer model: one producer only.

### Required Metrics

- operations/second with confidence/error output;
- producer-side latency distribution where JMH mode supports it;
- completed command count and result count;
- allocation rate using JMH GC profiler where practical;
- CPU/JDK/OS/JVM args, warmup, measurement, forks and thread topology;
- timeout, saturation and benchmark-harness limitations.

Producer enqueue latency must not be labelled end-to-end latency. A wait mode
is kept as the default only if correctness remains identical and evidence
supports the CPU/latency trade-off; otherwise `BLOCKING` remains default.

Raw JMH/JFR artifacts remain ignored unless separately authorized. Committed
reports record generation commands, summarized data and limitations.

## 12. Planned Repository Changes

| File or Directory | Task / Stage | Planned Change | Boundary |
| --- | --- | --- | --- |
| `pom.xml`, `core/pom.xml` | TASK-010 | pin Disruptor 4.0.0 and add core dependency | no unrelated dependency upgrade |
| `src/main/java/com/ultralatency/matching/pipeline/**` | TASK-010/011 | new project-owned API and Disruptor adapter | new package only |
| `src/test/java/com/ultralatency/matching/pipeline/**` | TASK-010-012 | contract/integration/determinism/failure tests | public API; no reflection/hooks |
| `benchmark/src/main/java/.../PipelineBenchmark.java` | TASK-013 | component benchmark | no benchmark-only production path |
| `docs/adr/ADR-0012-*.md` | all | decision synchronization | no silent decision change |
| `docs/architecture/overview.md` | TASK-013 | current Phase status and delivery boundary | no future capability claim |
| `docs/architecture/pipeline.md` | TASK-013 | verified implementation/lifecycle/backpressure | distinguish fact and benchmark evidence |
| `docs/benchmark/pipeline.md` | TASK-013 | reproducible method, results and limits | component evidence only |
| `README.md`, `.codex/AGENT_CONTEXT.md` | checkpoints/closure | compact current state | no historical duplication |
| `tasks/active|completed`, `tasks/reports` | all | plan/checkpoint/closure evidence | Blueprint-governed only |

Forbidden production paths:

```text
src/main/java/com/ultralatency/matching/orderbook/**
existing src/main/java/com/ultralatency/matching/engine/**
src/main/java/com/ultralatency/matching/domain/**
```

## 13. Exception Gates

Execution must stop for Human review when any standard governance Exception
Gate occurs, including:

- ADR or invariant conflict;
- scope expansion or unlisted Task/stage/file boundary;
- unapproved public API compatibility break;
- matching, command, TradeId or EventSequence semantic change;
- protocol/WAL/Snapshot/persistence/recovery format change;
- new critical dependency or different implementation strategy;
- verification exposing an architecture problem;
- inability to meet an acceptance criterion without weakening it;
- destructive Git, Release or other separately governed action.

Phase-specific Exception Gates:

- implementation requires multiple producer threads;
- producer publication order cannot preserve command Sequence order;
- any existing Domain, OrderBook or Engine production file must change;
- result handoff requires blocking I/O, an output ring or a new guarantee;
- Disruptor 4.0.0 is incompatible with Java 21/project build;
- wait-strategy configuration requires hidden production test hooks;
- graceful drain cannot be made bounded without command loss ambiguity;
- benchmark evidence is used to request an optimization/default change not
  explicitly authorized here;
- WAL, Replay, Network or durability semantics become necessary.

## 14. Git, Commit and CI Strategy

- Planning branch: `docs/phase4-event-pipeline-blueprint`.
- Implementation branch after approval: `feature/phase4-event-pipeline`, based
  on the approved planning commit from current `master`.
- Commit sequence:
  1. `docs(phase4): propose event pipeline blueprint`
  2. `feat(pipeline): add event pipeline foundation`
  3. `feat(pipeline): implement bounded matching pipeline`
  4. `test(pipeline): verify determinism and failure boundaries`
  5. `perf(pipeline): add event pipeline baseline`
  6. `docs(phase4): synchronize event pipeline evidence`
  7. Closure approval/evidence commits as required.
- Push after each logical Task checkpoint with exact-SHA CI evidence.
- Every code checkpoint runs focused tests and full `mvn verify` before push.
- Planning proposal runs documentation scope/link/diff checks and CI.
- Merge strategy: `--no-ff` after Human Phase Closure Approval.
- No squash, rebase, history rewrite, force push or tag movement.
- Candidate tag is created only after verified master and separate Human
  Closure authorization.

## 15. Rollback and Compatibility Plan

### Per-Task Rollback

- TASK-010: revert dependency and new unused pipeline types; no state/data
  migration exists.
- TASK-011: revert the new pipeline package; Phase 3 synchronous API remains
  fully usable.
- TASK-012: revert tests/fixtures only if the corresponding implementation is
  also reverted; never weaken assertions to retain code.
- TASK-013: revert benchmark/docs without changing product behavior.

### Phase Rollback

If Phase 4 cannot satisfy correctness or dependency gates, revert the Phase 4
commits or abandon the feature branch. `v0.2.0-engineering-baseline` remains a
complete runnable fallback.

No protocol, WAL, Snapshot or persistent data exists, so there is no data
migration or backward reader requirement in this Phase. Partial work is not a
new engineering baseline and must not be merged as a completed Phase.

## 16. Documentation and Evidence Plan

- ADR-0012 decision status and D1-D8 synchronization;
- one cumulative implementation report:
  `tasks/reports/PHASE-4-event-pipeline.md`;
- one Closure Report:
  `tasks/reports/PHASE-4-event-pipeline-closure.md`;
- architecture overview and pipeline boundary;
- pipeline benchmark method/result/limitations;
- README capability/non-goal update;
- compact `AGENT_CONTEXT.md` checkpoints;
- exact branch/commit/push/CI evidence;
- dependency version, license and official source references.

Each Task updates the cumulative report with only its delta and evidence.

## 17. Closure and Baseline Plan

- Closure report: `tasks/reports/PHASE-4-event-pipeline-closure.md`.
- Human Closure Approval requires Tasks 010-013 complete, D1-D8 synchronized,
  all acceptance criteria satisfied, no unresolved Exception Gate, local full
  verification and exact-SHA CI success.
- Master integration: normal `--no-ff` merge followed by `mvn verify`, push and
  exact master-SHA CI.
- Candidate tag: `v0.3.0-engineering-baseline`.
- Tag annotation: Phase 4 Event Pipeline correctness and component benchmark
  baseline; explicitly not a product Release.
- Tag CI must pass before the baseline is called frozen.
- WAL, Replay, Network, durability, output pipeline and production tuning
  remain excluded from the tag.
- Phase 5 remains unauthorized until a separately complete Blueprint is
  proposed and approved.

## 18. Human Phase Blueprint Approval

| Date | Reviewer | Decision | Approved ADRs / Tasks / Stages | Constraints |
| --- | --- | --- | --- | --- |
|  | Human Developer | `Pending` | ADR-0012 D1-D8; TASK-010 through TASK-013 | Implementation remains unauthorized until explicit approval |

Approval checklist:

- [ ] accept ADR-0012 D1-D8;
- [ ] authorize TASK-010 through TASK-013 in dependency order;
- [ ] authorize new pipeline package and Disruptor 4.0.0 dependency;
- [ ] preserve all frozen production paths and explicit non-goals;
- [ ] permit automatic progression through listed non-manual stages when
  evidence passes and no Exception Gate exists;
- [ ] retain Human Phase Closure Approval before merge/tag/freeze.

```text
Blueprint Status: Proposed
Implementation: Not Authorized
Next Gate: Human Phase Blueprint Approval
```

## 19. Execution Checkpoints

| Date | Task / Stage | Result | Evidence | Next State |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Blueprint Proposal | Prepared / pushed | baseline `mvn verify`: 61 tests, 0 failures, 0 Checkstyle violations; content commit `f0f18ff`; exact-SHA CI [32455576290](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32455576290) PASS | Human Blueprint Approval |

## 20. Phase Closure Checklist

- [ ] Blueprint approval recorded and synchronized into ADR-0012/Tasks
- [ ] TASK-010 foundation completed
- [ ] TASK-011 pipeline core completed
- [ ] TASK-012 determinism/failure verification completed
- [ ] TASK-013 benchmark/documentation completed
- [ ] all automated evidence gates pass
- [ ] no unresolved Exception Gate
- [ ] Phase 2/3 frozen production paths have zero diff
- [ ] architecture and documentation synchronized
- [ ] Phase Closure Report prepared
- [ ] Human Phase Closure Approval recorded
- [ ] authorized merge/tag/baseline actions verified
- [ ] active Tasks moved to completed
- [ ] Phase 5 remains explicitly unauthorized
