# Phase 6 Blueprint — Binary Network Protocol and Single-Session Gateway

## 1. Executive Status

| Field | Value |
| --- | --- |
| Phase | `Phase 6 — Binary Network Protocol and Single-Session Gateway` |
| Blueprint Status | `Approved` |
| Owner | Human Developer |
| Architect | Codex / Sol high-equivalent architecture pass |
| Created | `2026-08-21` |
| Updated | `2026-08-21` |
| Baseline | `v0.4.0-engineering-baseline` -> `f1e453a`; proposal base `2cf34b5` |
| Blueprint Branch | `docs/phase6-network-protocol-blueprint` |
| Planned Tasks | `TASK-20260821-019` through `TASK-20260821-023` |
| Next Gate | `TASK-020 Evidence Gate / exact-SHA CI` |

## 2. Phase Goal

Deliver a versioned binary TCP protocol and a bounded single-session Netty
gateway around the frozen Phase 4 pipeline. The Phase must prove that valid
Submit/Cancel requests cross a real loopback TCP boundary, receive gateway-
assigned command Sequence values, preserve matching/result order and expose
bounded backpressure and fail-stop behavior.

This is the next accepted delivery boundary after the Phase 5 persistence and
offline replay foundation. It creates a network engineering baseline; it does
not create a durable live exchange or Product Release.

## 3. Non-Goals and Frozen Boundaries

Not included:

- live Pipeline/WAL integration or durable client acknowledgement;
- WAL format changes, Snapshot, state restore or online Recovery;
- multiple active clients, pipelined requests, reconnect, retry deduplication
  or session recovery;
- TLS, authentication, authorization, rate limiting or Internet exposure;
- native epoll/kqueue/io_uring transports or platform-specific dependencies;
- multi-symbol routing, Market orders, asynchronous output ring, replication
  or HA;
- performance optimization, thread affinity or production tuning;
- deployment, Release Candidate or Product Release.

Frozen production paths with zero permitted modifications:

```text
src/main/java/com/ultralatency/matching/domain/**
src/main/java/com/ultralatency/matching/orderbook/**
src/main/java/com/ultralatency/matching/engine/**
src/main/java/com/ultralatency/matching/persistence/wal/**
src/main/java/com/ultralatency/matching/recovery/**
```

Existing Phase 4 pipeline behavior is frozen except one explicitly proposed,
additive terminal-failure observer in `MatchingEnginePipeline`. The current
constructor and all current public semantics must remain compatible.

The annotated tags `v0.1.0` through `v0.4.0-engineering-baseline` remain
immutable. `.vscode/` remains unrelated, untracked and untouched.

## 4. Current State and Dependencies

Verified baseline:

- Java 21 Maven reactor with 114 passing tests and Checkstyle zero;
- deterministic synchronous `MatchingEngine` with exact-next Sequence;
- bounded `MatchingEnginePipeline` with one producer and one consumer;
- `tryPublish` returns in-memory `ACCEPTED` or retryable `FULL`;
- ordered immutable `EngineResult` and `MatchResult` values;
- Command WAL v1 and strict offline replay, deliberately not live-integrated;
- Netty is not present.

Accepted upstream decisions:

- ADR-0001 single-thread matching ownership;
- ADR-0003 pipeline outside matching core;
- ADR-0011 upstream Command Sequence and deterministic result ownership;
- ADR-0012 SPSC bounded pipeline/backpressure/lifecycle;
- ADR-0013 command WAL authority independent from transport.

Known limitation carried forward: `FileChannel.force(true)` dynamic failure was
not injected in Phase 5 and no hardware power-loss safety is claimed. Phase 6
does not change that boundary.

## 5. ADR Set and Decision Matrix

All Phase 6 architecture decisions are drafted in
[`ADR-0014`](../../docs/adr/ADR-0014-network-protocol-and-single-session-gateway.md).

| ID | ADR | Proposed Decision | Scope / Constraint | Approval Result |
| --- | --- | --- | --- | --- |
| D1 | ADR-0014 | Network adapter is not persistence authority | no live WAL integration | Approved |
| D2 | ADR-0014 | Netty 4.2.17.Final BOM, transport+codec, Java NIO | no native/TLS; Netty types isolated | Approved |
| D3 | ADR-0014 | big-endian protocol v1 with exact 16-byte header | max frame 104; strict reserved fields | Approved |
| D4 | ADR-0014 | exact Submit/Cancel layouts; gateway allocates Sequence | request ID is transport-only | Approved |
| D5 | ADR-0014 | one active session and one request in flight | one worker is sole pipeline producer | Approved |
| D6 | ADR-0014 | fixed bounded Command/Match/Error response frames | match list order is observable | Approved |
| D7 | ADR-0014 | local write completion is not durable/client receipt ACK | ambiguous disconnect outcome documented | Approved |
| D8 | ADR-0014 | fail-stop gateway; additive pipeline failure observer | existing constructor remains compatible | Approved |
| D9 | ADR-0014 | loopback default and strict input/resource validation | no security/Internet-readiness claim | Approved |
| D10 | ADR-0014 | codec and sequential loopback evidence only | no production/multi-client claim | Approved |

Human Blueprint Approval accepts only D1-D10 and TASK-019 through TASK-023 as
written. Any protocol byte/layout change after approval triggers the Exception
Gate.

## 6. Target Architecture

```text
one TCP client session
    -> Netty length-field framing / strict protocol decoder
    -> single-session admission handler
         -> validate exact-next client request ID
         -> allocate candidate Command Sequence
         -> MatchingEnginePipeline.tryPublish
    -> one pipeline consumer / frozen MatchingEngine
    -> ordered EngineResult handler
    -> Netty event-loop result encoder/write
    -> COMMAND_RESULT + ordered MATCH_RESULT frames
```

Identity ownership:

| Identity | Owner | Meaning |
| --- | --- | --- |
| client request ID | client, validated by gateway | per-session transport correlation |
| Command Sequence | gateway | global logical engine input order for this genesis process |
| Disruptor sequence | pipeline | infrastructure slot metadata only |
| EventSequence | MatchingEngine | ordered match output sequence |
| TradeId | MatchingEngine | trade identity |
| WAL segment/offset | WAL | offline storage location, not used live |

Admission state:

```text
IDLE
  -> valid request / ACCEPTED -> IN_FLIGHT
  -> valid request / FULL     -> IDLE after retryable error write

IN_FLIGHT
  -> complete result write    -> IDLE and request next frame
  -> disconnect/write/failure -> FAILED
```

The server never reads a second request while `IN_FLIGHT`. This is the Phase 6
ordering and resource bound, not a future throughput target.

The event-loop handler installs one immutable request/command correlation
before calling `tryPublish`. `FULL` clears it without advancing identities;
`ACCEPTED` commits both identity counters. Pipeline results only schedule work
back onto the owning event loop, preventing a fast-consumer association race.

## 7. Task Decomposition

| Order | Task | Goal | Depends On | Authorized Scope | Report |
| ---: | --- | --- | --- | --- | --- |
| 1 | [`TASK-019`](../active/TASK-20260821-019-phase6-network-protocol-codec.md) | dependency, protocol contracts and exact codecs | Blueprint approval | POM + new protocol/codec packages/tests | cumulative Phase 6 report |
| 2 | [`TASK-020`](../active/TASK-20260821-020-phase6-pipeline-failure-observer.md) | additive terminal pipeline observer | TASK-019 evidence | one existing pipeline file + new interface/tests | cumulative report |
| 3 | [`TASK-021`](../active/TASK-20260821-021-phase6-netty-gateway.md) | single-session Netty gateway and ordered egress | TASK-020 evidence | new network gateway package/tests | cumulative report |
| 4 | [`TASK-022`](../active/TASK-20260821-022-phase6-network-verification.md) | protocol/system/failure evidence | TASK-021 evidence | tests/fixtures and fixes within approved files | cumulative report |
| 5 | [`TASK-023`](../active/TASK-20260821-023-phase6-network-benchmark-docs.md) | codec/loopback benchmark, docs and Closure proposal | TASK-022 evidence | benchmark/docs/evidence only | cumulative + Closure report |

Execution must stop after TASK-023 at Human Phase 6 Closure Review.

## 8. Stage Authorization Matrix

| Task / Stage | Files or Modules | Deliverable | Evidence Gate | Manual Gate? |
| --- | --- | --- | --- | --- |
| TASK-019 contracts/codec | POM; `network.protocol/**`; `network.netty.codec/**` | exact v1 vectors and strict codec | focused tests + full verify + diff + exact-SHA CI | No |
| TASK-020 failure observer | `pipeline/PipelineFailureHandler.java`; additive `MatchingEnginePipeline` edit | at-most-once terminal observer | focused regression + constructor compatibility + CI | No |
| TASK-021 gateway | `network.netty.gateway/**` | lifecycle, admission, result write | integration tests + full verify + CI | No |
| TASK-022 verification | network/pipeline integration tests | fragmentation, ordering, backpressure/failure matrix | repeated focused tests + full verify + CI | No |
| TASK-023 evidence/docs | `NetworkBenchmark`; docs/reports/context | reproducible bounded evidence and Closure proposal | JMH smoke/full + verify + CI | No; stop at Closure |

No routine Human approval is required between Tasks when the previous exact-SHA
CI passes and no Exception Gate is triggered.

## 9. Phase Acceptance Criteria and Invariants

### Functional / Correctness

- [ ] SubmitLimit and CancelOrder golden bytes exactly match ADR-0014.
- [ ] valid loopback requests produce the same ordered EngineResult values as
  direct execution of gateway-assigned commands.
- [ ] `COMMAND_RESULT` is followed by exactly the declared ordered match frames.
- [ ] one active session and one in-flight request are enforced.
- [ ] a second connection is rejected without reaching the pipeline.
- [ ] `FULL` is retryable and consumes neither request ID nor Command Sequence.

### Determinism / Ordering

- [ ] two genesis server runs over the same fixed client request stream produce
  byte-identical ordered response frames except ephemeral socket metadata.
- [ ] gateway Command Sequence begins at 1 and advances only after `ACCEPTED`.
- [ ] request ID, Command Sequence, ring sequence, EventSequence and TradeId
  never substitute for one another.
- [ ] every fragmentation of one valid frame produces the same command/result;
  the framing layer splits coalesced frames in order, while the Gateway rejects
  a second decoded request received before the first result completes and does
  not publish it.

### Failure / Recovery

- [ ] malformed/overlong/unsupported frames fail closed before publication.
- [ ] disconnect, write failure and pipeline failure become terminal and
  observable without continuing uncertain processing.
- [ ] the pipeline failure observer fires at most once and preserves first cause.
- [ ] no test relies on sleep, reflection or production-only test hooks.
- [ ] reconnect, idempotency, live WAL and online recovery remain explicitly
  unverified and unclaimed.

### Compatibility / Boundary

- [ ] frozen Domain/OrderBook/Engine/WAL/Recovery production diff is zero.
- [ ] existing MatchingEnginePipeline constructor and Phase 4 tests remain valid.
- [ ] Netty types do not leak outside `network.netty` implementation boundaries.
- [ ] no native dependency, TLS stack or additional critical library is added.
- [ ] `v0.4.0-engineering-baseline` is not moved or rewritten.

### Completion Evidence

- [ ] TASK-019 through TASK-023 complete in dependency order.
- [ ] focused and full tests, Checkstyle and exact-SHA CI pass.
- [ ] benchmark commands, environment, results and limitations are committed.
- [ ] Blueprint/ADR/Tasks/reports/docs/context are synchronized.
- [ ] Phase Closure Report receives separate Human approval.

## 10. Verification Strategy

| Layer | Required Evidence | Command / Method | Pass Condition |
| --- | --- | --- | --- |
| Protocol unit | golden bytes, round-trip, every invalid header/field | focused JUnit | exact values and fail-closed errors |
| Framing | split at every byte, coalesced frames, max length | Netty `EmbeddedChannel` | one exact decoded request per frame, in wire order |
| Pipeline compatibility | old constructors plus failure observer | existing + focused tests | no Phase 4 regression; callback once |
| TCP integration | real loopback server/client Submit/Cancel/result | bounded timeout/latches, ephemeral port | ordered values/bytes equal |
| Determinism | two genesis runs and every fragmentation of a valid frame | fixed request vectors + response digest | byte/order equality; no pipelining implied |
| Failure | malformed input, second client, FULL, disconnect, handler failure | deterministic integration fixtures | bounded fail-stop behavior |
| Static / Build | all modules and Checkstyle | `mvn verify` | success / zero violations |
| Boundary | tag-to-HEAD path audit | `git diff --name-only v0.4.0...HEAD -- <frozen>` | zero output |
| CI | exact pushed commit | GitHub Actions | completed / success |

Network tests use loopback and ephemeral ports only. They must close channels
and event-loop groups in bounded cleanup and may not rely on external services.

## 11. Benchmark and Profile Strategy

TASK-023 records evidence after correctness gates pass.

Required workloads:

| Workload | Meaning |
| --- | --- |
| protocol decode | fixed Submit/Cancel byte vectors to project request values |
| protocol encode | no-match and multi-match response frame encoding |
| sequential loopback round trip | one request in flight through TCP + pipeline + response |

Record Netty version/modules, allocator, loopback address, CPU/topology, RAM,
OS, JDK/JVM args, GC, JMH version, warmup, measurement, forks, threads, message
mix/sizes, command count and P50/P99/P999 where meaningful.

Benchmark claims are component/loopback only. They do not establish concurrent
connection capacity, durable acknowledgement, Internet latency, production
throughput or Release readiness. Profile and optimization are `Not applicable`
unless an approved Exception Gate extends scope.

## 12. Planned Repository Changes

| File or Directory | Task | Planned Change | Boundary |
| --- | --- | --- | --- |
| `pom.xml`, `core/pom.xml` | TASK-019 | Netty BOM/version and transport/codec modules | no unrelated upgrade |
| `src/main/java/.../network/protocol/**` | TASK-019 | project-owned wire constants/value messages | no Netty types |
| `src/main/java/.../network/netty/codec/**` | TASK-019 | strict framing/decoder/encoder | Netty isolated here |
| `src/test/java/.../network/**` | TASK-019/021/022 | golden, embedded and TCP tests | loopback/temp-only |
| `src/main/java/.../pipeline/PipelineFailureHandler.java` | TASK-020 | additive callback contract | non-blocking; at most once |
| `src/main/java/.../pipeline/MatchingEnginePipeline.java` | TASK-020 | additive constructor/callback wiring only | existing behavior compatible |
| `src/main/java/.../network/netty/gateway/**` | TASK-021 | configuration, lifecycle and server adapter | one session/one in-flight |
| `benchmark/src/main/java/.../NetworkBenchmark.java` | TASK-023 | codec and loopback evidence | no production shortcut |
| `docs/architecture/network.md` | TASK-023 | implemented boundary/limitations | no future claims |
| `docs/benchmark/network.md` | TASK-023 | reproducible evidence | component/loopback claims |
| `README.md`, `docs/architecture/overview.md` | TASK-023 | status/framework sync | Phase 6 only |
| ADR/Blueprint/Tasks/reports/context | all | approval/checkpoint/closure evidence | exact Git/CI facts only |

## 13. Exception Gates

Execution must stop for Human review when:

- any ADR-0014 frame byte, code, length or ordering rule must change;
- multiple sessions, pipelining, reconnect or sequence arbitration is required;
- request ID must become Command Sequence or WAL identity;
- live WAL integration, durable acknowledgement, Snapshot or Recovery enters
  scope;
- any frozen Domain/OrderBook/Engine/WAL/Recovery file or API must change;
- existing pipeline public behavior must break rather than receive the listed
  additive observer;
- a new dependency/module, native transport, TLS/security stack or different
  network framework is proposed;
- result correctness requires unbounded frames, result truncation or weakened
  order assertions;
- a benchmark result drives protocol/default/allocator/transport changes;
- tests require reflection, wall-clock sleeps or production-only seams;
- an acceptance criterion cannot be met without weakening it;
- destructive Git, Release, tag mutation or scope expansion is requested.

## 14. Git, Commit and CI Strategy

- Proposal branch: `docs/phase6-network-protocol-blueprint` from `2cf34b5`.
- Implementation branch after approval:
  `feature/phase6-network-protocol` from the approved proposal commit.
- Planned commits:

```text
docs(phase6): propose network protocol blueprint
build(network): add pinned netty modules
feat(network): add binary protocol codec
feat(pipeline): expose terminal failure observer
feat(network): add single-session tcp gateway
test(network): verify protocol ordering and failure boundaries
perf(network): add codec and loopback baseline
docs(phase6): synchronize network evidence
docs(phase6): prepare network closure
```

- Each Task ends with local gates, logical commit, push and exact-SHA CI.
- Dependent Tasks start only after previous exact-SHA CI PASS.
- Phase Closure separately authorizes merge and candidate tag.
- No squash, force push, shared rebase, amend of published commits, history
  rewrite, existing-tag movement or Release publication.
- `.vscode/` is never staged, modified, deleted or ignored by this Phase.

## 15. Rollback and Compatibility Plan

- TASK-019: remove unused Netty modules/new protocol packages; no runtime data.
- TASK-020: revert additive observer and constructor; existing constructor is
  retained throughout.
- TASK-021: remove new gateway package; core pipeline remains usable directly.
- TASK-022: revert tests only with any defect fix they prove; never weaken
  protocol vectors.
- TASK-023: revert benchmark/docs without changing runtime semantics.

Before merge, abandon the feature branch without affecting
`v0.4.0-engineering-baseline`. After authorized merge, use normal revert
commits. Protocol v1 becomes immutable only if Phase 6 Closure is approved and
the candidate baseline is frozen; future changes then require versioning and a
new ADR, not silent replacement.

There is no deployed user protocol and no migration during initial Phase 6.

## 16. Documentation and Evidence Plan

Proposal artifacts:

- ADR-0014;
- this Blueprint;
- TASK-019 through TASK-023 plans;
- `PHASE-6-network-protocol-blueprint-proposal.md`;
- current-gate synchronization in `AGENT_CONTEXT` and architecture index.

Execution artifacts after approval:

- cumulative `PHASE-6-network-protocol.md` report;
- protocol golden-vector evidence;
- `docs/architecture/network.md` and updated overview;
- `docs/benchmark/network.md` with raw local path/commands/summary/limitations;
- README, ADR, Blueprint, Tasks and context checkpoints;
- `PHASE-6-network-protocol-closure.md`.

## 17. Closure and Baseline Plan

- Closure report: `tasks/reports/PHASE-6-network-protocol-closure.md`.
- Human Closure requires TASK-019..023 complete, exact-SHA CI PASS, protocol
  vectors/system evidence accepted, frozen-path audit zero and claims in scope.
- Candidate baseline tag: `v0.5.0-engineering-baseline`.
- Proposed authorized closure sequence, only after separate approval:

```text
--no-ff merge to master
    -> local master mvn verify / frozen audit
    -> push master / exact-SHA CI PASS
    -> annotated v0.5.0-engineering-baseline
    -> tag CI PASS
    -> archive TASK-019..023 / final context sync
```

- Tag meaning: protocol v1 + single-session Netty NIO gateway + ordered
  loopback evidence.
- Explicitly not a Product Release, durable gateway, secure Internet service,
  multi-client server, Snapshot or online Recovery baseline.
- Phase 7 and Product Release remain unauthorized until separate Human gates.

## 18. Human Phase Blueprint Approval

| Date | Reviewer | Decision | Approved ADRs / Tasks / Stages | Constraints |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | `Proposal Authorized` | Discovery, ADR-0014 draft, TASK-019..023 plans and Complete Blueprint Proposal only | No implementation; stop at Human Blueprint Approval |
| 2026-08-21 | Human Developer | `Approved` | ADR-0014 D1-D10; TASK-019 through TASK-023 in dependency order | Frozen paths; one session/in-flight request; no live WAL, security expansion or Product Release; separate Closure approval |

```text
Blueprint Status: Approved
Implementation: Authorized in dependency order
Current Task: TASK-019
Phase Closure: Not Authorized
Merge / v0.5.0 tag: Not Authorized
Next Gate: TASK-019 Evidence Gate / exact-SHA CI
```

Approval must explicitly confirm D1-D10, TASK-019..023 dependency order, the
single-session/one-in-flight limit, frozen paths, no live WAL/security/recovery
scope, evidence/Exception Gates and separate Phase Closure.

## 19. Execution Checkpoints

| Date | Task / Stage | Result | Evidence | Next State |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Discovery / Blueprint Proposal | Prepared / pushed | `ecf0c27`; baseline 114 tests; Checkstyle 0; frozen diff 0; exact-SHA CI [32485900404](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32485900404) PASS | Human Phase 6 Blueprint Approval |
| 2026-08-21 | Human Blueprint Approval | Approved | ADR-0014 D1-D10 and TASK-019..023 authorized in dependency order | Begin TASK-019; stop on Exception Gate |
| 2026-08-21 | TASK-019 | Completed / Evidence PASS | `fdb68e3`; 120 tests; Checkstyle 0; frozen diff 0; exact-SHA CI [32488339314](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32488339314) PASS | TASK-020 Authorized / Next |

## 20. Phase Closure Checklist

- [x] Blueprint approval recorded and synchronized into ADR-0014/Tasks
- [x] TASK-019 protocol/codec completed
- [ ] TASK-020 pipeline failure observer completed
- [ ] TASK-021 Netty gateway completed
- [ ] TASK-022 network verification completed
- [ ] TASK-023 benchmark/documentation completed
- [ ] all automated evidence gates and exact-SHA CI pass
- [ ] frozen production-path diff is zero
- [ ] no unresolved Exception Gate
- [ ] architecture and documentation synchronized
- [ ] Phase Closure Report prepared
- [ ] Human Phase Closure Approval recorded
- [ ] authorized merge/tag/baseline actions verified
- [ ] active Tasks moved to completed
- [ ] Phase 7 remains explicitly unauthorized
