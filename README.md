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

## Current Stage

Phase 2 - Basic OrderBook：Structural Limit Matching、Verification、OrderBook
baseline Benchmark 和 Documentation Synchronization 已完成并经 Human
Developer 于 `2026-08-19` 批准。ADR-0009 Profiling ADR / Decision 已于
`2026-08-19` 批准，Profiling Execution 已于 `2026-08-19` 通过 Human
Approval。ADR-0010 Optimization ADR / Decision 已于 `2026-08-19` 批准，
并授权 Measurement-Isolation Execution。Isolation 与 Repository/CI Setup
均已完成并于 `2026-08-20` 通过 Human Review。Phase 2 已通过 Final Closure
Review，并冻结为 `v0.1.0-engineering-baseline`。

Phase 1 Domain Model and Correctness Baseline has been completed and approved.
ADR-0007 and ADR-0008 have been approved, and the Phase 2 OrderBook structure
and Structural Limit Matching are implemented within their recorded scope.
The approved benchmark is component-level experimental baseline evidence, not a
production throughput or latency claim. Profiling execution evidence is
recorded and approved as evidence collection. Measurement-isolation evidence
is recorded separately and does not replace B0 or authorize production
optimization. Phase 2 is closed at the engineering baseline tag. Phase 3 ADR
and TASK-008 are approved. Stage 1 Domain/API Foundation is completed and
approved; it adds EventSequence plus immutable command/result types only.
Stage 2 implements the synchronous MatchingEngine core: sequenced limit submit
and cancellation, frozen OrderBook delegation and immutable Trade/Execution
results. Stage 2 is completed and approved; Stage 3 verification remains
separately gated. Its verification-only scope is approved and execution is
authorized; Phase 3 closure still requires a later Human review.

The repository currently contains:

- Java 21 Maven multi-module build
- Core module compiled from the root `src/` layout
- JUnit 5 test setup
- JMH benchmark module
- Checkstyle validation
- Java version enforcement
- GitHub Actions CI
- Architecture, ADR, benchmark, and performance documentation skeleton
- Task workspace with ADR-first decisions and phase approval gates

Implemented Phase 2 behavior includes deterministic limit-order matching,
price-time priority, maker-price fragments, partial/full fills, and residual
resting. Stage 2 adds synchronous command processing, OrderBook integration
and deterministic Trade/Execution result generation. Publication, WAL, network
and performance optimization remain outside the current scope.

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
