# Phase 7 Closure Proposal — Live Durable Command Pipeline Integration

## Status

```text
Phase 7 implementation: Completed
TASK-024 through TASK-028: Completed / TASK-028 Evidence Gate pending
Closure Proposal: Prepared
Human Phase 7 Closure Review: Pending
Merge to master: Not authorized
v0.6.0-engineering-baseline: Not created
Phase 8: Not authorized
```

This proposal is the final evidence handoff for the approved Phase 7
Blueprint. It deliberately stops before the Human Closure Review. No merge,
tag or Product Release action is authorized by this document.

## Scope delivered

Phase 7 adds an opt-in live durable command composition around the frozen
Phase 2–6 components:

```text
Protocol v1 request
    -> durable single-session Gateway
    -> DurableCommandCoordinator
    -> Command WAL append + SYNC_EACH_APPEND force(true)
    -> MatchingEnginePipeline admission
    -> frozen MatchingEngine
    -> EngineResult
    -> owning Netty EventLoop response
```

The implementation preserves the identity domains:

```text
Request ID
    != Command Sequence
    != WAL physical position
    != Ring Sequence
    != EventSequence
    != TradeId
```

The non-empty-WAL startup rejection and closed-WAL offline replay boundary
remain explicit. Phase 7 does not add online Recovery, Snapshot, reconnect,
deduplication, multiple sessions or durable client acknowledgement.

## Dependency-ordered task evidence

| Task | Status | Evidence |
| --- | --- | --- |
| TASK-024 | Completed / PASS | Durable contracts and configuration; exact-SHA CI recorded in TASK-024 report |
| TASK-025 | Completed / PASS | WAL-before-pipeline coordinator; exact-SHA CI recorded in TASK-025 report |
| TASK-026 | Completed / PASS | Additive durable Netty composition; exact-SHA CI recorded in TASK-026 report |
| TASK-027 | Completed / PASS | 12 focused tests, 158 core regression tests, Round 2 terminal/disconnect evidence; final sync `c4be5b9` / CI `32573193281` |
| TASK-028 | Implementation complete / Gate pending | Four-boundary JMH matrix, reports and context sync; pending read-only reviewers and exact-SHA final gate |

The prior TASK-027 Evidence Gate was independently reviewed by the read-only
verifier and docs auditor. TASK-028 is the only remaining evidence task before
the Phase 7 Closure Review.

## TASK-028 benchmark evidence

The full Java 21 JMH matrix uses one fork, one thread, one one-second warmup and
two one-second measurement iterations. It records Throughput and SampleTime
with P50/P95/P99/P999. Separate boundaries are documented in
[`durable-pipeline.md`](../../docs/benchmark/durable-pipeline.md):

- WAL append plus synchronous force;
- append/force plus pipeline publication with explicit Submit and Cancel
  vectors;
- local Protocol response encoding;
- one-in-flight sequential loopback with alternating Submit/Cancel.

The results are component/local-host observations. They do not establish
durable ACK, client receipt, power-loss safety, online recovery, concurrent
capacity or production readiness. Large synchronous-WAL tails are retained as
host-specific observations.

## Frozen boundary audit

The following production paths remain frozen relative to the approved
`v0.5.0-engineering-baseline`:

```text
Domain
OrderBook
MatchingEngine
WAL v1
Offline Recovery / Replay
Pipeline
Protocol v1
```

TASK-028 changes are limited to the benchmark module and documentation. The
pre-existing `.vscode/` directory remains untracked and untouched.

## Required final Evidence Gate

Before Closure Review, the main agent must complete:

```text
benchmark-reviewer (read-only) PASS
verifier (read-only) PASS
docs-auditor (read-only) PASS
mvn verify PASS
Checkstyle 0
git diff --check PASS
frozen production diff = 0
exact-SHA CI PASS
```

The final evidence HEAD and CI run must replace any stale TASK-028 planning
references in this proposal, the task report, Blueprint and
`.codex/AGENT_CONTEXT.md`.

## Requested Human Closure decision

If the final Evidence Gate passes, Human review may decide whether to authorize
the normal `--no-ff` merge, master verification, annotated
`v0.6.0-engineering-baseline` tag and TASK-024 through TASK-028 archival. Until
that separate decision, all of those actions remain unauthorized.
