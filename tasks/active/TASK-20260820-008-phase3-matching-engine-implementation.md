# Task Plan — TASK-20260820-008

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260820-008` |
| Title | Implement Phase 3 MatchingEngine Orchestration Baseline |
| Status | `Proposed` |
| Owner | Human Developer |
| Implementer | Codex |
| Created | `2026-08-20` |
| Updated | `2026-08-20` |
| Related Phase | Phase 3 — MatchingEngine |
| Related ADR | ADR-0005 sequence revision and ADR-0011 (`Approved`) |
| Current Stage | `Task Planning completed — Pending Human Approval` |
| Next Approval Gate | `Human Task Plan Approval` |
| Branch | `docs/phase3-matching-engine-task-plan` |
| Parent Branch / HEAD | `docs/phase3-matching-engine-adr` at `96fe50b` |
| Engineering Baseline | `v0.1.0-engineering-baseline` at `cbfa957` |
| Remote | `origin` |
| CI | Planning commit `bd7fdf0`: [run 32378870274](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32378870274) PASS |

## 2. Background

Phase 2 is frozen as the Basic OrderBook engineering baseline. ADR-0011 is
finally approved and freezes a synchronous, single-owner MatchingEngine that:

- verifies upstream command sequence continuity;
- delegates structural limit matching to the existing OrderBook;
- converts ordered MatchFragments into deterministic Trade/Execution results;
- owns monotonic TradeId and EventSequence counters;
- returns immutable results without callback, publication or I/O;
- treats a future command WAL as the canonical replay authority.

ADR-0005 revision R1-R6 reserves `Sequence` for input commands and introduces
an explicit MatchingEngine-owned `EventSequence` for output match aggregates.
This Task converts those accepted decisions into a reviewable implementation
plan. No implementation is authorized while this Task remains `Proposed`.

## 3. Goal

Deliver the Phase 3 synchronous orchestration correctness baseline:

```text
EngineCommand
    -> MatchingEngine sequence validation
    -> Phase 2 OrderBook operation
    -> ordered MatchFragment translation
    -> immutable EngineResult
```

The completed implementation must support sequenced limit submission and
cancellation, deterministic Trade/Execution generation and replay-equivalent
observable results without embedding infrastructure concerns.

## 4. Non-Goals

- No market-order execution, IOC, FOK or slippage policy.
- No network, Netty, decoder or external protocol.
- No Disruptor, RingBuffer, Actor, executor or internal matching thread.
- No WAL storage, record encoding, durability acknowledgement or replay API.
- No snapshot, recovery or state-restore API.
- No event publisher, callback, logging, metrics or clock dependency.
- No multi-symbol router or concurrent ownership model.
- No OrderBook algorithm, data-structure or public API modification.
- No performance optimization, benchmark claim or Phase 2 baseline/tag change.
- No permanent historical OrderId registry beyond the existing active index.

## 5. Requirements and Acceptance Criteria

### Requirements

- [ ] Introduce positive `long`-backed `EventSequence` as approved by ADR-0005.
- [ ] Replace `Trade.sequence` with `Trade.eventSequence` and update domain
  tests/documentation explicitly.
- [ ] Introduce immutable submit-limit and cancel command values carrying one
  input `Sequence`; submit carries immutable order fields rather than a
  mutable `Order` aggregate.
- [ ] Reject null, malformed, duplicate, gap and out-of-order commands before
  OrderBook mutation or counter advancement.
- [ ] Construct the NEW limit `Order` inside MatchingEngine with the command
  sequence, making `Order.sequence()` equality true by construction.
- [ ] Keep command-sequence allocation upstream; MatchingEngine stores only
  the last successfully applied value.
- [ ] Allocate TradeId and EventSequence only inside MatchingEngine, once per
  MatchFragment and in fragment traversal order.
- [ ] Convert each fragment into one Trade, one maker Execution and one taker
  Execution with deterministic named roles.
- [ ] Return an immutable EngineResult with command outcome and ordered match
  results; never publish or invoke callbacks.
- [ ] Advance the command sequence for a successful unknown-order cancellation
  no-op, while returning an explicit `NOT_FOUND` outcome.
- [ ] Fail counter-capacity checks before any OrderBook mutation.
- [ ] Mark an engine instance failed after an unexpected apply/translation
  failure that may follow mutation; reject all later commands.
- [ ] Leave all Phase 2 OrderBook production files unchanged.

### Acceptance Criteria

- [ ] First accepted command has sequence 1; each later accepted command is
  exactly the previous accepted sequence plus one.
- [ ] Duplicate, skipped and out-of-order sequences leave book, engine
  sequence, TradeId and EventSequence state unchanged.
- [ ] A non-crossing limit order returns `ACCEPTED`, no match results, and rests
  through the existing OrderBook behavior.
- [ ] A single match returns exactly one immutable match result with the maker
  price, one TradeId, one EventSequence, maker Execution first and taker
  Execution second.
- [ ] A multi-level/multi-maker match allocates gap-free increasing TradeIds
  and EventSequences in exact MatchFragment order.
- [ ] Successful cancellation returns `CANCELED`; absent cancellation returns
  `NOT_FOUND`; both consume a valid command sequence and emit no match result.
- [ ] Invalid fields and active-duplicate submit commands fail before the
  engine sequence advances; market and terminal orders are unrepresentable at
  the command boundary.
- [ ] Equal command streams from empty engines produce equal EngineResults and
  equal observable OrderBook state, TradeIds and EventSequences.
- [ ] EngineResult and match collections cannot be mutated by callers.
- [ ] MatchingEngine has no I/O, queue, thread, callback, clock, randomness,
  logging, networking or persistence dependency.
- [ ] `mvn verify` passes with existing and new tests and zero Checkstyle
  violations on Java 21.
- [ ] Remote CI passes for every implementation-stage commit submitted for
  approval.

## 6. Current Implementation and Scope

### Current Implementation

- `Order.sequence()` stores a positive upstream logical input sequence.
- `Trade` currently carries `TradeId` plus `Sequence`; it has not yet received
  the approved EventSequence type migration.
- `Execution` contains TradeId, OrderId, price, quantity and remaining units.
- `OrderBook.matchLimit(Order)` mutates the book and returns immutable ordered
  `List<MatchFragment>`.
- `OrderBook.cancel(OrderId)` returns true for cancellation and false when the
  active order is absent.
- `OrderBook.activeOrderCount()` provides a conservative upper bound for the
  fragments one incoming order can create.
- No engine command, engine result, match aggregate or MatchingEngine exists.

### In Scope

- Approved EventSequence and Trade domain migration.
- Immutable engine command/result boundary.
- Submit-limit and cancel command processing.
- Contiguous input sequence validation.
- Deterministic TradeId/EventSequence allocation.
- MatchFragment-to-domain-result mapping.
- Failure-before-mutation and failed-engine behavior.
- Unit, integration and replay-determinism tests.
- Documentation, stage reports, Git/CI evidence and task closure.

### Out of Scope

All Non-Goals in section 4, plus any unapproved change to ADR-0005,
ADR-0011 or Phase 2 matching semantics.

## 7. Design Proposal

### Proposed Package and API

Use `com.ultralatency.matching.engine` for orchestration types and retain
domain value types in `com.ultralatency.matching.domain`.

Conceptual public API:

```text
sealed EngineCommand
    sequence() -> Sequence

SubmitLimitCommand(sequence, orderId, side, price, quantity)
CancelOrderCommand(sequence, orderId)

enum CommandOutcome
    ACCEPTED
    CANCELED
    NOT_FOUND

MatchResult(
    EventSequence eventSequence,
    Trade trade,
    Execution makerExecution,
    Execution takerExecution)

EngineResult(
    Sequence commandSequence,
    CommandOutcome outcome,
    List<MatchResult> matches)

MatchingEngine.process(EngineCommand) -> EngineResult
```

The exact Java types may use records and a sealed interface. SubmitLimitCommand
contains only immutable value types and `Side`; MatchingEngine constructs the
NEW `Order.limit(...)` internally using the same sequence. `MatchResult` uses
named maker/taker fields instead of an Execution list so role order is part of
the type contract. Its EventSequence must equal `trade.eventSequence()`.

### Minimal Internal State

MatchingEngine directly owns:

```text
OrderBook orderBook
long lastAppliedCommandSequence       // genesis value 0
long nextTradeId                       // genesis value 1
long nextEventSequence                 // genesis value 1
boolean failed
```

Counters remain private engine state. No standalone public factory, sequence
generator or validator abstraction is planned. This keeps ownership literal
and avoids adding extension points that ADR-0011 does not require.

The public correctness-baseline constructor creates an empty OrderBook and
genesis counters. Snapshot/restoration constructors are deferred to the
recovery ADR.

### Command Processing Order

For every command:

1. reject when the engine is failed;
2. validate command fields and exact next input sequence;
3. for submit, validate immutable order fields and construct a NEW limit Order
   whose `Order.sequence()` is the command sequence;
4. conservatively preflight TradeId and EventSequence capacity using
   `OrderBook.activeOrderCount()` as the maximum possible fragment count;
5. invoke `OrderBook.matchLimit` or `OrderBook.cancel` exactly once;
6. translate fragments in list order and advance output counters once per
   fragment;
7. construct the complete immutable result;
8. advance `lastAppliedCommandSequence` only after successful result creation;
9. return the result without external calls.

An accepted absent cancellation is a deterministic no-op with `NOT_FOUND` and
advances the input sequence. A rejected command never advances any counter.

If an unexpected exception occurs after the apply boundary begins, mark the
engine failed before rethrowing. A failed engine rejects subsequent commands
with `IllegalStateException`; it must not continue from uncertain state.

### Counter Semantics

- The first generated TradeId and EventSequence are both 1, but equality
  between the domains is never a contract.
- Each MatchFragment consumes one value from both output counters.
- A command with zero fragments consumes neither output counter.
- Preflight uses checked arithmetic and fails with `ArithmeticException`
  before mutation if the conservative fragment bound cannot fit.
- No public setter/reset/restore operation is included.

### Alternatives Considered

| Option | Advantages | Risks or Costs | Result |
| --- | --- | --- | --- |
| Separate Factory/Generator/Validator classes | Individually mockable | Dilutes MatchingEngine ownership and adds abstractions without another implementation | Rejected for baseline |
| One generic command record with nullable fields | Fewer files | Invalid states and runtime branching | Rejected |
| One Execution list in MatchResult | Generic collection | Maker/taker role becomes positional only | Rejected |
| Callbacks/event sink from process | Avoid returned aggregate | Couples mutation to publication failure | Rejected by ADR-0011 |
| Reuse `Sequence` for output | Minimal domain change | Confuses input and output ordering | Rejected by ADR-0005 revision |
| Modify OrderBook to create Trades | Less engine mapping code | Breaks frozen Phase 2 responsibility | Rejected |

### Decision

Implement the minimal synchronous API above. Standard Java validation
exceptions are sufficient for this baseline:

- `NullPointerException` for required null values;
- `IllegalArgumentException` for malformed commands and sequence violations;
- `ArithmeticException` for counter exhaustion;
- `IllegalStateException` for a failed engine instance or invalid lifecycle.

No public exception hierarchy is introduced. Rejected outcomes are reserved
for valid, sequenced business no-ops such as missing cancellation; malformed
commands throw and do not consume sequence.

### ADR Linkage

| Field | Value |
| --- | --- |
| ADR | `docs/adr/ADR-0005-domain-model-and-correctness-baseline.md`; `docs/adr/ADR-0011-matching-engine-orchestration-model.md` |
| Status | `Approved` |
| Decision Summary | Synchronous single-owner command orchestration, distinct Sequence/TradeId/EventSequence domains, immutable result and command-log replay authority |
| Scope Boundary | Limit submit/cancel correctness core only; infrastructure, market orders and optimization excluded |

### Architecture Impact

- [ ] No architecture change
- [x] ADR required and already approved
- [ ] New Human architecture decision required
- [x] Human Task Plan approval required before implementation

Any implementation need that changes D1-D7, EventSequence semantics,
OrderBook responsibilities, command/WAL authority or deferred scope must stop
and return to ADR review.

## 8. Planned File Changes

### Production Files

| File or Directory | Planned Change | Reason |
| --- | --- | --- |
| `src/main/java/com/ultralatency/matching/domain/EventSequence.java` | Add positive comparable value type | Approved output sequence domain |
| `src/main/java/com/ultralatency/matching/domain/Trade.java` | Replace `Sequence sequence` with `EventSequence eventSequence` | Apply ADR-0005 R3 explicitly |
| `src/main/java/com/ultralatency/matching/engine/EngineCommand.java` | Add sealed command contract | Typed synchronous input boundary |
| `.../engine/SubmitLimitCommand.java` | Add immutable submit command | Sequence plus OrderId/Side/Price/Quantity snapshot |
| `.../engine/CancelOrderCommand.java` | Add immutable cancel command | Sequence plus OrderId |
| `.../engine/CommandOutcome.java` | Add result outcome enum | Accepted/canceled/not-found behavior |
| `.../engine/MatchResult.java` | Add immutable Trade plus maker/taker aggregate | Deterministic fragment mapping |
| `.../engine/EngineResult.java` | Add immutable per-command result | No callback/publication boundary |
| `.../engine/MatchingEngine.java` | Add sequence validation, OrderBook delegation, counters and mapping | Phase 3 orchestration core |

### Test and Documentation Files

| File or Directory | Planned Change | Reason |
| --- | --- | --- |
| `src/test/java/com/ultralatency/matching/domain/DomainValueObjectTest.java` | Add EventSequence validation/progression tests | Domain correctness |
| `src/test/java/com/ultralatency/matching/domain/TradeExecutionTest.java` | Migrate Trade tests to EventSequence | Domain semantic revision |
| `src/test/java/com/ultralatency/matching/engine/EngineCommandResultTest.java` | Add command/result invariant and immutability tests | API boundary verification |
| `src/test/java/com/ultralatency/matching/engine/MatchingEngineTest.java` | Add orchestration, sequence, mapping and failure tests | Core correctness |
| `src/test/java/com/ultralatency/matching/engine/MatchingEngineDeterminismTest.java` | Add equal-stream replay-equivalence tests | ADR determinism evidence |
| `docs/architecture/matching-engine.md` | Synchronize implemented API and verified boundary | Architecture documentation |
| `README.md` | Update only verified Phase 3 capability status | Public project boundary |
| `.codex/AGENT_CONTEXT.md` | Update current stage and evidence | Session recovery |
| This Task and Phase 3 reports | Record approvals, commits, tests and CI | Governance evidence |

Explicitly prohibited production changes:

```text
src/main/java/com/ultralatency/matching/orderbook/**
benchmark/**
pom.xml / core/pom.xml
network, pipeline, WAL, snapshot or recovery packages
```

If an approved implementation cannot proceed without one of these changes,
stop and request a Task/ADR scope revision.

## 9. Test Plan

### Unit Tests

- [ ] EventSequence rejects non-positive values, compares by value and detects
  overflow on `next()`.
- [ ] Trade requires EventSequence and preserves deterministic value equality.
- [ ] Command records reject null fields; the submit type cannot represent a
  market order, terminal Order or command/order sequence mismatch.
- [ ] EngineResult defensively owns an immutable match list.
- [ ] MatchResult validates TradeId/EventSequence and maker/taker field
  agreement.

### Integration Tests

- [ ] Genesis sequence 1 and subsequent contiguous submit/cancel commands.
- [ ] Empty/no-cross submit, one match, partial fill and multi-level sweep.
- [ ] Maker price and exact MatchFragment-to-Trade/Execution field mapping.
- [ ] Existing and absent cancellation outcomes.
- [ ] Incoming residual rests through unchanged OrderBook behavior.

### Failure and Boundary Tests

- [ ] Null command and null command fields.
- [ ] First sequence not 1, duplicate, gap and out-of-order sequence.
- [ ] Invalid submit fields and active-duplicate submit rejection.
- [ ] TradeId/EventSequence capacity preflight before mutation.
- [ ] Failed-engine state rejects all later commands.
- [ ] Result lists reject caller mutation.

### Determinism or Replay Tests

- [ ] Run identical valid command streams through two genesis engines.
- [ ] Compare every EngineResult, TradeId, EventSequence, Trade and Execution.
- [ ] Apply identical probe/cancellation commands after replay and compare
  results to verify observable resting/canceled state without exposing mutable
  OrderBook internals.
- [ ] Do not compare object identity, memory address, internal node identity or
  allocation order.

## 10. Benchmark and Profile Plan

Benchmarking and profiling are not part of TASK-008. ADR-0011 permits a later
separately approved synchronous orchestration baseline after correctness.

- Benchmark: `Not applicable`
- Profile: `Not applicable`
- Dataset and distribution: `Not applicable`
- Metrics: `Not applicable`
- Baseline: Phase 2 component benchmark remains evidence only; no Phase 3
  performance conclusion

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Internal Order construction could drift from command fields | Incorrect resting/matching state | Construct once from all immutable command fields and verify mapping tests |
| Trade signature migration breaks Phase 1 tests/callers | Build failure or semantic drift | Change type, constructor, tests and docs in the same approved sub-stage |
| Sequence advances on rejected command | Replay divergence | Advance only after complete successful result; assert unchanged-state tests |
| Exception after OrderBook mutation | Engine could continue from uncertain state | Mark engine failed and reject later commands |
| Counter overflow after mutation | Partial transition without identifiers | Conservative checked capacity preflight using active order count |
| Result invariants duplicate fragment fields | Inconsistent Trade/Execution data | Centralize mapping in MatchingEngine and validate MatchResult constructor |
| Extra helper abstractions dilute ownership | Harder audit and unnecessary indirection | Keep counters/validation/mapping private to MatchingEngine baseline |
| Tests require internal memory equality | Brittle replay tests | Compare approved observable determinism scope only |
| Scope expands into market/pipeline/WAL | Phase 3 baseline loses reviewability | Prohibited-path audit and stop-on-scope-change rule |

## 12. Rollback Plan

Before merge, revert TASK-008 implementation commits on the dedicated future
implementation branch or abandon that branch. The current planning commit can
be revised without runtime/data rollback.

No persistent format, protocol or deployed state exists. If domain/API
implementation is rejected after work begins, revert the Phase 3 code and
tests together; do not modify or retag `v0.1.0-engineering-baseline`.

## 13. Verification Commands

Planning-stage checks:

```text
git status --short --branch
git diff --check
git diff --name-only <approved-parent>...HEAD
```

Approved implementation-stage checks:

```text
mvn -pl core -am test
mvn verify
git diff --check
git status --short --branch
```

Stage completion:

```text
git push origin <approved-implementation-branch>
observe exact-commit GitHub Actions result
```

## 14. Git Plan

Planning commit:

```text
docs(matching-engine): propose phase3 implementation plan
```

Implementation commits, only after approval and on a dedicated implementation
branch:

```text
feat(domain): separate event sequence semantics
feat(matching-engine): add command and immutable result boundary
feat(matching-engine): implement deterministic orchestration
test(matching-engine): verify ordering and replay determinism
docs(matching-engine): record phase3 implementation evidence
```

Commit boundaries may be combined only when tests remain coherent and review
scope stays clear. No squash, force push, history rewrite, merge, tag or
release is authorized by this plan.

- Remote: `origin`
- Planning push: after documentation validation
- Implementation push: after each approved stage
- CI verification: exact branch-head SHA and GitHub Actions run recorded in
  the Task and Stage Report

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
| 2026-08-20 | Human Developer | TASK-008 creation | `Planning Authorized` | Create a Proposed implementation plan on a dedicated documentation branch. No production code or implementation is authorized before Task approval. |
| Pending | Human Developer | Task Plan Review | `Pending` | Approve, revise or reject exact API, scope, tests and staged implementation. |

## 16. Phase Reports and Approval Gates

| Stage | Report Location | Status | Next Approval Gate | Human Approval |
| --- | --- | --- | --- | --- |
| ADR / Decision | `tasks/reports/PHASE-3-matching-engine-adr-decision.md` | Completed | Task Planning | ADR-0011 approved 2026-08-20 |
| Task Planning | `tasks/reports/PHASE-3-matching-engine-implementation-planning.md` | Completed — Pending Human Approval | Task Plan Review | Planning authorized 2026-08-20 |
| Task Approval | Same planning report | Pending | Domain/API Foundation | Pending |
| Domain/API Foundation | Not created | Not Authorized | Human Stage Approval | Not Authorized |
| MatchingEngine Implementation | Not created | Not Authorized | Human Stage Approval | Not Authorized |
| Correctness / Determinism Verification | Not created | Not Authorized | Human Stage Approval | Not Authorized |
| Benchmark / Profile | Not applicable | Not applicable | Documentation Sync | Not applicable |
| Documentation and Synchronization | Not created | Not Authorized | Completion Review | Not Authorized |
| Completion | Not created | Not Authorized | Human Completion Approval | Not Authorized |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-20 | Proposed | Converted approved ADR-0005/ADR-0011 decisions into exact API, file, test and gate plan | Documentation only; production code unchanged |
| 2026-08-20 | Planning Verified | Pushed dedicated planning branch and observed exact-SHA CI | `bd7fdf0`; GitHub Actions run `32378870274` PASS |

## 18. Completion Checklist

- [ ] Scope and acceptance criteria satisfied
- [ ] Tests added or updated
- [ ] Build passed
- [ ] Static or format checks passed
- [x] Benchmark or profile recorded as not applicable
- [ ] Documentation updated after implementation
- [x] Decision and ADR linkage verified
- [x] ADR existed before the technical decision and task approval
- [x] Every completed stage has a phase report
- [x] Human approval is recorded before each completed next stage
- [x] Task plan and `AGENT_CONTEXT.md` synchronized for planning
- [x] `AGENT_CONTEXT.md` updated for Proposed task gate
- [ ] Implementation diff reviewed
- [ ] Implementation commits created
- [ ] Implementation remote synchronization completed
- [ ] Implementation CI status recorded
- [ ] Post-implementation Git status confirmed
