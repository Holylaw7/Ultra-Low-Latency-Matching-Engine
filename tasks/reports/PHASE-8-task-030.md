# Phase 8 — TASK-20260822-030 / Snapshot v1 Codec and Atomic Store

## Executive Status

| Item | Status |
| --- | --- |
| Phase | Phase 8 — Snapshot Checkpoint and Online Recovery Bootstrap |
| Task | `TASK-20260822-030` — Snapshot v1 Codec and Atomic Store |
| Authorization | Human Phase 8 Blueprint Approval; TASK-029 Evidence Gate PASS |
| Scope | Exact Snapshot v1 codec, strict reader, atomic publication and offline generator |
| Implementation | Complete; Evidence Gate PASS |
| Branch | `feature/phase8-snapshot-online-recovery` |
| Evidence HEAD | `6907391` — strict Snapshot implementation and interleaved-side rejection |
| Remote / push | `origin` synchronized; push PASS |
| Exact-SHA CI | [32579065372](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32579065372) PASS |
| Working tree | Tracked tree clean; pre-existing `.vscode/` remains untouched |
| Next gate | TASK-031 recovery planner and replay executor |

## Delivered

- Added bounded `SnapshotLimits`, immutable `Snapshot` and strict
  `SnapshotFormatException` values under the authorized Snapshot package.
- Implemented Snapshot v1 big-endian encoding/decoding with the approved
  128-byte header, 48-byte canonical active-order records, WAL-prefix SHA-256,
  counter-sensitive canonical checkpoint SHA-256 and CRC32C.
- Added fail-closed validation for version, flags, reserved bytes, side and
  numeric domains, duplicate IDs, strict bid-then-ask canonical ordering,
  exact lengths and configured allocation limits before state allocation.
- Added immutable `SnapshotStore` publication using same-directory temporary
  files, complete write, `force(true)`, strict read-back validation and
  required `ATOMIC_MOVE`; final snapshots are never overwritten and corrupt
  latest finals are not bypassed.
- Added `RecoveryLease` and `WalInventory` ownership/stability helpers.
- Added `OfflineSnapshotGenerator` for closed-WAL strict replay while holding
  the shared recovery lease through publication and rejecting lease contention
  or changed WAL inventory/file sizes.

## Verification Evidence

```text
Focused TASK-030 tests: 11 passed, 0 failures
mvn verify: BUILD SUCCESS; 175 tests, 0 failures
Checkstyle: 0 violations
git diff --check: PASS
Approved/frozen-path audit: PASS; only persistence/snapshot/** and focused
tests were changed by the implementation checkpoint
Exact-SHA CI: 6907391 -> 32579065372 PASS
Read-only verifier: PASS after strict interleaved-side correction
```

## Acceptance Checklist

- [x] Golden layout assertions cover the 128-byte header, 48-byte records and
  exact total size.
- [x] WAL-prefix SHA-256, canonical checkpoint SHA-256 and CRC32C are encoded,
  verified and rejected on mutation.
- [x] Canonical digest includes checkpoint Sequence, next TradeId, next
  EventSequence, active count, record length and exact order payload.
- [x] Malformed version, flags, reserved bytes, side, numeric values,
  duplicate IDs, bid/ask ordering, lengths and configured limits fail closed
  before state allocation.
- [x] Publication is temp -> complete write -> `force(true)` -> strict
  read-back -> required `ATOMIC_MOVE`, with no overwrite or fallback.
- [x] Temporary files are ignored, while a corrupt latest final fails closed.
- [x] Closed-WAL generation performs strict contiguous replay and binds the
  Snapshot to the exact WAL prefix digest.
- [x] Recovery lease contention and changed segment inventory/file sizes reject
  generation; the lease spans scan through publication.

## Frozen Boundary and Non-Goals

No Domain, OrderBook, MatchingEngine, WAL, Pipeline, Protocol, Network or
Recovery Planner production path was modified. TASK-030 does not implement
Snapshot-tail recovery orchestration, recovered live runtime, listener binding,
WAL retention/deletion or online restart. Those concerns remain in TASK-031
through TASK-034.

## Known Limitations

`force(true)` and `ATOMIC_MOVE` remain host/filesystem primitives and do not
establish a hardware power-loss guarantee. Snapshot generation is offline and
requires a closed WAL plus cooperative `recovery.lock` ownership. Snapshot is
derived state; WAL remains authoritative. These are Blueprint limitations, not
TASK-030 evidence failures.

## Gate Status

`TASK-030 Evidence Gate PASS at 6907391 / CI 32579065372. TASK-030 is complete
and may be archived. TASK-031 is the next authorized task after this evidence
synchronization; Phase 8 Closure, merge, v0.7.0-engineering-baseline and Phase 9
remain unauthorized.`
