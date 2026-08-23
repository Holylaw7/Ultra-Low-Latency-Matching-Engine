# Phase 5 WAL / Replay Component Benchmark

## Status

Completed as TASK-20260821-018 remediation evidence. This is a
component-level JMH baseline for the approved persistence/replay
implementation; it is not an end-to-end durability, recovery or
exchange-throughput claim.

## Method

`WalBenchmark` measures three separate boundaries:

- `walAppend`: one command append, with `SYNC_EACH_APPEND` and `BUFFERED`
  reported separately;
- `walScan`: strict closed-WAL segment scan and decode without engine replay;
- `walReplay`: strict closed-WAL scan followed by genesis `MatchingEngine`
  replay.

The append workload is a deterministic alternating command stream:

```text
odd sequence:  SubmitLimitCommand(orderId = (sequence + 1) / 2)
even sequence: CancelOrderCommand(orderId = sequence / 2)
```

The fixed scan/replay fixtures use the same stream. Each JMH state creates its
own temporary WAL fixture outside the measured operation, consumes the result
through a `Blackhole`, and deletes only its own temporary directory after the
trial. The fixture is rebuilt for each fork and parameter set so stale WAL
bytes are not reused.

The remediation full matrix was run with:

```text
java -jar benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar \
  WalBenchmark -wi 1 -i 1 -f 1 -w 1s -r 1s -t 1 -foe true \
  -rf json -rff benchmark-results/wal-remediation-full.json
```

### Environment and JMH configuration

| Field | Value |
| --- | --- |
| OS | Microsoft Windows 11 Home Chinese, 10.0.26200 (build 26200) |
| CPU | 13th Gen Intel Core i9-13900H; 14 cores / 20 logical processors |
| Storage volume | `E:` fixed NTFS volume; host reports NVMe SSD media; volume-to-device mapping was not isolated |
| JDK / VM | OpenJDK 21.0.12 (Microsoft build 21.0.12+8-LTS), 64-bit Server VM |
| JMH | 1.37 |
| JVM arguments | none (`<none>` in JMH; normal Java launcher defaults) |
| GC | G1 GC (JDK default; not independently isolated) |
| Forks / threads | 1 / 1 |
| Warmup / measurement | 1 x 1 s / 1 x 1 s |
| Segment sizes | 4,128 and 65,536 bytes |
| Raw output | local ignored `benchmark-results/wal-remediation-full.json` |

### Deterministic fixture metadata

Submit records are 52 bytes and cancel records are 28 bytes. The table
includes the 32-byte segment header in total physical bytes. Segment counts
are deterministic for the approved writer and the listed segment sizes.

| Fixture commands | Submit / Cancel | Record bytes | 4,128-byte segments / total bytes | 65,536-byte segments / total bytes |
| ---: | ---: | ---: | ---: | ---: |
| 256 | 128 / 128 | 10,240 | 3 / 10,336 | 1 / 10,272 |
| 1,024 | 512 / 512 | 40,960 | 11 / 41,312 | 1 / 40,992 |

`walAppend` is intentionally streaming rather than a fixed-size fixture: its
measured operations alternate 52-byte and 28-byte records, and its total
bytes/segments grow during the JMH iteration. The fixed command/byte/segment
metadata above applies to `walScan` and `walReplay` setup fixtures.

## Recorded Results

Scores below are single-fork component observations from the remediation full
matrix. Throughput is `ops/us`; sample-time values are `us/op`. The
one-iteration setup is a reproducible baseline, not statistical production
capacity.

### Throughput

| Operation | Parameters | Throughput (ops/us) |
| --- | --- | ---: |
| walAppend | SYNC_EACH_APPEND / 4,128 | 0.004 |
| walAppend | SYNC_EACH_APPEND / 65,536 | 0.004 |
| walAppend | BUFFERED / 4,128 | 0.123 |
| walAppend | BUFFERED / 65,536 | 0.346 |
| walReplay | 256 / 4,128 | 0.004 |
| walReplay | 256 / 65,536 | 0.007 |
| walReplay | 1,024 / 4,128 | 0.001 |
| walReplay | 1,024 / 65,536 | 0.004 |
| walScan | 256 / 4,128 | 0.004 |
| walScan | 256 / 65,536 | 0.008 |
| walScan | 1,024 / 4,128 | 0.002 |
| walScan | 1,024 / 65,536 | 0.007 |

### SampleTime P50 / P99

| Operation | Parameters | Samples | Mean (us/op) | P50 (us/op) | P99 (us/op) |
| --- | --- | ---: | ---: | ---: | ---: |
| walAppend | SYNC_EACH_APPEND / 4,128 | 3,519 | 284.114 | 234.752 | 1,357.414 |
| walAppend | SYNC_EACH_APPEND / 65,536 | 4,281 | 234.691 | 209.152 | 444.508 |
| walAppend | BUFFERED / 4,128 | 19,222 | 7.535 | 2.500 | 115.092 |
| walAppend | BUFFERED / 65,536 | 21,723 | 3.936 | 2.400 | 13.296 |
| walReplay | 256 / 4,128 | 4,110 | 242.991 | 230.400 | 437.248 |
| walReplay | 256 / 65,536 | 6,843 | 146.338 | 134.912 | 293.151 |
| walReplay | 1,024 / 4,128 | 1,381 | 731.966 | 690.176 | 1,231.380 |
| walReplay | 1,024 / 65,536 | 3,087 | 391.928 | 213.760 | 439.296 |
| walScan | 256 / 4,128 | 4,460 | 225.085 | 209.920 | 448.200 |
| walScan | 256 / 65,536 | 7,869 | 127.058 | 115.712 | 288.563 |
| walScan | 1,024 / 4,128 | 1,628 | 619.519 | 584.704 | 1,007.155 |
| walScan | 1,024 / 65,536 | 6,543 | 152.517 | 138.752 | 369.725 |

The values are workload- and environment-specific. `BUFFERED` is not durable
throughput, and `SYNC_EACH_APPEND` does not establish a hardware power-loss
guarantee beyond the approved `FileChannel.force(true)` boundary. The large
tail observations remain evidence, not a production latency claim.

## Verification and Claim Limits

- `mvn verify`: remediation run passed, with 114 tests, 0 failures, Maven
  reactor 3/3 SUCCESS; Checkstyle reported 0 violations.
- Focused WAL writer/replay tests and the full benchmark smoke/full matrix
  completed successfully; the raw JSON remains local and ignored.
- No result changes the `SYNC_EACH_APPEND` default or WAL format.
- Append timing excludes client acknowledgement, pipeline admission, trade
  publication and network I/O.
- Scan timing is not recovery time; replay timing is not crash-recovery time.
- Snapshot, online recovery, live pipeline/WAL integration, Network,
  replication and production optimization remain out of scope. Phase 8
  records GC-profiler observations only as component evidence; they do not
  establish a production allocation or GC claim.
- Filesystem cache, Windows scheduling, one fork and one one-second sample
  limit inference. More rigorous performance work requires a separate
  evidence/optimization decision.

## Phase 8 TASK-034 Recovery Benchmark

TASK-034 adds a separate recovery benchmark without changing the WAL,
Snapshot, Recovery, Pipeline or Gateway production semantics. The code
checkpoint is `9835624cb4fa31368edda4f4483fa0c6eb78ae65` and its exact-SHA CI
run [32616029460](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32616029460)
passed. The raw JMH output is local and ignored at
`benchmark-results/phase8-recovery-full.json`.

Measured boundaries are intentionally separate:

- `pureWalReplay` — genesis replay of the authoritative WAL;
- `snapshotDecodeRestore` — Snapshot decode and checkpoint restore;
- `snapshotTailRecovery` — Snapshot restore followed by WAL-tail replay;
- `offlineSnapshotCreation` — closed-WAL Snapshot generation; and
- `bootstrapToListener` — process bootstrap to listener-ready.

The deterministic fixture uses three SubmitLimit records and one CancelOrder
record per four commands. The matrix uses 256 and 1,024 commands, 4,128 and
65,536-byte segments, half-prefix/half-tail recovery, and 128/512 active orders
respectively. Physical fixture metadata is:

| Commands | Snapshot sequence | Tail | 4,128-byte segments / bytes | 65,536-byte segments / bytes |
| ---: | ---: | ---: | ---: | ---: |
| 256 | 128 | 128 | 3 / 11,872 | 1 / 11,808 |
| 1,024 | 512 | 512 | 12 / 47,488 | 1 / 47,136 |

The run used Microsoft Windows 11 Home Chinese build 26200, a 13th Gen Intel
Core i9-13900H, an `E:` NTFS/NVMe host volume, Microsoft OpenJDK 21.0.12,
JMH 1.37, the G1 default collector, no explicit JVM arguments, an estimated
7.91 GiB maximum heap (`java -XshowSettings:vm -version`), one fork, one
thread, one 1-second warmup and one 1-second measurement. Required
SampleTime P50/P95/P99/P999 values for all 20 benchmark/parameter combinations
are recorded in [`PHASE-8-task-034.md`](../../tasks/reports/PHASE-8-task-034.md).

The same matrix was run with JMH's built-in `gc` profiler. Across all methods
and parameters, `gc.alloc.rate` ranged from 52.789 to 1,922.390 MB/sec,
`gc.alloc.rate.norm` from 51,663.677 to 1,354,989.795 B/op, `gc.count` from
0 to 6 counts and `gc.time` from 0 to 4 ms. The profiler output is local and
ignored at `benchmark-results/phase8-recovery-gc.json`.

The Throughput `primaryMetric.score` values (unit `ops/ms`) from the main raw
JSON are summarized in the task report across both command counts and segment
sizes; no best-case-only result is selected.

These are component/local-host observations only. They do not claim production
RTO, online crash-recovery time, availability, durable client acknowledgement,
power-loss safety, exactly-once behavior, capacity or Product Release
readiness. `SYNC_EACH_APPEND` remains the correctness default; benchmark
results cannot authorize an optimization or a default change. `force(true)`
and atomic-move fault injection remain explicitly unverified without a
production-only seam, and no hardware power-loss guarantee is claimed.

TASK-034 Closure Proposal is prepared but not approved. The technical Closure
input is `c59d7c0` / CI `32616802595` PASS with 195 tests, 0 failures and
Checkstyle 0. Human Phase 8 Closure Approval is recorded; the next gate is
master verification and `v0.7.0-engineering-baseline` tag CI. Phase 9 and
Product Release remain unauthorized.
