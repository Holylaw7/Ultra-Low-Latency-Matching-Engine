# ADR-0013: Command WAL and Deterministic Replay Foundation

## Status

Proposed — pending Human Phase 5 Blueprint Approval

## Context

The project is frozen at `v0.3.0-engineering-baseline`. It has a deterministic
synchronous `MatchingEngine` and a bounded single-producer event pipeline, but
process loss discards all matching state. ADR-0011 D6 already establishes the
ordered command stream as the future recovery authority and treats Trade and
Execution output as derived data.

The older ADR-0004 records only a high-level sequential-WAL baseline. It does
not freeze a byte format, ownership, durability acknowledgement, torn-tail
policy, replay boundary or the relationship to the frozen event pipeline.
Those decisions must exist before a persistent format is implemented.

Discovery considered two possible next phases:

1. define the external network protocol first; or
2. define an internal command WAL and deterministic replay foundation first.

The proposed order is the second option. Persistence is an internal authority
boundary that is already implied by the accepted engine decisions. Freezing it
before a network protocol prevents transport bytes, connection lifetime or
network retry behavior from becoming recovery semantics by accident.

## Decision Summary

Phase 5 will create a JDK-only, versioned, segmented command WAL and a strict
offline replay component. It will prove that an intact WAL reproduces the same
ordered `EngineResult` transcript and the same future behavior from a genesis
`MatchingEngine`.

Phase 5 will not connect the WAL to the live Phase 4 pipeline and will not
claim operational crash recovery. Snapshot, live durable admission,
acknowledgement semantics and restart orchestration require later decisions.

On approval, this ADR supersedes ADR-0004 for implemented WAL format and replay
semantics. ADR-0004 remains historical evidence of the original baseline.

## Decisions

### D1 — Phase ordering

Implement the internal command WAL and offline deterministic replay foundation
before Network/Protocol work.

- The persistent format represents project-owned `EngineCommand` semantics,
  not transport frames.
- A future protocol adapter must decode into the existing command contract.
- Network retries, sessions and framing must not define WAL identity or order.

### D2 — Recovery authority

The WAL stores the exact ordered command stream. `EngineResult`, `Trade` and
`Execution` remain derived and are not persisted as recovery authority.

- Command `Sequence` is the logical WAL order.
- Ring-buffer sequence, file offset and segment identifier are infrastructure
  metadata and never replace Command `Sequence`, `TradeId` or `EventSequence`.
- The writer accepts only the exact next positive command sequence.
- A syntactically decodable command can still be rejected by the engine during
  replay; replay fails explicitly and never skips or rewrites it.

### D3 — Versioned segmented binary format

Use a project-owned binary format with explicit big-endian byte order. A
record never crosses a segment boundary. Segment files are named by their
first command sequence:

```text
wal-00000000000000000001.log
```

Each segment begins with this 32-byte header:

| Offset | Size | Field | Rule |
| ---: | ---: | --- | --- |
| 0 | 8 | magic | ASCII `ULMEWAL1` |
| 8 | 4 | format version | `1` |
| 12 | 4 | header length | `32` |
| 16 | 8 | segment id | positive, contiguous from `1` |
| 24 | 8 | first command sequence | positive and exact for the first record |

Each record uses this envelope:

| Field | Size | Rule |
| --- | ---: | --- |
| total record length | 4 | includes length, body and checksum; bounded |
| record version | 1 | `1` |
| command type | 1 | `1=SUBMIT_LIMIT`, `2=CANCEL_ORDER` |
| flags | 2 | zero in version 1 |
| command sequence | 8 | exact next logical sequence |
| order id | 8 | project domain value |
| command payload | variable | type-specific fixed layout |
| CRC32C | 4 | body only; excludes length and checksum |

`SUBMIT_LIMIT` payload is `side(1) + reserved(7) + price(8) + quantity(8)`.
`CANCEL_ORDER` has no additional payload. Side codes are `1=BUY` and
`2=SELL`. Reserved bytes must be zero. Version 1 therefore has exact record
lengths of 52 bytes for submit-limit and 28 bytes for cancel.

Unknown versions, command types, flags, side values or non-zero reserved bytes
are unsupported format errors. The reader validates a conservative maximum
record length before allocation.

### D4 — Integrity and ordering

Every read validates, in order:

1. segment name/order and header;
2. record length bounds and type-specific exact length;
3. record version, flags and reserved bytes;
4. CRC32C;
5. domain-value construction;
6. exact-next command sequence across all segments.

Corruption reports the segment and byte offset. No reader or replay path
silently skips, sorts, repairs or substitutes a record.

### D5 — Writer ownership and segmentation

`CommandWalWriter` is synchronous, caller-thread-owned and single-writer.

- It creates or reopens one configured directory.
- It obtains an exclusive file lock for the active segment.
- It performs complete positional writes and detects lack of write progress.
- It rotates before a record when the configured segment-size bound would be
  exceeded.
- It has no internal thread, executor, queue or batching policy.
- Reader/replay requires a closed WAL in Phase 5.

Segment size is configuration, but format version and maximum record size are
not runtime-tunable semantics.

### D6 — Durability modes and acknowledgement

Two modes are permitted:

| Mode | Semantics | Intended use |
| --- | --- | --- |
| `SYNC_EACH_APPEND` | append returns only after `FileChannel.force(true)` succeeds | correctness default |
| `BUFFERED` | append returns after complete channel write; no power-loss durability claim | tests and explicit benchmark variable |

`SYNC_EACH_APPEND` remains the default. Benchmark results cannot change the
default automatically. JDK/OS force completion is the strongest Phase 5
claim; storage-controller or hardware power-loss guarantees are not inferred.

### D7 — Reopen, torn tail and hard corruption

Only an incomplete final record at the physical end of the last segment is a
recoverable torn tail.

- Strict scan reports it without mutation.
- Explicit writer reopen may truncate that final torn tail to the last fully
  validated record before accepting a new append.
- A checksum mismatch, invalid complete record, sequence gap, bad header,
  unexpected segment or corruption before the final physical tail is hard
  corruption and fails closed.
- Empty trailing segments created before their first record may be removed only
  by the explicit reopen recovery path.

No automatic best-effort salvage is permitted.

### D8 — Offline deterministic replay

`CommandWalReplayer` reads a closed WAL strictly from command sequence `1` and
applies every command to a new genesis `MatchingEngine`.

Required comparison is observable and order-sensitive:

- exact ordered `EngineResult` values;
- TradeId and EventSequence contained by those results;
- an independently computed SHA-256 digest of the canonical result transcript;
- equal results for a fixed public-API probe suffix after replay.

Object identity, memory address, allocation order and internal node identity
are not compared. The digest is a replay-transcript digest, not a snapshot or
complete internal-state hash.

### D9 — Frozen baseline and integration boundary

Phase 5 adds only new `persistence.wal` and `recovery` production packages.
Existing production files under these paths remain unchanged:

```text
src/main/java/com/ultralatency/matching/domain/**
src/main/java/com/ultralatency/matching/orderbook/**
src/main/java/com/ultralatency/matching/engine/**
src/main/java/com/ultralatency/matching/pipeline/**
```

The WAL is not inserted before or after the Phase 4 pipeline in this Phase.
No live command is described as durably acknowledged by the running matching
system. A later integration ADR must define append, publish, process,
acknowledgement and failure ordering before changing this boundary.

### D10 — Dependencies and deferred work

The implementation uses Java 21 APIs (`FileChannel`, `ByteBuffer`, `CRC32C`,
`MessageDigest`) and adds no runtime dependency.

Explicitly deferred:

- Network, Netty, decoder and external binary protocol;
- live pipeline/WAL integration and durable client acknowledgement;
- Snapshot format, state restore and online Recovery orchestration;
- WAL replication, high availability and distributed consensus;
- multi-symbol routing and per-symbol partitioning;
- compression, encryption, direct-I/O, memory mapping and off-heap buffers;
- output/event WAL, database/MQ publication and product Release;
- production performance tuning.

## Target Architecture

```text
Offline / Verification Path

EngineCommand stream
    -> WalCommandCodec
    -> CommandWalWriter
    -> versioned segmented files
    -> strict CommandWalReader
    -> CommandWalReplayer
    -> genesis MatchingEngine
    -> ordered EngineResult transcript + replay digest

Live Phase 4 Path (unchanged)

Caller
    -> MatchingEnginePipeline
    -> MatchingEngine
    -> EngineResultHandler
```

## Alternatives Considered

| Option | Advantages | Risks / Costs | Result |
| --- | --- | --- | --- |
| Network/Protocol before WAL | visible end-to-end ingress sooner | may bind persistence authority to transport retries/framing | Rejected for Phase 5 |
| Persist EngineResult/Trade events | replay avoids matching computation | duplicates derived truth and complicates audit consistency | Rejected |
| Java serialization / JSON | fast to prototype | unstable or verbose format, weak explicit compatibility | Rejected |
| One unsegmented file | minimal writer | unbounded recovery/rotation boundary and harder future retention | Rejected |
| Memory-mapped WAL | potential throughput | cleanup, crash and portability complexity before evidence | Deferred |
| WAL directly inside MatchingEngine | obvious write-ahead point | blocking I/O contaminates deterministic core | Rejected |
| WAL directly inside Phase 4 consumer | live integration | changes frozen pipeline failure/acknowledgement semantics | Deferred to a later ADR |
| Strict offline component baseline | isolates format and replay correctness | no live durability claim yet | Selected |

## Consequences

Positive:

- persistence has an explicit, reviewable and portable format;
- command authority remains consistent with ADR-0011;
- corruption, torn-tail and sequence failures are observable;
- replay can be verified without changing the matching core;
- Network remains an adapter rather than a recovery authority;
- no new runtime dependency or hidden thread is introduced.

Trade-offs and limitations:

- Phase 5 does not make the running pipeline durable;
- replay starts from genesis and scales with the full retained log;
- there is no complete internal state hash or snapshot restore;
- `force(true)` latency and guarantees are environment-dependent;
- semantically invalid but well-formed commands remain detectable replay
  failures rather than records that are silently omitted;
- format version 1 supports only existing limit-submit and cancel commands.

## Required Verification

| Area | Required evidence |
| --- | --- |
| Codec | exact golden bytes, round-trip, type/side/version/length rejection |
| Writer | contiguous append, rotation, exclusive ownership, reopen and close |
| Integrity | CRC mismatch, header corruption, segment gap and sequence gap fail closed |
| Torn tail | strict detection and explicit final-tail truncation only |
| Replay | identical ordered results, transcript digest and future probe behavior |
| Failure | no partial logical append after reported failure; poison command fails replay |
| Boundary | zero diff in frozen Domain/OrderBook/Engine/Pipeline production paths |
| Build | focused tests, repeated recovery matrix, full `mvn verify`, Checkstyle and exact-SHA CI |
| Evidence | component append/replay benchmark with durability modes separated and limitations recorded |

## Human Decision Gate

| ID | Proposed decision | Current state |
| --- | --- | --- |
| D1 | WAL/replay foundation precedes Network/Protocol | Pending |
| D2 | ordered commands are authoritative; results remain derived | Pending |
| D3 | versioned big-endian segmented binary format v1 | Pending |
| D4 | strict length, CRC32C, format and contiguous-sequence validation | Pending |
| D5 | synchronous single-writer ownership with bounded segments | Pending |
| D6 | `SYNC_EACH_APPEND` default; `BUFFERED` evidence-only | Pending |
| D7 | explicit torn-final-tail truncation; all other corruption fails closed | Pending |
| D8 | strict genesis offline replay with ordered transcript/digest/probes | Pending |
| D9 | frozen core/pipeline; no live durability integration | Pending |
| D10 | JDK-only implementation; Snapshot/Recovery/Network/optimization deferred | Pending |

## Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-21 | Human Developer | `Proposal Authorized` | Phase 5 Discovery, ADR draft and Complete Blueprint Proposal may be created. Implementation remains unauthorized pending Human Blueprint Approval. |
