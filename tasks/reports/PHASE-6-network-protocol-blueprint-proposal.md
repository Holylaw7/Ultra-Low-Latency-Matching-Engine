# Phase 6 Binary Network Protocol and Single-Session Gateway — Blueprint Proposal

## Status Dashboard

| Field | Value |
| --- | --- |
| Phase | Phase 6 — Binary Network Protocol and Single-Session Gateway |
| Stage | Discovery / Complete Blueprint Proposal |
| Result | `Prepared — Pending Human Blueprint Approval` |
| Production Changes | None |
| Tests | Baseline `mvn verify` PASS — 114 tests / 0 failures |
| Build | Maven reactor 3/3 SUCCESS; Checkstyle 0 violations |
| CI | Pending proposal commit/push |
| Commit | Pending |
| Branch | `docs/phase6-network-protocol-blueprint` |
| Next Gate | Human Phase 6 Blueprint Approval |

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
| [`TASK-019`](../active/TASK-20260821-019-phase6-network-protocol-codec.md) | dependency, protocol contracts and codec | Proposed |
| [`TASK-020`](../active/TASK-20260821-020-phase6-pipeline-failure-observer.md) | additive terminal pipeline observer | Proposed |
| [`TASK-021`](../active/TASK-20260821-021-phase6-netty-gateway.md) | single-session TCP gateway | Proposed |
| [`TASK-022`](../active/TASK-20260821-022-phase6-network-verification.md) | determinism/system/failure evidence | Proposed |
| [`TASK-023`](../active/TASK-20260821-023-phase6-network-benchmark-docs.md) | benchmark, documentation and Closure preparation | Proposed |

The complete scope, wire layout, acceptance criteria, test/benchmark plan,
risks, rollback, Git strategy and Closure plan are in the
[`Phase 6 Blueprint`](../blueprints/PHASE-6-network-protocol-blueprint.md).

## Frozen Boundary

This proposal changes documentation only. Implementation would retain zero
changes to Domain, OrderBook, Engine, WAL and Recovery production files. The
only proposed existing Pipeline change is the explicitly additive failure
observer; existing constructor behavior remains compatible.

`v0.4.0-engineering-baseline` remains immutable. `.vscode/` remains unrelated,
untracked and untouched.

## Dependency Evidence

Discovery selected Netty `4.2.17.Final` because the official Netty download
page identifies it as the stable recommended release on `2026-08-21`. The 4.2
migration guide recommends BOM-based dependency control and explicit allocator
choice; the proposal uses the current non-deprecated NIO handler factory API.

No dependency has been added during this proposal. Dependency implementation
is TASK-019 and remains unauthorized pending Human approval.

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
- proposal commit / exact-SHA CI: pending.

## Exception Gate Summary

Execution must stop for protocol byte/version changes, multiple sessions or
pipelining, identity-domain merging, live WAL/durable ACK, frozen-file/API
changes, broader pipeline redesign, native/TLS/new dependencies, weakened
ordering/failure assertions, production test seams, performance-driven semantic
changes, Snapshot/Recovery, Release or destructive Git actions.

## Human Decision Record

The Human Developer authorized entry into Phase 6 Blueprint Proposal. This
authorizes Discovery, ADR-0014 draft, TASK-019 through TASK-023 proposals and
the complete Blueprint only.

```text
Phase 6 Blueprint: Proposed
ADR-0014: Proposed
Implementation: Not Authorized
Merge / v0.5.0 tag: Not Authorized
Next Gate: Human Phase 6 Blueprint Approval
```
