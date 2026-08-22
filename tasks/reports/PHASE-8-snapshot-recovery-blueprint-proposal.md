# Phase 8 Complete Blueprint Proposal — Snapshot Checkpoint and Online Recovery Bootstrap

## Status

```text
Phase 8 Discovery: Completed
Architect Review: PASS
ADR-0016: Proposed
Complete Blueprint: Proposed
TASK-029 through TASK-034: Proposed / Not Authorized
Production implementation: Not Authorized
Merge / v0.7.0-engineering-baseline: Not Authorized
Next Gate: Human Phase 8 Blueprint Approval
```

## Discovery Decision

The selected recovery model retains the complete WAL as the sole authority and
supports two explicit paths:

```text
PURE_WAL
    -> genesis replay of commands 1..WAL end

SNAPSHOT_THEN_WAL
    -> restore validated derived Snapshot N
    -> replay WAL N+1..WAL end
```

Snapshot plus tail is the normal accelerated path when a Snapshot exists; pure
WAL remains the correctness/reference path. A corrupt published Snapshot fails
closed and does not trigger silent fallback.

## Proposed Architecture

Snapshot creation is deliberately offline and quiescent:

```text
offline WAL under the exclusive recovery lease
    -> strict scan
    -> genesis replay
    -> canonical checkpoint at Sequence N
    -> bind to WAL prefix + canonical checkpoint digest
    -> write / force / validate / atomic move
    -> immutable Snapshot v1
```

Online bootstrap is listener-last:

```text
exclusive recovery
    -> strict WAL scan / approved final-tail repair
    -> pure-WAL or Snapshot-tail restore
    -> engine/writer/coordinator next-sequence convergence
    -> recovered-engine Pipeline
    -> durable Gateway
    -> bind listener
    -> RUNNING
```

No recovery output is sent as a new client response. A new TCP session resets
RequestId to 1 while Command Sequence, TradeId and EventSequence continue from
validated recovered state.

## Proposed Decisions and Tasks

- [`ADR-0016`](../../docs/adr/ADR-0016-snapshot-checkpoint-and-online-recovery-bootstrap.md)
  freezes D1-D14: authority, format, publication, corruption, startup, handoff,
  identity, evidence and deferred scope.
- [`Phase 8 Blueprint`](../blueprints/PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md)
  defines the complete authorization boundary.
- TASK-029 through TASK-034 are present under `tasks/active/` as proposed plans.

Dependency order after Human approval would be:

```text
TASK-029 canonical checkpoint
    -> TASK-030 Snapshot v1 codec/store
    -> TASK-031 recovery planner/replay
    -> TASK-032 live handoff
    -> TASK-033 crash/corruption/determinism verification
    -> TASK-034 benchmark/docs/Closure Proposal
    -> STOP at Human Phase 8 Closure Review
```

## Frozen Boundary and Requested Exceptions

The `v0.6.0-engineering-baseline` tag remains immutable. WAL v1, Protocol v1,
matching outcomes, Domain/identity semantics, Phase 7 WAL-before-execute and
the single-session/one-in-flight topology remain frozen.

Human Blueprint Approval is specifically required before these additive
recovery boundaries may be implemented:

- OrderBook canonical checkpoint export/restore;
- MatchingEngine checkpoint export/restore;
- recovered-engine MatchingEnginePipeline construction;
- validated DurableCommandCoordinator sequence seeding; and
- new Snapshot/recovery/runtime packages.

The proposal does not hide these necessary exceptions behind a zero-diff claim.
All other existing production files remain outside scope.

## Claim and Risk Boundary

The proposed Phase does not include hot Snapshot, WAL retention, reconnect,
deduplication, exactly-once, multiple sessions, HA or Product Release. Snapshot
is derived state and never authority. `force(true)` and atomic move do not prove
hardware power-loss safety. Benchmarks would be component/local-host recovery
evidence, not production RTO or availability evidence.

The offline generator must hold the same cooperative `recovery.lock` lease from
before scan through Snapshot publication and verify an unchanged WAL segment
inventory/file-size boundary. A non-participating legacy writer must be stopped;
concurrent generation against it is unsupported.

## Git and Workspace State

- Proposal branch: `docs/phase8-snapshot-recovery-blueprint`.
- Baseline: `v0.6.0-engineering-baseline` at `6473365`.
- Proposal content checkpoint: `c51e759` / exact-SHA CI
  [`32576740050`](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32576740050) PASS.
- Remote: pushed to `origin/docs/phase8-snapshot-recovery-blueprint`.
- Production code changes in this proposal: none.
- `.vscode/`: pre-existing untracked user directory, untouched and excluded.
- Implementation branch, merge and tag remain unauthorized.

## Proposal Verification

```text
Architect / Sol High Discovery: PASS
Architect staged-delta review: PASS after three proposal corrections
Docs-auditor read-only review: PASS
mvn verify: PASS — 158 tests, 0 failures
Checkstyle: 0 violations
git diff --check: PASS
Markdown relative-link check: PASS
Production/build diff: 0
Proposal content exact-SHA CI: 32576740050 PASS
```

The corrected proposal makes the canonical checkpoint digest counter-sensitive,
protects offline Snapshot generation with the shared recovery lease and stable
WAL inventory check, and compares pure-WAL versus Snapshot-tail results only for
their common `N+1..M` replay suffix.

## Required Human Decision

Human review must either approve or reject:

```text
ADR-0016 D1-D14
TASK-20260822-029 through TASK-20260822-034
the exact additive frozen-boundary exceptions
continuous dependency-ordered execution after per-Task Evidence Gates
the listed Exception Gates and deferred scope
```

Until that decision is recorded, no Phase 8 implementation may begin.
