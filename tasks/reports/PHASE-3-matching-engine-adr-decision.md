# Phase 3 — MatchingEngine ADR / Decision Proposal

## Executive Status

| Item | Status |
| --- | --- |
| Phase | Phase 3 — MatchingEngine |
| Task | `TASK-20260820-007` |
| Stage | ADR / Decision Proposal |
| Result | Proposal Completed — Pending Human Architecture Review |
| Production code | Not changed |
| Tests / benchmark | Not run — documentation-only stage |
| Branch | `docs/phase3-matching-engine-adr` |
| Baseline | `f4a21c5` from `master` |
| ADR | ADR-0011 — `Proposed` |
| Proposal commit | `df0dc05` |
| Proposal CI | [Run 32375889447](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32375889447) — PASS |
| Next Gate | Human decisions D1-D7; implementation remains unauthorized |

## Outcome

The proposal preserves the Phase 2 OrderBook boundary and defines a minimal
orchestration model for Human review. No architecture choice is recorded as
accepted and no production implementation has begun.

Recommended direction:

```text
External owner / future WAL adapter
    -> sequenced command
    -> synchronous MatchingEngine
    -> Phase 2 OrderBook
    -> immutable ordered result
```

MatchingEngine remains independent from Disruptor, Actor runtime, network,
publication and persistence implementations.

## Evidence Reviewed

- ADR-0001 single-writer matching model.
- ADR-0005 domain identifiers, Trade, Execution and input Sequence semantics.
- ADR-0007 OrderBook ownership and cancellation boundaries.
- ADR-0008 ordered `MatchFragment` structural boundary.
- ADR-0010 measurement and optimization gate.
- Current `Order`, `Trade`, `Execution`, `MatchFragment` and `OrderBook` APIs.
- Frozen Phase 2 baseline and passing GitHub Actions evidence.

## Proposed Decisions

| ID | Proposal | Rationale |
| --- | --- | --- |
| D1 | Synchronous single-owner core | Keeps correctness independent of scheduling and queue technology |
| D2 | Upstream command allocation; engine verifies exact next sequence | Enables deterministic apply-once behavior |
| D3 | Separate command, TradeId and output-event sequence domains | One command can create multiple matches |
| D4 | One ordered aggregate per MatchFragment | Preserves traversal and maker/taker roles |
| D5 | Immutable return value with no callbacks or I/O | Prevents publication failure from entering mutation semantics |
| D6 | Commands are canonical WAL/replay input | Produces one deterministic recovery authority |
| D7 | Defer market orders, pipeline, WAL implementation and optimization | Prevents Phase 3 scope expansion |

## Important Domain Impact

`Order.sequence()` currently describes upstream input ordering while
`Trade.sequence()` uses the same `Sequence` type. The proposal recommends an
explicit output-event sequence domain and therefore requires Human approval
to refine the Phase 1 Trade shape and amend ADR-0005 during a future
implementation task.

If that change is rejected, ADR-0011 must record the fallback of disjoint
logical namespaces carried by the existing type before implementation starts.

## Scope Boundary

Completed in this stage:

- architecture options and trade-offs documented;
- sequence and identity ownership proposed;
- deterministic fragment translation proposed;
- logical WAL/replay boundary proposed;
- failure, atomicity, verification and deferred scope recorded.

Not authorized:

- `MatchingEngine` or command/result type implementation;
- domain-type changes;
- market-order policy;
- Disruptor, Actor, network, protocol or event publication;
- WAL, snapshot or recovery implementation;
- production optimization or performance claims.

## Verification

This is a documentation-only stage. No production/test/build/benchmark file
is changed, so no Maven or JMH conclusion is claimed. Repository validation
for the proposal consists of documentation diff checks, exact file-scope
review, branch push and GitHub Actions on the committed branch head.

The proposal is committed at `df0dc05`, pushed to
`origin/docs/phase3-matching-engine-adr`, and verified by GitHub Actions run
`32375889447` with conclusion `success`. The final evidence-only commit and its
CI result are reported at handoff to avoid a recursive documentation/CI update
loop.

## Risks Requiring Review

- Explicit event sequence typing modifies the accepted Phase 1 Trade boundary.
- Command persistence before mutation needs a later crash-consistency ADR.
- Immutable result allocation is acceptable only as a correctness baseline,
  not a performance conclusion.
- Fatal post-mutation invariant handling depends on future recovery design.
- Multi-symbol ownership and routing are not solved by this ADR.

## Approval Request

Please record accept/reject/revise for ADR-0011 decisions D1-D7.

Approval of ADR-0011 does not authorize implementation. The next sequence is:

```text
Human ADR decision
    -> exact implementation Task Plan
    -> Human Task approval
    -> implementation
```

## Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-20 | Human Developer | `Proposal Authorized` | Only Phase 3 ADR / Decision work is authorized. Implementation remains gated. |
