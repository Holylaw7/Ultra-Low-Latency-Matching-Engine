# Persistence and Replay Architecture

## Status

Phase 5 implements a JDK-only, versioned command WAL and strict offline
deterministic replay baseline, completed and frozen at
`v0.4.0-engineering-baseline`. Phase 7 has an approved Blueprint; TASK-024
durable contracts/configuration and TASK-025 WAL-before-pipeline coordination
are complete with exact-SHA Evidence Gates, including TASK-026 durable Netty
composition at `a978fe7` / CI `32565087793`. The Human-approved TASK-027 Round 2
terminal remediation is complete at `7b9106f` / CI `32571940187` after baseline
and prior remediation runs `32565591806`, `32566165212` and `32570890919`; its
read-only Evidence Gate review is pending and TASK-028 is paused. Final evidence
synchronization is `62ae68f` / CI `32572441090`; status-only reconciliation
`b24db93` / CI `32572561973` and final Evidence-Gate documentation
verification `b6eaa8d` / CI `32572786850` also passed without production
changes.
Online
crash recovery and Snapshot restore remain future work.

## Implemented Offline Flow

```text
EngineCommand
    -> WalCommandCodec
    -> CommandWalWriter
    -> versioned segmented WAL files
    -> strict CommandWalReader
    -> CommandWalReplayer
    -> genesis MatchingEngine
    -> ordered EngineResult transcript
    -> SHA-256 transcript digest / public probe
```

The command sequence is the logical WAL order. Segment offsets and any future
ring sequence remain storage or infrastructure metadata; they cannot replace
`Sequence`, `EventSequence` or `TradeId`.

## Format and Failure Boundary

- Segment headers and record envelopes use the approved version-1 big-endian
  format with CRC32C and explicit reserved-byte validation.
- `SYNC_EACH_APPEND` forces complete bytes before logical append success;
  `BUFFERED` is evidence-only and makes no durability claim.
- The writer is synchronous, single-owner and terminal after write, force or
  rotation failure. A failed force does not prove that record bytes are absent;
  strict scan/reopen determines the valid persisted boundary.
- Only an incomplete physical tail in the final segment may be explicitly
  truncated. CRC, header, sequence, segment-order and complete-record failures
  fail closed without salvage.

## Deferred Online Recovery Flow

```text
Snapshot
    -> record snapshot position
    -> restart
    -> load snapshot
    -> replay WAL records
    -> verify state hash
```

Snapshot format, state restore, online recovery orchestration, durable
acknowledgements, and replication remain future work and require a separate
approved Blueprint. Phase 7 has an approved Blueprint; TASK-024 through
TASK-026 and the approved TASK-027 Round 2 runtime-composition remediation are
complete at `7b9106f` / CI `32571940187`; TASK-027 awaits its read-only Evidence
Gate review, and TASK-028 is paused:

```text
Protocol request
    -> WAL append + SYNC_EACH_APPEND
    -> Pipeline admission
    -> MatchingEngine
```

This ordering is authorized only within the Phase 7 Blueprint and its Evidence
Gates. It does not claim online recovery, client-received durability or
production readiness.

## Verification

The Phase 5 public evidence compares ordered `EngineResult` values, TradeId,
EventSequence, trade/execution fields, SHA-256 transcript digests and a fixed
public probe suffix. It also verifies strict corruption handling, final
torn-tail repair and repaired-prefix replay. It does not claim a full state
hash, Snapshot recovery time or power-loss guarantee.
