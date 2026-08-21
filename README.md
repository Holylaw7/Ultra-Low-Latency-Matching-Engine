# Ultra-Low-Latency Matching Engine

A deterministic, single-node matching engine implemented with Java 21.

The project is developed in small, verifiable steps:

```text
Correctness
    -> Baseline
    -> Benchmark
    -> Profile
    -> Hypothesis
    -> Optimization
    -> Re-benchmark
```

New multi-task phases use Phase Blueprint Mode:

```text
Sol architecture / complete Phase Blueprint
    -> one Human Blueprint Approval
    -> Terra implementation with test, diff and CI checkpoints
    -> Exception Gate only when scope or architecture changes
    -> Sol final Closure Review
    -> one Human Phase Closure Approval
```

The Blueprint must enumerate ADRs, Tasks, stages, boundaries and evidence.
Approval never extends to unlisted work. Reducing repeated Human reviews does
not reduce tests, static checks, Git review, documentation or CI requirements.
Model roles are recommendations rather than authority; the current official
OpenAI model-selection guidance is linked from `.codex/MASTER_PROMPT.md`.

## Current Stage

Phase 3 — MatchingEngine is completed, approved and frozen at
`v0.2.0-engineering-baseline`. The baseline contains the Domain Model, frozen
Phase 2 OrderBook, synchronous MatchingEngine orchestration, immutable
Trade/Execution results and deterministic execution evidence. It does not
contain WAL, Replay, Snapshot, Recovery, Network or production optimization.

Phase Blueprint Mode is completed, approved and active as the governance
standard for future multi-task phases. The complete Phase 4 Event Pipeline
Blueprint, ADR-0012 and TASK-010 through TASK-013 have received Human Blueprint
Approval. Phase 4 implementation is authorized in dependency order with
automated evidence gates; the frozen `v0.2.0-engineering-baseline` remains
unchanged.

The repository currently contains:

- Java 21 Maven multi-module build
- Core module compiled from the root `src/` layout
- JUnit 5 test setup
- JMH benchmark module
- Checkstyle validation
- Java version enforcement
- GitHub Actions CI
- Architecture, ADR, benchmark, and performance documentation skeleton
- Task workspace with ADR-first Phase Blueprints, evidence gates and Exception Gates

Implemented Phase 2 behavior includes deterministic limit-order matching,
price-time priority, maker-price fragments, partial/full fills, and residual
resting. Stage 2 adds synchronous command processing, OrderBook integration
and deterministic Trade/Execution result generation. Publication, WAL, network
and performance optimization remain outside the current scope.

The proposed Phase 4 boundary adds a bounded single-producer/single-consumer
event pipeline in front of the existing synchronous MatchingEngine. WAL,
Replay, Snapshot, Recovery, Network and production optimization remain
explicit non-goals. See
[`PHASE-4-event-pipeline-blueprint.md`](tasks/blueprints/PHASE-4-event-pipeline-blueprint.md).

## Build

```bash
mvn test
mvn verify
```

## Benchmark

Build the benchmark module:

```bash
mvn verify
```

Run the bootstrap benchmark:

```bash
java -jar benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar BootstrapBenchmark
```

Run the approved Phase 2 OrderBook baseline:

```bash
java -jar benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar \
  OrderBookBaselineBenchmark \
  -f 2 -wi 3 -i 5 -w 1s -r 1s -t 1 -rf json \
  -rff benchmark-results/orderbook-baseline.json
```

The approved baseline uses Java 21, JMH 1.37, two forks, one matching-owner
thread, deterministic fixed workloads and the current TreeMap + intrusive FIFO
and active OrderId index implementation. Results are component-level evidence
only; they must not be presented as production throughput, latency, allocation
or GC claims. JFR profiling evidence is recorded in
`tasks/reports/PHASE-2-profiling-execution.md`; measurement-isolation evidence
is recorded in `tasks/reports/PHASE-2-measurement-isolation.md`. The evidence
set is accepted and frozen in `v0.1.0-engineering-baseline`.

## Structure

```text
.codex/       Project-level agent rules and current context
docs/         Architecture, ADR, benchmark, and performance records
tasks/        Approved plans, execution state, stage reports, and approval gates
src/          Core production and test source
core/         Maven module that packages the root core source
benchmark/    Independent JMH module
config/       Build and static-check configuration
```

## Engineering Rules

Before changing code, read:

1. `.codex/MASTER_PROMPT.md`
2. `.codex/DEVELOPMENT_RULES.md`
3. `.codex/AGENT_CONTEXT.md`
4. `tasks/README.md`
5. Relevant plans under `tasks/active/` and their linked ADRs

These files have distinct authority: `MASTER_PROMPT` defines governance,
`DEVELOPMENT_RULES` defines engineering rules, `AGENT_CONTEXT` indexes current
state, and `tasks/` defines approved work. Every change follows the applicable
verification, Stage Report, Human approval, Git synchronization and CI status
rules. Unavailable remote or CI evidence must be reported as unavailable, not
inferred as successful.
