# ADR-0017: System Qualification, Performance Characterization and Long-Run Reliability

## Status

**Accepted — Human Phase 9 Blueprint Approval, 2026-08-23**

Phase 9 is authorized to add qualification tooling, benchmarks, profiling and
evidence documentation around the frozen `v0.7.0-engineering-baseline`.
Production optimization, Product Release and Phase 10 remain unauthorized.

## Context

`v0.7.0-engineering-baseline` contains the deterministic matching core, bounded
pipeline, Command WAL, Protocol v1 gateway, WAL-before-execute durable path and
Snapshot-plus-WAL-tail online bootstrap. Existing evidence is primarily unit,
integration, deterministic replay and short component/local-host benchmark
evidence. Long-run operation, repeated restart/recovery, process termination,
resource stability and a reproducible full-path performance profile remain to
be characterized.

## Decisions

### D1 — Phase 9 is a qualification phase

Phase 9 adds verification harnesses, deterministic workloads, benchmarks,
profiles and reports. It does not change trading, persistence, protocol,
recovery or runtime semantics and does not grant Product Release authority.

### D2 — v0.7.0 runtime semantics remain frozen

Domain, OrderBook, MatchingEngine, Pipeline, Protocol v1, WAL v1, Snapshot v1,
recovery modes, single-session/one-in-flight behavior, `SYNC_EACH_APPEND`,
WAL-before-execute and listener-last handoff remain unchanged. Existing
`src/main/java/**` is frozen for this Phase.

### D3 — Qualification uses public system boundaries

Full-path evidence uses a Protocol v1 TCP client and the real
`RecoverableDurableMatchingEngineTcpServer`. Qualification must not bypass the
Network, WAL, Pipeline or recovery handoff. Component benchmarks remain
explicitly labeled as component evidence.

### D4 — Workloads are fixed and versioned

`QualificationWorkloadV1` uses a recorded seed and deterministic command
generator. It covers lifecycle outcomes, crossing multi-match and resting
depth. Each run records counts, WAL/Snapshot inventory and final checkpoint,
transcript and public-probe digests. Workload semantics or version changes
require an Exception Gate.

### D5 — Quick and Full evidence lanes are separate

The CI lane is bounded at 10,000 commands and three restart cycles. The Full
Qualification lane is an explicit run requiring both at least 60 minutes and
at least 1,000,000 accepted commands. Quick evidence cannot substitute for the
Full lane.

### D6 — Long-run success is deterministic and fail-closed

The Full lane requires no unexpected terminal state, timeout, correlation or
sequence mismatch, unexplained exception, WAL scan failure or recovery digest
mismatch. PURE_WAL and SNAPSHOT_THEN_WAL must converge after each planned
recovery.

### D7 — Resource stability is bounded evidence

Qualification records owned threads, lock reacquisition, listener rebinding,
temporary files, WAL/Snapshot inventory, JFR and GC samples. A gross-retention
guard compares natural post-GC first and last quartiles. It detects obvious
retention but does not claim proof of absence of leaks.

### D8 — Restart and termination campaigns are explicit

The Full campaign includes 20 graceful restart cycles and 10 child-process
forced terminations at an acknowledged response boundary. An unacknowledged
termination remains ambiguous. Process termination is not hardware power-loss
evidence.

### D9 — Recovery convergence is checked per cycle

Each recovery cycle must converge WAL end, recovered engine sequence, WAL
writer sequence and coordinator sequence, together with checkpoint digest,
Snapshot prefix/tail, TradeId, EventSequence and public probe.

### D10 — Performance is characterization only

The live mode remains `SYNC_EACH_APPEND`; `BUFFERED` is a component-only
comparison. The benchmark separates steady-state response latency from
restart-to-ready latency and covers the approved deterministic workloads and
WAL segment sizes.

### D11 — Benchmark and profile methodology is fixed

Full JMH evidence uses three forks, five two-second warmups and five
five-second measurements with one thread. It records throughput, P50/P95/P99/
P999/max, samples, allocation, GC and complete host/JVM/storage metadata.
JFR is required; async-profiler is optional.

### D12 — Results never authorize optimization

Any production optimization requires a separate baseline/profile/hypothesis
cycle, Optimization ADR, Human approval or Exception Gate, comparable
before/after evidence and an explicit keep/revert decision.

### D13 — Failed runs remain evidence

Failed forks, outliers, crashes and timeouts may not be deleted, hidden or
re-run until a passing result is obtained. Thresholds and workload parameters
cannot change after seeing a result.

### D14 — Every Full run has a qualification manifest

The manifest records run ID, Git SHA, baseline tag, workload/version/seed,
environment, counts, inventories, restart/termination counts, digests,
resource samples, artifact hashes, result and limitations. Raw artifacts stay
local or in CI artifacts; summaries and hashes are committed.

### D15 — No new critical dependency

Phase 9 uses Java 21/JDK tools, JUnit 5, existing JMH, Netty, Disruptor, JFR,
GC logging, Maven and GitHub Actions. A new dependency is an Exception Gate.

### D16 — Claims remain bounded

Phase 9 may claim reproducible qualification on the recorded host, JDK,
filesystem and fixed single-session workload. It may not claim Production
Ready, SLA/RTO, guaranteed P99/P999, hardware power-loss safety, exactly-once,
HA, capacity guarantees or Product Release.

## Scope Boundary

Authorized work is limited to the Phase 9 Blueprint and TASK-035 through
TASK-040. Existing production source, Protocol/WAL/Snapshot formats, recovery
authority and baseline tags remain immutable. `.vscode/` is unrelated user
state and remains untouched.

## Consequences

Phase 9 produces stronger, reproducible engineering evidence without
conflating local qualification with production readiness. The cost is that
full qualification is an explicit long-running campaign and optimization is
deferred until evidence identifies a bounded hypothesis.

## Exception Gate

Stop for Human review on production-source/API changes, semantic or format
changes, new dependencies, changed workload/thresholds, omitted failures,
production optimization, new sessions/producers/queues/ownership models,
quick/full evidence substitution, or any Product Release/merge/tag action.

## Approval Record

| Date | Reviewer | Decision | Notes |
| --- | --- | --- | --- |
| 2026-08-23 | Human Developer | Approved | D1-D16 approved. TASK-20260823-035 through TASK-20260823-040 authorized in strict dependency order. Production optimization, merge, `v0.8.0-engineering-baseline`, Phase 10 and Product Release remain unauthorized. |
