# Phase 5 Command WAL and Deterministic Replay — Cumulative Evidence Report

## Status Dashboard

| Field | Value |
| --- | --- |
| Phase | Phase 5 — Command WAL and Deterministic Replay Foundation |
| Current Task | Limited Closure Remediation R1-R3 |
| Completed Task | TASK-20260821-018 — WAL Benchmark, Documentation and Closure Preparation |
| Result | `Remediation completed / Final Closure Review Pending` |
| Baseline | `v0.3.0-engineering-baseline` remains frozen |
| Full Verification | `mvn verify` PASS; 114 tests, 0 failures; Maven reactor 3/3 SUCCESS |
| Checkstyle | 0 violations |
| Latest Commit | `0e6ac954a2525fbea19b4c9e818f7c6c90098d97` (R3 documentation synchronization) |
| Latest CI | [run 32482054086](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32482054086) PASS |
| Branch | `feature/phase5-command-wal-replay` |
| Next Gate | Final Human Phase 5 Closure Review after R3 exact-SHA CI |

## Human Blueprint Authorization

ADR-0013 D1-D10 and TASK-014 through TASK-018 were approved by the Human Phase
5 Blueprint Approval. Execution is authorized in strict dependency order when
the preceding automated Evidence Gate and exact-SHA CI pass. Phase Closure,
merge, `v0.4.0-engineering-baseline`, live pipeline/WAL integration, Network,
Snapshot, online Recovery and Product Release remain unauthorized.

The approved force-failure interpretation remains in force: a failed write,
force or rotation is never reported as a successful logical append and makes
the writer terminal, but a failed `force(true)` does not prove that record
bytes are physically absent. Strict scan/reopen determines the valid persisted
boundary. Limited remediation dynamically verifies rotation failure through a
deterministic segment-name collision; dynamic `force(true)` injection is not
claimed because it would require a new production test seam.

## TASK-014 Format / Configuration / Codec (Completed)

TASK-014 adds a JDK-only, pure version-1 WAL format foundation under the new
`com.ultralatency.matching.persistence.wal` package:

- `WalCommandCodec` encodes and strictly decodes the approved 32-byte segment
  header and 52-byte `SUBMIT_LIMIT` / 28-byte `CANCEL_ORDER` records;
- all multi-byte fields are explicit big-endian values and CRC32C covers only
  the record body, excluding the length and checksum fields;
- `WalConfiguration` validates the directory, minimum segment bound and
  durability mode, with `SYNC_EACH_APPEND` as the correctness default;
- unsupported version/type/flags/side/reserved bytes, invalid lengths, CRC
  mismatches and invalid domain values fail with `WalFormatException`;
- `WalDurabilityMode`, `WalSegmentHeader` and `WalFormatException` are
  project-owned value/error contracts;
- no filesystem I/O, writer, reader, replay, pipeline integration or frozen
  production-file modification was introduced.

Golden-byte fixtures cover the segment header and both command record layouts.
Round-trip tests cover both command types and maximum positive domain values;
invalid-format tests cover header, envelope, type, side, reserved bytes, CRC
and maximum-length rejection. Repeated encoding returns independent arrays.

## TASK-014 Evidence Gate

Focused verification:

```text
mvn -pl core -am test "-Dtest=WalCommandCodecTest" \
  "-Dsurefire.failIfNoSpecifiedTests=false"
```

Result: 9 focused tests passed, 0 failures.

Full verification:

```text
mvn verify
```

Result: 92 tests passed, 0 failures, Checkstyle 0 violations and Maven reactor
3/3 SUCCESS. `git diff --check` passed. The frozen Domain, OrderBook, Engine
and Pipeline production paths have zero changes relative to the Phase 4
baseline. The unrelated `.vscode/settings.json` remains untracked and
untouched.

Commit `e5e4c9677ca926c5f774930472d092e42f50008e` passed exact-SHA GitHub
Actions CI run
[32464648365](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32464648365).

## TASK-015 Segmented Command WAL Storage (Completed)

TASK-015 adds synchronous caller-owned segmented storage around the approved
codec:

- `CommandWalWriter` creates or reopens a configured directory, owns one
  active segment with an exclusive `FileLock`, writes complete positional
  records and rotates before a record would exceed the configured bound;
- `SYNC_EACH_APPEND` forces the channel after complete bytes are written and
  before logical append success; `BUFFERED` performs no durability claim and
  is never the configuration default;
- exact-next command sequences are enforced before mutation, close is
  idempotent, and any write/force/rotation failure makes the writer terminal;
- `CommandWalReader` strictly orders segments by validated first sequence,
  validates headers, lengths, CRC and command sequences, and reports path and
  byte offset for corruption;
- strict reads never mutate files. Explicit `CommandWalWriter.reopen` may
  truncate only an incomplete final physical record or remove an empty
  trailing segment, then rescans before accepting appends;
- complete-record corruption, earlier truncation, segment gaps and bad
  headers fail closed; no salvage or background I/O is introduced.

The implementation remains isolated under the WAL package. Domain, OrderBook,
MatchingEngine and Pipeline production paths are unchanged.

## TASK-015 Evidence Gate

The focused storage matrix was run three consecutive times:

```text
mvn -pl core -am test "-Dtest=CommandWalWriterTest,CommandWalReaderTest" \
  "-Dsurefire.failIfNoSpecifiedTests=false"
```

Each run passed 10 tests. The matrix covers multi-segment rotation, exact-next
sequence rejection, idempotent close, exclusive ownership, final torn-tail
reopen, complete corruption, earlier truncation, segment gaps, header
corruption and empty trailing segments.

Full `mvn verify` passed with 102 tests, 0 failures, Checkstyle 0 violations
and Maven reactor 3/3 SUCCESS. `git diff --check` and frozen-path audit passed.
Commit `7da0069a82d821cecd95815cfeae37fe50268830` passed exact-SHA GitHub
Actions CI run
[32466198050](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32466198050).

## TASK-016 Deterministic Command Replay (Completed)

TASK-016 adds the offline `recovery` package without changing the matching
engine or pipeline:

- `CommandWalReplayer` reads a strictly closed WAL, creates a genesis
  `MatchingEngine`, applies every command once in order and stops at the exact
  rejected command sequence;
- `ReplayTranscript` is immutable and contains ordered public `EngineResult`
  values plus a canonical lowercase SHA-256 digest;
- `ReplayTranscriptDigest` uses explicit big-endian primitive framing, stable
  outcome codes and order-significant result/match/execution fields, with no
  `toString`, object identity, locale, clock or platform-default charset;
- `ReplayProbeResult` applies a fixed public command suffix after replay so
  direct and replayed prefixes can be compared without exposing private state;
- no Snapshot, online Recovery, WAL mutation, Pipeline integration or internal
  state hash is claimed.

## TASK-016 Evidence Gate

The focused replay suite passed 5 tests. It covers a fixed 1,024-command
multi-segment stream, direct-vs-replay ordered equality, two independent replay
digests, a fixed future public probe suffix, digest order sensitivity and a
poison command rejected at its exact sequence.

Full `mvn verify` passed with 107 tests, 0 failures, Checkstyle 0 violations
and Maven reactor 3/3 SUCCESS. `git diff --check` and frozen-path audit passed.
Commit `f4344314fed8bd2893d57d35e803a0631e00d8e2` passed exact-SHA GitHub
Actions CI run
[32466659845](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32466659845).

## TASK-017 Corruption and Recovery-Boundary Verification (Completed)

TASK-017 adds deterministic public-boundary failure evidence without changing
the approved WAL/recovery semantics:

- truncation of every incomplete byte offset in the final record is classified
  as an eligible torn tail, while exact record boundaries remain valid prefixes;
- strict scans never mutate damaged files, and explicit reopen preserves the
  valid prefix while truncating only the final incomplete record;
- header, body, checksum, complete-record, segment-gap and misnamed-segment
  corruption fail closed with segment/offset diagnostics;
- repeated reopen is stable, and repaired-prefix replay equals direct prefix
  execution; no reflection, sleep, production hook or salvage path is used.

The focused failure matrix passed 6 tests in three consecutive runs. Full
`mvn verify` passed with 113 tests, 0 failures, Checkstyle 0 violations and
Maven reactor 3/3 SUCCESS. `git diff --check` and frozen-path audit passed.
Commit `16dc9578923e2165291741389d092eef863d790d` passed exact-SHA GitHub
Actions CI run
[32467018067](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32467018067).

## TASK-018 WAL Benchmark, Documentation and Closure Preparation (Completed)

TASK-018 adds `WalBenchmark` in the benchmark module and keeps the measured
boundaries separate:

- `walAppend` measures one append with `SYNC_EACH_APPEND` and `BUFFERED`
  reported independently;
- `walScan` measures strict closed-WAL validation/decode without engine replay;
- `walReplay` measures strict scan followed by genesis engine replay.

Each benchmark state creates and cleans its own temporary fixture outside the
measured operation. The full JMH matrix used JMH 1.37, OpenJDK 21.0.12, one
fork, one thread, one 1-second warmup and one 1-second measurement across
4,128/65,536-byte segments and 256/1,024-command datasets. The remediation
uses a deterministic alternating SubmitLimit/CancelOrder stream and records
CPU, storage, JVM/GC, workload bytes/segments and SampleTime P50/P99. Raw
output remains local and ignored at `benchmark-results/wal-remediation-full.json`.

The remediation full-matrix observations (throughput `ops/us`, sample mean,
P50 and P99 in `us/op`) are recorded in
[`docs/benchmark/recovery.md`](../../docs/benchmark/recovery.md).

```text
append SYNC  4128: 0.004 ops/us, mean 284.114 us/op, P50 234.752, P99 1,357.414
append SYNC 65536: 0.004 ops/us, mean 234.691 us/op, P50 209.152, P99 444.508
append BUFFERED 4128: 0.123 ops/us, mean 7.535 us/op, P50 2.500, P99 115.092
append BUFFERED 65536: 0.346 ops/us, mean 3.936 us/op, P50 2.400, P99 13.296
scan 256/4128: mean 225.085 us/op, P50 209.920, P99 448.200
scan 1024/4128: mean 619.519 us/op, P50 584.704, P99 1,007.155
replay 256/4128: mean 242.991 us/op, P50 230.400, P99 437.248
replay 1024/4128: mean 731.966 us/op, P50 690.176, P99 1,231.380
```

The numbers are component observations only. `BUFFERED` is not durable
throughput; append latency is not client acknowledgement or trade latency;
scan/replay are not online recovery time. No format, default durability or
production claim was changed.

Evidence gate:

- benchmark smoke and full matrix completed successfully;
- `mvn verify` passed with 114 tests, 0 failures, Checkstyle 0 violations and
  Maven reactor 3/3 SUCCESS;
- `git diff --check` passed and frozen Domain/OrderBook/Engine/Pipeline
  production diff relative to `v0.3.0-engineering-baseline` is zero;
- R1 rotation-failure test commit `83e5544` passed exact-SHA CI [run 32481266960](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32481266960);
- R2 mixed-command benchmark commit `bd37382` passed exact-SHA CI [run 32481451533](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32481451533);
- architecture, README, ADR, Blueprint, Task and context documents were
  synchronized by R3 commit `0e6ac95`; its exact-SHA CI `32482054086` passed.
  Final Closure Review remains pending.

## Next State

TASK-014 through TASK-018 and the authorized limited remediation are complete.
Phase 5 Closure is **not** approved: merge to `master`,
`v0.4.0-engineering-baseline`, live pipeline/WAL integration, Snapshot, online
Recovery, Network and Product Release remain unauthorized. Stop after the R3
documentation commit and exact-SHA CI for the final Human Phase 5 Closure
Review.

## Known Scope Boundary

Phase 5 remains a persistence/replay engineering baseline. It does not claim
durable live acknowledgements, crash recovery, hardware power-loss safety,
network durability, production throughput or Release readiness.
