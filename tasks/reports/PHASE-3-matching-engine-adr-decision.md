# Phase 3 — MatchingEngine ADR / Decision Proposal

## Executive Status

| Item | Status |
| --- | --- |
| Phase | Phase 3 — MatchingEngine |
| Task | `TASK-20260820-007` |
| Stage | D3 Semantic Revision Approved / Final ADR Review |
| Result | D3 Condition Satisfied — ADR-0011 Final Approval Pending |
| Production code | Not changed |
| Tests / benchmark | Not run — documentation-only stage |
| Branch | `docs/phase3-matching-engine-adr` |
| Baseline | `f4a21c5` from `master` |
| ADR | ADR-0011 — `Approved with conditions` |
| Proposal commit | `df0dc05` |
| Proposal CI | [Run 32375889447](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32375889447) — PASS |
| Proposal evidence commit | `a226c50` |
| Proposal head CI | [Run 32375989030](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32375989030) — PASS |
| Conditional approval commit | `e98481a` |
| Conditional approval CI | [Run 32376750616](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32376750616) — PASS |
| Next Gate | Human final approval of ADR-0011; implementation remains unauthorized |

## Outcome

The Human Developer approved ADR-0005 revision R1-R6 after adding explicit
EventSequence ownership and replay determinism scope. D1-D7 are now approved
at the decision-item level. ADR-0011 still awaits its separate final approval
record, and no production implementation has begun.

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

## Architecture Review Result

| ID | Decision | Status |
| --- | --- | --- |
| D1 | Synchronous single-owner core | Approved |
| D2 | Upstream command allocation; engine verifies exact next sequence | Approved |
| D3 | Separate command, TradeId and output-event sequence domains | Approved — R1-R6 accepted |
| D4 | One ordered aggregate per MatchFragment | Approved |
| D5 | Immutable return value with no callbacks or I/O | Approved |
| D6 | Commands are canonical WAL/replay input | Approved in principle; implementation deferred |
| D7 | Defer market orders, pipeline, WAL implementation and optimization | Approved |

## Important Domain Impact

The proposed ADR-0005 revision now freezes the requested D3 semantics for
review:

- `Sequence` is reserved for accepted input-command order;
- a new `EventSequence` orders output match-result aggregates;
- `Trade.sequence` becomes `Trade.eventSequence` with the new type;
- one MatchFragment aggregate receives one EventSequence;
- maker/taker Executions use deterministic aggregate order and receive no
  independent sequence in the Phase 3 baseline;
- replay must reproduce TradeId and EventSequence counters.

These items are approved as ADR-0005 R1-R6. EventSequence is owned exclusively
by MatchingEngine. Replay determinism covers observable OrderBook state,
TradeId, EventSequence, Trade price/quantity and maker/taker Execution data; it
does not cover memory addresses, object identity or allocation order.
Production types remain unchanged until implementation is separately approved.

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

The proposal is committed at `df0dc05`, with evidence recorded at `a226c50`.
The conditional approval is recorded at `e98481a`. All were pushed to
`origin/docs/phase3-matching-engine-adr`; GitHub Actions runs `32375889447`,
`32375989030` and `32376750616` completed successfully. The R1-R6 approval
synchronization commit and its CI result are reported at handoff to avoid a
recursive documentation/CI update loop.

## Risks Requiring Review

- Explicit event sequence typing modifies the accepted Phase 1 Trade boundary.
- Command persistence before mutation needs a later crash-consistency ADR.
- Immutable result allocation is acceptable only as a correctness baseline,
  not a performance conclusion.
- Fatal post-mutation invariant handling depends on future recovery design.
- Multi-symbol ownership and routing are not solved by this ADR.

## Approval Request

Please record the final accept/reject/revise decision for ADR-0011. D3 is no
longer blocking at the semantic-revision level.

Approval of ADR-0011 does not authorize implementation. The next sequence is:

```text
ADR-0005 R1-R6 approved
    -> Human ADR-0011 final approval record
    -> exact implementation Task Plan
    -> Human Task approval
    -> implementation
```

## Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-20 | Human Developer | `Proposal Authorized` | Only Phase 3 ADR / Decision work is authorized. Implementation remains gated. |
| 2026-08-20 | Human Developer | `ADR-0011 Approved with conditions` | D1, D2 and D4-D7 approved. D3 requires an explicit ADR-0005 sequence semantic revision before final approval or implementation planning. |
| 2026-08-20 | Human Developer | `ADR-0005 R1-R6 Approved` | EventSequence ownership and replay determinism scope clarified. D3 condition satisfied; ADR-0011 final approval remains pending. |
