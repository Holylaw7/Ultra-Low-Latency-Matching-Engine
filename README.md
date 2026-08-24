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

Phase 10 — Release-Candidate Runtime Assembly is the active approved phase.
Phase 9 remains completed, approved and frozen at
`v0.8.0-engineering-baseline` from merge `ef73f60`; Master CI is
`32711512036` and tag CI is `32711649980`.

Phase 9 Blueprint is approved. [`ADR-0017`](docs/adr/ADR-0017-system-qualification-performance-reliability.md),
the [Complete Phase 9 Blueprint](tasks/blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md)
and TASK-035 through TASK-040 are authorized in strict dependency order.
TASK-035 qualification foundation is complete at `22d13fe` with exact-SHA CI
`32625554518` PASS; TASK-036 Evidence Gate is complete after remediation at
`f90e42c` with standard CI `32627744868` and quick-lane CI `32627744878` PASS;
TASK-037 is completed and archived on the `feature/phase9-system-qualification` branch;
its Limited Qualification-Only Remediation Evidence Gate is PASS at `c420313`
with standard CI `32645549709` and Quick Lane `32645549694` PASS. The approved
v2 `MEMORY_STEADY_STATE_V1` Full Campaign then completed as exactly two
independent qualifying runs: A' (`1,784,601` accepted commands, `3,619,093 ms`,
22 natural samples, chronological heap guard PASS) and B' (`1,741,681`,
`3,620,413 ms`, 22 natural samples, heap guard PASS). Their immutable campaign
summary is at
`qualification/qualification-results-v2-campaign-20260824/qualification-campaign-summary-v1.txt`
with SHA-256
`5bf1b84b30226807d79f5a0a4950ae649c3a72a860d6d6b13edd9fa715e24112` and records
`campaign.result=true` with 44 cumulative natural samples. Historical Run A/B
remain preserved/non-qualifying. Sol High Final Campaign Closure Review and
Human TASK-037 Evidence / Closure Approval are complete. Phase 9 is an
engineering qualification phase: production source, runtime semantics and
existing formats remain frozen; TASK-038 Evidence Gate is now complete. TASK-
039 is completed and archived with its full JMH/JFR evidence, read-only
reviewer PASS and final status synchronization. TASK-040 is completed,
archived and its Evidence Gate PASS is accepted. The current Closure
Input is `8e5d39d`, Standard CI `32709188522` PASS and Qualification Quick
Lane `32709188327` PASS. Sol High review and Human Phase 9 Closure Approval are
complete. Phase 9 merge `ef73f60` passed Master CI `32711512036`; the
`v0.8.0-engineering-baseline` tag passed Tag CI `32711649980`.

Phase 10 Discovery is complete and Human Blueprint Approval is recorded.
[`ADR-0018`](docs/adr/ADR-0018-release-candidate-runtime-boundary.md) and the
[Complete Phase 10 Blueprint](tasks/blueprints/PHASE-10-release-candidate-runtime-assembly-blueprint.md)
authorize Release-Candidate Runtime Assembly through TASK-041 to TASK-046.
TASK-041 runtime contracts and lifecycle/status boundaries are implemented at
`cc9a957`; Standard CI `32718394177` and Qualification Quick Lane
`32718394269` both pass. TASK-042 composition/runtime assembly is complete at
`1eba2c5`; Standard CI `32720292382` and Qualification Quick Lane
`32720292393` both pass. TASK-043 strict configuration and reproducible
packaging is complete at `247d526`; Standard CI `32724123762` and
Qualification Quick Lane `32724123745` both pass. TASK-044 bounded health,
readiness and operational status is complete at `c3f0883`; Standard CI
`32726203105` and Qualification Quick Lane `32726203076` both pass. TASK-045
shutdown and terminal-failure hardening is complete at `f024aef`; Standard CI
`32728038236` and Qualification Quick Lane `32728038263` both pass. TASK-046
pre-campaign qualification is complete at `0a96593`; Standard CI
`32730760419` and Qualification Quick Lane `32730760501` both pass. The
packaged Java 21 lifecycle matrix passed 30/30 cycles (10 empty/PURE_WAL, 10
Snapshot-tail and 10 approved post-response forced terminations), with immutable
summary/hash evidence recorded in [`TASK-046 report`](tasks/reports/PHASE-10-task-046.md).
Human then authorized exactly two `RC_ASSEMBLED_RUNTIME_V1` Full Runs. The
assembled runner checkpoint `1a02e66` passed Standard CI `32734798459` and
Qualification Quick Lane `32734798461`; both independent Full Runs passed the
60-minute/1M-command gates, and the immutable campaign summary records `2/2`
qualifying runs, 17 cumulative natural post-GC samples and
`campaign.result=true`. Final latency/profile evidence reconciliation and Sol
High delta-only Closure Review remain pending. The qualification-only
characterization remediation then produced 30/30 empty-WAL and 30/30
Snapshot-tail lifecycle samples, raw Protocol response and management samples,
two fixed 10-minute management-idle/STATUS@1Hz trials, 62 JFR files and 62
resource CSV files. Its immutable summary is
`9d7bbfad3fe1464b776b34f714054528989e02ff132ddb6b448ddaa02bf9e888` under
`qualification-results/phase10-characterization/`; it records no >10%
throughput or response-P99 regression trigger. Protocol v1, WAL v1, Snapshot v1,
matching, durability and single-session semantics remain frozen; merge,
`v0.9.0-rc.1` and Product Release remain unauthorized.

Phase 8 remains documented by [`ADR-0016`](docs/adr/ADR-0016-snapshot-checkpoint-and-online-recovery-bootstrap.md),
the [Complete Phase 8 Blueprint](tasks/blueprints/PHASE-8-snapshot-checkpoint-and-online-recovery-blueprint.md)
and TASK-029 through TASK-034 are authorized in dependency order. TASK-029
canonical checkpoint export/restore is complete at `66fc9d2` with exact-SHA CI
`32577713667` PASS; TASK-030 Snapshot v1 codec/store is complete at `6907391`
with exact-SHA CI `32579065372` PASS; TASK-031 offline recovery planner/replay
is complete at `eaed8b8` with exact-SHA CI `32580018903` PASS; TASK-032
recoverable live handoff is complete at `22568e6` with exact-SHA CI
`32613235358` PASS; TASK-033 verification is complete at `eff5955` with exact-SHA
CI `32614610701` PASS; TASK-034 recovery benchmark and Closure Proposal
preparation is complete at `9835624` with exact-SHA CI `32616029460` PASS;
verifier, benchmark-reviewer and docs-auditor all PASS. The technical Closure
input is `c59d7c0` with exact-SHA CI `32616802595` PASS and a 195-test,
zero-failure, Checkstyle-zero regression.
The benchmark matrix and limitations are recorded in
[`PHASE-8-task-034.md`](tasks/reports/PHASE-8-task-034.md) and
[`recovery.md`](docs/benchmark/recovery.md). The proposal keeps WAL as the
sole authority, compares pure-WAL recovery with a derived Snapshot-plus-tail
path, and makes listener-last recovery a required invariant. Closure and Sol
High delta review are approved. Human Phase 8 Closure Approval was followed by
merge `87abbc1`, Master CI `32622722649`, and annotated
`v0.7.0-engineering-baseline` tag CI `32622757607`. TASK-029 through TASK-034
are archived and Phase 8 is frozen. Phase 9 qualification is now authorized in
strict dependency order; production optimization and Product Release remain
separately governed.

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

The approved Phase 7 boundary adds the WAL-before-pipeline integration layer as
a dependency-ordered live durable foundation. TASK-024 through TASK-028 are
complete with exact-SHA evidence; TASK-028 has its four-boundary Java 21 JMH
benchmark and component claim summary at `9fed6b2` / CI `32574274905`. Human
Phase 7 Closure is approved and the resulting `v0.6.0-engineering-baseline`
is frozen at merge `6473365`. No online Recovery, reconnect, deduplication,
multi-session support, Phase 8 implementation, Product Release or
production-readiness claim is authorized.

Run the Phase 8 recovery component benchmark with Java 21:

```powershell
mvn -pl benchmark -am -DskipTests package
& 'E:\Java\microsoft-jdk-21\bin\java.exe' -jar `
  benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar `
  RecoveryBenchmark -wi 1 -i 1 -f 1 -w 1s -r 1s -t 1 -foe true `
  -rf json -rff benchmark-results/phase8-recovery-full.json
```

This measures pure-WAL replay, Snapshot decode/restore, Snapshot-tail replay,
offline Snapshot creation and bootstrap-to-listener as separate component
boundaries over deterministic 256/1,024-command and 4,128/65,536-byte segment
fixtures. The raw JSON is local and ignored. The recorded P50/P95/P99/P999
values, host/JVM metadata and claim limits are in
[`PHASE-8-task-034.md`](tasks/reports/PHASE-8-task-034.md). These are
local-host engineering observations, not production RTO, online recovery,
power-loss, durable-ACK or Product Release claims.

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

Run the Phase 7 live durable component/loopback baseline with Java 21:

```powershell
mvn -pl benchmark -am -DskipTests package
& 'E:\Java\microsoft-jdk-21\bin\java.exe' -jar `
  benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar `
  'DurablePipelineBenchmark.*' -wi 1 -i 2 -f 1 -t 1 `
  -w 1s -r 1s -foe true -rf json `
  -rff benchmark-results/phase7-durable-full.json
```

This keeps WAL append/force, append-plus-publish, local result encoding and
one-in-flight alternating Submit/Cancel loopback separate. The committed
summary records CPU, storage, JDK/JVM/GC, Netty allocator, workload, segment
settings, warmup/forks and P50/P95/P99/P999. It is component/local-host
evidence only; it is not a durable ACK, power-loss, online Recovery,
concurrent-client or Product Release claim. See
[`durable-pipeline.md`](docs/benchmark/durable-pipeline.md).

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
