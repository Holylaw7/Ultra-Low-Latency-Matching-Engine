# Task Plan — TASK-20260820-007

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260820-007` |
| Title | Phase 3 MatchingEngine ADR / Decision Proposal |
| Status | `In Progress — ADR / Decision only` |
| Owner | Human Developer |
| Implementer | Codex |
| Created | `2026-08-20` |
| Updated | `2026-08-20` |
| Related Phase | Phase 3 — MatchingEngine |
| Related ADR | `docs/adr/ADR-0011-matching-engine-orchestration-model.md` (`Proposed`) |
| Current Stage | `ADR / Decision Proposal completed — Pending Human Approval` |
| Next Approval Gate | `Human Architecture Review / ADR Decision` |
| Branch | `docs/phase3-matching-engine-adr` |
| Baseline HEAD | `f4a21c5` on `master` |
| Engineering Baseline | `v0.1.0-engineering-baseline` at `cbfa957` |
| Remote | `origin` |
| CI | Master run `32374543039` PASS before branch creation |

## 2. Background

Phase 2 is completed and frozen as an engineering baseline. `OrderBook`
performs deterministic structural limit matching and returns ordered immutable
`MatchFragment` values, while `Trade`, `Execution`, identifiers, event
sequencing and publication remain outside its boundary.

Phase 3 must define the orchestration architecture before any implementation.
The Human Developer authorized only an ADR / Decision Proposal and explicitly
kept Phase 3 implementation and production optimization unauthorized.

## 3. Goal

Produce a reviewable ADR proposal that freezes or explicitly defers:

- execution and ownership model;
- command, trade and event sequence ownership;
- mapping from `MatchFragment` to Trade/Execution output;
- WAL/replay boundary;
- deterministic ordering, failure semantics and Phase 3 scope.

## 4. Non-Goals

- No `MatchingEngine` implementation, tests or benchmark code.
- No Disruptor, Actor runtime, network, protocol, WAL or recovery code.
- No market-order policy decision unless separately approved.
- No production optimization or OrderBook structure change.
- No Release or change to the Phase 2 engineering baseline tag.

## 5. Requirements and Acceptance Criteria

### Requirements

- [x] Compare direct single-owner orchestration, Disruptor and Actor options.
- [x] Define input command, trade identifier and output event sequence owners.
- [x] Define the deterministic Trade/Execution mapping and ordering boundary.
- [x] Decide or explicitly defer command-log versus derived-event WAL roles.
- [x] Preserve ADR-0005, ADR-0007, ADR-0008 and ADR-0010 constraints or
  identify any required supersession.
- [x] State implementation scope that remains unauthorized pending approval.

### Acceptance Criteria

- [x] ADR-0011 exists with status `Proposed`.
- [x] Options, recommendation, trade-offs, invariants and verification plan are
  understandable without reading source code.
- [x] Open Human decisions are explicit and individually approvable.
- [x] Task, architecture document, Stage Report and `AGENT_CONTEXT` agree.
- [x] No production/test/build/benchmark file changes are present.
- [ ] Documentation diff and remote CI pass.

## 6. Current Implementation and Scope

### Current Implementation

- `Order.sequence()` stores the upstream logical input sequence.
- `OrderBook.matchLimit(Order)` mutates book state and returns ordered
  `List<MatchFragment>`.
- `Trade` requires caller-supplied `TradeId` and `Sequence`.
- `Execution` represents one order side of a trade and has no event sequence.
- No `MatchingEngine`, command envelope, engine result or publication event
  type exists.
- ADR-0001 accepts one matching-thread owner per symbol OrderBook.

### In Scope

- Architecture discovery and ADR-0011 proposal.
- Phase 3 task boundaries and future verification plan.
- Documentation/context synchronization and ADR-stage report.

### Out of Scope

- Any executable implementation or performance conclusion.

## 7. Design Proposal

The ADR will recommend a minimal synchronous single-owner orchestration
baseline with no embedded queue. A command envelope supplies a contiguous
input sequence; MatchingEngine verifies ordering, delegates structural
matching to OrderBook, allocates deterministic trade/output identities, and
returns immutable ordered results. Disruptor/Actor scheduling remains a later
pipeline decision.

For recovery, the proposal recommends a command/WAL boundary outside the pure
MatchingEngine: accepted commands become the canonical replay input before
state mutation, while Trade/Execution outputs are deterministic derived
evidence. Flush, acknowledgement and crash-consistency policy remain a future
WAL ADR.

### Alternatives Considered

| Area | Options | Proposed Direction |
| --- | --- | --- |
| Execution | Direct single owner / Disruptor / Actor | Direct synchronous owner baseline |
| Sequence model | One shared sequence / dual domains / upstream-allocation | Separate input and output sequence domains |
| Output | Return fragments / construct domain values / publish callbacks | Immutable ordered engine result; no callbacks |
| WAL source | Commands / derived events / both authoritative | Commands authoritative; outputs derived |

### ADR Linkage

| Field | Value |
| --- | --- |
| ADR | `docs/adr/ADR-0011-matching-engine-orchestration-model.md` |
| Status | `Proposed` |
| Decision Summary | Synchronous deterministic orchestration proposal; Human decision pending |
| Scope Boundary | Documentation and decision only; implementation prohibited |

### Architecture Impact

- [ ] No architecture change
- [x] ADR required
- [x] Human architecture decision required

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `docs/adr/ADR-0011-matching-engine-orchestration-model.md` | Add proposed ADR | Architecture decision input |
| `docs/architecture/matching-engine.md` | Link proposal and current gate | Durable architecture index |
| `tasks/reports/PHASE-3-matching-engine-adr-decision.md` | Add Stage Report | Human-readable review evidence |
| `.codex/AGENT_CONTEXT.md` | Record current proposal and gate | Current-state index |
| This task | Track scope, decisions and approvals | Execution authority |

## 9. Test Plan

No tests run in this documentation-only ADR stage. The ADR must define future
verification for:

- monotonic/contiguous command sequencing;
- deterministic TradeId and output sequence allocation;
- fragment-to-Trade/Execution ordering;
- no-output, single-fill and multi-fill commands;
- cancellation and invalid command behavior;
- equal-input replay equality and state hash;
- overflow and failure-before-mutation guarantees;
- single-owner enforcement and absence of infrastructure I/O.

## 10. Benchmark and Profile Plan

- ADR-stage Benchmark/Profile: `Not applicable`.
- Future Phase 3 baseline: pure synchronous orchestration benchmark only after
  correctness implementation and separate authorization.
- Disruptor/Actor comparisons: deferred to an approved pipeline/performance
  task; not valid Phase 3 assumptions.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Mixing orchestration with queue technology | Premature complexity | Keep synchronous engine boundary independent of ingress transport |
| Ambiguous sequence namespaces | Replay/WAL redesign | Explicitly assign input, trade and output sequence ownership |
| Output callbacks during mutation | Partial publication on failure | Return immutable result only after deterministic state transition |
| Premature WAL details | Phase 3 scope explosion | Freeze logical boundary; defer format/fsync/crash policy |
| Market semantics hidden in orchestration | Incorrect trading behavior | Keep market orders out until separately decided |

## 12. Rollback Plan

If rejected, revise or withdraw the Proposed ADR and task documents. No code,
runtime state, protocol or persistent data rollback is required.

## 13. Verification Commands

```text
git status --short --branch
git diff --check
git diff --cached --check
rg --files
git push -u origin docs/phase3-matching-engine-adr
after push: observe exact-SHA GitHub Actions result
```

## 14. Git Plan

```text
docs(matching-engine): propose phase3 orchestration decision
```

One documentation-only commit for the proposal, Task, Stage Report and current
context. Normal branch push; no merge.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
| 2026-08-20 | Human Developer | Phase 3 entry | `ADR Proposal Authorized` | TASK-007 and ADR-0011 proposal may be prepared. Phase 3 implementation, production optimization and architecture selection remain unauthorized pending explicit ADR approval. |

## 16. Phase Reports and Approval Gates

| Stage | Report Location | Status | Next Approval Gate | Human Approval |
| --- | --- | --- | --- | --- |
| ADR / Decision Proposal | `tasks/reports/PHASE-3-matching-engine-adr-decision.md` | Completed — Pending Human Approval | Human Architecture Review | Proposal authorized 2026-08-20 |
| Task Approval | Same report | Pending | Human Approval | Pending |
| Implementation | Not created | Not Authorized | Approved ADR + Task required | Not Authorized |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-20 | In Progress | Recovered Phase 2 baseline and inspected current domain/OrderBook boundaries | Read-only source, ADR and repository review |
| 2026-08-20 | Proposal Prepared | Proposed synchronous ownership, distinct sequence domains, deterministic output and command-WAL boundary | ADR-0011 decisions D1-D7; no implementation files changed |

## 18. Completion Checklist

- [x] ADR proposal and explicit Human decisions prepared
- [x] Architecture/task/context synchronized
- [x] Production implementation absent
- [x] Benchmark/Profile not applicable
- [x] Stage report completed
- [ ] Diff reviewed and committed
- [ ] Branch pushed and CI status recorded
- [ ] Human ADR decision recorded before implementation
