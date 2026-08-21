# Task Plan — TASK-20260821-018

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260821-018` / Phase 5 WAL Benchmark, Documentation and Closure Preparation |
| Status | `Completed / Remediation Evidence Passed; Final Closure Review Pending` |
| Owner / Implementer | Human Developer / Codex |
| Created / Updated | `2026-08-21` |
| Phase / ADR / Blueprint | Phase 5 / ADR-0013 / `PHASE-5-command-wal-and-replay-blueprint.md` |
| Authorization Mode | `Blueprint` |
| Depends On | TASK-017 exact-SHA evidence PASS |
| Current Stage / Next Gate | Remediation completed / Final Human Phase 5 Closure Review |
| Branch / CI | `feature/phase5-command-wal-replay` / R3 [run 32482054086](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32482054086) PASS for `0e6ac95` |

## 2. Background and Goal

Measure the completed component without changing its production semantics,
interpret evidence within the approved claim boundary, synchronize
architecture/current-state documents and prepare (but do not approve) the
Phase 5 Closure Report. The limited remediation also requires a deterministic
Submit/Cancel mix and complete environment/workload/percentile metadata.

## 3. Non-Goals

- optimization, format/default changes or production tuning;
- live pipeline, Network, Snapshot or online Recovery benchmarks;
- end-to-end trade/client durability claims;
- raw benchmark/JFR artifacts in Git;
- merge, tag, Release or Phase 6 authorization.

## 4. Requirements and Acceptance Criteria

- [x] JMH separates append, strict scan and engine replay boundaries;
- [x] `SYNC_EACH_APPEND` and `BUFFERED` are reported separately;
- [x] `walAppend` and fixed scan/replay fixtures use the deterministic
  SubmitLimit/CancelOrder mix required by the Blueprint;
- [x] dataset, segments, bytes, environment, warmup/measurement/forks/threads,
  JVM/GC and storage limitations are recorded;
- [x] SampleTime P50 and P99 are recorded for every matrix row;
- [x] raw evidence remains ignored with path/command/summary documented;
- [x] no result changes `SYNC_EACH_APPEND` default automatically;
- [x] architecture/recovery/benchmark/README/context/ADR/Blueprint/Tasks agree;
- [x] cumulative Task report and Phase Closure proposal contain exact evidence;
- [x] frozen production diff remains zero;
- [x] full `mvn verify`, diff checks and exact-SHA CI pass;
- [x] stop at separate Human Phase 5 Closure Approval.

## 5. Current Implementation and Scope

TASK-014..017 provided the verified WAL/replay component. This Task adds
only benchmark code and documentation, except an implementation defect triggers
return to the owning Task or an Exception Gate.

## 6. Design and ADR Linkage

Use separate JMH states/directories per fork/trial, consume outputs to prevent
dead-code elimination, clean only benchmark-owned temporary paths and never
reuse stale WAL data across measurements.

| Field | Value |
| --- | --- |
| ADR | ADR-0013 (`Approved`) |
| Decision Summary | D6, D8-D10 define evidence variables and claim limits |
| Scope Boundary | benchmark/docs/closure proposal only; no optimization or default change |
| Blueprint Status | `Approved — inherited Human Blueprint Approval; dependency-gated` |
| Exception Gates | benchmark exposes architecture defect, format/default change, product claim |

Alternatives considered: a single mixed end-to-end number or ad-hoc timing.
Separate JMH append/scan/replay boundaries are selected so admission,
durability and replay costs cannot be conflated.

## 7. Planned File Changes

| File or Directory | Change |
| --- | --- |
| `benchmark/src/main/java/.../WalBenchmark.java` | append/scan/replay component JMH |
| `docs/benchmark/recovery.md` | reproducible method/results/limitations |
| `docs/architecture/recovery.md`, `overview.md` | implemented vs deferred boundary |
| `README.md`, `.codex/AGENT_CONTEXT.md` | concise Phase status/evidence |
| ADR/Blueprint/TASK plans | approved implementation status/evidence sync |
| `tasks/reports/PHASE-5-command-wal-replay.md` | cumulative execution evidence |
| `tasks/reports/PHASE-5-command-wal-replay-closure.md` | Human Closure proposal |
| `benchmark-results/wal-remediation-full.json` | local ignored remediation raw evidence |

## 8. Test and Evidence Plan

- JMH smoke validates benchmark wiring only.
- Full component matrix records append/scan/replay separately.
- Full regression and Checkstyle run before benchmark commit and closure prep.
- Documentation link/scope audit and frozen-path audit run before commit.
- No benchmark value is fabricated, cherry-picked or presented without method.

## 9. Benchmark and Profile Plan

- Benchmark: required; see Blueprint Section 11.
- Profile: not planned; requires Exception/optimization approval if needed.
- Metrics: throughput, sample-time percentiles where meaningful, bytes,
  commands, segments, allocation/GC when reproducible.
- Baseline: new Phase 5 component baseline; no production target.

## 10. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| filesystem cache distorts results | document lifecycle/cache limits; separate modes |
| buffered result mistaken for durable | prominent mode-specific claims |
| force result over-generalized | record host/storage/JDK and narrow semantics |
| benchmark contaminates product | no benchmark-only production paths |
| closure hides missing recovery | explicit Snapshot/online Recovery limitations |

## 11. Rollback Plan

Revert benchmark and documentation only. Raw artifacts remain local and may be
discarded only from the explicitly benchmark-owned directory. No product data
or baseline tag is changed.

## 12. Verification Commands

```text
mvn verify
java -jar benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar WalBenchmark <smoke options>
java -jar benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar WalBenchmark <approved full options>
git diff --check
<frozen-path diff audit from Blueprint>
```

## 13. Git Plan

Commits:

```text
perf(wal): add append and replay component baseline
docs(phase5): synchronize wal replay evidence
docs(phase5): prepare command wal replay closure
test(wal): verify terminal rotation failure
perf(wal): complete mixed command benchmark evidence
docs(phase5): synchronize remediation evidence
```

Push each checkpoint and record exact-SHA CI. Merge/tag wait for Human Closure.

## 14. Approval, Reports and Implementation Log

| Date | Reviewer | Stage | Decision | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Proposal creation | `Authorized` | Plan only; implementation awaits Blueprint approval |
| 2026-08-21 | Human Developer | Phase Blueprint Approval | `Approved (Inherited)` | TASK-018 authorized only after TASK-017 evidence PASS; benchmark cannot change SYNC default; Phase Closure remains Human gate |

| Stage | Report | Status | Next Gate |
| --- | --- | --- | --- |
| Proposal | Phase 5 proposal report | Approved | TASK-017 evidence |
| Benchmark / Docs | cumulative Phase 5 report | Completed / Remediation Evidence PASS | Final Human Phase 5 Closure Review |
| Closure Preparation | Phase 5 Closure Report | Prepared / Remediation synchronized | Final Human Phase 5 Closure Review |

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Approved | Human Blueprint Approval inherited; execution waits for TASK-017 evidence | dependency-gated; Closure remains Human gate |
| 2026-08-21 | Authorized | TASK-017 Evidence Gate passed with exact-SHA CI `32467018067`; TASK-018 may begin | TASK-018 Evidence Gate; stop at Closure proposal |
| 2026-08-21 | Completed | JMH append/scan/replay matrix, documentation synchronization and Closure Proposal prepared; `mvn verify` passed with 113 tests and frozen-path diff is zero | Human Phase 5 Closure Approval; merge/tag remain unauthorized |
| 2026-08-21 | Remediated | Deterministic Submit/Cancel mix benchmark rerun; environment/workload metadata and SampleTime P50/P99 synchronized; raw output `benchmark-results/wal-remediation-full.json` | Full JMH matrix PASS; local `mvn verify` 114 tests; R2 `bd37382` / CI `32481451533`; R3 `0e6ac95` / CI `32482054086` PASS |

## 15. Completion Checklist

- [x] inherited approval and TASK-017 dependency recorded
- [x] reproducible component evidence recorded honestly
- [x] all documents/evidence synchronized
- [x] full build/Checkstyle/diff/frozen audit pass
- [x] commits/push/exact-SHA CI recorded
- [x] Closure proposal prepared and execution stopped for final Human review
- [x] Limited Closure Remediation R2 completed without production optimization or default changes
