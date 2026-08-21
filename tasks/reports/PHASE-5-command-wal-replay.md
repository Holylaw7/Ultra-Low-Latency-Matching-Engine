# Phase 5 Command WAL and Deterministic Replay — Cumulative Evidence Report

## Status Dashboard

| Field | Value |
| --- | --- |
| Phase | Phase 5 — Command WAL and Deterministic Replay Foundation |
| Current Task | TASK-20260821-018 — WAL Benchmark, Documentation and Closure Preparation |
| Completed Task | TASK-20260821-018 — WAL Benchmark, Documentation and Closure Preparation |
| Result | `TASK-018 Completed / Evidence Gate Passed; Closure Proposal Prepared` |
| Baseline | `v0.3.0-engineering-baseline` remains frozen |
| Full Verification | `mvn verify` PASS; 113 tests, 0 failures; Maven reactor 3/3 SUCCESS |
| Checkstyle | 0 violations |
| Latest Commit | `cd6997c8f456b88e658183483d057aceffe1e1a5` |
| Latest CI | [run 32467692149](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32467692149) PASS |
| Branch | `feature/phase5-command-wal-replay` |
| Next Gate | Human Phase 5 Closure Approval |

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
boundary.

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
4,128/65,536-byte segments and 256/1,024-command datasets. Raw output remains
local and ignored at `benchmark-results/wal-full.json`.

Representative full-matrix observations (throughput `ops/us`, sample mean
`us/op`) are recorded in [`docs/benchmark/recovery.md`](../../docs/benchmark/recovery.md):

```text
append SYNC  4128: 0.003542858 ops/us, 283.741 us/op
append SYNC 65536: 0.004224853 ops/us, 246.111 us/op
append BUFFERED 4128: 0.120667710 ops/us, 4.796 us/op
append BUFFERED 65536: 0.335894622 ops/us, 4.510 us/op
scan  256/4128: 276.955 us/op; 1024/4128: 783.633 us/op
scan  256/65536: 129.520 us/op; 1024/65536: 154.410 us/op
replay 256/4128: 348.866 us/op; 1024/4128: 1,013.116 us/op
replay 256/65536: 205.167 us/op; 1024/65536: 397.084 us/op
```

The numbers are component observations only. `BUFFERED` is not durable
throughput; append latency is not client acknowledgement or trade latency;
scan/replay are not online recovery time. No format, default durability or
production claim was changed.

Evidence gate:

- benchmark smoke and full matrix completed successfully;
- `mvn verify` passed with 113 tests, 0 failures, Checkstyle 0 violations and
  Maven reactor 3/3 SUCCESS;
- `git diff --check` passed and frozen Domain/OrderBook/Engine/Pipeline
  production diff relative to `v0.3.0-engineering-baseline` is zero;
- benchmark commit `cd6997c` passed exact-SHA CI [run 32467692149](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32467692149);
- architecture, README, ADR, Blueprint, Task and context documents are
  synchronized; Closure Proposal is prepared.

## Next State

TASK-014 through TASK-018 are completed under the approved dependency order.
Phase 5 Closure is **not** approved: merge to `master`,
`v0.4.0-engineering-baseline`, live pipeline/WAL integration, Snapshot, online
Recovery, Network and Product Release remain unauthorized. Stop now for Human
Phase 5 Closure Approval.

## Known Scope Boundary

Phase 5 remains a persistence/replay engineering baseline. It does not claim
durable live acknowledgements, crash recovery, hardware power-loss safety,
network durability, production throughput or Release readiness.
