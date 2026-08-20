# ADR-0006: ADR-First Decision Governance

## Status

Accepted

## Decision Record

- Decision date: `2026-08-19`
- Reviewer: Human Developer
- Approval basis: explicit request to make ADR-first decisions, enforce
  phase-report approval gates, and synchronize the project documentation.

## Context

The project already requires important technical decisions to be recorded in
Architecture Decision Records (ADRs), but the existing wording primarily
describes ADR creation as an implementation prerequisite. That leaves an
ambiguity in the decision workflow: a technical decision could be made first
and documented afterward.

For a deterministic matching engine, this order is insufficient. Architecture,
protocol, event-ordering, persistence, recovery, and performance decisions
must be reviewable before they become implementation commitments. The task
plan, ADR, approval record, and implementation must also describe the same
decision and scope.

Development is also performed in controlled stages. Completing one stage must
produce an explicit phase report and pause for Human approval before the next
stage starts. Without this gate, a task can pass its initial approval and then
continue through multiple unreviewed design or implementation changes.

## Problem

The project needs one explicit governance rule for every technical decision:

```text
Requirement
    -> Identify Decision
    -> Create ADR Draft
    -> Record Context, Options, Proposed Decision, and Scope
    -> Human Review and Decision
    -> Update ADR Status
    -> Approve Task Plan
    -> Complete One Development Stage
    -> Produce Phase Report
    -> Human Approval
    -> Start Next Stage
    -> Verify and Record Evidence
    -> Synchronize Documents and Context
```

Without this ordering:

- decisions may be made without documented alternatives or consequences;
- task approval may refer to an ADR that does not yet exist;
- implementation can drift from the approved decision;
- later sessions cannot reliably reconstruct why a decision was made.

## Options Considered

### Option 1 — Document decisions after implementation

This minimizes up-front documentation, but makes ADRs historical summaries
instead of decision gates and allows undocumented design drift.

**Result:** Rejected.

### Option 2 — Create an ADR only before implementation

This prevents code from starting before documentation exists, but still allows
the technical decision and task approval to happen before the ADR has been
reviewed.

**Result:** Rejected.

### Option 3 — Create an ADR draft before the decision

The ADR is created with `Proposed` status before the decision. It records the
context, problem, alternatives, proposed decision, scope boundary, risks, and
evidence plan. Human review then changes the ADR to an accepted or rejected
state before task approval and implementation.

**Result:** Accepted.

## Decision

Adopt an ADR-first decision workflow for all technical decisions.

### Required Ordering

Before a technical decision is made or approved, the responsible developer or
Codex must create or update the relevant ADR in `docs/adr/` with status
`Proposed`. The ADR is a decision input and review artifact, not a
post-implementation summary.

The ADR must contain, as applicable:

- Context
- Problem
- Options considered
- Proposed decision
- Scope boundary
- Consequences and risks
- Verification or benchmark evidence plan

Human review and the technical decision must occur only after the ADR draft
exists. The reviewer then records one of the following outcomes:

- `Accepted`
- `Accepted with constraints`
- `Rejected`

The ADR status must be updated to match the review outcome before the related
task plan is approved. A rejected ADR cannot authorize implementation.

### Phase Report and Approval Gate

Every task must divide development into explicit stages appropriate to its
scope. At minimum, a task must distinguish planning/decision, implementation,
verification, and documentation/synchronization stages.

After each stage is complete, Codex must:

1. Record the completed work, evidence, scope changes, risks, and next-stage
   proposal in the task plan's phase report section.
2. Set the next gate to `Pending Human Approval`.
3. Stop before starting the next stage.

The next stage may start only after Human approval is recorded in the task
plan. A rejection, new constraint, or material scope change requires updating
the task plan and, when a technical decision is affected, creating or updating
the ADR before requesting approval again.

The phase report is the durable hand-off between stages. It may be recorded
directly in the task plan; a longer report may use a separate file under
`tasks/reports/` linked from the task plan.

### Task and ADR Alignment

Every task plan must link the exact ADR path, identifier, title, and status.
The task plan's decision summary and scope boundary must match the ADR. If the
ADR, task plan, approval constraints, or implementation scope diverge, work
must pause until the documents are synchronized and the decision is reviewed
again.

For a change that genuinely does not require an ADR, the task plan must
explicitly record `ADR: Not required` and explain why the change does not alter
architecture, protocol, data format, event ordering, persistence, recovery,
concurrency, or runtime semantics. This exemption does not remove the
phase-report and approval gates.

### Decision Changes

Any material change to an accepted decision requires an ADR revision before
implementation continues. The task plan must record the reason, impact, new
constraints, and re-verification. The prior decision must remain traceable in
Git history.

## Scope Boundary

This governance decision applies to:

- architecture and module boundaries;
- core data structures and domain semantics;
- concurrency and event ordering;
- network protocols;
- WAL, Snapshot, and Recovery formats or policies;
- critical dependencies;
- performance design choices and optimization conclusions;
- any other technical decision that affects long-term behavior.
- every implementation task stage that produces a hand-off to another stage.

It does not require a separate ADR for purely local implementation details
that do not change observable behavior, interfaces, data formats, or project
architecture. Such exclusions still require an explicit reason in the task
plan when the task's decision section is completed.

This ADR changes project governance documentation only. It does not introduce
OrderBook, MatchingEngine, Network, Pipeline, WAL, Snapshot, Recovery, or
performance implementation.

## Consequences

Positive:

- Decisions become reviewable before implementation commitments.
- Task plans and ADRs provide a consistent, recoverable source of intent.
- Alternative designs, constraints, risks, and evidence requirements are
  recorded before code creates accidental commitments.
- Codex cannot silently convert an implementation preference into an approved
  architecture decision.

Trade-offs:

- Every material decision requires documentation before approval.
- Small tasks may need an explicit `ADR: Not required` justification.
- ADR status and task status must be maintained together.
- Each development stage has an auditable report and explicit approval gate.
- Document and context synchronization is a required completion stage.
- The workflow adds up-front coordination cost in exchange for traceability and
  controlled scope.

## Verification

The governance implementation must be verified by checking that:

- the project rules describe ADR creation before decision and approval;
- the project rules require a phase report and Human approval before each next
  development stage;
- the task template requires ADR-first linkage and status;
- the task template records phase reports, gate status, and approvals;
- the task workspace documentation describes the same lifecycle;
- `AGENT_CONTEXT.md` records the accepted governance decision;
- the governing documents use the same stage names, gate semantics, and
  synchronization requirement;
- no production code or runtime behavior is changed.
