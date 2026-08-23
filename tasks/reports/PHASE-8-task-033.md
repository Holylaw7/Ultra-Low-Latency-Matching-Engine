# Phase 8 — TASK-20260822-033 / Crash, Corruption and Determinism Verification

## Executive Status

| Item | Status |
| --- | --- |
| Phase | Phase 8 — Snapshot Checkpoint and Online Recovery Bootstrap |
| Task | `TASK-20260822-033` — Crash, Corruption and Determinism Verification |
| Authorization | Phase 8 Blueprint approval; TASK-032 Evidence Gate PASS |
| Implementation | Test fixtures and public-contract verification complete |
| Checkpoint | `eff5955` — `test-phase8-determinism-probe-and-evidence` |
| Exact-SHA CI | [32614610701](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32614610701) PASS |
| Working tree | Tracked tree clean after checkpoint; pre-existing `.vscode/` remains untouched |
| Next gate | TASK-034 Evidence Gate; Phase 8 Closure remains separately locked |

## Added Dynamic Evidence

`Phase8RecoveryVerificationTest` adds four public-contract tests:

- temporary Snapshot files are ignored while a higher-sequence published
  corruption fails closed;
- three repeated PURE_WAL and SNAPSHOT_THEN_WAL cycles converge on the same
  canonical checkpoint, WAL digest, next sequence and ordered tail TradeId /
  EventSequence values, and accept the same fixed public command probe;
- hard WAL header corruption fails before the recovered Netty listener binds,
  leaving the runtime terminal and without an address.
- recovered runtime emits no offline replay results and the first live command
  continues from the exact next WAL sequence.

## Existing Matrix Evidence

TASK-033 evidence is aggregated from the new tests and the existing focused
suites. Normal publication and deterministic ordering are verified; the
filesystem fault-injection limitation is recorded explicitly below:

| Boundary | Evidence |
| --- | --- |
| final torn-tail-only repair and hard WAL corruption/gaps | `WalCorruptionRecoveryTest` |
| Snapshot version, reserved bytes, CRC, digest, duplicate and non-canonical state | `SnapshotCodecTest` |
| final Snapshot selection, orphan temp handling, no overwrite, force/read-back and inventory stability | `SnapshotStoreTest` / `OfflineSnapshotGeneratorTest` / `Phase8RecoveryVerificationTest`; force/move fault injection is a known limitation |
| PURE_WAL, Snapshot-tail, newer/missing/corrupt Snapshot and no fallback | `RecoveryPlannerTest` |
| repeated restart convergence and suffix-only identity | `Phase8RecoveryVerificationTest` |
| listener-last, failure-before-bind, sequence convergence and lease continuity | `RecoverableDurableRuntimeTest` / `RecoverableDurableMatchingEngineTcpServerTest` |
| deterministic append/publish/disconnect crash windows | Phase 7 durability and runtime-composition verification suites |

## Acceptance Checklist

- [x] WAL hard corruption and logical sequence gaps fail closed.
- [x] Only the approved final torn-tail behavior is repairable.
- [x] Snapshot CRC, digest, prefix, version, ordering and newer-than-WAL
  failures fail closed.
- [x] Temporary Snapshot files are ignored; published corruption is not.
- [x] PURE_WAL and SNAPSHOT_THEN_WAL converge repeatedly on the same complete
  checkpoint and public probe.
- [x] Snapshot restore emits only the `N+1..M` suffix; prefix results are not
  reconstructed or sent.
- [x] Tail TradeId and EventSequence ordering remains equal across modes.
- [x] Hard recovery failure leaves the listener unbound and runtime terminal.
- [x] Existing lease ownership, listener-last and first-live-command tests
  remain green.
- [x] No reflection, sleep-based correctness oracle, production-only seam,
  recovery fallback, retry or semantic weakening was added.

## Verification Evidence

```text
Focused new tests: 4 passed, 0 failures
mvn verify: BUILD SUCCESS; 195 tests, 0 failures
Checkstyle: 0 violations
git diff --check: PASS
Exact-SHA CI: eff5955 -> 32614610701 PASS
Documentation/status synchronization: `15e68bf` -> CI `32615035994` PASS
Production code changes: 0
Frozen WAL/Snapshot/Protocol/runtime semantics: unchanged
```

## Limitations

This task does not claim hardware power-loss safety or dynamic injection of
arbitrary filesystem failures. The specific `force(true)`-failure-before-move
and completed-move failure windows are not dynamically injected because doing
so would require a new production-only seam. Successful force/read-back/
ATOMIC_MOVE behavior, immutable publication and inventory stability are
verified, while the two fault-injection cases remain explicitly unverified.
Existing barrier tests prove logical append/publish/apply ordering without
process-crash simulation. Online Recovery, Snapshot retention,
reconnect/deduplication, multi-session behavior, benchmarking and Product
Release remain outside this task.

## Gate Status

`TASK-033` implementation evidence is accepted at `eff5955` / CI
`32614610701`; final documentation/status synchronization is `15e68bf` / CI
`32615035994` PASS. TASK-034 is authorized as the next task. Phase 8 Closure,
merge, `v0.7.0-engineering-baseline` and Phase 9 remain unauthorized.
