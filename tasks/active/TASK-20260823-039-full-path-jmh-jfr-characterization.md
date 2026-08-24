# Task Plan — TASK-20260823-039

## 1. Metadata

| Field | Value |
| --- | --- |
| Task ID | `TASK-20260823-039` |
| Title | Full-path JMH baseline and JFR profile |
| Status | `In Progress` |
| Owner | Human Developer |
| Implementer | Main Codex / Luna Max — only writer |
| Created | `2026-08-24` |
| Related Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Related ADR | [`ADR-0017`](../../docs/adr/ADR-0017-system-qualification-performance-reliability.md) |
| Phase Blueprint | [`PHASE-9-system-qualification-and-long-run-reliability-blueprint.md`](../blueprints/PHASE-9-system-qualification-and-long-run-reliability-blueprint.md) |
| Authorization Mode | `Blueprint` |
| Current Stage | Additive benchmark implementation and evidence characterization |
| Next Gate | TASK-039 Evidence Gate; TASK-040 remains locked |
| Branch | `feature/phase9-system-qualification` |
| Baseline | `v0.7.0-engineering-baseline` / `87abbc1` |
| Dependency | `TASK-038 Completed / Evidence Gate PASS` |

## 2. Goal

Add a reproducible JMH/JFR characterization of the approved full public path
and restart-to-ready boundary without changing production source, runtime
semantics or benchmark defaults. The evidence is component/local-host
characterization only.

## 3. In Scope

- additive `SystemQualificationBenchmark` in the existing benchmark module;
- real Protocol v1 TCP client/server round trips through the durable gateway;
- deterministic lifecycle, crossing and resting-depth workload vectors;
- pure-WAL and Snapshot-plus-WAL-tail bootstrap-to-listener measurements;
- declared three-fork, five-warmup, five-measurement JMH matrix;
- JMH GC-profiler and JFR evidence commands;
- environment, workload, storage and claim-boundary documentation;
- ignored raw benchmark/profile artifacts and committed summary evidence.

## 4. Out of Scope

- changes under `src/main/java/**` or `src/test/java/**`;
- changes to existing benchmark classes, production defaults or dependencies;
- WAL, Snapshot, Protocol, Pipeline, Recovery or matching semantic changes;
- production optimization or wait/allocator/durability changes;
- Full Qualification, restart campaign or TASK-040 closure work;
- Product Release, merge, tag or `v0.8.0-engineering-baseline`.

## 5. Acceptance Criteria

- [ ] `SystemQualificationBenchmark` compiles with the existing JMH 1.37 setup.
- [ ] Full-path durable round-trip and recovery bootstrap boundaries are
  measured through public runtime composition; fixture setup/cleanup is outside
  measured operations.
- [ ] Deterministic workload and segment-size parameters are declared and
  documented; no result-dependent parameter changes occur.
- [ ] JMH uses three forks, five two-second warmups, five five-second
  measurements and one thread.
- [x] Full declared matrix completes without omitted failures; SampleTime
  P50/P95/P99/P999/max, throughput and sample counts are retained.
- [x] `-prof gc` and `-prof jfr` runs are recorded as separate observational
  evidence; no optimization claim is made.
- [x] Host/JDK/JVM/GC/heap/storage/Netty/allocator/workload metadata is
  synchronized with raw artifact paths and hashes.
- [x] `mvn verify`, Checkstyle 0, `git diff --check`, frozen-path audit and
  read-only reviewer gates pass.

## 6. Frozen Boundary

No file under `src/main/java/**`, `src/test/java/**`, `core/pom.xml`, existing
benchmark classes, Protocol v1, WAL v1, Snapshot v1, recovery semantics or
baseline tags may change. Only the additive benchmark class and authorized
Phase 9 evidence/status documentation may be written. `.vscode/` remains
untouched and untracked.

## 7. Evidence Rules

JMH, GC-profiler and JFR lanes remain separate from Quick/Full qualification
and restart evidence. A run records its exact command, SHA, parameters,
environment, raw artifact path/hash, failures and limitations. Results are
component/local-host observations and never imply Production Ready, SLA/RTO,
guaranteed P99/P999, power-loss safety, exactly-once or capacity guarantees.

## 8. Verification Commands

```text
mvn -pl benchmark -am -DskipTests package
mvn verify
git diff --check
git status --short --branch
```

## 9. Exception Gate

Stop immediately for production-source/API changes, changes to existing
benchmark classes, new dependencies/profilers, altered workload/threshold/
JVM/GC configuration after observing results, omitted failures/forks, or any
optimization, merge, tag, Product Release or Phase 10 action.
