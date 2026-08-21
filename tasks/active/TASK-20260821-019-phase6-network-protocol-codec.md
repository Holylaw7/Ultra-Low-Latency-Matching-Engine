# Task Plan — TASK-20260821-019

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260821-019` |
| Title | Phase 6 Network Dependency, Protocol Contracts and Codec |
| Status | `Proposed` |
| Owner | Human Developer |
| Implementer | Codex |
| Created / Updated | `2026-08-21` |
| Related Phase | Phase 6 — Binary Network Protocol and Single-Session Gateway |
| Related ADR | [`ADR-0014`](../../docs/adr/ADR-0014-network-protocol-and-single-session-gateway.md) |
| Phase Blueprint | [`PHASE-6`](../blueprints/PHASE-6-network-protocol-blueprint.md) |
| Authorization Mode | Blueprint |
| Current Stage | ADR / Decision |
| Next Gate | Human Phase 6 Blueprint Approval |
| Branch | `feature/phase6-network-protocol` after approval |
| Baseline HEAD | approved Phase 6 proposal commit based on `2cf34b5` |
| Remote / CI | `origin` / Pending |

## 2. Background

No network dependency or wire contract exists. ADR-0014 freezes the exact
big-endian protocol v1 and isolates Netty from the matching/persistence core.

## 3. Goal

Add the pinned Netty modules, project-owned protocol values and strict framing,
request decoding and response encoding with exact golden vectors.

## 4. Non-Goals

- no bound socket, server lifecycle or pipeline publication;
- no result-handler integration;
- no live WAL, Snapshot, Recovery, TLS or native transport;
- no benchmark or production optimization.

## 5. Requirements and Acceptance Criteria

- [ ] use `io.netty:netty-bom:4.2.17.Final` and only transport/codec modules;
- [ ] implement the exact ADR-0014 header, type codes, lengths and field codes;
- [ ] reject invalid magic/version/type/flags/reserved/length/numeric values;
- [ ] decode SubmitLimit/Cancel values without assigning Command Sequence;
- [ ] encode CommandResult, ordered MatchResult and Error response frames;
- [ ] fragmented/coalesced frames decode deterministically;
- [ ] Netty reference-counted buffers are released by normal pipeline ownership;
- [ ] no frozen production file changes.

## 6. Current Implementation and Scope

### In Scope

- `pom.xml`, `core/pom.xml`;
- new `network.protocol/**` project-owned values/constants;
- new `network.netty.codec/**` framing/codec implementation;
- focused golden-byte and `EmbeddedChannel` tests.

### Out of Scope

Everything in Tasks 020-023 and every Blueprint non-goal.

## 7. Design Proposal

Use immutable request/response records without Netty types. Keep ByteBuf and
ChannelHandler subclasses package-local under `network.netty.codec`. Decode
one complete length-framed message, validate every byte-level invariant, then
produce a project-owned request. Encode bounded response frames independently.

| Alternative | Advantages | Risks | Result |
| --- | --- | --- | --- |
| Netty BOM + modules | consistent versions/minimal scope | several transitive jars | selected |
| `netty-all` | simple declaration | broad dependency surface | rejected |
| manual NIO codec | no dependency | duplicates framing/ownership machinery | rejected |

### ADR Linkage

| Field | Value |
| --- | --- |
| ADR | ADR-0014 |
| Status | Proposed |
| Decision Summary | D2-D4 and D6 exact dependency/protocol/codec rules |
| Scope Boundary | codec only; no server or runtime integration |

### Blueprint Linkage

| Field | Value |
| --- | --- |
| Blueprint | PHASE-6 network protocol Blueprint |
| Blueprint Status | Proposed |
| Authorized Task / Stages | TASK-019 only after Human approval |
| Exception Gates | any byte/layout/code/version/dependency change |

### Architecture Impact

- [x] ADR required
- [x] Human architecture decision required

## 8. Planned File Changes

| File or Directory | Change | Reason |
| --- | --- | --- |
| `pom.xml`, `core/pom.xml` | pinned Netty BOM/modules | reproducible dependency |
| `src/main/java/.../network/protocol/**` | constants and value messages | project-owned contract |
| `src/main/java/.../network/netty/codec/**` | strict codecs | Netty adapter |
| `src/test/java/.../network/**` | golden/framing tests | byte-level evidence |

## 9. Test Plan

- Unit: every request/response value and exact golden bytes.
- Integration: `EmbeddedChannel` fragmentation/coalescing and reference counts.
- Failure: each invalid header, length, code, flag, reserved and numeric field.
- Determinism: repeated equal values produce byte-identical frames.

## 10. Benchmark and Profile Plan

Not applicable; TASK-023 owns evidence.

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| protocol ambiguity | incompatible clients | exact offsets/golden vectors |
| ByteBuf leak | memory growth | EmbeddedChannel/ref-count tests |
| dependency sprawl | larger attack surface | BOM + two modules only |

## 12. Rollback Plan

Revert new packages and dependency entries. No server, persisted network data or
existing API depends on them at this stage.

## 13. Verification Commands

```text
mvn -pl core -am -Dtest=*Network* test
mvn verify
git diff --check
git diff --name-only v0.4.0-engineering-baseline...HEAD -- <frozen paths>
```

## 14. Git Plan

```text
build(network): add pinned netty modules
feat(network): add binary protocol codec
```

Push each logical commit after gates; exact-SHA CI must pass before TASK-020.

## 15. Approval Record

| Date | Reviewer | Stage | Decision | Constraints / Notes |
| --- | --- | --- | --- | --- |
| 2026-08-21 | Human Developer | Proposal authorization | Proposal only | no implementation |
|  | Human Developer | Phase Blueprint Approval | Pending | D1-D10/TASK-019..023 |

## 16. Phase Reports and Approval Gates

| Stage | Report | Status | Next Gate | Authorization |
| --- | --- | --- | --- | --- |
| ADR / Task Approval | Phase 6 proposal report | Pending | Human Blueprint Approval | Pending |
| Implementation | cumulative Phase 6 report | Pending | verification | Blueprint |
| Verification | cumulative report | Pending | exact-SHA CI | Blueprint |
| Benchmark / Profile | Not applicable | N/A | documentation | Blueprint |
| Completion | cumulative report | Pending | TASK-020 / Exception Gate | Blueprint |

## 17. Implementation Log

| Date | Status | Summary | Verification |
| --- | --- | --- | --- |
| 2026-08-21 | Proposed | protocol/codec plan prepared | baseline 114 tests PASS |

## 18. Completion Checklist

- [ ] scope and tests complete
- [ ] full build/Checkstyle/diff/frozen audit pass
- [ ] ADR/Blueprint/report synchronized
- [ ] logical commits pushed and exact-SHA CI recorded
- [ ] no Exception Gate
