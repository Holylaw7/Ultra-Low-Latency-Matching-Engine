# ADR-0011: MatchingEngine Orchestration Model

## Status

Approved with conditions — D3 sequence semantic revision pending; Phase 3
implementation not authorized

## Decision Record

- Proposal date: `2026-08-20`
- Reviewer: `Human Developer`
- Decision date: `2026-08-20`
- Decision: `Approved with conditions`
- Related decisions:
  - [`ADR-0001-matching-model.md`](ADR-0001-matching-model.md)
  - [`ADR-0005-domain-model-and-correctness-baseline.md`](ADR-0005-domain-model-and-correctness-baseline.md)
  - [`ADR-0007-basic-orderbook-structure-and-boundaries.md`](ADR-0007-basic-orderbook-structure-and-boundaries.md)
  - [`ADR-0008-structural-limit-matching.md`](ADR-0008-structural-limit-matching.md)
  - [`ADR-0010-optimization-decision-after-profiling.md`](ADR-0010-optimization-decision-after-profiling.md)

The Human Developer approved D1, D2 and D4-D7 on `2026-08-20`. D3 is
conditionally approved and blocks implementation until the proposed
ADR-0005 sequence semantic revision is explicitly accepted and synchronized.
This conditional decision authorizes no production implementation.

## Context

Phase 2 is complete and frozen at `v0.1.0-engineering-baseline`.
`OrderBook.matchLimit(Order)` owns price-time traversal and in-memory order
lifecycle mutation, and returns an ordered immutable `List<MatchFragment>`.
It intentionally does not create `Trade` or `Execution` values, allocate
identifiers, publish events, perform I/O, or define replay semantics.

The existing domain model provides:

- `Order.sequence()` as the upstream logical input-event sequence;
- `TradeId` as a positive identifier;
- `Trade.sequence()` using the same `Sequence` value type;
- `Execution` as one order-side view of a completed match;
- no command envelope, engine result, output-event sequence type, or
  `MatchingEngine` implementation.

Phase 3 must freeze orchestration ownership and deterministic output behavior
before introducing code. Queue technology, networking and persistence must
not leak into the core engine boundary.

## Problem

The project needs a deterministic orchestration boundary that answers:

1. who owns and calls the mutable OrderBook;
2. how submit/cancel commands are ordered and rejected;
3. who allocates `TradeId` and output event sequence values;
4. how every `MatchFragment` becomes a Trade and two Executions;
5. what the engine returns without publishing or performing I/O;
6. which record is authoritative for future WAL replay;
7. which responsibilities remain outside the Phase 3 baseline.

Without these decisions, a convenient implementation could silently combine
input sequencing, output sequencing, queue consumption, durability and event
publication. That would make replay and failure behavior ambiguous.

## Options Considered

### Execution option A — synchronous single-owner orchestration

One caller thread invokes a synchronous MatchingEngine. The caller is the
sole owner of one symbol's engine and OrderBook. The engine contains no queue,
thread, executor or callback.

Advantages:

- smallest deterministic boundary;
- directly testable without scheduling noise;
- consistent with ADR-0001's single-writer model;
- transport and queue technology can change without changing match semantics.

Costs:

- ownership is a contract that the caller must enforce;
- ingress backpressure and multi-producer sequencing remain outside the core.

**Decision: approved for the Phase 3 correctness baseline.**

### Execution option B — embedded Disruptor

MatchingEngine owns or directly consumes a RingBuffer.

Advantages include a familiar low-latency pipeline and explicit consumer
sequence. Costs include coupling correctness to lifecycle, wait strategy,
producer configuration and backpressure before a synchronous baseline exists.

**Decision: approved for deferral to a separate pipeline ADR and benchmarked
task.**

### Execution option C — symbol Actor

An actor owns each symbol book and receives asynchronous messages.

This provides isolation, but actor mailbox, scheduler, supervision and message
ordering semantics would become part of the matching contract.

**Decision: rejected for the Phase 3 baseline; reconsider only for a future
multi-symbol architecture.**

### Sequence option A — one shared namespace

Reuse a single numeric sequence for command ordering, Trade emission and all
output events.

This is superficially simple but cannot represent multiple matches from one
command without either duplicate output sequence values or an undocumented
encoding scheme.

**Decision: rejected.**

### Sequence option B — separate meanings using the same value type

Keep `Sequence` everywhere but document separate counters for input and
output values.

This is minimally invasive, but the compiler cannot prevent namespace mixups
and the current `Trade.sequence` meaning stays ambiguous.

**Decision: retained only as a rejected-revision fallback.**

### Sequence option C — explicit input and output domains

Keep `Sequence` as the command/order input sequence and introduce an explicit
output-event sequence value type. Each emitted match result receives one
monotonic output-event sequence. `TradeId` supplies the independent monotonic
trade identity.

**Decision: conditionally approved.** The direction is accepted, but the
explicit ADR-0005 revision must be approved before implementation. The
revision is documented in ADR-0005 as R1-R6 and must not be applied silently.

### WAL option A — commands are authoritative

Persist the accepted, sequenced command before applying it. Rebuild by
replaying commands into the deterministic engine. Trade and Execution output
is derived evidence.

**Decision: approved in principle as the logical recovery boundary.**

### WAL option B — derived events are authoritative

Persist only the resulting trades/executions and reconstruct OrderBook state
from output events.

This requires a much larger event model for resting, cancellation, rejection
and residual state, and bypasses the already deterministic command path.

**Decision: rejected for the baseline.**

### WAL option C — commands and outputs are both authoritative

Persist both and permit either to drive recovery.

This creates two competing sources of truth and additional consistency rules.

**Decision: rejected. Outputs may be recorded or verified, but are not a second
recovery authority.**

## Conditionally Approved Decision

### 1. Core Execution Boundary

MatchingEngine is a synchronous deterministic state machine. One external
owner thread invokes one engine instance for one symbol OrderBook.

Conceptually:

```text
Sequenced EngineCommand
    -> validate sequence and command contract
    -> apply exactly once to OrderBook
    -> translate ordered MatchFragments
    -> return immutable EngineResult
```

The core contains no RingBuffer, Disruptor, Actor, network, WAL writer,
logger, clock, executor, callback or publication dependency. A future ingress
pipeline may call it, but does not become part of it.

The baseline does not require runtime thread-ID checks. Single ownership is a
construction and integration invariant, verified by architecture tests and
the absence of concurrent APIs.

### 2. Command Boundary and Input Sequence Ownership

The Phase 3 command boundary contains only the behaviors already supported by
the Phase 2 book:

```text
EngineCommand
    SubmitLimit(sequence, order)
    CancelOrder(sequence, orderId)
```

Names are conceptual until the implementation Task is approved. For submit,
the envelope sequence and `Order.sequence()` must be equal. Cancel requires
its own command sequence because no new Order value exists.

The upstream ingress/WAL adapter owns input-sequence allocation. MatchingEngine
owns the last-applied sequence and accepts only the exact next positive value.
It must reject a duplicate, gap or out-of-order command before mutation. A
successfully applied deterministic no-op, such as cancellation of an unknown
inactive order, still consumes its accepted command sequence and returns an
explicit result status.

Malformed commands and sequence-invalid commands do not mutate state, do not
advance the engine sequence and do not produce match output.

### 3. Trade Identity and Output Event Sequence Ownership

MatchingEngine owns two independent, strictly monotonic counters:

- `TradeId`: one value per `MatchFragment`; this is the baseline trade
  sequence and stable trade identity;
- output event sequence: one value per emitted match result; this orders
  externally visible match results independently of input commands.

The proposal introduces an explicit output-event sequence domain instead of
reusing input `Sequence` by convention. One command may emit zero, one or
multiple values, so command and output counters must not be assumed equal.

No timestamp participates in identity or ordering. Counter state becomes part
of a future snapshot/recovery boundary. Restore mechanics remain outside this
ADR, but replay must reproduce the same final counters and outputs.

### 4. MatchFragment Translation and Output Order

OrderBook remains unchanged and returns structural fragments. MatchingEngine
maps them in list order. Each fragment becomes one immutable match-result
aggregate containing:

1. one output event sequence;
2. one `Trade` with a newly allocated `TradeId`;
3. one maker `Execution`;
4. one taker `Execution`.

The mapping is deterministic:

- Trade price and quantity equal the fragment price and quantity;
- maker and taker IDs preserve the fragment roles;
- maker Execution precedes taker Execution in the aggregate;
- both Executions use the same TradeId, price and quantity;
- remaining quantities equal the fragment post-match quantities;
- result aggregates preserve fragment traversal order.

The conditionally approved domain refinement replaces `Trade.sequence` with
`Trade.eventSequence` using an explicit `EventSequence` value type. The exact
semantic revision and Human approval items R1-R6 are recorded in ADR-0005.
No implementation may begin while any of those items remains pending.

### 5. Engine Result Boundary

Every accepted command returns one immutable result that contains:

- input command sequence;
- command outcome, including applied, cancelled or deterministic no-op;
- an immutable ordered list of match-result aggregates;
- no mutable OrderBook node or collection reference.

Results are returned only. The engine does not publish them or invoke user
callbacks. Submit-limit with no cross may rest the incoming order and return
an empty match list. Cancellation never creates a Trade.

Rejected precondition/sequence failures use an exception or explicit rejected
result chosen by the implementation Task, but must obey the same
failure-before-mutation invariant.

### 6. Failure and Atomicity Invariants

The engine validates command shape, sequence continuity and submit-sequence
agreement before OrderBook mutation.

Trade/output counter exhaustion must also fail before mutation. The engine may
conservatively reserve capacity using the current active-order count as the
maximum number of fragments one incoming limit order can create. Counter
arithmetic must use checked operations.

After a successful OrderBook mutation, fragment translation must be total and
side-effect-free except for deterministic counter advancement. Domain values
must be constructed from already validated fragment data. No external call
may occur between mutation and immutable result completion.

Unexpected invariant failure after mutation is fatal to that engine instance;
processing must stop rather than continue from an uncertain state. Recovery
then depends on the future WAL/snapshot design.

### 7. Logical WAL and Replay Boundary

The canonical recovery input is the accepted sequenced command stream:

```text
validate record shape and sequence
    -> append command to WAL
    -> satisfy future durability policy
    -> invoke MatchingEngine
    -> return/publish derived EngineResult
```

Any command that advances engine sequence must have an authoritative command
record before application. A command rejected before sequence acceptance is
not part of replay. Derived Trade/Execution results may be stored for audit or
verified during replay, but do not become a second source of truth.

This ADR freezes only the logical boundary. WAL record encoding, checksum,
segmenting, group commit, flush/ack policy, crash windows, snapshot cut and
publication guarantees require a later WAL/recovery ADR. MatchingEngine
performs no durability acknowledgement or I/O.

### 8. Explicitly Deferred Scope

The following remain outside this proposal and any initial Phase 3
implementation authorization:

- market orders and IOC/FOK/slippage semantics;
- Disruptor/RingBuffer and multi-producer ingress;
- Actor scheduling and multi-symbol routing;
- event publication, networking and protocol encoding;
- WAL implementation, snapshot and recovery;
- production performance optimization;
- replacement of the Phase 2 OrderBook data structures.

## Invariants

If accepted, implementation must preserve:

1. one owner mutates one engine/OrderBook;
2. accepted command sequences are positive, contiguous and applied once;
3. input sequence, TradeId and output event sequence are distinct domains;
4. one fragment maps to exactly one Trade and two ordered Executions;
5. result order equals OrderBook fragment order;
6. no publication or I/O occurs within MatchingEngine;
7. any rejected precondition fails before mutation;
8. equal initial state and equal command stream produce equal results, final
   book state, counters and state hash.

## Consequences and Risks

Positive:

- correctness can be tested independently of transport and scheduling;
- queue, WAL and network choices remain replaceable adapters;
- deterministic identity and ordering support replay verification;
- OrderBook retains its approved structural responsibility.

Trade-offs and risks:

- an immutable result graph introduces allocation in the correctness baseline;
- explicit event sequence typing changes the accepted Phase 1 Trade shape;
- the upstream adapter must coordinate sequencing and future durability;
- fatal post-mutation invariant handling requires later recovery design;
- single-symbol ownership does not yet solve multi-symbol scaling.

No low-allocation, throughput, latency, lock-free or production-readiness claim
follows from this proposal.

## Verification Plan for an Approved Implementation

| Area | Required evidence |
| --- | --- |
| Command order | First, next, duplicate, gap and out-of-order sequences |
| Submit agreement | Envelope and `Order.sequence()` equality |
| No output | Resting limit command returns an empty immutable match list |
| Single fill | One TradeId, one output sequence and maker/taker Executions |
| Multi-fill | Monotonic identities and exact fragment traversal order |
| Cancellation | Existing cancellation and unknown-order deterministic no-op |
| Failure atomicity | Invalid commands and counter exhaustion leave state unchanged |
| Determinism | Equal command streams produce equal ordered results and state hash |
| Ownership boundary | No queue, I/O, callback, clock or publication dependency |
| Replay model | Command replay reproduces output counters and final state |

Benchmarking is not an ADR acceptance criterion. After correctness and Human
approval, a separate task may measure only the synchronous orchestration
baseline. Disruptor/Actor comparison requires its own approved decision and
workload.

## Human Decision Gate

The first architecture review produced these decisions:

| ID | Proposed decision | Current state |
| --- | --- | --- |
| D1 | Synchronous single-owner MatchingEngine with no embedded queue/thread | Approved |
| D2 | Upstream-owned contiguous command sequence verified by the engine | Approved |
| D3 | Engine-owned TradeId and explicit output-event sequence domains | Conditional — ADR-0005 R1-R6 pending |
| D4 | One ordered aggregate per fragment: Trade, maker Execution, taker Execution | Approved |
| D5 | Immutable return result; no callbacks, publication or I/O | Approved |
| D6 | Command log is canonical replay input; derived outputs are non-authoritative | Approved in principle; WAL implementation deferred |
| D7 | Market order, pipeline, WAL implementation and optimization remain deferred | Approved |

The next gate is Human approval of ADR-0005 revision R1-R6. After that approval
is recorded, ADR-0011 may become fully approved. Full ADR approval alone still
does not authorize code: a separate implementation Task Plan must define exact
types, failure API, tests, files and verification commands and receive Human
approval.

## Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-20 | Human Developer | `Proposal Authorized` | Phase 3 ADR / Decision proposal may be prepared. Architecture selection and implementation remain unauthorized pending review. |
| 2026-08-20 | Human Developer | `Approved with conditions` | D1, D2 and D4-D7 approved. D3 is approved only after explicit ADR-0005 sequence semantic revision. Phase 3 implementation remains unauthorized. |
