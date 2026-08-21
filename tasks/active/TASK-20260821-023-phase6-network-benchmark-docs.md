# Task Plan — TASK-20260821-023

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260821-023` / Phase 6 Network Benchmark, Documentation and Closure Preparation |
| Status | `Completed / Evidence PASS` |
| Owner / Implementer | Human Developer / Codex |
| Created / Updated | `2026-08-21` |
| Phase / ADR / Blueprint | Phase 6 / ADR-0014 / [`PHASE-6`](../blueprints/PHASE-6-network-protocol-blueprint.md) |
| Authorization Mode | Blueprint |
| Current Stage / Next Gate | Completed / Human Phase 6 Closure Review |
| Branch / Baseline | `feature/phase6-network-protocol` after approval / approved proposal commit |
| Remote / CI | `origin` / benchmark CI [32491817494](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32491817494) PASS; final docs CI [32492948441](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32492948441) PASS |

## 2. Background

Correctness evidence must be followed by bounded protocol/loopback measurements
and synchronized documentation before Phase Closure review.

## 3. Goal

Record reproducible codec and one-in-flight loopback TCP component evidence,
synchronize all Phase 6 documents and prepare the Closure Report.

## 4. Non-Goals

- no optimization, allocator/default/transport change from benchmark numbers;
- no concurrent-client, durable ACK, Internet or production claim;
- no live WAL, Snapshot, Recovery, security or Release work.

## 5. Requirements and Acceptance Criteria

- [x] benchmark deterministic Submit/Cancel decode and result encode workloads;
- [x] benchmark sequential loopback request-to-complete-result round trip;
- [x] validate benchmark outputs to prevent dead-code/semantic drift;
- [x] record CPU/topology, RAM, OS, JDK/JVM, GC, Netty/modules, allocator,
  warmup, measurements, forks, threads, message mix/sizes and commands;
- [x] record throughput and SampleTime P50/P99/P999 where meaningful;
- [x] keep raw output local/ignored and record path/command/summary/limitations;
- [x] distinguish codec, loopback, pipeline and durability boundaries;
- [x] synchronize README, architecture, ADR, Blueprint, Tasks, reports and
  AGENT_CONTEXT;
- [x] prepare Closure Proposal and stop for Human review.

## 6. Current Implementation and Scope

### Current Implementation

`NetworkBenchmark` and the network benchmark/architecture evidence are now
implemented within the approved component and sequential loopback scope.

### In Scope

`NetworkBenchmark.java`, network benchmark/architecture docs, Phase 6 reports
and project status documents.

### Out of Scope

Production changes except an Exception-Gate-approved correctness defect fix,
profiling and every Blueprint Non-Goal.

## 7. Design Proposal

Use JMH with fixed vectors and one thread. Keep codec methods allocation-aware
but do not add benchmark-only production paths. The loopback benchmark runs the
approved one-request-in-flight topology and verifies each correlation/result.

| Alternative | Advantages | Risks | Result |
| --- | --- | --- | --- |
| JMH codec + loopback workloads | repeatable component evidence | not production E2E | selected |
| ad-hoc stopwatch | quick | warmup/statistical errors | rejected |
| external load generator | realistic | new dependency/environment scope | deferred |

### ADR / Blueprint Linkage

| Field | Value |
| --- | --- |
| ADR Status | ADR-0014 Approved |
| Decision | D10 evidence and claim boundary |
| Blueprint | Approved; TASK-023 after TASK-022 exact-SHA CI |
| Exception Gates | performance-driven semantic/default/code redesign |

### Architecture Impact

- [x] No architecture change
- [x] evidence for approved architecture only

## 8. Planned File Changes

| File | Change |
| --- | --- |
| `benchmark/.../NetworkBenchmark.java` | codec/loopback JMH workloads |
| `docs/benchmark/network.md` | method/results/limits |
| `docs/architecture/network.md`, overview, README | implemented status |
| ADR/Blueprint/Tasks/context/reports | evidence/closure synchronization |
| `tasks/reports/PHASE-6-network-protocol-closure.md` | Closure Proposal |

## 9. Test Plan

Benchmark smoke plus full matrix, then focused network tests and full
`mvn verify`. Benchmark setup validates golden vectors and result counts.

## 10. Benchmark and Profile Plan

- Benchmark: required JMH codec and sequential loopback workloads.
- Profile: Not applicable unless an Exception Gate is approved.
- Baseline: new Phase 6 component/loopback evidence; no target.
- Keep/revert: evidence only; no runtime default changes.

## 11. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| loopback numbers overclaimed | explicit component/local-host wording |
| flaky benchmark lifecycle | one fork-owned server, bounded teardown |
| benchmark changes behavior | correctness assertions and no prod shortcut |

## 12. Rollback Plan

Revert benchmark/docs only. Do not change runtime behavior to preserve a number.

## 13. Verification Commands

```text
mvn verify
& 'E:\Java\microsoft-jdk-21\bin\java.exe' -jar benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar NetworkBenchmark -wi 1 -i 2 -f 1 -t 1 -rff benchmark-results/phase6-network-full-jdk21.csv -rf csv
<Blueprint-approved full JMH matrix>
git diff --check
frozen path audit
```

## 14. Git Plan

```text
perf(network): add codec and loopback baseline
docs(phase6): synchronize network evidence
docs(phase6): prepare network closure
```

Push checkpoints and record exact-SHA CI. Stop after Closure Proposal.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Proposal | Proposal only | no implementation |
| 2026-08-21 | Human Developer | Blueprint | Approved / Conditional | benchmark/claims frozen by D10; start after TASK-022 exact-SHA CI |

## 16. Phase Reports and Approval Gates

| Stage | Report | Status | Next Gate | Authorization |
| --- | --- | --- | --- | --- |
| Approval | Phase 6 proposal | Approved | Blueprint Approval | Completed |
| Benchmark/Docs | cumulative report | Completed / Evidence PASS | Human Phase Closure Review | Blueprint |
| Completion | Closure Proposal | Prepared | Human Phase Closure Review | Strict Human gate |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Proposed | evidence/closure plan | baseline PASS |
| 2026-08-21 | Implemented | `NetworkBenchmark` codec and sequential loopback evidence | `0c924dd`; exact-SHA CI [32491817494](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32491817494) PASS |
| 2026-08-21 | Synchronized | benchmark, architecture, cumulative report and Closure Proposal | `d628ffb`; exact-SHA CI [32492948441](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32492948441) PASS |

## 18. Completion Checklist

- [x] benchmark smoke/full evidence complete and bounded claims recorded
- [x] documentation/context/ADR/Blueprint/Tasks synchronized
- [x] full build/static/diff/frozen audit and exact-SHA CI pass
- [x] Closure Proposal prepared
- [x] stop at Human Phase 6 Closure Review
