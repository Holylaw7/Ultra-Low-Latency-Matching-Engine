# Task Plan — TASK-20260821-021

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID / Title | `TASK-20260821-021` / Phase 6 Single-Session Netty Gateway |
| Status | `Proposed` |
| Owner / Implementer | Human Developer / Codex |
| Created / Updated | `2026-08-21` |
| Phase / ADR / Blueprint | Phase 6 / ADR-0014 / [`PHASE-6`](../blueprints/PHASE-6-network-protocol-blueprint.md) |
| Authorization Mode | Blueprint |
| Current Stage / Next Gate | ADR / Decision / Human Phase 6 Blueprint Approval |
| Branch / Baseline | `feature/phase6-network-protocol` after approval / approved proposal commit |
| Remote / CI | `origin` / Pending |

## 2. Background

TASK-019 supplies strict protocol codecs and TASK-020 supplies pipeline failure
notification. This Task connects them through a real bounded TCP lifecycle.

## 3. Goal

Implement a loopback-default Netty NIO server that owns one active session,
one request in flight and one existing MatchingEnginePipeline, with gateway-
assigned Sequence and ordered result frames.

## 4. Non-Goals

- no live WAL, reconnect, multi-client, pipelining or deduplication;
- no TLS/auth/native transport;
- no Snapshot/Recovery, benchmark or optimization.

## 5. Requirements and Acceptance Criteria

- [ ] explicit `NEW/RUNNING/DRAINING/STOPPED/FAILED` lifecycle;
- [ ] loopback default and explicit bind/port/watermark/timeout validation;
- [ ] Netty 4.2 NIO groups and explicit pooled allocator;
- [ ] one active channel and one manual-read request in flight;
- [ ] exact-next client request ID begins at 1;
- [ ] candidate Command Sequence advances only on pipeline `ACCEPTED`;
- [ ] `FULL` writes retryable error and advances neither identity;
- [ ] result header and match frames write in exact list order;
- [ ] second client, protocol, disconnect, pipeline and write failures follow
  ADR-0014 fail-stop semantics;
- [ ] all resources close within caller-supplied bounded timeout.

## 6. Current Implementation and Scope

### Current Implementation

There is no socket server. The Phase 4 pipeline is an in-memory facade with one
publisher thread and asynchronous ordered results.

### In Scope

New `network.netty.gateway/**`, gateway-focused tests and wiring to the
approved codec/pipeline APIs.

### Out of Scope

Frozen production paths, existing protocol bytes and all Non-Goals.

## 7. Design Proposal

`MatchingEngineTcpServer` owns Netty boss/worker groups, server/client channel,
one MatchingEnginePipeline and lifecycle. A project-owned immutable
`NetworkConfiguration` supplies bind address and resource bounds. The worker
event-loop thread decodes and publishes; the pipeline result handler schedules
ordered writes back onto that event loop.

The channel starts with manual reads. Acceptance sets one in-flight correlation
record. Only successful complete write clears it and requests another frame.

| Option | Advantages | Risks | Result |
| --- | --- | --- | --- |
| owned single-session server | deterministic boundary | limited concurrency | selected |
| inject arbitrary Channel | easier tests | leaks Netty API | rejected |
| multiple channels | realistic | violates sequence/SPSC | deferred |

### ADR / Blueprint Linkage

| Field | Value |
| --- | --- |
| ADR Status | ADR-0014 Proposed |
| Decision | D1, D2, D5-D9 |
| Blueprint | Proposed; TASK-021 after TASK-020 exact-SHA CI |
| Exception Gates | reconnect/multiple clients/live WAL/API/format changes |

### Architecture Impact

- [x] ADR required
- [x] Human architecture decision required

## 8. Planned File Changes

| Directory | Change |
| --- | --- |
| `src/main/java/.../network/netty/gateway/**` | configuration, state, admission, server lifecycle |
| `src/test/java/.../network/netty/gateway/**` | loopback lifecycle/integration tests |

## 9. Test Plan

- Unit: configuration and lifecycle invalid transitions.
- Integration: real loopback Submit/Cancel/no-match/multi-match.
- Failure: second connection, invalid request ID, FULL fixture, disconnect,
  pipeline callback, outbound failure and bounded shutdown.
- Determinism: equal request streams yield equal ordered response values.
- No sleep, external port, reflection or production-only hook.

## 10. Benchmark and Profile Plan

Not applicable; TASK-023 owns evidence.

## 11. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| resource leak | bounded cleanup/futures in every test |
| cross-thread state race | event-loop ownership + volatile lifecycle only |
| ambiguous disconnect | explicit terminal limitation/no retry claim |
| outbound growth | one in-flight + write watermarks/manual reads |

## 12. Rollback Plan

Remove the new gateway package. Existing codec and in-memory pipeline remain
independently usable; no deployed session/state migration exists.

## 13. Verification Commands

```text
mvn -pl core -am -Dtest=*Network*,*Tcp* test
mvn verify
git diff --check
frozen path audit against v0.4.0-engineering-baseline
```

## 14. Git Plan

`feat(network): add single-session tcp gateway`; push and exact-SHA CI before
TASK-022.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Proposal | Proposal only | no implementation |
|  | Human Developer | Blueprint | Pending | one session/one in-flight only |

## 16. Phase Reports and Approval Gates

| Stage | Report | Status | Next Gate | Authorization |
| --- | --- | --- | --- | --- |
| Decision/Approval | Phase 6 proposal | Pending | Blueprint Approval | Pending |
| Implementation/Verification | cumulative report | Pending | exact-SHA CI | Blueprint |
| Benchmark | Not applicable | N/A | completion | Blueprint |
| Completion | cumulative report | Pending | TASK-022 / Exception Gate | Blueprint |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Proposed | gateway plan prepared | baseline PASS |

## 18. Completion Checklist

- [ ] lifecycle/admission/result/failure evidence complete
- [ ] full build/static/diff/frozen audit pass
- [ ] exact-SHA CI/report synchronized
- [ ] no Exception Gate
