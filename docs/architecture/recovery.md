# Persistence and Replay Architecture

## Status

Phase 5 implements a JDK-only, versioned command WAL and strict offline
deterministic replay baseline. It does not implement online crash recovery,
Snapshot restore or live pipeline durability.

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

Snapshot format, state restore, online recovery orchestration, live
pipeline/WAL integration, durable acknowledgements, Network and replication
remain future work and require a separate approved Blueprint.

## Verification

The Phase 5 public evidence compares ordered `EngineResult` values, TradeId,
EventSequence, trade/execution fields, SHA-256 transcript digests and a fixed
public probe suffix. It also verifies strict corruption handling, final
torn-tail repair and repaired-prefix replay. It does not claim a full state
hash, Snapshot recovery time or power-loss guarantee.
