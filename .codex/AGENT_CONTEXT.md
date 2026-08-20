# AGENT_CONTEXT — Matching Engine Current State

> Last Updated: 2026-08-20
> Purpose: compact current-state index; detailed history lives in Tasks, Stage
> Reports, ADRs and Git.

## Project Dashboard

| Item | Current State |
| --- | --- |
| Project | Ultra-Low-Latency Matching Engine |
| Product scope | Single-node, in-memory, deterministic matching engine |
| Phase | Phase 2 — Basic OrderBook |
| Product task | `TASK-20260819-004` — Basic OrderBook (`In Progress`) |
| Product stage | Phase 2 closure actions |
| Product approval | Final Closure Review approved |
| Latest infrastructure task | [`TASK-20260820-006`](../tasks/completed/TASK-20260820-006-repository-remote-ci-setup.md) — Completed |
| Branch | `chore/repository-remote-ci` |
| Latest remote-verified commit | `f1f2a85` |
| Remote | `origin` — `git@github.com:Holylaw7/Ultra-Low-Latency-Matching-Engine.git` |
| Remote sync | `master` and `chore/repository-remote-ci` published; current branch tracks `origin` |
| CI | Passing for `f1f2a85` — [GitHub Actions run 32371665075](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32371665075) |

## Project Progress

| Phase | Status | Evidence |
| --- | --- | --- |
| Phase 0 — Bootstrap | Completed | Maven reactor, Java 21, JUnit 5, JMH, Checkstyle and CI workflow |
| Phase 1 — Domain Model | Completed / Approved | [`PHASE-1-domain-model.md`](../tasks/reports/PHASE-1-domain-model.md) |
| Phase 2 — Basic OrderBook | Closure Approved | OrderBook tests, JMH/JFR evidence, remote repository and passing CI |
| Phase 3 — Matching Engine | Pending / Not Authorized | Requires completion and approval of the current Phase 2 gate |
| Phase 4+ — Pipeline, network, recovery and performance evolution | Future Work | Architecture documents and future ADRs/tasks |

## Current Product Gate

The Phase 2 implementation and evidence tracks are complete. Final Closure
Review was approved on `2026-08-20`. The authorized closure sequence is:

```text
normal --no-ff merge to master
    -> verify master locally and in GitHub Actions
    -> create/push v0.1.0-engineering-baseline
    -> close TASK-20260819-004
```

Release, production optimization, Phase 3 implementation and history rewrite
remain unauthorized.

Current plan:
[`TASK-20260819-004-basic-orderbook.md`](../tasks/active/TASK-20260819-004-basic-orderbook.md).

Current evidence:

- [`PHASE-2-measurement-isolation.md`](../tasks/reports/PHASE-2-measurement-isolation.md)
  — completed and accepted as Phase 2 closure evidence.
- [`PHASE-2-repository-remote-ci-setup.md`](../tasks/reports/PHASE-2-repository-remote-ci-setup.md)
  — completed and approved; remote CI established.
- [`PHASE-2-final-closure-review.md`](../tasks/reports/PHASE-2-final-closure-review.md)
  — approved; closure actions authorized.
- [`PHASE-2-profiling-execution.md`](../tasks/reports/PHASE-2-profiling-execution.md)
  — completed and approved as evidence collection.
- [`PHASE-2-benchmark-orderbook-baseline.md`](../tasks/reports/PHASE-2-benchmark-orderbook-baseline.md)
  — approved component-level baseline.
- [`PHASE-2-verification-structural-limit-matching.md`](../tasks/reports/PHASE-2-verification-structural-limit-matching.md)
  — approved correctness evidence.

## Accepted Decisions

| Decision | Status | Source |
| --- | --- | --- |
| Domain model and correctness baseline | Accepted with constraints | [`ADR-0005`](../docs/adr/ADR-0005-domain-model-and-correctness-baseline.md) |
| ADR-first governance | Accepted | [`ADR-0006`](../docs/adr/ADR-0006-adr-first-decision-governance.md) |
| TreeMap side books, intrusive FIFO and active OrderId index | Accepted with constraints | [`ADR-0007`](../docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md) |
| Structural limit matching and `MatchFragment` boundary | Approved | [`ADR-0008`](../docs/adr/ADR-0008-structural-limit-matching.md) |
| JFR-first profiling evidence | Approved | [`ADR-0009`](../docs/adr/ADR-0009-performance-profiling-evidence.md) |
| Defer production optimization until measurement isolation | Approved | [`ADR-0010`](../docs/adr/ADR-0010-optimization-decision-after-profiling.md) |

If a Task and linked ADR disagree, stop and synchronize them before work.

## Verified Current Implementation

- Positive `long`-backed domain identifiers, price, quantity and sequence.
- Controlled limit/market order lifecycle plus deterministic Trade and
  Execution value objects.
- `TreeMap` bid/ask price indexes with intrusive FIFO levels.
- Active `OrderId -> OrderNode` cancellation index.
- Deterministic structural limit matching with price-time priority, maker
  price, partial/full fills and one-time residual resting.
- OrderBook-focused correctness, invariant and determinism tests.

`MatchingEngine` orchestration, market-order execution, event publication,
Disruptor pipeline, Netty protocol, WAL, snapshot and recovery are not
implemented.

## Performance Evidence

Verified fact:

- A component-level JMH OrderBook baseline and JFR/measurement-isolation
  evidence exist under the linked Phase 2 reports.
- Raw JSON and JFR artifacts are local and ignored; reports record their paths,
  generation commands, summaries and limitations.

Not verified:

- 1M+ orders/s
- Microsecond P99
- Zero GC or zero-copy
- Lock-free/wait-free execution
- End-to-end system throughput

These remain targets or hypotheses, never measured project claims.

## Planned System Framework

```text
Client
  -> Netty / Protocol                 [Future Work]
  -> Decoder / Validation             [Future Work]
  -> Ingress + RingBuffer/Disruptor   [Future Work]
  -> MatchingEngine                   [Phase 3]
  -> OrderBook                        [Phase 2 baseline implemented]
       -> BidBook / AskBook
       -> PriceLevel / OrderQueue
       -> active OrderId index
  -> Trade / Execution events         [Domain types implemented; orchestration future]
       -> WAL / Recovery              [Future Work]
       -> Output / Metrics            [Future Work]
```

One matching thread owns one symbol OrderBook. Any change to matching semantics,
core structure, concurrency, event ordering, protocol, persistence or recovery
requires an approved ADR and Task.

## Known Risks

- Current benchmark evidence is workload-specific and not end-to-end.
- Windows scheduling and setup/profiler overhead limit performance inference.
- Raw evidence is local; reproducibility depends on committed commands and
  summaries.
- The older `feature/domain-model` branch name predates the broader Phase 2
  work; new infrastructure work uses a dedicated branch.
- Branch protection, merge policy automation and release evidence remain
  Future Work; they were outside `TASK-20260820-006`.

## Session Recovery Checklist

1. Read `MASTER_PROMPT.md`, `DEVELOPMENT_RULES.md`, this file and
   `tasks/README.md`.
2. Read every relevant `tasks/active/*` plan and linked ADR.
3. Run the mandatory Git bootstrap commands from `MASTER_PROMPT.md`.
4. Reconcile live Git state with this index; live Git is authoritative for
   repository state.
5. Confirm the current approval gate before any modification.
