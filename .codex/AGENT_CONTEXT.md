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
| Product stage | Measurement-Isolation Execution completed |
| Product approval | Steady-State Evidence Review pending Human approval |
| Current infrastructure task | [`TASK-20260820-006`](../tasks/active/TASK-20260820-006-repository-remote-ci-setup.md) — In Progress |
| Branch | `chore/repository-remote-ci` |
| Baseline HEAD | `89ba9e2` |
| Remote | `origin` — `git@github.com:Holylaw7/Ultra-Low-Latency-Matching-Engine.git` |
| Remote sync | Pending initial branch push |
| CI | GitHub Actions workflow exists; remote verification pending |

## Project Progress

| Phase | Status | Evidence |
| --- | --- | --- |
| Phase 0 — Bootstrap | Completed | Maven reactor, Java 21, JUnit 5, JMH, Checkstyle and CI workflow |
| Phase 1 — Domain Model | Completed / Approved | [`PHASE-1-domain-model.md`](../tasks/reports/PHASE-1-domain-model.md) |
| Phase 2 — Basic OrderBook | In Progress | OrderBook tests, JMH baseline and Phase 2 reports |
| Phase 3 — Matching Engine | Pending / Not Authorized | Requires completion and approval of the current Phase 2 gate |
| Phase 4+ — Pipeline, network, recovery and performance evolution | Future Work | Architecture documents and future ADRs/tasks |

## Current Product Gate

The approved Phase 2 OrderBook structure, structural limit matching,
correctness verification, component benchmark, profiling and
measurement-isolation experiment are complete. The next product action is:

```text
Steady-State Evidence Review
    -> Human Approval
    -> approved next Task/Stage only
```

Production optimization, Phase 3 implementation and any unrelated runtime
change remain unauthorized.

Current plan:
[`TASK-20260819-004-basic-orderbook.md`](../tasks/active/TASK-20260819-004-basic-orderbook.md).

Current evidence:

- [`PHASE-2-measurement-isolation.md`](../tasks/reports/PHASE-2-measurement-isolation.md)
  — completed; Human review pending.
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
- Remote synchronization and CI evidence are pending completion of
  `TASK-20260820-006`.

## Session Recovery Checklist

1. Read `MASTER_PROMPT.md`, `DEVELOPMENT_RULES.md`, this file and
   `tasks/README.md`.
2. Read every relevant `tasks/active/*` plan and linked ADR.
3. Run the mandatory Git bootstrap commands from `MASTER_PROMPT.md`.
4. Reconcile live Git state with this index; live Git is authoritative for
   repository state.
5. Confirm the current approval gate before any modification.
