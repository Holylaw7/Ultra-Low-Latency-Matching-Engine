# Task Plan — TASK-20260823-036

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260823-036` |
| Title | Deterministic public-boundary end-to-end qualification harness |
| Status | `In Progress` |
| Owner | Human Developer |
| Implementer | Main Codex / Luna Max — only writer |
| Created | `2026-08-23` |
| Related Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Related ADR | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) |
| Phase Blueprint | [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md) |
| Authorization Mode | `Blueprint` |
| Current Stage | `Implementation` |
| Next Gate | `TASK-036 Evidence Gate` |
| Branch | `feature/phase9-system-qualification` |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |

## 2. Goal

Add a qualification-only client and runner that drive the real Protocol v1 TCP
server, durable WAL, event pipeline and MatchingEngine through their public
system boundary. The runner must support the bounded Phase 9 quick lane with
three recovery-backed sessions and deterministic transcript/WAL evidence.

## 3. In Scope

- JDK socket Protocol v1 request encoder and response validator;
- immutable public exchange observations;
- real `RecoverableDurableMatchingEngineTcpServer` lifecycle;
- WAL-authoritative recovery between sessions;
- deterministic transcript, checkpoint and public-probe digests;
- 10,000-command / three-session quick smoke test enabled explicitly;
- focused small deterministic integration test;
- bounded qualification workflow and evidence documentation.

## 4. Out of Scope

- production source or API changes;
- direct coordinator, pipeline or MatchingEngine calls from the harness;
- child-process termination and full soak (TASK-037/038);
- JMH/JFR performance work (TASK-039);
- reconnect, deduplication, multiple sessions or request pipelining;
- WAL/Snapshot/Protocol format or recovery semantic changes;
- new dependencies and production optimization.

## 5. Acceptance Criteria

- [ ] Requests are encoded and sent through Protocol v1 TCP frames.
- [ ] Responses are validated for request identity, command sequence, ordering,
  match count and response-frame integrity.
- [ ] Three sessions recover the same WAL-backed runtime without sequence gaps.
- [ ] Persisted WAL commands equal the versioned workload command stream.
- [ ] Repeated runs with the same workload produce identical result digests.
- [ ] Explicit quick lane executes 10,000 commands across three sessions.
- [ ] `mvn verify`, Checkstyle, `git diff --check`, frozen-path audit and exact-
  SHA CI pass.
- [ ] verifier and docs-auditor return PASS before TASK-037 unlock.

## 6. Frozen Boundary

No file under `src/main/java/**`, `src/test/java/**`, `core/pom.xml`, existing
benchmark paths, Protocol v1, WAL v1, Snapshot v1 or recovery semantics may
change. `.vscode/` remains untouched/untracked.

## 7. Verification Commands

```text
mvn -pl qualification -am test
mvn -pl qualification -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dqualification.quick=true -Dtest=QualificationQuickSmokeTest test
mvn verify
git diff --check
git status --short --branch
```

The quick workflow is an explicit bounded lane; it does not replace the later
full 60-minute / 1,000,000-command qualification lane.

## 8. Gate

TASK-037 remains locked until this task's focused tests, quick smoke, full
verification, exact-SHA CI and read-only audits all pass. Phase 9 Closure,
merge and `v0.8.0-engineering-baseline` remain unauthorized.
