# Task Plan — TASK-20260821-013

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260821-013` |
| Title | Benchmark and Document Phase 4 Event Pipeline |
| Status | `Completed — evidence gate passed; Closure pending Human approval` |
| Owner / Implementer | Human Developer / Codex |
| Created / Updated | `2026-08-21` |
| Related Phase | Phase 4 — Event Pipeline |
| Related ADR | [`ADR-0012`](../../docs/adr/ADR-0012-event-pipeline-execution-and-backpressure.md) — Approved |
| Phase Blueprint | [`PHASE-4 Blueprint`](../blueprints/PHASE-4-event-pipeline-blueprint.md) — Approved |
| Authorization Mode | `Blueprint inherited Human approval` |
| Current Stage | `Benchmark / Documentation / Closure Preparation` |
| Next Gate | `Human Phase 4 Closure Approval` |
| Branch | `feature/phase4-event-pipeline` |
| Baseline HEAD | TASK-012 evidence commit `1636a56` |
| Remote / CI | `origin/feature/phase4-event-pipeline`; benchmark `e546051` / CI `32459574518` PASS; docs `bbb30f5` / CI `32459663240` PASS |

## 2. Background

After correctness and determinism are established, Phase 4 needs reproducible
component evidence and synchronized architecture documentation before Closure.

## 3. Goal

Measure direct-engine and pipeline component baselines, document the actual
CPU/latency trade-offs and limitations, synchronize project documentation and
prepare the consolidated Phase 4 Closure Report.

## 4. Non-Goals

- no production optimization or default wait-mode change without evidence;
- no network/durable end-to-end performance claim;
- no JFR/raw benchmark artifact commit unless separately authorized;
- no WAL, Replay, Snapshot, Network, output ring or Release;
- no product-code change except a separately approved Exception Gate fix.

## 5. Requirements and Acceptance Criteria

### Requirements

- [x] add a JMH pipeline benchmark with fixed reproducible workloads;
- [x] compare direct engine, producer admission and batch completion separately;
- [x] compare approved wait modes and capacities without changing semantics;
- [x] record environment, JVM, warmup, measurement, forks and limitations;
- [x] distinguish enqueue latency from end-to-end completion;
- [x] synchronize ADR, architecture, benchmark, README, Task report and context;
- [x] prepare Phase Closure Report without marking Closure approved.

### Acceptance Criteria

- [x] benchmark smoke and planned full runs complete without correctness loss;
- [x] accepted/completed/result counts are validated outside timing claims;
- [x] no best-run-only or unsupported performance statement appears;
- [x] `BLOCKING` remains default unless approved evidence criteria support a
  Blueprint-compatible change;
- [x] full `mvn verify`, Checkstyle, diff/link/scope checks and CI pass;
- [x] all known limitations and deferred capabilities remain explicit;
- [x] Closure status is `Pending Human Phase Closure Approval`.

## 6. Current Implementation and Scope

TASK-012 is expected to provide the fully verified correctness baseline.

### In Scope

- `benchmark/src/main/java/.../PipelineBenchmark.java`;
- `docs/benchmark/pipeline.md`;
- `docs/architecture/overview.md`, `docs/architecture/pipeline.md`;
- ADR-0012 status synchronization already authorized by Blueprint;
- README, AGENT_CONTEXT, cumulative report and Closure Report;
- Task state/documentation movement at Closure only.

### Out of Scope

Production code changes, new dependencies, profiler implementation, future
infrastructure and merge/tag before Human Closure Approval.

## 7. Design Proposal

### Proposed Benchmark Design

Use JMH with fixed command fixtures and separate benchmark methods/states:

1. direct synchronous engine processing baseline;
2. producer-side `tryPublish` acceptance cost under unsaturated SPSC load;
3. batch publish plus verified drain-completion throughput;
4. capacity and `BLOCKING`/`YIELDING`/`BUSY_SPIN` parameter matrix;
5. optional JMH GC profiler for allocation rate.

No production timestamp field or benchmark-only execution path is allowed.

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
| separate enqueue and completion measures | honest boundary | more benchmark methods | Selected |
| call enqueue latency end-to-end | simple headline | false claim | Prohibited |
| custom ad-hoc timer loop | flexible | weaker harness controls | Rejected |

### ADR and Blueprint Linkage

| Field | Value |
| --- | --- |
| ADR | ADR-0012 D7/D8 and benchmark plan — `Approved` |
| Decision Summary | evidence-only wait-mode/capacity comparison; no optimization claim |
| Scope Boundary | benchmark/docs/closure preparation only |
| Blueprint | `tasks/blueprints/PHASE-4-event-pipeline-blueprint.md` — `Approved` |
| Authorized Stage | TASK-013 after TASK-012 evidence |
| Exception Gates | product change, new metric semantics, optimization/default change |

No new architecture decision is permitted outside an approved Blueprint
amendment.

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `benchmark/src/main/java/.../PipelineBenchmark.java` | JMH states/methods | component evidence |
| `docs/benchmark/pipeline.md` | method, environment, results, limitations | reproducibility |
| `docs/architecture/overview.md` | Phase 3/4 current status | remove stale roadmap state |
| `docs/architecture/pipeline.md` | verified topology/contracts | durable architecture |
| `docs/adr/ADR-0012-*.md` | approval/implementation synchronization | decision integrity |
| `README.md`, `.codex/AGENT_CONTEXT.md` | current capability/gate | state recovery |
| `tasks/reports/PHASE-4-event-pipeline.md` | cumulative evidence | Task checkpoints |
| `tasks/reports/PHASE-4-event-pipeline-closure.md` | Closure proposal | Human gate |

## 9. Test and Evidence Plan

### Benchmark Validation

- benchmark compiles and smoke run discovers the expected methods;
- command/result counts and failures are asserted in setup/teardown;
- each wait-mode instance shuts down and releases its thread;
- fixed command workloads avoid random or wall-clock business order.

### Full Verification

- all Phase 1-4 tests and `mvn verify`;
- Checkstyle zero violations;
- production frozen-path diff audit;
- local Markdown link and stale-status scans;
- exact-SHA CI for benchmark/docs checkpoint.

## 10. Benchmark and Profile Plan

- Benchmark: `PipelineBenchmark` using JMH;
- Profile: optional JMH `-prof gc`; JFR only if the Blueprint evidence plan is
  insufficient and no product change is required;
- Dataset: fixed no-match, single-match and mixed deterministic command sets;
- Parameters: capacity `1024`, `65536`; approved wait modes;
- Metrics: ops/s, producer-side sample time where used, completion throughput,
  allocation rate, accepted/completed/result counts;
- Baseline: direct synchronous engine in the same benchmark build;
- Evidence classification: component baseline, not Network/durable end-to-end.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| async harness measures enqueue only | misleading latency | separate and label metrics |
| busy spin distorts shared host | noisy/CPU-heavy results | record topology; bounded runs; no default claim |
| benchmark state leaks threads | invalid runs | lifecycle assertions/teardown |
| performance encourages scope drift | premature optimization | evidence only; Exception Gate for changes |

## 12. Rollback Plan

Revert benchmark and documentation commits. No production or persistent state
changes. If evidence reveals a correctness problem, return to the owning Task
or Exception Gate and do not publish a Phase baseline.

## 13. Verification Commands

```text
mvn verify
java -jar benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar PipelineBenchmark -wi 1 -i 1 -f 1
approved full JMH command recorded in docs/benchmark/pipeline.md
optional JMH -prof gc run
git diff --check
documentation link and frozen-scope audit
```

## 14. Git Plan

Planned commits:

```text
perf(pipeline): add event pipeline baseline
docs(phase4): synchronize event pipeline evidence
docs(phase4): prepare event pipeline closure
```

Push logical evidence checkpoints and require exact-SHA CI. Merge/tag are not
authorized until Human Phase Closure Approval.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Phase Blueprint Approval | `Approved` | TASK-013 authorized after TASK-012 evidence; wait-mode default remains separately evidence-gated |

## 16. Phase Reports and Approval Gates

| Stage | Report | Status | Next Gate | Authorization |
| --- | --- | --- | --- | --- |
| ADR / Decision | ADR-0012 / Blueprint | Approved | TASK-012 evidence | Blueprint inherited |
| Benchmark | `tasks/reports/PHASE-4-event-pipeline.md` | Completed | Closure preparation | Blueprint |
| Documentation | same report | Completed | Closure preparation | Blueprint |
| Closure Preparation | `tasks/reports/PHASE-4-event-pipeline-closure.md` | Prepared | Human Closure Approval | Strict Human Gate |
| Phase Closure | Closure Report | Pending | Human Closure Approval | Strict Human Gate |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Proposed | Benchmark/documentation/Closure scope prepared | Awaiting Blueprint Approval |
| 2026-08-21 | Approved | Blueprint approval recorded; TASK-013 is dependency-gated | TASK-012 evidence gate |
| 2026-08-21 | In Progress | Pipeline component benchmark and Phase 4 documentation synchronization started | TASK-012 evidence CI PASS; benchmark/docs evidence pending |
| 2026-08-21 | Completed | JMH smoke/full matrix and documentation synchronization completed; Closure proposal prepared | Java 21 JMH PASS; `mvn verify` PASS; exact-SHA CI `32459663240` PASS |

## 18. Completion Checklist

- [x] Blueprint approval inherited and prior Task evidence confirmed
- [x] benchmark and evidence report complete
- [x] documentation and ADR synchronized
- [x] full build/static/diff/link/scope gates pass
- [x] no unsupported performance claim
- [x] checkpoint commits pushed and exact-SHA CI recorded
- [x] Closure Report prepared with Human approval pending
- [x] no Exception Gate unresolved
