# Task Plan — TASK-20260823-040

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260823-040` |
| Title | Phase 9 final qualification evidence reconciliation and Closure Proposal |
| Status | `In Progress` |
| Owner | Human Developer |
| Implementer | Main Codex / Luna Max — only writer |
| Related Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Related ADR | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) |
| Phase Blueprint | [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md) |
| Dependency | TASK-039 Evidence Gate PASS |
| Current Gate | Documentation/evidence reconciliation and Closure Proposal |
| Next Gate | Sol High Phase 9 Final Closure Review; Human Closure Approval remains required |
| Branch | `feature/phase9-system-qualification` |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |

## 2. Objective

Reconcile the final Phase 9 qualification evidence across TASK-035 through
TASK-039, record the approved claim boundaries and known limitations, and
prepare (but do not approve) the Phase 9 Closure Proposal. This task is
documentation/evidence-only. It must not change production, test, benchmark,
dependency, protocol, WAL, Snapshot or recovery semantics.

## 3. Authorized Scope

- add the TASK-040 task plan and report;
- synchronize Phase 9 Blueprint status and evidence references;
- synchronize `README.md`, `.codex/AGENT_CONTEXT.md` and the blueprint index;
- reconcile task status, commit, CI, reviewer and artifact references;
- record the final Closure Proposal and the required stop at Sol High review.

## 4. Forbidden Scope

- any `src/main/**`, `src/test/**`, `core/**`, qualification implementation or
  benchmark implementation change;
- dependency, JVM/GC, workload, threshold or runtime configuration changes;
- rewriting, filtering or regenerating ignored qualification/benchmark/JFR
  artifacts;
- Phase 9 Closure approval, merge, tag, `v0.8.0-engineering-baseline`,
  Product Release or Phase 10 work;
- handling `.vscode/`; the pre-existing directory remains untouched and
  untracked.

## 5. Acceptance Criteria

- [ ] One authoritative evidence matrix covers TASK-035 through TASK-039.
- [ ] Final commit/CI/reviewer references are internally consistent and stale
  “TASK-039 next/not started” status text is removed from current documents.
- [ ] The TASK-039 JMH/JFR matrix and raw artifact hashes are recorded without
  filtering outliers or upgrading component evidence to a production claim.
- [ ] TASK-037 A'/B' campaign and TASK-038 20/10 campaign evidence are kept
  separate and their historical non-qualifying evidence remains preserved.
- [ ] Known limitations and explicitly unclaimed guarantees remain visible.
- [ ] Closure Proposal is prepared but clearly marked `NOT AUTHORIZED`.
- [ ] No production/test/benchmark/dependency diff is introduced.
- [ ] `git diff --check`, `mvn verify`, approved-path audit, verifier,
  benchmark-reviewer and docs-auditor all pass.
- [ ] Exact-SHA CI passes for the documentation checkpoint.

## 6. Evidence Gate

```text
TASK-040 docs/evidence changes
        ↓
focused stale-evidence and link checks
        ↓
mvn verify / Checkstyle / git diff --check
        ↓
production, test, benchmark and dependency diff = 0
        ↓
verifier + benchmark-reviewer + docs-auditor (read-only) PASS
        ↓
exact-SHA CI PASS
        ↓
STOP — Sol High Phase 9 Final Closure Review
```

## 7. Exception Gate

Stop and request Sol High review if reconciliation requires changing a
technical acceptance criterion, production/runtime semantics, benchmark
methodology, frozen format, workload/threshold, dependency or any scope not
listed above.

