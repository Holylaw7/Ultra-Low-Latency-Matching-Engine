# Phase 8 — TASK-20260822-034 / Recovery Benchmark, Documentation and Closure Proposal

## Executive Status

| Item | Status |
| --- | --- |
| Phase | Phase 8 — Snapshot Checkpoint and Online Recovery Bootstrap |
| Task | `TASK-20260822-034` — Recovery Benchmark, Documentation and Closure Proposal |
| Authorization | Phase 8 Blueprint approval; TASK-033 Evidence Gate PASS |
| Implementation | Recovery benchmark and evidence/documentation synchronization complete |
| Code checkpoint | `9835624cb4fa31368edda4f4483fa0c6eb78ae65` — `feat-phase8-recovery-benchmark` |
| Code CI | [32616029460](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32616029460) PASS |
| Raw benchmark output | `benchmark-results/phase8-recovery-full.json` (local, ignored) |
| Closure state | Completed / Human Closure Approved / Baseline Frozen at `v0.7.0-engineering-baseline` |
| Working tree policy | Pre-existing `.vscode/` remains untouched and untracked |

## Scope and Implementation

`RecoveryBenchmark` adds only JMH measurement code in the benchmark module. It
does not change WAL, Snapshot, Recovery, MatchingEngine, Pipeline, Gateway or
Protocol production semantics. Fixture construction, temporary-file setup and
cleanup are outside measured operations.

The measured boundaries are deliberately separate:

```text
pure WAL genesis replay
Snapshot decode / checkpoint restore
Snapshot + WAL-tail recovery
offline Snapshot creation
process bootstrap to listener-ready
```

The fixture uses a deterministic three-Submit/one-Cancel command pattern. The
command-count parameters are 256 and 1,024; the segment-size parameters are
4,128 and 65,536 bytes. Snapshot sequence and WAL-tail length are each half of
the command count. The live bootstrap fixture uses the existing recovery
runtime and a `SYNC_EACH_APPEND` configuration; shutdown is benchmark teardown,
not part of the measured operation.

## Environment and JMH Configuration

| Field | Value |
| --- | --- |
| OS | Microsoft Windows 11 Home Chinese, build 26200 |
| CPU | 13th Gen Intel Core i9-13900H; 14 cores / 20 logical processors |
| Storage | `E:` fixed NTFS volume; host reports NVMe SSD media; device mapping not isolated |
| JDK / VM | OpenJDK 21.0.12 Microsoft build; 64-bit OpenJDK Server VM |
| Java executable | `E:\Java\microsoft-jdk-21\bin\java.exe` |
| Estimated max heap | 7.91 GiB (`java -XshowSettings:vm -version`; no explicit `-Xmx`) |
| JMH | 1.37 |
| JVM arguments | none; launcher defaults |
| GC | G1 GC, JDK default; not independently isolated |
| Forks / threads | 1 / 1 |
| Warmup / measurement | 1 × 1 s / 1 × 1 s |
| Raw result | `benchmark-results/phase8-recovery-full.json` (ignored) |

The full matrix completed successfully with 20 benchmark/parameter
combinations (five methods × two command counts × two segment sizes), with
Throughput and SampleTime modes recorded.

The same matrix was also run with JMH's built-in `gc` profiler, producing the
ignored `benchmark-results/phase8-recovery-gc.json`. Its secondary metrics
across all methods and parameters ranged as follows:

| Metric | Minimum | Maximum | Unit |
| --- | ---: | ---: | --- |
| `gc.alloc.rate` | 52.789 | 1,922.390 | MB/sec |
| `gc.alloc.rate.norm` | 51,663.677 | 1,354,989.795 | B/op |
| `gc.count` | 0 | 6 | counts |
| `gc.time` | 0 | 4 | ms |

These are one-fork, one-iteration JMH profiler observations; they are not
allocation-free or production-GC claims.

## Deterministic Fixture Metadata

Each four-command group contains three 52-byte SubmitLimit records and one
28-byte CancelOrder record. Active orders are two per group.

| Commands | Active orders | Snapshot sequence | Tail length | 4,128-byte segments / total bytes | 65,536-byte segments / total bytes |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 256 | 128 | 128 | 128 | 3 / 11,872 | 1 / 11,808 |
| 1,024 | 512 | 512 | 512 | 12 / 47,488 | 1 / 47,136 |

The totals include segment headers and the exact approved WAL v1 record layout.

## SampleTime Results

Values are `ms/op` from the one-fork, one-iteration JMH run. P50/P95/P99/P999
are reported to preserve the required tail evidence; they are observations,
not statistical production guarantees.

| Benchmark | Commands | Segment | P50 | P95 | P99 | P999 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| bootstrapToListener | 256 | 4,128 | 3.301 | 4.146 | 6.651 | 9.716 |
| bootstrapToListener | 256 | 65,536 | 3.160 | 4.071 | 4.888 | 83.231 |
| bootstrapToListener | 1,024 | 4,128 | 5.112 | 6.336 | 15.785 | 20.546 |
| bootstrapToListener | 1,024 | 65,536 | 3.480 | 4.386 | 12.207 | 79.430 |
| offlineSnapshotCreation | 256 | 4,128 | 3.805 | 4.815 | 7.132 | 7.496 |
| offlineSnapshotCreation | 256 | 65,536 | 3.291 | 4.211 | 4.588 | 4.727 |
| offlineSnapshotCreation | 1,024 | 4,128 | 19.284 | 23.781 | 25.657 | 25.657 |
| offlineSnapshotCreation | 1,024 | 65,536 | 23.429 | 28.926 | 29.786 | 29.786 |
| pureWalReplay | 256 | 4,128 | 0.495 | 0.708 | 0.920 | 1.357 |
| pureWalReplay | 256 | 65,536 | 0.398 | 0.609 | 0.793 | 1.199 |
| pureWalReplay | 1,024 | 4,128 | 1.063 | 1.431 | 1.757 | 2.306 |
| pureWalReplay | 1,024 | 65,536 | 0.550 | 0.768 | 1.011 | 1.576 |
| snapshotDecodeRestore | 256 | 4,128 | 0.060 | 0.091 | 0.149 | 0.255 |
| snapshotDecodeRestore | 256 | 65,536 | 0.059 | 0.081 | 0.149 | 0.243 |
| snapshotDecodeRestore | 1,024 | 4,128 | 0.091 | 0.140 | 0.215 | 0.509 |
| snapshotDecodeRestore | 1,024 | 65,536 | 0.088 | 0.141 | 0.213 | 0.459 |
| snapshotTailRecovery | 256 | 4,128 | 0.638 | 0.920 | 1.288 | 2.779 |
| snapshotTailRecovery | 256 | 65,536 | 0.514 | 0.757 | 1.017 | 1.589 |
| snapshotTailRecovery | 1,024 | 4,128 | 1.243 | 1.663 | 2.063 | 2.589 |
| snapshotTailRecovery | 1,024 | 65,536 | 0.684 | 0.958 | 1.231 | 1.571 |

Throughput is the JMH `primaryMetric.score` with unit `ops/ms` in
`benchmark-results/phase8-recovery-full.json`. The observed scores were:

| Benchmark | 256 / 4,128 | 256 / 65,536 | 1,024 / 4,128 | 1,024 / 65,536 |
| --- | ---: | ---: | ---: | ---: |
| bootstrapToListener | 0.262 | 0.292 | 0.186 | 0.255 |
| offlineSnapshotCreation | 0.261 | 0.280 | 0.051 | 0.059 |
| pureWalReplay | 1.897 | 2.309 | 0.821 | 1.693 |
| snapshotDecodeRestore | 15.410 | 15.430 | 10.440 | 10.580 |
| snapshotTailRecovery | 1.492 | 1.802 | 0.758 | 1.350 |

Scores are not recast as end-to-end or production throughput. The high P999
observations are reported rather than discarded, which is important for
interpreting this single-fork local-host baseline.

## Evidence Gate

- [x] Required recovery boundaries and dimensions were executed.
- [x] Environment, JDK/JVM/GC, storage, workload and JMH parameters were recorded.
- [x] SampleTime P50/P95/P99/P999 evidence is synchronized with raw output.
- [x] `mvn -pl benchmark -am -DskipTests package` passed with Checkstyle 0.
- [x] Benchmark smoke and full matrix completed successfully.
- [x] No production code, WAL/Snapshot format, or recovery semantic changes were made.
- [x] Read-only verifier, benchmark-reviewer and docs-auditor report PASS.
- [x] Benchmark implementation checkpoint `9835624` has exact-SHA CI
  [32616029460](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32616029460) PASS.
- [x] Technical Closure input is final checkpoint `c59d7c0`; exact-SHA CI
  [32616802595](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32616802595) PASS.
- [x] The final technical regression recorded 195 tests, 0 failures, 0 errors,
  0 skipped and Checkstyle 0.

The verifier confirmed benchmark/production boundary compliance, the
benchmark-reviewer confirmed raw-matrix and profiler consistency, and the
docs-auditor confirmed status/claim synchronization at the prior evidence
checkpoint. The later Closure Review identified only stale final SHA/CI
references; no implementation or architecture defect was found.

## Claim Boundary and Known Limitations

These measurements are component/local-host engineering evidence only. They do
not establish production RTO, availability SLA, durable client acknowledgement,
power-loss safety, exactly-once behavior, capacity guarantees or Product
Release readiness. `pureWalReplay` is not online crash-recovery time;
`bootstrapToListener` is not a production readiness guarantee; Snapshot-tail
results are not an RTO claim. The benchmark cannot change correctness defaults
or authorize optimization.

`SYNC_EACH_APPEND` remains the correctness/durability default. Setup fixtures
may use buffered construction, but no benchmark result changes that default.
`force(true)` and atomic-move failure injection remain not dynamically tested;
no production-only seam was added and no hardware power-loss guarantee is
claimed. Filesystem cache, Windows scheduling, one fork and one one-second
measurement limit inference.

## Closure Proposal (Not Yet Approved)

The Phase 8 technical Closure input is `c59d7c0` with exact-SHA CI
`32616802595` PASS. The docs/evidence-only Limited Closure Remediation is
complete at `4bdfb97` with exact-SHA CI `32620164524` PASS. Human Phase 8
Closure Approval is now recorded; normal `--no-ff` merge, master verification
and annotated `v0.7.0-engineering-baseline` are authorized. TASK-034 was
archived before Human Closure Approval; this is a governance sequencing
deviation only.

After the Evidence Gate PASS, the only authorized next gate is:

```text
TASK-034 Evidence Gate PASS
    -> Sol High Phase 8 Closure Review
    -> Human Phase 8 Closure Approval
```

Human Closure Approval was followed by normal `--no-ff` merge `87abbc1`, Master
CI `32622722649` PASS, annotated tag `v0.7.0-engineering-baseline` pointing to
that merge commit, and Tag CI `32622757607` PASS. TASK-029 through TASK-034 are
archived. Phase 9 and Product Release remain unauthorized.
