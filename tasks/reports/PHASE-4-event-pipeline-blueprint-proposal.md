# Phase 4 Event Pipeline Blueprint Proposal

## Status Dashboard

| Field | Value |
| --- | --- |
| Phase | Phase 4 — Event Pipeline |
| Stage | Complete Blueprint Proposal |
| Result | `Approved — Implementation Authorized in Dependency Order` |
| Production Changes | None |
| Tests | Baseline `mvn verify` PASS — 61 tests |
| Build | Maven reactor 3/3 SUCCESS; Checkstyle 0 violations |
| CI | [run 32455576290](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32455576290) PASS for proposal content SHA |
| Commit | `f0f18ffac683318c07df739e4eeabd74be7c2ddb` |
| Next Gate | One Human Phase Blueprint Approval |

## Proposal Boundary

Phase 4 proposes a bounded event-pipeline correctness and evidence baseline in
front of the frozen synchronous MatchingEngine. It does not authorize any code
until the complete Blueprint receives Human approval.

```text
Caller / Future Ingress
    -> bounded tryPublish
    -> single-producer Disruptor ring buffer
    -> one matching consumer
    -> frozen MatchingEngine
    -> synchronous in-memory result handler
```

The frozen `v0.2.0-engineering-baseline` is the dependency, not a mutable
target. Existing Domain, OrderBook and MatchingEngine production files are
outside the planned change set.

## Proposed Decisions

| Decision | Proposal |
| --- | --- |
| D1 | LMAX Disruptor `4.0.0` as the pipeline mechanism |
| D2 | One external producer and one pipeline-owned matching consumer |
| D3 | Command Sequence remains authoritative; ring sequence is infrastructure-only |
| D4 | Bounded non-blocking `tryPublish` with explicit `ACCEPTED` / `FULL` |
| D5 | Synchronous deterministic in-memory `EngineResultHandler` |
| D6 | Explicit lifecycle, bounded drain and terminal fail-stop behavior |
| D7 | `BLOCKING` default; yielding/busy-spin only benchmark variables |
| D8 | Multi-producer, Network, WAL, Replay and advanced tuning deferred |

The durable decision record is
[`ADR-0012`](../../docs/adr/ADR-0012-event-pipeline-execution-and-backpressure.md).

## Task Breakdown

| Task | Purpose | Status |
| --- | --- | --- |
| [`TASK-010`](../active/TASK-20260821-010-phase4-pipeline-foundation.md) | dependency, configuration and public pipeline contracts | Approved / next |
| [`TASK-011`](../active/TASK-20260821-011-phase4-pipeline-core.md) | bounded runtime, lifecycle, matching and result handling | Approved / after TASK-010 evidence |
| [`TASK-012`](../active/TASK-20260821-012-phase4-pipeline-verification.md) | determinism, ordering, backpressure and failure evidence | Approved / after TASK-011 evidence |
| [`TASK-013`](../active/TASK-20260821-013-phase4-pipeline-benchmark-docs.md) | component benchmark, documentation and Closure preparation | Approved / after TASK-012 evidence |

The complete scope, file boundaries, acceptance criteria, evidence plan,
risks, rollback, Git strategy and Closure plan are in the
[`Phase 4 Blueprint`](../blueprints/PHASE-4-event-pipeline-blueprint.md).

## Verification Performed for the Proposal

- baseline `mvn verify`: PASS;
- 61 tests, 0 failures;
- Checkstyle: 0 violations;
- Maven reactor: 3/3 SUCCESS;
- Phase 3 tag verified at `928112414a9bde581b2ac75e2606373d61be77b8`;
- proposal branch: `docs/phase4-event-pipeline-blueprint`;
- proposal content commit: `f0f18ffac683318c07df739e4eeabd74be7c2ddb`;
- exact-SHA GitHub Actions CI: run `32455576290` PASS;
- production, test, build and runtime files changed by this proposal: none.

## Non-Goals

- Network, protocol and decoder implementation;
- WAL, Replay, Snapshot and Recovery;
- multi-producer or multi-symbol routing;
- asynchronous output ring or external I/O;
- Market Order execution;
- production optimization, deployment or Release.

## Risks and Exception Gates

Execution must pause if implementation requires a frozen public-contract
change, persistence/protocol semantics, a new unlisted dependency, a topology
change, scope expansion, weakened acceptance criteria or destructive Git /
Release action. A full ring buffer must never cause silent loss or overwrite.
Consumer failure must never be hidden as successful continued operation.

## Human Decision Requested

One Human Phase Blueprint Approval is requested for ADR-0012 D1-D8 and the
enumerated TASK-010 through TASK-013 only. Approval would permit continuous
execution through automated evidence checkpoints, subject to the Blueprint's
Exception Gates. It would not approve Phase Closure, merge to `master`, the
candidate `v0.3.0-engineering-baseline` tag, or any non-goal.

**Phase Blueprint — Pending Human Approval. Phase 4 implementation remains
locked.**
