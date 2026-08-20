# MASTER_PROMPT — Ultra-Low-Latency Matching Engine Engineering Controller

> Project: Ultra-Low-Latency Matching Engine
> Language: Java 21
> Development Model: Human-led Architecture + Codex-assisted Engineering
> Repository Model: Git + Remote Repository + CI
>
> This file defines the highest-priority project-level operating rules for Codex.
>
> Project state MUST NOT be inferred from this file.
> Current phase, task, ADR, approval gate and repository state are maintained in:
>
> `.codex/AGENT_CONTEXT.md`

---

# 1. Mission

Build a technically credible, deterministic, measurable and reproducible
single-node ultra-low-latency matching engine.

The project is evaluated by:

1. Correctness
2. Determinism
3. Architecture quality
4. Testability
5. Reproducibility
6. Performance evidence
7. Recovery correctness
8. Git history quality
9. Documentation quality
10. Explainability

Code volume is not a success metric.

A feature without verification is incomplete.

A performance claim without evidence is invalid.

An architectural decision without a recorded rationale is incomplete.

---

# 2. Authority Model

Human Developer is the final authority for:

- Requirements
- Architecture
- Trading semantics
- Performance targets
- ADR approval
- Task approval
- Phase approval
- Release approval

Codex is responsible for:

- Repository inspection
- Design assistance
- Implementation
- Testing
- Benchmarking
- Profiling
- Documentation
- Git preparation
- Progress reporting

Codex MUST NOT silently make architecture-level decisions.

When an unapproved architecture decision is required:

STOP
→ document the issue
→ propose alternatives
→ create/update ADR
→ request Human approval

---

# 3. Source of Truth

The project has four authoritative information layers.

## 3.1 Governance

`.codex/MASTER_PROMPT.md`

Defines how Codex works.

## 3.2 Engineering Rules

`.codex/DEVELOPMENT_RULES.md`

Defines implementation, testing, performance, security and Git rules.

## 3.3 Current State

`.codex/AGENT_CONTEXT.md`

Defines:

- current Phase
- current Task
- current ADR
- completed work
- current approval gate
- known risks
- latest benchmark evidence
- latest commit
- next authorized action

## 3.4 Task State

`tasks/`

Defines approved work.

No production change may exist outside an approved Task.

---

# 4. Mandatory Session Bootstrap

At the beginning of EVERY Codex session:

1. Read `.codex/MASTER_PROMPT.md`
2. Read `.codex/DEVELOPMENT_RULES.md`
3. Read `.codex/AGENT_CONTEXT.md`
4. Read `tasks/README.md`
5. Read current `tasks/active/*`
6. Read linked ADRs
7. Inspect repository state

Run:

git status --short --branch
git branch --show-current
git log --oneline --decorate -10
git diff --stat
git diff --cached --stat
git remote -v

Determine:

- current branch
- HEAD
- working tree state
- remote tracking state
- current Phase
- current Task
- Task status
- current Stage
- ADR status
- Human approval status
- next authorized action

DO NOT modify files before completing context recovery.

If repository state conflicts with AGENT_CONTEXT or Task state:

STOP.

Report the inconsistency before implementation.

---

# 5. Software Engineering Lifecycle

Every engineering change MUST follow:

Requirement
↓
Discovery
↓
Scope
↓
ADR / Design Decision
↓
Human Approval
↓
Task Plan
↓
Human Approval
↓
Implementation
↓
Focused Verification
↓
Regression Verification
↓
Review
↓
Documentation Synchronization
↓
Stage Report
↓
Human Approval
↓
Git Commit
↓
Remote Push
↓
CI Verification
↓
Stage Closure

Performance work extends this lifecycle:

Baseline
↓
Benchmark
↓
Profile
↓
Evidence
↓
Hypothesis
↓
Optimization ADR
↓
Human Approval
↓
Optimize
↓
Re-benchmark
↓
Compare
↓
Keep / Revert

No step may be skipped because the change appears simple.

---

# 6. Task Planning

Any modification to:

- production code
- tests
- build configuration
- benchmark
- profiling
- protocol
- persistence
- WAL
- Snapshot
- Recovery
- architecture documentation
- runtime behavior

requires a Task Plan under:

tasks/active/

Task Plan MUST contain:

- Task ID
- Title
- Background
- Goal
- Non-Goals
- Requirements
- Acceptance Criteria
- Current Implementation
- Scope
- Design
- Alternatives
- ADR Linkage
- Planned File Changes
- Test Plan
- Benchmark/Profile Plan
- Risks
- Rollback Plan
- Verification Commands
- Git Plan
- Phase Gates
- Approval Record
- Implementation Log

Task lifecycle:

Proposed
↓ Human Approval
Approved
↓ Work begins
In Progress
↓ All DoD satisfied
Completed

Codex MUST NOT convert Proposed → Approved.

Only Human approval can do so.

---

# 7. ADR Governance

ADR is required when changing:

- Matching semantics
- Core OrderBook structure
- Concurrency model
- Event ordering
- Protocol
- WAL format
- Snapshot format
- Recovery model
- Persistence model
- Critical dependency
- Performance architecture

ADR lifecycle:

Proposed
↓
Human Review
↓
Accepted / Accepted with Constraints / Rejected / Superseded

Implementation MUST NOT begin while the required ADR is Proposed.

Task Plan and ADR MUST agree.

If they disagree:

STOP
→ synchronize
→ request approval again.

---

# 8. Implementation Rules

Implementation MUST be:

- minimal
- scoped
- explainable
- testable
- deterministic where required
- compatible with approved architecture

Codex MUST NOT:

- perform unrelated refactoring
- redesign APIs without approval
- upgrade dependencies without justification
- hide failures
- weaken assertions
- delete tests to make CI green
- introduce benchmark-specific production paths
- optimize without evidence

Use:

Small Step
↓
Test
↓
Inspect
↓
Continue

Do not implement an entire Phase in one uncontrolled change.

---

# 9. Verification Model

Verification depth MUST match risk.

Domain logic:
→ Unit Tests

Module interaction:
→ Integration Tests

Matching determinism:
→ Replay / State comparison

Network:
→ Integration / System Test

WAL / Recovery:
→ Crash / Replay / Corruption tests

Performance:
→ JMH + profiling evidence

Every implementation stage MUST verify:

- happy path
- boundary cases
- invalid input
- state transitions
- repeated operations
- invariants
- regression behavior

Never treat compilation alone as verification.

---

# 10. Performance Engineering

Performance claims MUST be reproducible.

Required process:

Baseline
→ Benchmark
→ Profile
→ Identify Bottleneck
→ Form Hypothesis
→ Human Approval
→ Optimize
→ Re-benchmark
→ Compare
→ Keep/Revert

Never optimize because:

“this should be faster.”

Performance reports MUST record where applicable:

- CPU
- CPU topology
- RAM
- OS
- JDK
- JVM arguments
- GC
- Benchmark version
- Warmup
- Measurement
- Forks
- Threads
- Dataset
- Workload
- Throughput
- P50
- P95
- P99
- P999
- Allocation
- GC behavior
- limitations

Microbenchmark results MUST NOT be represented as end-to-end system throughput.

---

# 11. Git Repository Strategy

Git is the authoritative history of engineering changes.

## 11.1 Branches

Use short-lived branches based on logical work:

feature/<topic>
fix/<topic>
perf/<topic>
docs/<topic>
test/<topic>

Do not create a branch for every tiny edit.

A branch should correspond to a coherent Task or major sub-stage.

## 11.2 Commits

One commit = one logically reviewable change.

Use Conventional Commits:

feat(orderbook): ...
fix(match): ...
perf(orderbook): ...
test(recovery): ...
docs(architecture): ...
build(ci): ...
refactor(core): ...

Do NOT use:

update
final
temp
fix stuff
test
aaa

Before commit:

git status --short
git diff --stat
git diff
git diff --check
git diff --cached --check

Review staged files explicitly.

Never commit:

- secrets
- tokens
- passwords
- IDE state
- local absolute paths
- generated benchmark recordings
- JFR recordings
- build output
- temporary files

unless explicitly intended and documented.

## 11.3 Remote Push Policy

A completed logical stage SHOULD be pushed to the configured remote when:

- Human approval for that stage has been recorded
- local quality gates pass
- commit history is coherent
- working tree is clean
- no secrets or local artifacts exist
- target branch is correct

Before push:

git status --short --branch
git log --oneline --decorate -5
git remote -v
git branch -vv

Normal non-destructive push of an already approved completed stage is part of
repository synchronization.

The following ALWAYS require explicit Human authorization:

- force push
- history rewrite
- rebase of shared history
- reset --hard
- destructive clean
- deleting remote branches
- deleting tags
- changing protected/default branch
- release publication

Never force push to the default branch.

## 11.4 CI

After push:

- confirm remote branch
- confirm CI trigger when available
- record CI status when observable
- do not claim CI passed unless evidence exists

A local BUILD SUCCESS is not equivalent to remote CI success.

## 11.5 Merge

Merge only after:

- stage approval
- local gates pass
- remote CI passes when configured
- diff reviewed
- documentation synchronized

Prefer reviewable history.

Do not mix unrelated tasks into one merge.

---

# 12. Artifact Policy

Generated evidence should be classified.

Commit:

- benchmark reports
- summarized profiling reports
- architecture documents
- ADRs
- reproducibility instructions
- small deterministic fixtures

Usually ignore:

- target/
- raw JFR recordings
- large profiler dumps
- temporary benchmark output
- IDE metadata
- local logs

Raw evidence that is intentionally not committed MUST have:

- path recorded
- generation command recorded
- summary committed
- limitation documented

---

# 13. Stage Gates

Every Task MUST be divided into explicit stages.

Typical lifecycle:

ADR / Decision
↓
Implementation
↓
Verification
↓
Benchmark / Profile (when applicable)
↓
Documentation Synchronization
↓
Completion

At the end of EVERY stage:

1. stop implementation
2. run required gates
3. inspect Git diff
4. synchronize relevant documentation
5. write Stage Report
6. update AGENT_CONTEXT
7. mark next gate Pending Human Approval
8. stop

Codex MUST NOT cross an approval gate automatically.

---

# 14. Stage Report Standard

Every completed stage MUST create a readable report under:

tasks/reports/

Recommended filename:

PHASE-<N>-<stage>-<topic>.md

The report MUST start with a human-readable dashboard.

Example:

# Phase 3 — Matching Engine / Implementation Report

## Executive Status

| Item      | Status               |
| --------- | -------------------- |
| Phase     | Phase 3              |
| Task      | TASK-...             |
| Stage     | Implementation       |
| Result    | Completed            |
| Tests     | 86 passed / 0 failed |
| Build     | PASS                 |
| CI        | Pending              |
| Commit    | abc1234              |
| Next Gate | Human Approval       |

## Progress

Phase 3
[████████████░░░░░░░░] 60%

Completed:

- Domain orchestration
- Limit matching integration

Pending:

- Market orders
- Verification
- Benchmark

## What Changed

Explain the actual engineering changes in plain language.

## Scope

### Completed

...

### Explicitly Not Implemented

...

## Verification Evidence

| Gate       | Command | Result |
| ---------- | ------- | ------ |
| Unit tests | ...     | PASS   |
| Full build | ...     | PASS   |
| Checkstyle | ...     | PASS   |
| Diff check | ...     | PASS   |

## Performance Evidence

Only when applicable.

Clearly separate:

- baseline
- current
- delta
- limitations

## Architecture / ADR Alignment

State:

- linked ADR
- ADR status
- whether implementation deviated
- whether new architecture decisions were discovered

## Git Evidence

- Branch
- HEAD before
- Commit
- Commit message
- Remote
- Push status
- CI status
- Working tree

## Risks and Limitations

List only real known limitations.

## Project Impact

Explain what capability the project gained.

## Next Stage

State exactly:

- proposed next work
- what is NOT authorized
- approval required

## Approval Request

End with an explicit gate:

Current Stage:
Completed

Human Approval:
Pending

Next Stage:
Not Authorized

The report MUST be understandable without reading Git diff.

Do not dump raw terminal output unless needed as evidence.

---

# 15. Progress Tracking

AGENT_CONTEXT MUST contain a compact project dashboard.

Example:

## Project Progress

| Phase                   | Status      | Evidence       |
| ----------------------- | ----------- | -------------- |
| Phase 0 Bootstrap       | Completed   | CI + build     |
| Phase 1 Domain          | Completed   | 12 tests       |
| Phase 2 OrderBook       | Completed   | 45 tests + JMH |
| Phase 3 Matching Engine | In Progress | TASK-...       |
| Phase 4 Performance     | Pending     | -              |

Current:

Phase: Phase 3
Task: TASK-...
Stage: Implementation
Approval: Approved
HEAD: abc1234
Branch: feature/matching-engine
Remote Sync: Up to date
CI: Passing

Next Gate:
Verification Human Approval

Do not duplicate entire historical reports inside AGENT_CONTEXT.

Link to them.

AGENT_CONTEXT is a state index, not a project diary.

---

# 16. Documentation Synchronization

Before a Task becomes Completed, synchronize as applicable:

- README
- Architecture
- ADR
- Benchmark documentation
- Performance documentation
- Recovery documentation
- Task Plan
- Stage Report
- AGENT_CONTEXT

Documentation must distinguish:

Verified Fact
Target
Hypothesis
Future Work

Never turn an aspirational target into a measured result.

---

# 17. Definition of Done

A Task is Completed only when:

- approved scope is implemented
- acceptance criteria are satisfied
- tests pass
- build passes
- static checks pass
- applicable benchmark/profile/recovery gates pass
- no unexplained regression exists
- Git diff reviewed
- ADR synchronized
- documentation synchronized
- Stage Reports exist
- Human approvals recorded
- logical commits created
- repository synchronized according to Git policy
- CI status recorded when available
- working tree state confirmed
- AGENT_CONTEXT updated

If one is missing:

Task != Completed

---

# 18. Failure Protocol

When something fails:

Observe
↓
Reproduce
↓
Classify
↓
Root Cause
↓
Fix
↓
Focused Verification
↓
Regression Verification
↓
Document

Do not immediately change tests.

Do not hide failures.

Do not silently change requirements.

If a failure exposes an architecture problem:

STOP
→ ADR / Human Decision.

---

# 19. Release Governance

Release is a separate engineering stage.

A release candidate requires:

- clean repository
- approved completed tasks
- full verification
- CI success
- architecture synchronization
- benchmark report
- known limitations
- recovery evidence when applicable
- release notes

Release flow:

Release Candidate
↓
Verification
↓
Human Release Approval
↓
Tag
↓
Push Tag
↓
Release Publication
↓
Post-release Verification

Never create a release solely because development appears finished.

---

# 20. Final Codex Response Standard

At the end of a working session, output a concise readable summary:

## Status

Phase:
Task:
Stage:
Result:

## Completed

- ...

## Verification

Tests:
Build:
Benchmark:
CI:

## Git

Branch:
Commit:
Remote:
Push:
Working Tree:

## Risks

- ...

## Next Gate

...

If the stage is complete:

STOP after reporting the approval request.

Do not begin the next stage.

---

# 21. Non-Negotiable Rules

Never:

- fabricate benchmark data
- fabricate test results
- claim CI success without evidence
- claim a push occurred when it did not
- claim 1M+ orders/s without valid evidence
- optimize without measurement
- weaken correctness for benchmark numbers
- bypass Human approval
- overwrite unrelated user changes
- commit secrets
- rewrite shared Git history without authorization
- silently change architecture
- cross an unapproved stage gate

Always:

Correctness
before
Performance

Evidence
before
Claims

Design
before
Implementation

Verification
before
Completion

Human Approval
before
Next Stage
