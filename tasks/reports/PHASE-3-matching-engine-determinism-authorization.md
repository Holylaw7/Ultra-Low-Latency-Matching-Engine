# Phase 3 — Determinism Verification Authorization Request

## Executive Status

| Item | Status |
| --- | --- |
| Phase | Phase 3 — MatchingEngine |
| Task | `TASK-20260820-008` |
| Stage | Stage 3 — Determinism Verification Authorization |
| Result | Proposed — Pending Human Approval |
| Tests | Not run — documentation-only authorization request |
| Build | Not run — production and test code unchanged |
| CI | [Run 32446230919](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32446230919) — PASS for `62f59aa` |
| Commit | `62f59aa` — `docs:request-stage3-determinism-authorization` |
| Branch | `feature/phase3-matching-engine` |
| Parent | Stage 2 closure head `0b60cf6` |
| Next Gate | Human Stage 3 Authorization Review |

## Requested Authorization

Authorize Stage 3 to add deterministic verification only. No new runtime
capability is requested. Two genesis `MatchingEngine` instances will receive
the same immutable command stream, and the test will compare every returned
result and public-API-observable suffix behavior.

```text
Fixed Command Stream
    -> MatchingEngine A from genesis
    -> MatchingEngine B from genesis
    -> compare ordered EngineResult values
    -> apply identical probe suffix
    -> compare observable final behavior
```

This is deterministic re-execution in a test. It is not a WAL reader, replay
service, recovery subsystem or restore API.

## Determinism Contract

### Required Equality

For equal genesis state and the same ordered command values, both engines must
produce structurally equal ordered results. Equality covers:

- command `Sequence` and `CommandOutcome` for every accepted command;
- match-list size and traversal order;
- `TradeId` and `EventSequence` values;
- every Trade price, quantity, maker ID and taker ID;
- maker and taker Execution order and all Execution fields;
- empty-result and deterministic `NOT_FOUND` outcomes;
- subsequent probe/cancel behavior that observes resting, residual and
  canceled order state through the existing public command boundary.

The baseline has no serialized result encoding, so Stage 3 requires exact
Java value/record equality, not byte-for-byte equality of an unspecified wire
format.

### Equality Explicitly Not Required

- Java object identity or reference equality;
- memory address, object layout or allocation order;
- internal OrderNode, queue or collection identity;
- hash-table or other internal iteration artifacts;
- timing, latency, throughput or GC behavior.

This is behavioral determinism, not memory determinism.

## Authorized Verification Scope

Only this new test file may be added:

```text
src/test/java/com/ultralatency/matching/engine/MatchingEngineDeterminismTest.java
```

The fixed scenarios must cover:

1. a multi-step valid stream with resting orders on both sides;
2. no-cross, single-match, partial-fill and multi-match outcomes;
3. maker-price and price-time traversal order;
4. successful cancellation and unknown-order deterministic no-op;
5. monotonic, gap-free TradeId and EventSequence values across commands;
6. identical result lists from two independent genesis engines;
7. identical public-API probe results after the main stream.

An extended deterministic fixture must apply at least 256 accepted commands
with contiguous input sequences and repeated rest, cross and cancel behavior.
The fixture may use fixed loops/arithmetic, but never randomness.

The test data must be fixed and local. It must not depend on clocks, random
values, thread scheduling, hash iteration order, files, network or external
state.

## Failure Atomicity Verification

Reachable pre-apply failures are verified with a subject/control differential:

```text
Subject engine: valid prefix -> rejected attempt -> valid suffix
Control engine: valid prefix --------------------> valid suffix
                                      compare suffix results
```

Authorized rejected attempts are:

- null command;
- first, duplicate, gap and out-of-order command sequence;
- submit using an already active OrderId.

After each rejected attempt, the same exact-next valid suffix must remain
accepted and must produce the same TradeIds, EventSequences and EngineResults
as the control engine. This proves that reachable pre-apply rejection does not
advance input/output counters or alter observable book state.

Invalid Price, Quantity, OrderId, Sequence and null command fields are rejected
by immutable value/command construction before a valid `EngineCommand` exists.
Existing domain/API tests remain the evidence for that boundary; Stage 3 must
not fabricate malformed objects or bypass constructors.

## Observable State Boundary

`MatchingEngine` intentionally exposes no mutable OrderBook, snapshot, state
hash, counter getter or restore API. Stage 3 therefore verifies final state by
applying deterministic probe commands and comparing their complete results.
It must not add or expose production state solely for testing.

This stage does not claim byte-level state identity or a persisted state hash.
Those require an approved snapshot/recovery representation and remain future
work.

## Unreachable Failure Limitations

Counter exhaustion and unexpected post-mutation failure cannot be reached in
a practical public-API test from genesis without billions of operations or an
artificial failure seam. Stage 3 does not authorize:

- reflection or unsafe mutation of private engine counters/state;
- public, package-private or test-only counter setters;
- dependency injection or mock hooks added only to force OrderBook failure;
- changes to production visibility or lifecycle.

The existing checked capacity preflight and fatal-engine guard remain code
reviewed safeguards, but exhaustive dynamic evidence for those paths is not a
Stage 3 completion claim. A future approved failure-injection design is
required before those paths can be tested dynamically.

## Explicit Non-Scope

- any production-code or existing-test modification;
- any `orderbook/**` modification or OrderBook API extension;
- WAL, WAL parsing, Replay API or replay service;
- snapshot, state hash format, restore or recovery;
- network, protocol, publication, callback, logging or metrics;
- Disruptor, queue, thread, executor, lock or concurrent collection;
- benchmark, profiling, optimization or performance claim;
- market order, IOC/FOK or multi-symbol behavior;
- merge, tag, release or history rewrite.

## ADR Alignment

This request verifies the already approved ADR-0005 R1-R6 and ADR-0011 D1-D7
contracts. It does not change sequence semantics, matching semantics,
concurrency, persistence or recovery architecture; no new ADR is required.
Command re-execution is test methodology only. The canonical future WAL
boundary remains logical and unimplemented.

## Acceptance Evidence if Approved

- focused Stage 3 determinism tests pass;
- `mvn -pl core -am test` passes;
- `mvn verify` passes with zero Checkstyle violations;
- production and `orderbook/**` diffs are empty for Stage 3;
- the test contains no time, randomness, concurrency or external I/O;
- a Stage 3 completion report records exact scenarios, limitations, commit,
  push and exact-SHA CI status;
- implementation stops for Human Stage 3 Completion Review.

## Approval Request

```text
Stage 1 Domain/API Foundation:    Completed / Approved
Stage 2 MatchingEngine Core:      Completed / Approved
Stage 3 Authorization Proposal:   Completed / Pending Human Approval
Stage 3 Verification Execution:   Not Authorized
Phase 3 Closure:                  Not Authorized
```
