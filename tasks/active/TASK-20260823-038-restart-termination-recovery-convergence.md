# Task Plan — TASK-20260823-038

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260823-038` |
| Title | Restart, forced termination and recovery convergence qualification |
| Status | `Authorized / Next — Not Started` |
| Owner | Human Developer |
| Implementer | Main Codex / Luna Max — only writer |
| Created | `2026-08-23` |
| Related Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Related ADR | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) |
| Phase Blueprint | [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md) |
| Authorization Mode | `Blueprint` |
| Current Stage | `TASK-037 Human Closure Approved; TASK-038 authorized / not started` |
| Next Gate | `TASK-038 Evidence Gate; TASK-039 remains locked` |
| Branch | `feature/phase9-system-qualification` |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Dependency | `TASK-037 Completed / Archived / Human Closure Approved` |

## 2. Goal

Qualify repeated graceful restart and acknowledged-boundary forced-termination
cycles through the public Protocol v1 boundary, validating WAL-authoritative
recovery convergence without changing production runtime semantics.

## 3. In Scope

- qualification-only child-process lifecycle orchestration;
- 20 graceful restart cycles;
- 10 acknowledged-boundary forced-termination cycles;
- per-cycle WAL/Snapshot/recovery convergence checks;
- Command Sequence, TradeId, EventSequence, checkpoint digest and public-probe
  comparison;
- listener-last startup and recovery-lease lifecycle evidence;
- explicit classification of ambiguous in-flight outcomes;
- immutable per-cycle manifests and aggregate evidence with SHA-256 references;
- focused tests and Phase 9 evidence documentation.

## 4. Out of Scope

- production source, API, protocol, WAL, Snapshot or recovery semantic changes;
- reconnect, deduplication, exactly-once or client-outcome recovery claims;
- multiple active sessions or request pipelining;
- online recovery architecture changes;
- JVM/GC/workload/threshold tuning;
- JMH/profile optimization work (TASK-039);
- Phase 9 Closure, merge, tag, `v0.8.0-engineering-baseline` or Product Release.

## 5. Acceptance Criteria

- [ ] 20 graceful restart cycles complete with deterministic convergence.
- [ ] 10 acknowledged-boundary forced-termination cycles complete with
  deterministic convergence.
- [ ] Every cycle validates strict WAL scan, recovery mode, checkpoint digest,
  Command Sequence, TradeId/EventSequence and fixed public-probe suffix.
- [ ] Listener remains unbound until recovery and runtime handoff succeed.
- [ ] Recovery lease ownership and release are verified for every cycle.
- [ ] Ambiguous in-flight outcomes remain explicitly ambiguous and are never
  upgraded to exactly-once or retry claims.
- [ ] Every terminal cycle publishes immutable runtime-captured evidence with
  artifact SHA-256 references; failed evidence is preserved.
- [ ] No retry-until-pass behavior, sample filtering or threshold changes.
- [ ] `mvn verify`, Checkstyle 0, `git diff --check`, frozen-path audit,
  verifier/docs-auditor review and exact-SHA CI pass.

## 6. Frozen Boundary

No file under `src/main/java/**`, existing production tests, `core/pom.xml`,
Protocol v1, WAL v1, Snapshot v1 or recovery semantics may change. Qualification
code and evidence documentation are the only authorized write areas. `.vscode/`
remains untouched and untracked.

## 7. Evidence Rules

Each cycle is an independent evidence unit. Run configuration, workload
identity, seed, JVM/GC settings and thresholds remain frozen for the campaign.
Graceful restart and forced termination results must remain separately labeled.
Any production/runtime defect, unexplained convergence mismatch, terminal-state
violation or need to alter a frozen contract triggers the Exception Gate.

## 8. Verification Commands

```text
mvn -pl qualification -am test
mvn verify
git diff --check
git status --short --branch
```

## 9. Gate

TASK-038 may proceed because TASK-037 has completed its campaign Evidence Gate,
Sol High Final Campaign Closure Review and Human Evidence / Closure Approval.
TASK-039 and TASK-040 remain dependency-locked. Phase 9 Closure, merge,
baseline tagging and Product Release remain unauthorized.

## 10. Exception Gate

Stop immediately for production-file/API changes, format or recovery semantic
changes, new dependencies, reconnect/deduplication behavior, weakened
convergence criteria, altered JVM/GC/workload/threshold configuration, deleted
or filtered failure evidence, or any scope expansion.
