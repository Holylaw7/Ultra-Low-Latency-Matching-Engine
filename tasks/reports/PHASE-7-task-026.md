# Phase 7 — TASK-20260822-026 / Durable Netty Composition

## Executive Status

| Item | Status |
| --- | --- |
| Phase | Phase 7 — Live Durable Command Pipeline Integration |
| Task | `TASK-20260822-026` — Durable Netty Composition |
| Authorization | Human Phase Blueprint Approval; TASK-025 Evidence Gate passed |
| Scope | New `network/netty/durable/**` production and test files |
| Implementation | Complete for the authorized opt-in composition stage |
| Branch | `feature/phase7-live-durable-command-pipeline` |
| HEAD at final implementation evidence | `a978fe7` — `test(phase7): stabilize durable server shutdown` |
| Commit | `9aef2fe` implementation + `a978fe7` test stabilization |
| Remote | `origin` — `git@github.com:Holylaw7/Ultra-Low-Latency-Matching-Engine.git` |
| Push | PASS — `origin/feature/phase7-live-durable-command-pipeline` contains `a978fe7` |
| CI | PASS — exact-SHA run [32565087793](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32565087793) |
| Working tree | Documentation synchronization is pending; pre-existing `.vscode/` remains untouched |
| Focused evidence | PASS — 2 tests |
| Regression evidence | PASS — `mvn verify`, 146 core tests |
| Checkstyle | PASS — 0 violations |
| Frozen-path audit | PASS — zero changes under Domain/OrderBook/Engine/WAL/Recovery/Pipeline/Protocol |
| Exception Gate | Not triggered |
| Next gate | TASK-027 Evidence Gate |

## Delivered

- Added `DurableNetworkConfiguration` as an additive transport/durable
  composition boundary. Live mode remains `SYNC_EACH_APPEND` and the configured
  WAL directory must be empty at startup.
- Added `DurableMatchingEngineTcpServer` with one active session, one in-flight
  request, Protocol v1 fragmentation/coalescing handling through the frozen
  codecs, EventLoop result scheduling and bounded shutdown.
- Composed `CommandWalWriter.append`, `MatchingEnginePipeline.tryPublish` and
  the TASK-025 coordinator without changing any existing WAL, Pipeline, Engine,
  Gateway or Protocol production file.
- Rejected non-empty WAL startup instead of replaying or switching into online
  recovery. Durable append followed by pipeline `FULL` remains terminal through
  the coordinator and is never reported as retryable `BACKPRESSURE_FULL`.
- Added loopback protocol-result and fresh-WAL persistence evidence plus a
  non-empty-WAL startup rejection test.

## Verification Evidence

```text
mvn -pl core '-Dtest=com.ultralatency.matching.network.netty.durable.DurableMatchingEngineTcpServerTest' test
  BUILD SUCCESS; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0

mvn verify
  BUILD SUCCESS; core Tests run: 146, Failures: 0, Errors: 0, Skipped: 0
  Checkstyle: 0 violations

git diff --check
  PASS
```

The frozen-path audit found no changed files under the Phase 2–6 production
boundaries or the frozen Protocol/WAL/Pipeline paths. The first CI attempt
(`32564961239`) exposed a Linux shutdown-order race in the loopback test; the
test was stabilized without changing production code in `a978fe7`, and the
exact-SHA CI run above passed.

## Architecture / ADR Alignment

- Protocol v1 bytes and existing Phase 6 Gateway behavior remain unchanged.
- The durable server is opt-in and starts only from an empty WAL directory;
  non-empty WAL state is rejected rather than replayed.
- Request ID, coordinator command sequence, ring sequence, EventSequence,
  TradeId and WAL physical positions remain separate identity domains.
- A durable append completes before Pipeline publication. A durable-then-FULL
  result is terminal and never becomes a retryable protocol response.
- Results are scheduled back to the owning Netty EventLoop before response
  encoding/writing. Disconnect and outbound write failure transition the
  durable server to a terminal state; no durable client acknowledgement or
  exactly-once claim is made.

## Risks and Limitations

- The live server rejects non-empty WAL directories because online replay and
  restart handoff are deferred to a future Blueprint.
- Dynamic fault injection for every outbound failure window is deferred to
  TASK-027; no production-only seam was added for TASK-026.
- This Task provides component/loopback composition only. It does not add
  reconnect, deduplication, multiple sessions, Snapshot, online Recovery,
  network durability acknowledgement or Product Release semantics.

## Next Stage

TASK-026 Evidence Gate has passed. TASK-027 is the next authorized
dependency-ordered Task; TASK-028 remains authorized conditionally on its
Evidence Gate. Phase Closure,
merge to `master`, `v0.6.0-engineering-baseline`, Snapshot, online Recovery,
reconnect/deduplication and Product Release remain unauthorized.

## Gate Status

`TASK-026 Evidence Gate PASS — TASK-027 is the next authorized Task.`
