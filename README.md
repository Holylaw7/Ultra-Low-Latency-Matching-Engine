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
Developer 于 `2026-08-19` 批准。当前进入 Profiling ADR / Decision 提案阶段，
等待 Human Approval。

Phase 1 Domain Model and Correctness Baseline has been completed and approved.
ADR-0007 and ADR-0008 have been approved, and the Phase 2 OrderBook structure
and Structural Limit Matching are implemented within their recorded scope.
The approved benchmark is component-level experimental baseline evidence, not a
production throughput or latency claim. Profiling has not been executed.

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
resting. MatchingEngine, Trade/Execution publication, WAL, network and
performance optimization remain outside the current scope.

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
or GC claims. The next gate is approval of the Profiling ADR / Task Plan.

## Structure

```text
.codex/       Project-level agent rules and current context
docs/         Architecture, ADR, benchmark, and performance records
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

Every change must be tested, reviewed in Git, committed with a meaningful message, and followed by a clean working-tree check.
