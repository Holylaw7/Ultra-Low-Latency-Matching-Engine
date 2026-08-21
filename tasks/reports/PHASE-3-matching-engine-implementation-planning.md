# Phase 3 — MatchingEngine Implementation Planning

## Executive Status

| Item | Status |
| --- | --- |
| Phase | Phase 3 — MatchingEngine |
| Task | `TASK-20260820-008` |
| Stage | Task Plan Approval |
| Result | TASK-008 Approved — Stage 1 Authorized / Not Started |
| Tests | Not run — planning-only documentation |
| Build | Not run locally — planning-only documentation |
| CI | [Run 32378870274](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32378870274) — PASS |
| Commit | `bd7fdf0` |
| Branch | `docs/phase3-matching-engine-task-plan` |
| Parent | ADR branch head `96fe50b` |
| Next Gate | Stage 1 Domain/API Foundation completion review |

## Planning Outcome

TASK-008 translates the approved Phase 3 architecture into a bounded
implementation plan. It defines the exact conceptual API, production/test file
scope, processing order, counter semantics, failure behavior, verification
matrix and staged Human gates.

The proposed core remains:

```text
SubmitLimitCommand / CancelOrderCommand
    -> MatchingEngine.process
    -> unchanged Phase 2 OrderBook
    -> ordered MatchFragments
    -> immutable EngineResult
```

## API Boundary Proposed for Approval

- Sealed `EngineCommand` with typed submit-limit and cancel variants; submit
  carries immutable Sequence/OrderId/Side/Price/Quantity fields.
- `CommandOutcome`: `ACCEPTED`, `CANCELED`, `NOT_FOUND`.
- `EventSequence` as a positive domain value owned only by MatchingEngine.
- `MatchResult` with EventSequence, Trade, maker Execution and taker Execution.
- `EngineResult` with input Sequence, outcome and immutable ordered matches.
- Synchronous `MatchingEngine.process(EngineCommand)`.

Counters and validation remain private MatchingEngine responsibilities. No
standalone factory, generator, validator, callback or publication abstraction
is proposed for the correctness baseline.

## Scope Review

In scope:

- EventSequence/Trade domain migration approved by ADR-0005;
- limit submission and cancellation orchestration;
- contiguous command sequence checks;
- deterministic TradeId/EventSequence allocation;
- ordered Trade/Execution mapping and immutable results;
- failure atomicity, failed-engine behavior and correctness tests.

Explicitly out of scope:

- any OrderBook production-file change;
- market orders;
- Disruptor, Actor, internal thread or multi-symbol routing;
- network/protocol/publication;
- WAL implementation, replay API, snapshot or recovery;
- benchmark, profiling and production optimization.

## Key Semantics Proposed for Task Approval

1. Genesis expects command sequence 1.
2. Only a completely successful command advances the input sequence.
3. Unknown-order cancellation is an accepted deterministic no-op and advances
   sequence with outcome `NOT_FOUND`.
4. Each MatchFragment consumes exactly one TradeId and EventSequence.
5. Zero-match commands consume no output identifiers.
6. Counter capacity is conservatively checked before OrderBook mutation.
7. Any unexpected failure after the apply boundary poisons the engine instance.
8. Maker/taker roles are named fields, not a weak positional list.

## Planned Verification

- Domain value and Trade migration tests.
- Command/result invariant and immutability tests.
- No-cross, single-fill, partial-fill and multi-fill orchestration tests.
- Duplicate/gap/out-of-order sequence and invalid-field rejection tests.
- Cancellation success/no-op tests.
- Counter exhaustion and failed-engine boundary tests.
- Two-engine equal-command-stream replay-equivalence tests covering only the
  observable determinism scope approved by ADR-0005.
- Full Maven verify, Checkstyle and exact-SHA GitHub Actions evidence.

## Risks Requiring Human Review

- MatchingEngine constructs a NEW limit Order from immutable command fields;
  market/terminal Orders and sequence mismatch are unrepresentable at ingress.
- The Trade constructor/accessor migration is intentionally source-breaking
  before any public protocol or persistent format exists.
- Standard Java exceptions are proposed instead of a public rejection/error
  hierarchy.
- Snapshot restore constructors are intentionally omitted, so this baseline
  starts only from genesis counters.
- Conservative counter preflight uses current active-order count and may reject
  only at theoretical long exhaustion; it prevents post-mutation overflow.

## ADR Alignment

| Decision | Plan Alignment |
| --- | --- |
| ADR-0005 R1-R6 | Explicit Sequence/EventSequence types, MatchingEngine ownership and observable replay scope |
| ADR-0011 D1 | Synchronous core; no embedded thread or queue |
| ADR-0011 D2 | Upstream sequence, exact-next engine validation |
| ADR-0011 D3 | Distinct Sequence, TradeId and EventSequence counters |
| ADR-0011 D4 | One Trade plus named maker/taker Executions per fragment |
| ADR-0011 D5 | Immutable returned result; no callbacks/I/O |
| ADR-0011 D6 | No WAL implementation; commands remain future recovery source |
| ADR-0011 D7 | Market, pipeline, persistence and optimization excluded |

No new architecture decision is introduced. Any requested deviation returns
to ADR review before implementation.

## Git Evidence

- Parent branch: `docs/phase3-matching-engine-adr`
- Parent head: `96fe50b`
- Planning branch: `docs/phase3-matching-engine-task-plan`
- Remote: `origin`
- Production/test changes: none
- Planning commit: `bd7fdf0`
- Push: completed; branch tracks `origin/docs/phase3-matching-engine-task-plan`
- CI: run `32378870274` completed successfully

The evidence synchronization commit and its CI result are reported at handoff
to avoid a recursive documentation/CI update loop.

## Approval Outcome and Next Gate

The Human Developer approved the exact command/result boundary, private engine
ownership, processing/failure ordering, file allowlist, tests and
Benchmark/Profile exclusion.

Two constraints are repeated as release conditions:

1. OrderBook is an external frozen dependency. No algorithm, production file,
   API, visibility, convenience method or test hook may be changed.
2. Implementation proceeds through three separate approval gates: Domain/API
   Foundation, MatchingEngine Core, then Determinism Verification.

Only Stage 1 is authorized. Its completion report must show exact files,
tests, build, CI and absence of OrderBook changes before Stage 2 can be
considered. Stage 1 must run on `feature/phase3-matching-engine`, created from
the final approved planning head; that branch has not been created in this
approval-sync stage.

## Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-20 | Human Developer | `Planning Authorized` | Create TASK-008 as Proposed. Production implementation remains unauthorized pending Task Plan approval. |
| 2026-08-20 | Human Developer | `Approved` | TASK-008 implementation plan approved. Scope limited to synchronous MatchingEngine correctness baseline. Phase 2 OrderBook remains a frozen external dependency. Implementation must follow staged approval gates. Performance optimization, WAL, network and recovery remain out of scope. |
