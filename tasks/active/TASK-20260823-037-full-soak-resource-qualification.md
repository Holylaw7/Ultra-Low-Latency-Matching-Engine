# Task Plan — TASK-20260823-037

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260823-037` |
| Title | Full soak and resource lifecycle qualification |
| Status | `In Progress` |
| Owner | Human Developer |
| Implementer | Main Codex / Luna Max — only writer |
| Created | `2026-08-23` |
| Related Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Related ADR | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) |
| Phase Blueprint | [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md) |
| Authorization Mode | `Blueprint` |
| Current Stage | `Implementation / Evidence Gate` |
| Next Gate | TASK-038 after Evidence Gate PASS |
| Branch | `feature/phase9-system-qualification` |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |

## 2. Goal

Add an explicit Full Qualification lane that drives the frozen runtime through
the public Protocol v1 TCP boundary and records one immutable soak/resource
evidence unit. The full lane requires both the approved 60-minute duration and
1,000,000 accepted commands; the short test lane is harness evidence only and
must never be reported as Full Qualification.

## 3. In Scope

- immutable Full/Test lane configuration and threshold validation;
- public-boundary full-run orchestration over the real recoverable server;
- JFR and non-invasive GC/thread/heap resource sampling;
- natural post-GC heap guard without `System.gc()`;
- listener rebind, recovery lease and WAL inventory evidence;
- raw JFR, resource CSV, manifest and failure artifacts with SHA-256 hashes;
- focused short-lane tests for the full-run composition and threshold guards.

## 4. Out of Scope

- production source, API, format or dependency changes;
- restart/forced-termination campaign (TASK-038);
- JMH/profile optimization work (TASK-039);
- retries, filtering, deletion of failed evidence or threshold changes after a run;
- direct coordinator, pipeline or MatchingEngine calls from the harness;
- reconnect, deduplication, multiple sessions or request pipelining;
- WAL/Snapshot/Protocol/recovery semantic changes;
- Phase 9 Closure, merge, tag or Product Release.

## 5. Acceptance Criteria

- [ ] Full configuration rejects fewer than 1,000,000 commands, less than 60
  minutes or fewer than five natural post-GC samples.
- [ ] Full runner uses only the public Protocol v1 boundary and records every
  accepted command/result without retry or filtering.
- [ ] Full qualification requires both duration and command-count thresholds;
  the short lane is explicitly non-full evidence.
- [ ] Resource evidence records owned runtime threads, listener/lease state,
  temporary-file/inventory checks and natural post-GC heap observations.
- [ ] JFR, resource CSV, manifest and failure artifacts are preserved and
  hashed; environment and immutable run configuration are recorded.
- [ ] Focused qualification tests pass without reflection, sleep-based
  correctness or production-only test seams.
- [ ] `mvn verify`, Checkstyle, `git diff --check`, frozen-path audit and
  exact-SHA CI pass.
- [ ] verifier and docs-auditor return PASS before TASK-038 unlocks.

## 6. Frozen Boundary

No file under `src/main/java/**`, `src/test/java/**`, `core/pom.xml`, existing
benchmark paths, Protocol v1, WAL v1, Snapshot v1 or recovery semantics may
change. `.vscode/` remains untouched/untracked.

## 7. Verification Commands

```text
mvn -pl qualification -am test
mvn verify
git diff --check
git diff --name-only 87abbc1..HEAD -- src/main/java src/test/java core/pom.xml
git status --short --branch
```

The short `QualificationFullRunnerTest` exercises composition quickly. A real
Full lane run remains a manual evidence unit and must not be replaced by the
short test.

## 8. Evidence Gate

Evidence is incomplete until implementation, focused tests, full regression,
resource/artifact semantics, frozen-path audit, read-only reviewer PASS and
exact-SHA CI all agree. Any production defect, unexpected terminal state,
timeout, digest mismatch, resource leak or configuration drift is retained as
failure evidence and escalated through the Exception Gate; it is not repaired
inside this task by changing frozen production code.
