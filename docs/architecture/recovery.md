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
 read-only Evidence Gate is PASS. TASK-028 benchmark and closure evidence are
 complete at `9fed6b2` / CI `32574274905`, with verifier,
benchmark-reviewer and docs-auditor PASS. Human Phase 7 Closure is approved;
the merge `6473365`, master CI `32574891113` and tag CI `32574958017` are
verified. Online crash recovery and Snapshot restore are governed by approved
ADR-0016 and the Complete Phase 8 Blueprint. TASK-029 canonical checkpoint
export/restore is complete at `66fc9d2` / exact-SHA CI `32577713667` PASS;
TASK-030 Snapshot codec/store is complete at `6907391` / exact-SHA CI
`32579065372` PASS; TASK-031 recovery planner is next and later implementation
remains gated by dependency Evidence Gates.

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

## Proposed Phase 8 Recovery Flow

```text
closed WAL
    -> strict scan and genesis replay
    -> canonical engine checkpoint at Sequence N
    -> immutable Snapshot v1 bound to WAL prefix 1..N

restart
    -> explicit PURE_WAL or SNAPSHOT_THEN_WAL mode
    -> strict WAL validation / approved final-tail repair
    -> restore engine and replay required commands
    -> verify engine / writer / coordinator next Sequence
    -> bind network listener last
```

Snapshot format, state restore and online recovery orchestration remain
approved work governed by ADR-0016 and TASK-030 through TASK-034. The TASK-029
canonical checkpoint foundation is complete. Snapshot is a derived acceleration
checkpoint; WAL remains authoritative. Hot Snapshot,
WAL retention, reconnect/deduplication, exactly-once and replication remain
deferred. Phase 7 completed TASK-024 through TASK-028 with approved evidence;
the engineering baseline is frozen at `v0.6.0-engineering-baseline`:

```text
Protocol request
    -> WAL append + SYNC_EACH_APPEND
    -> Pipeline admission
    -> MatchingEngine
```

This ordering is authorized only within the Phase 7 Blueprint and its Evidence
Gates. It does not claim online recovery, client-received durability or
production readiness.

The approved Phase 8 design preserves those boundaries. TASK-029 has delivered
the narrowly additive canonical checkpoint export/restore APIs at `66fc9d2` /
CI `32577713667`; TASK-030 Snapshot codec/store is complete at `6907391` /
CI `32579065372`. Recovered-runtime construction remains gated by TASK-031
and the later dependency Tasks.

## Verification

The Phase 5 public evidence compares ordered `EngineResult` values, TradeId,
EventSequence, trade/execution fields, SHA-256 transcript digests and a fixed
public probe suffix. It also verifies strict corruption handling, final
torn-tail repair and repaired-prefix replay. It does not claim a full state
hash, Snapshot recovery time or power-loss guarantee.
