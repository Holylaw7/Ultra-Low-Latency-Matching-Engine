# Phase 9 Blueprint — System Qualification, Performance Characterization and Long-Run Reliability

## 1. Executive Status

| Field | Value |
| --- | --- |
| Phase | `Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability` |
| Blueprint Status | `Approved` |
| Owner | Human Developer |
| Architect | Codex / Sol High |
| Created | `2026-08-23` |
| Updated | `2026-08-23` |
| Baseline | `v0.7.0-engineering-baseline` |
| Blueprint Branch | `docs/phase9-system-qualification-blueprint` |
| Implementation Branch | `feature/phase9-system-qualification` |
| Planned Tasks | `TASK-20260823-035` through `TASK-20260823-040` |
| Next Gate | `TASK-036 Implementation / Evidence Gate` |

## 2. Phase Goal

Build a reproducible qualification harness around the frozen Phase 8 runtime
that proves deterministic long-run operation, repeated restart/recovery,
bounded resource behavior and full-path performance characterization on a
recorded host and workload.

Phase 9 is an engineering qualification baseline. It is not a Product Release
or a production-readiness declaration.

## 3. Non-Goals and Frozen Boundaries

The following are not authorized:

```text
Production runtime changes
Matching or OrderBook changes
Protocol/WAL/Snapshot format changes
live BUFFERED durability
multiple sessions or request pipelining
reconnect/deduplication/exactly-once
TLS/auth/security expansion
WAL retention/compaction
hot Snapshot
replication/HA
deployment packaging
CPU affinity/OS tuning
production optimization
Product Release
```

`src/main/java/**`, existing tests, existing benchmark classes, Protocol v1,
WAL v1, Snapshot v1, recovery semantics and all existing baseline tags remain
frozen. `.vscode/` remains untouched and untracked.

## 4. Current State and Dependencies

`v0.7.0-engineering-baseline` contains the Protocol v1 gateway, live
WAL-before-execute path, deterministic pipeline, Command WAL, Snapshot v1,
PURE_WAL/SNAPSHOT_THEN_WAL recovery and listener-last live handoff. Existing
Phase 8 evidence is component/local-host and does not cover the required
long-run or repeated process lifecycle campaigns.

Governing decision: [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md).

## 5. ADR and Decision Matrix

| Decision | ADR | Approved Decision | Scope |
| --- | --- | --- | --- |
| D1-D16 | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) | Qualification-only system harness, fixed workload, long-run/restart evidence, JMH/JFR characterization and bounded claims | No production semantic or format changes; optimization requires a separate gate |

## 6. Target Architecture

```text
Qualification Orchestrator
        |
        +-- Versioned Workload Manifest
        |
        +-- Protocol v1 TCP Client
        |       |
        |       v
        |   Recoverable Durable Server
        |       -> WAL-before-execute
        |       -> Pipeline
        |       -> MatchingEngine
        |
        +-- Restart / Child Process Controller
        +-- Strict WAL/Snapshot/Probe Validator
        +-- Resource Sampler (JFR/GC/lifecycle)
        +-- Evidence Manifest + SHA-256
```

The qualification layer is downstream of frozen runtime components and may
not bypass their public boundaries.

## 7. Task Decomposition

| Order | Task | Goal | Depends On | Report |
| ---: | --- | --- | --- | --- |
| 1 | `TASK-20260823-035` | Qualification module, immutable contracts, manifest and workload vectors | `v0.7.0` | `tasks/reports/PHASE-9-task-035.md` |
| 2 | `TASK-20260823-036` | Public Protocol v1 end-to-end harness | TASK-035 PASS | `tasks/reports/PHASE-9-task-036.md` |
| 3 | `TASK-20260823-037` | Full soak and resource lifecycle evidence | TASK-036 PASS | `tasks/reports/PHASE-9-task-037.md` |
| 4 | `TASK-20260823-038` | Restart, forced termination and recovery convergence | TASK-037 PASS | `tasks/reports/PHASE-9-task-038.md` |
| 5 | `TASK-20260823-039` | Full-path JMH baseline and JFR profile | TASK-038 PASS | `tasks/reports/PHASE-9-task-039.md` |
| 6 | `TASK-20260823-040` | Evidence audit, documentation and Closure Proposal | TASK-039 PASS | `tasks/reports/PHASE-9-task-040.md` |

## 8. Stage Authorization Matrix

| Task | Authorized Files/Modules | Deliverable | Evidence Gate | Manual Gate |
| --- | --- | --- | --- | --- |
| TASK-035 | `qualification/**`, root `pom.xml`, Phase 9 docs/tasks | Contracts, manifest, workload vectors | focused tests, `mvn verify`, Checkstyle, diff, exact-SHA CI | No |
| TASK-036 | qualification module | Public-boundary quick qualification | 10,000-command/3-cycle smoke | No |
| TASK-037 | qualification module and evidence docs | 60-minute/1,000,000-command soak and resource evidence | Full lane manifest and guards | No |
| TASK-038 | qualification module and evidence docs | restart/termination campaign | per-cycle convergence | No |
| TASK-039 | additive benchmark class and evidence docs | JMH/JFR characterization | full matrix, profile and metadata | No |
| TASK-040 | reports/docs/context | Closure Proposal and synchronized evidence | reviewer PASS and exact-SHA CI | Stop for Closure |

## 9. Phase Acceptance Criteria

### Correctness and Determinism

- Every accepted request has exactly one correlated result set.
- Command Sequence is contiguous and RequestId semantics remain session-local.
- TradeId/EventSequence and final public probe are deterministic.
- Strict WAL scan, checkpoint digest and recovery digest pass.
- PURE_WAL and SNAPSHOT_THEN_WAL converge after each recovery cycle.

### Long-Run Reliability

- Full run reaches both 60 minutes and 1,000,000 accepted commands.
- No unexpected terminal state, timeout, mismatch or unexplained exception.
- Owned threads, locks, listener, temporary files and inventory satisfy D7.
- JFR/GC and manifest evidence are complete.

### Restart Reliability

- 20 graceful restart cycles pass.
- 10 acknowledged-boundary forced-termination cycles pass.
- Every cycle proves sequence/checkpoint/probe convergence.
- Ambiguous in-flight outcomes are never upgraded to exactly-once claims.

### Performance Evidence

- Full declared JMH matrix completes without omitted failures.
- Three forks, declared warmup/measurement and complete environment metadata
  are recorded.
- P50/P95/P99/P999/max, throughput, allocation and GC evidence are present.
- JFR reports observations only; no production optimization is implied.

### Completion Evidence

- Focused tests, `mvn verify`, Checkstyle 0 and `git diff --check` pass.
- Quick and Full evidence are separately identified.
- `verifier`, `benchmark-reviewer` and `docs-auditor` pass.
- No production source diff exists relative to `v0.7.0-engineering-baseline`.

## 10. Verification Strategy

| Layer | Required Evidence | Pass Condition |
| --- | --- | --- |
| Unit | workload vectors, manifest, digest and guard logic | deterministic golden values |
| Integration | Protocol v1 client/server lifecycle | exact response/transcript validation |
| System | real durable recoverable server | public-boundary qualification passes |
| Determinism | transcript/checkpoint/probe comparison | exact convergence |
| Long-run | 60-minute/1,000,000-command Full lane | all D6/D7 guards pass |
| Lifecycle | start/stop, lock, listener, temp files | resources return to baseline |
| Process recovery | child JVM graceful/forced campaign | per-cycle recovery convergence |
| Performance | JMH + GC profiler | complete matrix and metadata |
| Profile | JFR | reproducible profile artifact/hash |
| Static/Build | Maven, Checkstyle, diff | all pass |
| Remote | exact-SHA GitHub Actions | PASS |

`Thread.sleep()`, reflection and production-only seams are prohibited as
correctness oracles.

## 11. Benchmark and Profile Strategy

Evidence lanes remain separate:

```text
Quick CI
Full local qualification
JMH performance
JFR/GC profile
```

Full Qualification is one immutable evidence unit. Workload, seed, JVM args,
GC, WAL/Pipeline/Netty configuration, filesystem and thresholds cannot change
after the run begins. Any change invalidates that run; results cannot be
concatenated across configurations.

Raw artifacts remain ignored or CI artifacts. Committed reports contain
commands, environment, hashes, failures, reruns and limitations.

## 12. Planned Repository Changes

| Path | Task | Planned Change | Boundary |
| --- | --- | --- | --- |
| `pom.xml` | 035 | add `qualification` module only | no dependency change |
| `qualification/**` | 035-038 | JDK-only qualification contracts, client, harness and campaign | no production imports beyond public APIs |
| `benchmark/src/main/java/.../SystemQualificationBenchmark.java` | 039 | additive JMH benchmark | existing dependency set only |
| `.github/workflows/qualification.yml` | 036-039 | bounded quick/manual qualification workflow | Full lane not forced on push |
| `docs/qualification/**` | 040 | manifests and evidence summaries | documentation only |
| `docs/adr/ADR-0017-*` | 035 | decision record | approved scope |
| `tasks/active/TASK-20260823-035..040*` | 035-040 | task plans | governance records |
| `tasks/reports/PHASE-9-*` | 035-040 | evidence reports | status/evidence only |
| `README.md`, `.codex/AGENT_CONTEXT.md`, blueprint index | 040 | final synchronization | no runtime semantics |

Frozen: `src/main/java/**`, `src/test/java/**`, `core/pom.xml`, existing
benchmark classes, all protocol/persistence formats and baseline tags.

## 13. Exception Gates

Stop immediately for:

- production source/API changes;
- matching, Sequence, ACK, failure or listener-last changes;
- Protocol/WAL/Snapshot/recovery format changes;
- new session, producer, queue or thread ownership;
- new dependency or profiling agent;
- live `BUFFERED` mode or changed defaults;
- changed workload/threshold after observing results;
- omitted failure fork/outlier/campaign;
- replacing Full evidence with Quick evidence;
- any production optimization or Product Release action.

## 14. Git, Commit and CI Strategy

```text
Blueprint branch: docs/phase9-system-qualification-blueprint
Implementation branch: feature/phase9-system-qualification
Candidate tag: v0.8.0-engineering-baseline
```

One logical checkpoint per Task. Push after each evidence checkpoint and require
exact-SHA CI. Standard CI runs `mvn verify` and bounded Quick Qualification;
Full Qualification is manual or `workflow_dispatch`. No squash, force push or
shared-history rewrite.

## 15. Rollback and Compatibility

Phase 9 changes are additive qualification artifacts. Reverse Task order can
remove the module, benchmark and workflow without changing WAL, Snapshot,
Protocol or runtime compatibility. The `v0.7.0-engineering-baseline` tag is
never moved. Qualification temporary directories may only be cleaned when
explicitly created by the harness.

## 16. Closure Plan

```text
TASK-040 Evidence Gate PASS
    ↓
STOP
    ↓
Sol High Phase 9 Closure Review
    ↓
Human Phase 9 Closure Approval
    ↓
--no-ff merge → master verification/CI
    ↓
v0.8.0-engineering-baseline → tag CI
    ↓
archive/final synchronization
```

The candidate tag is an engineering qualification baseline, not a Product
Release. Phase 10 remains unauthorized.

## 17. Human Phase 9 Blueprint Approval

| Date | Reviewer | Decision | Approved ADRs/Tasks | Constraints |
| --- | --- | --- | --- | --- |
| 2026-08-23 | Human Developer | Approved | ADR-0017 D1-D16; TASK-20260823-035..040 | Strict dependency order; existing runtime frozen; production optimization, merge/tag, Phase 10 and Product Release unauthorized |

```text
Blueprint Status: Approved
Implementation: TASK-035 Evidence Gate PASS; TASK-036 Authorized / Next
Phase 9 Closure: Not Authorized
```
