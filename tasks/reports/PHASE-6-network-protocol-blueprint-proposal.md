# Phase 6 Binary Network Protocol and Single-Session Gateway — Blueprint Proposal

## Status Dashboard

| Field | Value |
| --- | --- |
| Phase | Phase 6 — Binary Network Protocol and Single-Session Gateway |
| Stage | Discovery / Complete Blueprint Proposal |
| Result | `Approved — Implementation Authorized in Dependency Order` |
| Production Changes | None |
| Tests | Baseline `mvn verify` PASS — 114 tests / 0 failures |
| Build | Maven reactor 3/3 SUCCESS; Checkstyle 0 violations |
| CI | [32485900404](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32485900404) PASS |
| Commit | `ecf0c27` |
| Branch | `docs/phase6-network-protocol-blueprint` |
| Next Gate | Human Phase 6 Closure Review |

## Discovery Outcome

The accepted roadmap places Network Adapter / Binary Protocol after the Phase 5
WAL/replay foundation and before Snapshot/online Recovery. Phase 6 should add a
real TCP system boundary while keeping transport independent from persistence
and recovery authority.

The proposed first boundary is deliberately bounded:

```text
one TCP client
    -> strict binary protocol v1
    -> one Netty worker / one request in flight
    -> gateway-owned Command Sequence
    -> frozen SPSC pipeline
    -> frozen MatchingEngine
    -> ordered result frames
```

Multi-client admission would require sequence arbitration or a new MPSC layer;
it is deferred rather than silently mixed into this baseline.

## Proposed Decisions

| Decision | Proposal |
| --- | --- |
| D1 | Network adapter remains separate from WAL/recovery authority |
| D2 | Netty 4.2.17.Final BOM with transport+codec, Java NIO, pooled allocator |
| D3 | exact big-endian protocol v1 and bounded 16-byte header |
| D4 | exact Submit/Cancel layouts; gateway assigns Command Sequence |
| D5 | one active session, one request in flight, one pipeline producer |
| D6 | bounded CommandResult/MatchResult/Error frames with order significance |
| D7 | local write completion is not durable or client-receipt ACK |
| D8 | fail-stop lifecycle and additive at-most-once pipeline failure observer |
| D9 | loopback default/strict validation; security features deferred |
| D10 | codec and sequential loopback evidence only; no production claim |

The durable draft is
[`ADR-0014`](../../docs/adr/ADR-0014-network-protocol-and-single-session-gateway.md).

## Task Breakdown

| Task | Purpose | Status |
| --- | --- | --- |
| [`TASK-019`](../active/TASK-20260821-019-phase6-network-protocol-codec.md) | dependency, protocol contracts and codec | Completed / Evidence PASS |
| [`TASK-020`](../active/TASK-20260821-020-phase6-pipeline-failure-observer.md) | additive terminal pipeline observer | Completed / Evidence PASS |
| [`TASK-021`](../active/TASK-20260821-021-phase6-netty-gateway.md) | single-session TCP gateway | Completed / Evidence PASS |
| [`TASK-022`](../active/TASK-20260821-022-phase6-network-verification.md) | protocol/system/failure evidence | Completed / Evidence PASS |
| [`TASK-023`](../active/TASK-20260821-023-phase6-network-benchmark-docs.md) | benchmark, documentation and Closure preparation | Completed / Evidence PASS |

The complete scope, wire layout, acceptance criteria, test/benchmark plan,
risks, rollback, Git strategy and Closure plan are in the
[`Phase 6 Blueprint`](../blueprints/PHASE-6-network-protocol-blueprint.md).

## Frozen Boundary

This proposal changes documentation only; implementation is now authorized
under the approved Blueprint. The authorized implementation retains zero
changes to Domain, OrderBook, Engine, WAL and Recovery production files. The
only existing Pipeline change allowed is the explicitly additive failure
observer; existing constructor behavior remains compatible.

`v0.4.0-engineering-baseline` remains immutable. `.vscode/` remains unrelated,
untracked and untouched.

## Dependency Evidence

Discovery selected Netty `4.2.17.Final` because the official Netty download
page identifies it as the stable recommended release on `2026-08-21`. The 4.2
migration guide recommends BOM-based dependency control and explicit allocator
choice; the proposal uses the current non-deprecated NIO handler factory API.

No dependency has been added during the proposal stage. Dependency
implementation is TASK-019 and is now authorized by the recorded Human
Blueprint Approval; later Tasks remain conditional on predecessor evidence.

## Evidence and Claim Boundary

The planned evidence can prove strict byte contracts, loopback TCP behavior,
ordering and bounded failure semantics. It cannot prove:

- durable acceptance or replayable live processing;
- client receipt after a local channel write completes;
- reconnect safety, request deduplication or exactly-once semantics;
- concurrent-client scalability;
- TLS/authentication/Internet security;
- Snapshot, online Recovery or production readiness.

## Proposal Verification

- baseline `mvn verify`: PASS;
- tests: 114 passed, 0 failures/errors/skips;
- Checkstyle: 0 violations;
- Maven reactor: 3/3 SUCCESS;
- proposal local Markdown links: PASS across all 11 proposal/status files;
- `git diff --check`: PASS;
- frozen production working-tree changes: zero;
- `.vscode/`: unrelated, untracked and untouched;
- proposal content commit: `ecf0c27`;
- proposal content exact-SHA CI:
  [32485900404](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32485900404)
  PASS.

## Exception Gate Summary

Execution must stop for protocol byte/version changes, multiple sessions or
pipelining, identity-domain merging, live WAL/durable ACK, frozen-file/API
changes, broader pipeline redesign, native/TLS/new dependencies, weakened
ordering/failure assertions, production test seams, performance-driven semantic
changes, Snapshot/Recovery, Release or destructive Git actions.

## Final Implementation Evidence

TASK-019 through TASK-023 completed in dependency order on the approved
implementation branch. The latest benchmark commit is `0c924dd` with exact-SHA
CI [32491817494](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32491817494)
PASS. Documentation, architecture status and the Closure Proposal are
synchronized at final evidence checkpoint `3ca54ad`, which passed exact-SHA CI
[32493384924](https://github.com/Holylaw7/Ultra-Low-Latency-Matching-Engine/actions/runs/32493384924).
The final record explicitly distinguishes implementation-path evidence from
dynamic Gateway fault injection: FULL identity preservation, outbound write
failure terminal handling and pipeline-failure-to-Gateway terminal propagation
were not dynamically fault-injected through a live Gateway test. No
production-only test seam was introduced; this is an accepted Phase 6 baseline
limitation. Phase 6 is stopped at Human Closure Review. Merge, a future
`v0.5.0-engineering-baseline` tag, Phase 7 and Product Release remain
unauthorized.

## Human Decision Record

The Human Developer approved the complete Phase 6 Blueprint and ADR-0014 D1-D10
for dependency-ordered implementation. TASK-019 through TASK-023 have completed
their evidence gates. The Exception Gate and separate Phase Closure approval
remain active.

```text
Phase 6 Blueprint: Approved
ADR-0014: Approved
Implementation: Authorized in dependency order
Current Task: TASK-023 Completed
Phase Closure: Pending Human Review
Merge / v0.5.0 tag: Not Authorized
Next Gate: Human Phase 6 Closure Review
```
