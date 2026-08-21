# ADR-0014: Binary Network Protocol and Single-Session Gateway

## Status

Approved — Human Phase 6 Closure recorded; baseline frozen at
`v0.5.0-engineering-baseline`

## Context

The project is frozen at `v0.4.0-engineering-baseline`. It has a deterministic
single-owner `MatchingEngine`, a bounded single-producer Disruptor pipeline,
and a versioned command WAL with strict offline replay. It has no external
transport or wire protocol.

The accepted delivery order places Network/Protocol before Snapshot and online
Recovery. Phase 5 deliberately established command persistence independently
of transport so network frames, connections and retry behavior cannot become
recovery authority.

Phase 4 accepts commands from exactly one producer thread. A naïve Netty server
with many channel event loops would violate that ownership model or require an
unapproved multi-producer sequencing layer. A first network baseline therefore
needs a deliberately narrow session and flow-control model.

## Problem

Phase 6 must define:

1. the network dependency and transport boundary;
2. a stable, bounded binary frame format;
3. ownership of client request identity and command Sequence;
4. how TCP input preserves the Phase 4 single-producer invariant;
5. how pipeline `FULL` maps to a retryable network result without consuming a
   command Sequence;
6. how ordered `EngineResult` values are encoded without one unbounded frame;
7. lifecycle and failure behavior for malformed input, disconnects, engine
   failure and write failure;
8. what a network response does and does not acknowledge;
9. which durability, recovery, security and multi-client capabilities remain
   deferred.

## Options Considered

### Option A — Single active TCP session with one request in flight

Use one Netty NIO worker event loop as the only Phase 4 producer. The gateway
assigns command Sequence after decoding a valid request and permits one request
to be outstanding until its ordered result is written. Client request identity
is a separate per-session correlation domain.

Advantages:

- preserves the existing SPSC pipeline without sequence arbitration;
- makes backpressure and result correlation deterministic;
- keeps connection scheduling out of matching order;
- supports an auditable first TCP system boundary.

Costs:

- no pipelining, reconnect or concurrent clients;
- throughput evidence is intentionally a sequential round-trip baseline;
- disconnect after acceptance can lose the response.

**Proposed decision: select.**

### Option B — Multiple Netty channels publish directly

This violates the Phase 4 single-producer contract and makes channel scheduling
part of command order.

**Proposed decision: reject.**

### Option C — Multi-producer Disruptor

This requires changing pipeline concurrency, global sequence arbitration and
fairness semantics before the network contract is stable.

**Proposed decision: defer.**

### Option D — Dedicated MPSC ingress sequencer

This can support multiple channels while preserving one pipeline producer, but
introduces another queue, ownership domain and backpressure boundary.

**Proposed decision: defer.**

### Option E — JSON/HTTP protocol

Easy to inspect, but adds parsing variability, larger frames and unrelated HTTP
semantics to the low-latency binary baseline.

**Proposed decision: reject.**

## Proposed Decision

### D1 — Phase Scope and Authority

Phase 6 implements a single-session TCP binary protocol and Netty adapter. It
does not connect the live pipeline to the WAL. Network input is an adapter to
the existing in-memory pipeline, not a persistence or recovery authority.

### D2 — Netty Dependency and Runtime

Use Netty `4.2.17.Final`, the stable recommended release observed during
Discovery on `2026-08-21`, controlled by `io.netty:netty-bom`. Add only the
required `netty-transport` and `netty-codec` modules.

The implementation uses Java NIO through `MultiThreadIoEventLoopGroup` and
`NioIoHandler.newFactory()`. It explicitly uses the pooled allocator. Native
epoll/kqueue/io_uring, TLS and platform-specific dependencies are deferred.

Netty types remain inside `network.netty`; no Netty type enters Domain,
OrderBook, Engine, WAL, Recovery or existing Pipeline public contracts.

Official references:

- <https://netty.io/downloads.html>
- <https://netty.io/wiki/netty-4.2-migration-guide.html>
- <https://netty.io/4.2/api/io/netty/handler/codec/LengthFieldBasedFrameDecoder.html>
- <https://netty.io/4.2/api/io/netty/channel/nio/NioIoHandler.html>

### D3 — Protocol v1 Common Header

Every frame is big-endian and begins with this exact 16-byte header:

| Offset | Size | Field | v1 value / rule |
| ---: | ---: | --- | --- |
| 0 | 4 | magic | `0x554C4D45` (`ULME`) |
| 4 | 1 | version | `1` |
| 5 | 1 | message type | enumerated below |
| 6 | 2 | flags | `0`; unknown bits rejected |
| 8 | 4 | total frame length | includes header; unsigned semantic bounded by `int` |
| 12 | 4 | reserved | `0`; non-zero rejected |

Inbound framing uses a fail-fast length-field decoder with length field offset
`8`, length `4`, adjustment `-12` and zero stripped bytes. The maximum v1 frame
length is `104` bytes. Partial TCP reads are accumulated; overlong, undersized
or malformed frames fail closed.

### D4 — Request Types and Exact Layout

Request message types:

| Code | Name | Total length |
| ---: | --- | ---: |
| `0x01` | `SUBMIT_LIMIT` | `56` |
| `0x02` | `CANCEL_ORDER` | `32` |

`SUBMIT_LIMIT` payload:

| Offset | Size | Field |
| ---: | ---: | --- |
| 16 | 8 | positive client request ID |
| 24 | 8 | positive OrderId |
| 32 | 1 | Side: `1=BUY`, `2=SELL` |
| 33 | 7 | reserved, all zero |
| 40 | 8 | positive Price units |
| 48 | 8 | positive Quantity units |

`CANCEL_ORDER` payload:

| Offset | Size | Field |
| ---: | ---: | --- |
| 16 | 8 | positive client request ID |
| 24 | 8 | positive OrderId |

The protocol carries no command Sequence. The gateway is the ADR-0011 upstream
owner and allocates exact-next `Sequence` values. Client request ID is a
per-session transport correlation value and must never be copied into Command
Sequence, ring sequence, EventSequence, TradeId or WAL position.

Request IDs start at `1` and are exact-next within the single session. A request
that receives retryable `BACKPRESSURE_FULL` consumes neither request ID nor
command Sequence and may be retried byte-for-byte.

### D5 — Single-Session Admission and Ordering

The server permits one active client channel for its lifetime and one request
in flight. A second connection receives `SERVER_BUSY` when possible and is
closed. Reconnect and session replacement are unsupported.

The child channel uses manual reads. After one valid frame is decoded, no next
read is requested until the current request receives either:

- a retryable pre-admission error; or
- its complete ordered engine result write completion.

Admission order is:

```text
decode and validate request
    -> verify exact-next client request ID
    -> construct candidate EngineCommand with next gateway Sequence
    -> install the single immutable in-flight correlation before publication
    -> MatchingEnginePipeline.tryPublish
        -> FULL: clear correlation; do not advance either counter;
                 write BACKPRESSURE_FULL
        -> ACCEPTED: advance both counters; mark one request in flight
```

Pre-installing the correlation prevents a fast consumer result from racing
ahead of request association. The result handler only schedules work onto the
same Netty event loop; it never mutates gateway state on the pipeline thread.

No command is sorted, internally retried, dropped or submitted from another
thread. One Netty worker event-loop thread remains the pipeline producer.

### D6 — Result and Error Frames

Response message types:

| Code | Name | Total length |
| ---: | --- | ---: |
| `0x81` | `COMMAND_RESULT` | `40` |
| `0x82` | `MATCH_RESULT` | `104` |
| `0xE0` | `ERROR` | `32` |

`COMMAND_RESULT` payload:

| Offset | Size | Field |
| ---: | ---: | --- |
| 16 | 8 | client request ID |
| 24 | 8 | applied Command Sequence |
| 32 | 1 | outcome: `1=ACCEPTED`, `2=CANCELED`, `3=NOT_FOUND` |
| 33 | 3 | reserved, all zero |
| 36 | 4 | unsigned match count |

Each ordered match is one fixed-size `MATCH_RESULT` frame:

| Offset | Size | Field |
| ---: | ---: | --- |
| 16 | 8 | client request ID |
| 24 | 8 | Command Sequence |
| 32 | 4 | zero-based match index |
| 36 | 4 | total match count |
| 40 | 8 | EventSequence |
| 48 | 8 | TradeId |
| 56 | 8 | execution Price units |
| 64 | 8 | executed Quantity units |
| 72 | 8 | maker OrderId |
| 80 | 8 | maker remaining Quantity units |
| 88 | 8 | taker OrderId |
| 96 | 8 | taker remaining Quantity units |

The server writes one `COMMAND_RESULT` followed by exactly `matchCount`
`MATCH_RESULT` frames in list order, then flushes. This avoids an unbounded
single result frame. Collection order is protocol behavior.

`ERROR` contains client request ID (or `0` when unavailable), a two-byte error
code and six zero reserved bytes. Approved v1 codes distinguish malformed
frame, unsupported version/type, invalid field, unexpected request ID,
retryable pipeline full, server busy and terminal server failure.

| Code | Name | Retry / connection rule |
| ---: | --- | --- |
| `1` | `MALFORMED_FRAME` | fatal; close |
| `2` | `UNSUPPORTED_VERSION` | fatal; close |
| `3` | `UNSUPPORTED_MESSAGE_TYPE` | fatal; close |
| `4` | `INVALID_FIELD` | fatal; close |
| `5` | `UNEXPECTED_REQUEST_ID` | fatal; close |
| `6` | `BACKPRESSURE_FULL` | retryable; same request bytes may be retried |
| `7` | `SERVER_BUSY` | connection rejected and closed |
| `8` | `TERMINAL_SERVER_FAILURE` | best-effort error, then close |

### D7 — Acknowledgement Boundary

A successful result write means the server encoded the applied in-memory
`EngineResult` and Netty completed the local channel write future. It does not
prove client receipt, durable command storage, durable result storage or
recoverability.

There is no separate durable ACK in Phase 6. `BACKPRESSURE_FULL` is retryable
and means the command was not accepted. Connection loss after `ACCEPTED` has an
ambiguous client outcome; retry/idempotency requires a future session design.

### D8 — Lifecycle and Failure Semantics

The gateway lifecycle is `NEW -> RUNNING -> DRAINING -> STOPPED`, with
`FAILED` terminal. It owns the server channel, one worker group, one boss group
and one `MatchingEnginePipeline`.

Protocol violations are reported when safe and close the client channel before
admission. An engine, result handler, pipeline infrastructure or outbound write
failure is terminal: stop new reads, preserve the first cause, close channels
and fail-stop. Accepted-but-unacknowledged commands remain a documented
limitation.

To make pipeline terminal state observable without polling, Phase 6 may add a
project-owned `PipelineFailureHandler` and an additive
`MatchingEnginePipeline` constructor. Existing constructors and Phase 4
behavior must remain compatible. The callback fires at most once, performs no
blocking work and is used only to schedule network shutdown on the Netty event
loop. No Engine, OrderBook, WAL or Recovery API changes are permitted.

### D9 — Security and Resource Boundary

The baseline validates magic, version, exact lengths, flags, reserved bytes,
type codes, numeric domains and request ordering before publication. It uses
bounded frames, one active channel, one in-flight request, manual reads and
explicit write-buffer watermarks.

TLS, authentication, authorization, rate limiting, IP allowlists, production
hardening and Internet exposure are not implemented. The default bind address
is loopback; non-loopback binding must be explicit configuration and carries no
security claim.

### D10 — Evidence and Deferred Scope

Phase 6 records codec component evidence and loopback sequential
request-to-result evidence. It must separate decode/encode cost, TCP round-trip,
pipeline processing and durability. No result is a production throughput,
multi-client, durable ACK, Internet security or deployment claim.

Deferred:

- live WAL/pipeline integration and durable acknowledgement;
- multiple clients, pipelining, reconnect, duplicate suppression and session
  recovery;
- TLS/authentication/authorization;
- Snapshot and online Recovery;
- native transports, thread affinity and performance optimization;
- Market orders, multi-symbol routing, output ring, replication and HA;
- Product Release.

## Invariants

1. one active channel and one request in flight;
2. one Netty worker thread is the only pipeline producer;
3. gateway Command Sequence advances only after pipeline `ACCEPTED`;
4. retryable `FULL` advances no request or command identity;
5. request ID, Command Sequence, ring sequence, EventSequence and TradeId are
   separate domains;
6. response order equals `EngineResult.matches()` order;
7. malformed input never reaches the pipeline;
8. pipeline or network output failure is terminal and observable;
9. network frames never become WAL or replay authority;
10. Domain, OrderBook, Engine, WAL and Recovery production paths remain
    unchanged;
11. the existing Phase 4 constructor and in-memory semantics remain compatible;
12. no response is described as durable or client-received acknowledgement.

## Consequences

Positive:

- the project gains an external binary TCP boundary without changing matching
  or persistence authority;
- ordering and backpressure remain explainable and testable;
- protocol bytes are versioned and suitable for golden-vector verification;
- result framing is bounded regardless of match count.

Trade-offs:

- one session and one in-flight request intentionally limit throughput;
- network loss after command acceptance is ambiguous to the client;
- there is no durable live admission or restart continuity;
- a pipeline/result failure terminates the gateway;
- Netty becomes a critical dependency and increases integration surface.

## Verification Plan

| Area | Required evidence |
| --- | --- |
| Golden bytes | exact request/result/error vectors and round-trip values |
| Framing | every single-frame fragmentation; ordered coalesced-frame decoding; Gateway rejection of a second in-flight request; invalid length; overlong frame |
| Validation | magic/version/type/flags/reserved/numeric/request-ID failures |
| Admission | gateway-assigned Sequence, one producer, FULL retry identity |
| Ordering | command results and match frames preserve exact order |
| Lifecycle | bind, single session, manual read, drain, close, terminal failure |
| Failure | malformed frame, second client and disconnect are exercised; gateway FULL identity, outbound write failure and pipeline-failure-to-gateway propagation are implementation-path verified, but dynamic gateway fault injection is not performed |
| Regression | existing 114 tests and public pipeline constructor behavior |
| Boundary | zero diff in Domain/OrderBook/Engine/WAL/Recovery production paths |
| Build | focused tests, `mvn verify`, Checkstyle, diff review and exact-SHA CI |

Benchmark evidence must include fixed protocol vectors, loopback address,
allocator, Netty/JDK/OS/CPU, warmup/measurement/forks, message sizes and
P50/P99/P999 where meaningful. It may not imply durability or production
readiness.

## Human Decision Matrix

| ID | Proposed decision | Current state |
| --- | --- | --- |
| D1 | Network/Protocol baseline; transport is not persistence authority | Approved |
| D2 | Netty 4.2.17.Final BOM/modules, Java NIO and pooled allocator | Approved |
| D3 | exact big-endian 16-byte header and bounded v1 framing | Approved |
| D4 | exact Submit/Cancel layouts; gateway owns Command Sequence | Approved |
| D5 | one active session, one in-flight request and one producer | Approved |
| D6 | bounded ordered Command/Match/Error response frames | Approved |
| D7 | local write completion is not durable/client receipt acknowledgement | Approved |
| D8 | fail-stop lifecycle plus additive pipeline failure observer | Approved |
| D9 | loopback default and strict validation; security features deferred | Approved |
| D10 | component/loopback evidence only; advanced capabilities deferred | Approved |

## Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-21 | Human Developer | `Proposal Authorized` | Phase 6 Discovery, ADR draft, complete Blueprint and TASK-019 through TASK-023 proposals only. No implementation. |
| 2026-08-21 | Human Developer | `Approved` | ADR-0014 D1-D10 and the complete Phase 6 Blueprint approved. TASK-019 through TASK-023 authorized in strict dependency order; all task evidence gates are complete and separate Closure approval remains active. |
| 2026-08-21 | Human Developer | `Closure Approved` | Limited docs-only remediation accepted. Merge `b7cf68e`, master CI `32495076976`, annotated `v0.5.0-engineering-baseline` tag CI `32495218654`; TASK-019 through TASK-023 archived. Dynamic Gateway FULL/write/pipeline fault injection remains an accepted unverified limitation. |

## Next Gate

```text
ADR-0014: Approved
Phase 6 Blueprint: Approved
Implementation: Authorized in dependency order
Current Task: TASK-019..023 Archived
Phase Closure: Approved / Baseline Frozen
Baseline: `v0.5.0-engineering-baseline` at `b7cf68e`
Next Gate: Phase 7 Blueprint only; implementation not authorized
```
