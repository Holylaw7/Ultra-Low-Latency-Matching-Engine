# ADR-0016: Snapshot Checkpoint and Online Recovery Bootstrap

## Status

Approved — Human Phase 8 Blueprint Approval recorded on `2026-08-22`.
Implementation is authorized only in the enumerated dependency order and under
the per-Task Evidence Gates. Merge and baseline tagging remain unauthorized.

## Context

The project is frozen through `v0.6.0-engineering-baseline` with:

- a deterministic Domain, OrderBook and MatchingEngine core;
- a bounded single-producer Event Pipeline;
- WAL v1 with strict scan and genesis offline replay;
- Protocol v1 and a single-session Netty Gateway; and
- a live `SYNC_EACH_APPEND` WAL-before-pipeline composition.

Phase 7 deliberately rejects a non-empty WAL at startup because it cannot
restore engine state and safely hand that state to the live runtime. Phase 8
must close that gap without making Snapshot a second authority, weakening WAL
validation, changing matching semantics or claiming production recovery.

## Goal

Define a deterministic recovery bootstrap that can restore from either the
complete authoritative WAL or a validated Snapshot checkpoint plus WAL tail,
then enter the existing live durable path only after all sequence and ownership
invariants agree.

## Decisions

### D1 — Two explicit recovery modes

Support two explicitly selected modes:

```text
PURE_WAL
    -> genesis MatchingEngine
    -> replay commands 1..WAL end

SNAPSHOT_THEN_WAL
    -> validate and restore latest published Snapshot
    -> replay commands snapshot Sequence + 1..WAL end
```

`SNAPSHOT_THEN_WAL` is the normal mode when a Snapshot exists. `PURE_WAL`
remains the deterministic reference path. It is not an automatic fallback from
a corrupt or incompatible published Snapshot.

### D2 — WAL remains the sole recovery authority

WAL v1 commands remain the authoritative recovery source. A Snapshot is an
immutable, derived acceleration checkpoint bound to an exact validated WAL
prefix. It cannot authorize commands absent from the WAL, replace WAL integrity
checks, or establish an independent business history.

Phase 8 retains the WAL from Sequence 1. WAL retention, prefix deletion,
compaction and truncation beyond the already approved final-torn-tail rule are
deferred.

### D3 — Offline, quiescent Snapshot consistency point

Phase 8 creates a Snapshot only through an offline generator that owns the same
exclusive `recovery.lock` lease used by recovery/live runtime. It acquires the
lease before WAL inspection and holds it through Snapshot publication. Strict
scan and genesis replay must succeed for contiguous commands `1..N`. It does
not capture a hot or concurrently mutating live engine.

The generator records the segment inventory and exact file sizes at its strict
scan boundary and verifies they remain unchanged before publication. A held
lease or changed inventory rejects generation. Phase 8 writers and recovering
runtimes must participate in this lease protocol. Running the generator against
a non-participating legacy writer is unsupported; that writer must be stopped
and its WAL treated as offline input.

The consistency point is:

```text
checkpoint.lastAppliedCommandSequence = N
authoritative WAL prefix              = commands 1..N
```

This deliberately defers a running-service checkpoint barrier and avoids
introducing a second mutation owner.

### D4 — Canonical engine checkpoint

The checkpoint contains:

```text
MatchingEngineCheckpoint
├─ lastAppliedCommandSequence
├─ nextTradeId
├─ nextEventSequence
└─ OrderBookCheckpoint
   ├─ BUY: price descending, FIFO within price
   └─ SELL: price ascending, FIFO within price
```

Each active resting order stores OrderId, Side, Price, original Quantity,
remaining Quantity and original Command Sequence. Restore reconstructs the
existing order lifecycle using current Domain behavior and must preserve
price-time priority exactly. It does not snapshot object identity, memory
address, node identity or allocation order.

### D5 — Snapshot binary format v1

Snapshot v1 is fixed-width, big-endian, versioned and bounded. The file name is:

```text
snapshot-00000000000000000123.bin
```

The 128-byte header is:

| Offset | Size | Field |
| ---: | ---: | --- |
| 0 | 8 | ASCII magic `ULMESNP1` |
| 8 | 4 | format version `1` |
| 12 | 4 | header length `128` |
| 16 | 8 | total file length |
| 24 | 8 | checkpoint Command Sequence |
| 32 | 8 | next TradeId |
| 40 | 8 | next EventSequence |
| 48 | 4 | active order count |
| 52 | 4 | order record length `48` |
| 56 | 4 | bound WAL format version `1` |
| 60 | 4 | flags `0` |
| 64 | 32 | SHA-256 of canonical WAL record bytes `1..N` |
| 96 | 32 | SHA-256 canonical checkpoint digest |

Each 48-byte active-order record is:

| Field | Size |
| --- | ---: |
| OrderId | 8 |
| Side | 1 |
| reserved zero bytes | 7 |
| Price units | 8 |
| original Quantity units | 8 |
| remaining Quantity units | 8 |
| original Command Sequence | 8 |

The WAL-prefix digest hashes, in Command Sequence order, the exact WAL v1
record envelope bytes from `total record length` through each record CRC32C. It
excludes segment headers and unused/trailing segment bytes, so segment rotation
does not change the logical prefix digest. The canonical checkpoint digest
hashes the big-endian 32-byte sequence/counter descriptor followed by the exact
active-order payload:

```text
checkpoint Command Sequence (8)
next TradeId               (8)
next EventSequence         (8)
active order count         (4)
order record length        (4, value 48)
active-order payload       (count * 48)
```

Both digests use raw 32-byte SHA-256 output. The checkpoint digest therefore
covers the engine counters as well as canonical OrderBook state.

The file ends with a four-byte CRC32C of `header + payload`. The exact length is:

```text
132 + activeOrderCount * 48
```

Readers validate length, count and configured limits before allocation. Unknown
versions, flags, non-zero reserved bytes, invalid Side, invalid numeric values,
duplicate OrderId, invalid quantity relationships, non-canonical ordering,
checksum mismatch or digest mismatch fail closed.

Snapshot v1 requires checkpoint Command Sequence `N >= 1`. An empty WAL starts
from genesis and does not produce or select a Snapshot. The zero-active-order
case is valid after at least one command. The zero-padded numeric suffix in the
file name must equal the checkpoint Sequence encoded in the header.

### D6 — Atomic immutable publication

Snapshot publication is:

```text
same-directory snapshot-N.tmp
    -> complete positional write
    -> force(true)
    -> read-back strict validation
    -> required ATOMIC_MOVE
    -> immutable snapshot-N.bin
```

Final files are never overwritten. If atomic move is unsupported, creation
fails. Orphan temporary files are ignored by recovery and require explicit
maintenance cleanup. `force(true)` and atomic move are JDK/filesystem completion
boundaries, not hardware power-loss or directory-entry durability guarantees.

### D7 — Strict Snapshot selection and corruption policy

Recovery selects the published Snapshot with the greatest Command Sequence.
The selected Snapshot must strictly validate and bind to the retained WAL
prefix through its recorded SHA-256 digest. A corrupt, incompatible, mismatched
or WAL-newer Snapshot fails closed. Recovery does not silently skip it or fall
back to an older Snapshot or `PURE_WAL`.

`PURE_WAL` is an explicit startup policy chosen before recovery begins.

### D8 — WAL repair and tail replay

Recovery may apply only WAL v1's existing explicit repair for an incomplete
record at the physical tail of the final segment. Header corruption, CRC
mismatch, complete malformed records, sequence gaps and earlier corruption
remain fail-closed errors. After strict scan, Snapshot recovery replays only
commands `N+1..WAL end`.

### D9 — Recovery startup lifecycle

The recovery bootstrap lifecycle is:

```text
NEW -> RECOVERING -> RECOVERED -> STARTING -> RUNNING
                     |              |
                     +-----> FAILED <+
```

Recovery acquires an exclusive JDK `FileLock` on a fixed `recovery.lock` file
in the WAL directory before scan or repair. The owning channel and lock remain
held through live runtime shutdown, preventing a second bootstrap from scanning
or opening the same WAL concurrently. Lock acquisition failure fails closed;
the lock file contents are not recovery authority and the file may remain after
shutdown.

No listener is bound and no request is admitted before the runtime reaches
`RUNNING`. Any recovery, validation, resource construction, pipeline start or
bind failure retains the first cause, releases owned resources and leaves the
runtime terminal.

### D10 — Exact live handoff

Before network bind, all live components must agree:

```text
recoveredEngine.expectedNextCommandSequence
    = walWriter.nextCommandSequence
    = coordinator.nextCommandSequence
    = lastStrictWalSequence + 1
```

The Pipeline consumer exclusively owns the recovered MatchingEngine. Recovery
results are verification artifacts and are never emitted as new client
responses. The first live command continues the WAL Command Sequence exactly.

### D11 — Identity and acknowledgement boundaries

After restart, a new TCP session begins RequestId at 1 while Command Sequence,
TradeId and EventSequence continue from recovered state. RequestId, Command
Sequence, WAL physical position, ring sequence, EventSequence and TradeId
remain distinct.

Recovery does not resolve the client outcome of a command that became durable
before a previous disconnect. Reconnect, retry deduplication, exactly-once and
session recovery remain deferred.

### D12 — Narrow additive frozen-boundary exceptions

The `v0.6.0-engineering-baseline` tag remains immutable. WAL v1 bytes and
corruption rules, Protocol v1 bytes, Domain identifier semantics, matching
outcomes, price-time priority, Phase 7 WAL-before-execute ordering and the
single-session/one-in-flight topology remain frozen.

Human Blueprint Approval is required to authorize only these additive API
exceptions:

- canonical OrderBook checkpoint export/restore;
- MatchingEngine checkpoint export/restore;
- MatchingEnginePipeline construction around a recovered engine; and
- DurableCommandCoordinator initialization with a validated next sequence.

Existing constructor behavior must remain compatible. Any broader change is an
Exception Gate.

### D13 — Determinism and performance evidence

Pure-WAL and Snapshot-plus-tail recovery must converge on the same complete
canonical checkpoint digest and fixed public probe results. For Snapshot N and
WAL end M, ordered recovery results are compared only over the common replay
suffix:

```text
PURE_WAL results for commands N+1..M
    = SNAPSHOT_THEN_WAL results for commands N+1..M
```

Results for the Snapshot-covered prefix `1..N` are not reconstructed by
Snapshot restore and are never emitted to clients. Tail results must retain
equal TradeId and EventSequence. Benchmarks separate pure replay, Snapshot
decode and restore, Snapshot plus tail, offline Snapshot creation and
startup-to-listener ready.

Results are component/local-host engineering evidence only. They do not prove
production RTO, availability SLA, power-loss safety or operational readiness,
and cannot change correctness defaults.

### D14 — Deferred scope

Hot Snapshot capture, WAL retention/deletion/compaction, automatic corrupt
Snapshot fallback, reconnect/dedup/exactly-once, multiple sessions, replication,
HA, deployment, production optimization and Product Release are deferred.

## Target Architecture

```text
Offline checkpoint creation

closed WAL v1
    -> strict scan
    -> genesis replay
    -> canonical MatchingEngine checkpoint
    -> WAL-prefix and canonical checkpoint digests
    -> temp write + force + validate + atomic move
    -> immutable Snapshot v1

Restart / online bootstrap

process start
    -> exclusive recovery ownership
    -> approved final-torn-tail repair
    -> strict WAL scan
    -> PURE_WAL or SNAPSHOT_THEN_WAL
    -> recovered MatchingEngine
    -> exact next-sequence convergence
    -> recovered-engine Pipeline
    -> durable coordinator and Gateway
    -> bind listener last
    -> RUNNING
```

## Crash-window semantics

| Window | Required restart behavior |
| --- | --- |
| Before append | command is absent |
| Partial WAL append | only final torn tail may be explicitly repaired |
| Durable append before publish | replay applies the command |
| Engine apply before response | replay restores state; client outcome remains ambiguous |
| Snapshot temporary write | published Snapshot remains unchanged; temp ignored |
| Snapshot force before move | published Snapshot remains unchanged |
| Atomic move completed | new Snapshot strictly validates or recovery fails closed |
| Snapshot N plus WAL N+1..M | restore N and replay N+1..M |
| Snapshot newer than WAL | fail closed |
| Any failure before live handoff | listener remains unbound; runtime enters `FAILED` |

## Consequences

### Positive

- Recovery can be proved against the complete authoritative WAL.
- Snapshot speeds deterministic state reconstruction without becoming a second
  source of truth.
- Listener-last handoff prevents partially recovered service admission.
- Exact format and additive API decisions avoid implementation-time protocol
  invention.

### Costs and limitations

- WAL must still be retained and strictly scanned from Sequence 1.
- Snapshot generation is offline and requires a closed WAL.
- Snapshot corruption fails startup instead of falling back automatically.
- Recovery remains single-node and does not solve client ambiguity.
- File forcing and atomic rename do not establish hardware power-loss safety.
- CRC32C and SHA-256 detect integrity mismatch but do not authenticate files
  against a malicious writer with filesystem access.

## Exception Gate

Stop for Human review if implementation requires changing WAL v1 or Protocol
v1 bytes, deleting/compacting WAL, hot Snapshot capture, making Snapshot an
authority, automatic fallback from a corrupt published Snapshot, binding before
recovery, accepting commands during recovery, synthesizing unvalidated
counters, changing matching semantics, adding a producer/session/thread model,
reconnect/dedup/exactly-once behavior, a new critical dependency, a
production-only test seam, reflection or sleep-based correctness, weakened
integrity/equivalence assertions, a performance-driven default change, or any
unlisted file/API/scope change.

## Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-22 | Architect / Sol High | Proposed | Discovery completed; D1-D14 and the complete Phase 8 Blueprint are ready for Human review. |
| 2026-08-22 | Human Developer | Approved | D1-D14 and TASK-029 through TASK-034 authorized in strict dependency order. Additive checkpoint/restore, recovered-engine construction, validated coordinator sequence seed and new Snapshot/recovery packages are authorized exactly as listed. Per-Task Evidence Gates and Exception Gates remain mandatory; merge/tag/Phase 9 remain unauthorized. |

```text
ADR-0016: Approved
Implementation: Authorized in dependency order
Merge / v0.7.0-engineering-baseline: Not authorized
Next Gate: TASK-029 Evidence Gate
```
