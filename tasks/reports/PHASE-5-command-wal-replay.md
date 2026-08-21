# Phase 5 Command WAL and Deterministic Replay — Cumulative Evidence Report

## Status Dashboard

| Field | Value |
| --- | --- |
| Phase | Phase 5 — Command WAL and Deterministic Replay Foundation |
| Current Task | TASK-20260821-015 — Segmented WAL Storage |
| Completed Task | TASK-20260821-014 — WAL Format and Command Codec Foundation |
| Result | `TASK-014 Completed / Evidence Gate Passed` |
| Baseline | `v0.3.0-engineering-baseline` remains frozen |
| Full Verification | `mvn verify` PASS; 92 tests, 0 failures; Maven reactor 3/3 SUCCESS |
| Checkstyle | 0 violations |
| TASK-014 Commit | `e5e4c9677ca926c5f774930472d092e42f50008e` |
| TASK-014 CI | [run 32464648365](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32464648365) PASS |
| Branch | `feature/phase5-command-wal-replay` |
| Next Gate | TASK-015 Implementation / Evidence Gate |

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

## Next State

TASK-014 is completed under the approved dependency order. TASK-015 is the
next authorized task and may begin without another routine Human approval as
long as its Evidence Gate passes and no Exception Gate is triggered. Phase
Closure, merge and baseline-tag actions remain separate Human decisions.

## Known Scope Boundary

Phase 5 remains a persistence/replay engineering baseline. It does not claim
durable live acknowledgements, crash recovery, hardware power-loss safety,
network durability, production throughput or Release readiness.
