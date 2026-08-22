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
    -> Main Luna Max implementation with test, diff and CI checkpoints
    -> parallel read-only verifier/docs/benchmark auditors when useful
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

Phase 7 — Live Durable Command Pipeline Integration is active under approved
ADR-0015 and its Complete Blueprint. TASK-024 durable contracts are completed
with exact-SHA CI evidence; TASK-025 coordinator implementation and Evidence
Gate are complete at `2342897` / CI `32564005988`; TASK-026 durable Netty
composition is implemented locally and awaits its exact-SHA Evidence Gate.
Phase Closure, merge, `v0.6.0-engineering-baseline`,
Snapshot, online Recovery and Product Release remain unauthorized. The frozen `v0.5.0-engineering-baseline`
continues to protect the Phase 2–6 production paths.

The Phase 3 MatchingEngine baseline is completed, approved and frozen at
`v0.2.0-engineering-baseline`. It contains the Domain Model, frozen Phase 2
OrderBook, synchronous MatchingEngine orchestration, immutable Trade/Execution
results and deterministic execution evidence.

Phase Blueprint Mode is completed, approved and active as the governance
standard for future multi-task phases. The complete Phase 4 Event Pipeline
Blueprint, ADR-0012 and TASK-010 through TASK-013 have received Human Blueprint
Approval. Phase 4 implementation, verification, component benchmark evidence
and Closure are complete and frozen at `v0.3.0-engineering-baseline`. The
earlier `v0.2.0-engineering-baseline` remains immutable. Phase 5 Command WAL
and Deterministic Offline Replay is completed, approved and frozen at
`v0.4.0-engineering-baseline`. TASK-014 through TASK-018 are archived.
Product Release remains a separate Human gate. Phase 6 is completed, merged and
frozen at `v0.5.0-engineering-baseline` for the versioned binary TCP protocol and
single-session Netty gateway. TASK-019 through TASK-023 are archived after their
dependency-ordered evidence gates, including deterministic network verification
and component/loopback JMH evidence. Gateway FULL identity preservation,
outbound-write failure and pipeline-failure terminal propagation retain an
explicit limitation: implementation-path evidence is verified, but dynamic
Gateway fault injection was not performed. Live WAL/pipeline integration,
multi-client ingress, request pipelining, Snapshot and online Recovery remain
excluded.
See the [Phase 6 Blueprint](tasks/blueprints/PHASE-6-network-protocol-blueprint.md),
[network boundary](docs/architecture/network.md),
[network benchmark evidence](docs/benchmark/network.md) and
[ADR-0014](docs/adr/ADR-0014-network-protocol-and-single-session-gateway.md).

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

Historical Phase 2 behavior includes deterministic limit-order matching,
price-time priority, maker-price fragments, partial/full fills, and residual
resting. Phase 3 adds synchronous command processing, OrderBook integration
and deterministic Trade/Execution result generation. Later phases add the
pipeline, WAL, protocol and the currently active live durable integration.

The Phase 4 boundary added a bounded single-producer/single-consumer
event pipeline in front of the existing synchronous MatchingEngine. WAL,
Replay, Snapshot, Recovery, Network and production optimization remain
explicit non-goals for that historical phase. See
[`PHASE-4-event-pipeline-blueprint.md`](tasks/blueprints/PHASE-4-event-pipeline-blueprint.md).

The approved Phase 5 boundary is a versioned segmented command WAL plus strict
offline deterministic replay. TASK-014 through TASK-018 and the authorized
limited closure remediation are implemented with 114 passing tests,
corruption/torn-tail evidence, deterministic rotation-failure evidence and
component-level JMH evidence. Human Phase 5 Closure Approval, normal merge,
master CI and the annotated `v0.4.0-engineering-baseline` tag workflow are
complete. Product Release remains separately governed. The phase deliberately
excludes live pipeline durability integration, Snapshot, online Recovery and
Network. See
[`PHASE-5-command-wal-and-replay-blueprint.md`](tasks/blueprints/PHASE-5-command-wal-and-replay-blueprint.md)
and [`recovery.md`](docs/benchmark/recovery.md).

Phase 6 adds a single-session, one-request-in-flight Netty adapter around the
frozen pipeline and matching core. Its protocol, gateway failure behavior,
fragmentation/coalescing checks and bounded codec/loopback benchmark are
component evidence only; a local write is not a durable or client-receipt
acknowledgement. See [`network.md`](docs/benchmark/network.md) and the
[Phase 6 Closure Proposal](tasks/reports/PHASE-6-network-protocol-closure.md).

The approved Phase 7 boundary adds the WAL-before-pipeline integration layer
as a dependency-ordered live durable foundation. TASK-024 is complete;
TASK-025 is complete with exact-SHA Evidence Gate PASS. TASK-026 is locally
implemented with its Evidence Gate pending; failure/replay verification and
benchmark/docs work remain in TASK-027 through TASK-028. No online Recovery, reconnect,
deduplication, multi-session support, Product Release or production-readiness
claim is authorized.

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

Run the Phase 5 WAL/replay component baseline after `mvn verify`:

```bash
java -jar benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar \
  WalBenchmark -wi 1 -i 1 -f 1 -w 1s -r 1s -t 1 -foe true \
  -rf json -rff benchmark-results/wal-remediation-full.json
```

This measures append (with a deterministic Submit/Cancel mix), strict scan and
offline replay separately. The raw JSON is intentionally local and ignored;
the method, environment/workload metadata, SampleTime P50/P99 summary and
limitations are recorded in
[`docs/benchmark/recovery.md`](docs/benchmark/recovery.md). These are
component observations, not durable acknowledgement, recovery-time or
production throughput claims.

Run the Phase 6 network component/loopback baseline with Java 21:

```powershell
mvn -pl benchmark -am -DskipTests package
& 'E:\Java\microsoft-jdk-21\bin\java.exe' -jar `
  benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar `
  NetworkBenchmark -wi 1 -i 2 -f 1 -t 1 `
  -rff benchmark-results/phase6-network-full-jdk21.csv -rf csv
```

This measures fixed Submit/Cancel codec vectors and a sequential loopback
request-to-complete-result round trip. Results are local-host component
evidence, not durable ACK, concurrent-client, Internet or Product Release
claims; see [`network.md`](docs/benchmark/network.md).

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
