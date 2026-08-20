# Phase 3 — MatchingEngine Core Authorization Request

## Executive Status

| Item | Status |
| --- | --- |
| Phase | Phase 3 — MatchingEngine |
| Task | `TASK-20260820-008` |
| Stage | Stage 2 Authorization |
| Result | Proposed — Pending Human Approval |
| Tests | Not run — documentation-only authorization request |
| Build | Not run — production code unchanged |
| CI | Pending authorization-request commit and push |
| Branch | `feature/phase3-matching-engine` |
| Parent | Stage 1 approval head `282e7a8` |
| Next Gate | Human Stage 2 Authorization Review |

## Requested Authorization

Authorize Stage 2 to add one synchronous `MatchingEngine` implementation that
validates upstream command order, constructs limit orders, invokes the frozen
OrderBook, translates ordered MatchFragments and returns immutable results.

```text
Command
  -> Sequence Validation
  -> NEW Order Construction / Cancellation
  -> Frozen OrderBook
  -> MatchFragment Order
  -> Trade + maker/taker Execution
  -> Immutable EngineResult
```

Only `MatchingEngine.java` and its focused `MatchingEngineTest.java` are added.
Existing domain, Stage 1 API and OrderBook production files remain unchanged.

## Implementation Boundaries

### 2.1 Command Processing

- synchronous `process(EngineCommand)` dispatch;
- genesis and exact-next command-sequence validation;
- private last-applied/output-counter/failed state;
- pre-mutation rejection for invalid commands.

### 2.2 OrderBook Integration

- construct NEW limit Order from immutable command values;
- call `matchLimit` or `cancel` exactly once;
- map accepted submit, successful cancel and missing cancel outcomes;
- no OrderBook API, visibility, algorithm or test-hook change.

### 2.3 Result Generation

- conservative counter-capacity preflight before mutation;
- one TradeId and EventSequence per fragment in list order;
- one Trade with named maker/taker Executions per fragment;
- immutable ordered EngineResult and fatal post-apply failure guard.

## Focused Verification if Approved

- first/contiguous, duplicate, gap and out-of-order command sequences;
- non-crossing submit and residual resting behavior;
- single, partial and multi-fragment mapping and ordering;
- maker price, maker/taker identity and remaining-quantity mapping;
- existing and absent cancellation outcomes;
- pre-apply rejection leaves observable state/counters unchanged;
- `mvn verify`, Checkstyle, diff/path audit and branch CI.

Stage 3 retains full equal-stream/replay determinism, exhaustive failure
verification and final regression evidence. No Stage 3 work is authorized by
this request.

## Explicit Non-Scope

- changes to existing Stage 1 API or frozen OrderBook production files;
- market orders, WAL, replay implementation, snapshot or recovery;
- network, protocol, publication, callbacks or I/O;
- queue, Disruptor, threading or multi-symbol orchestration;
- benchmark, profiling, optimization or performance claims;
- release, merge, tag or history rewrite.

## ADR Alignment

The request implements ADR-0011 D1-D5 and the already approved ADR-0005
sequence separation. D6 remains a logical future WAL boundary only, and D7
deferred scope remains excluded. No new architecture decision is introduced.

## Risks and Stop Conditions

- A needed change to OrderBook or existing Stage 1 API stops implementation.
- A need for a public restore/test hook stops implementation and returns to
  Task review.
- Any ambiguity in post-mutation failure or output ordering returns to the
  approved ADR rather than being resolved silently in code.

## Approval Request

```text
Current Stage: Stage 2 Authorization Proposed
Human Approval: Pending
Stage 2 Implementation: Not Authorized
Stage 3 Verification: Not Authorized
```
