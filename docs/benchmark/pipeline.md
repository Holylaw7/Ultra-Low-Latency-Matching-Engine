# Phase 4 Event Pipeline Benchmark Evidence

## Status

`Evidence accepted — Phase 4 Closure approved`

This document records component-level JMH evidence for the Phase 4 event
pipeline. It is not a product performance claim and does not replace the
correctness and lifecycle evidence in the Phase 4 cumulative report.

## Scope and boundaries

`PipelineBenchmark` compares three deliberately separate measurements:

| Method | Timed region | Result validation outside timing |
| --- | --- | --- |
| `directSynchronous` | one direct `MatchingEngine.process` call | JMH consumes the immutable result |
| `producerAdmission` | one non-blocking `tryPublish` call while a pipeline is running | accepted commands are drained; accepted/result counts must match |
| `batchCompletion` | publish a fixed 256-command batch and wait for its result latch | pipeline shutdown and total completion count are checked |

Pipeline construction, thread startup, batch preparation, drain and shutdown
are intentionally outside the measured producer and batch regions. The batch
method measures a verified pipeline component operation whose unit is one
256-command batch; it is not an end-to-end request latency measurement.

The command fixture is deterministic and contains price-time matches, residual
orders and cancellations. Capacities are `1024` and `65536`; wait modes are
`BLOCKING`, `YIELDING` and `BUSY_SPIN`. `BLOCKING` remains the correctness and
portable default. The other modes are experimental variables only.

## Reproduction

Build the shaded JMH jar with the project Java 21 toolchain:

```text
mvn --batch-mode --no-transfer-progress -pl benchmark -am package -DskipTests
```

The local full-matrix run used:

```text
E:\Java\microsoft-jdk-21\bin\java.exe -jar benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar PipelineBenchmark -wi 1 -i 1 -f 1 -w 1s -r 1s -t 1 -foe true -rf json -rff benchmark-results/pipeline-full.json
```

The smoke run used the same matrix with `-wi 0 -i 1 -w 100ms -r 100ms` and
completed all methods, capacities and wait modes. `benchmark-results/` is
ignored; the JSON is a local raw artifact rather than a committed baseline.

## Environment and run configuration

| Item | Value |
| --- | --- |
| OS | Windows 11, amd64 |
| Java | OpenJDK 21.0.12 (Microsoft), `E:\Java\microsoft-jdk-21` |
| JMH | 1.37 |
| Forks | 1 |
| Threads | 1 |
| Warmup | 1 iteration × 1 second |
| Measurement | 1 iteration × 1 second |
| Benchmark modes | Throughput and SampleTime |
| GC/JFR profiler | not enabled for this evidence run |

The Maven build uses Java 21. The `java` executable currently first on the
interactive PATH is JDK 17, so the explicit Java 21 path above is required to
run the generated class files.

## Full-matrix component snapshot

The following values are the single local run described above. They are
recorded for reproducibility and method checking, not as stable cross-machine
claims. Throughput uses the JMH operation unit; `batchCompletion` therefore
reports batches, not individual commands.

| Method | Parameter set | Throughput range | Sample-time range |
| --- | --- | ---: | ---: |
| `directSynchronous` | fixed direct command | 25.675 ops/µs | 0.064 µs/op |
| `producerAdmission` | 2 capacities × 3 wait modes | 11.590–19.097 ops/µs | 0.087–0.143 µs/op |
| `batchCompletion` | 2 capacities × 3 wait modes, 256 commands/batch | 0.0362–0.0466 batches/µs | 21.47–28.78 µs/batch |

The result counts and lifecycle checks passed for every row. These numbers do
not establish network throughput, durable latency, P99 behavior, zero-GC,
lock-free/wait-free execution or production readiness. They also do not justify
changing the `BLOCKING` default. Any wait-strategy default change requires new
evidence and an approved ADR/Blueprint update.

## Evidence interpretation

- Admission is an in-memory producer boundary and does not mean applied,
  durable or recoverable.
- Batch completion includes a consumer result handoff and bounded completion
  wait, but excludes network, WAL, persistence and downstream publication.
- The run is one host-specific component snapshot. A future performance phase
  must define CPU topology, repetitions, profiler controls and comparison
  hypotheses before making optimization decisions.
- No raw JSON, JFR file or profiler output is committed by TASK-013.

## Deferred work

WAL, Replay, Snapshot, Recovery, Network, multi-producer ingress, output rings,
thread affinity and production wait-strategy selection remain outside Phase 4.

## Evidence gate

The benchmark module compiled, the smoke and full matrix completed under Java
21, and the result/lifecycle assertions passed. Human Phase 4 Closure Approval
accepted this component evidence and authorized the normal merge/master-CI/
engineering-baseline-tag workflow. Product Release remains unauthorized.
