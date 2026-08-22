# Phase 7 Live Durable Pipeline Benchmark Evidence

## Status and boundary

This document records the TASK-028 component and sequential-loopback benchmark
evidence for Phase 7. It is an engineering baseline for the approved
`SYNC_EACH_APPEND` live composition. It is not a product, durability, recovery
or production-capacity claim.

The benchmark source is
`benchmark/src/main/java/com/ultralatency/matching/benchmark/DurablePipelineBenchmark.java`.
The ignored raw artifact is `benchmark-results/phase7-durable-full.json`.

The four measurements are intentionally separate:

| Method | Measured boundary |
| --- | --- |
| `walAppendForce` | One command WAL append including the configured synchronous `force(true)` action. |
| `appendPlusPublish` | One `DurableCommandCoordinator` admission through append/force and pipeline publication. `SUBMIT` and `CANCEL` are explicit deterministic parameters. |
| `localResultWrite` | Local Protocol v1 response encoding into an EmbeddedChannel; no socket or client acknowledgement. |
| `loopbackSequentialRoundTrip` | One persistent local TCP connection, alternating Submit/Cancel requests, exactly one request in flight, and a complete ordered response read. |

No setup or teardown resource creation is intended to be part of the measured
operation. The benchmark consumes results and validates response correlation so
the methods are not dead-code-only loops.

## Reproduction

The evidence host uses Microsoft OpenJDK 21.0.12. Build the shaded JMH jar and
run the deterministic full matrix with:

```powershell
mvn -pl benchmark -am -DskipTests package
& 'E:\Java\microsoft-jdk-21\bin\java.exe' -jar `
  benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar `
  'DurablePipelineBenchmark.*' -wi 1 -i 2 -f 1 -t 1 `
  -w 1s -r 1s -foe true -rf json `
  -rff benchmark-results/phase7-durable-full.json
```

The completed run used JMH 1.37, one fork, one thread, one one-second warmup
and two one-second measurement iterations. Both Throughput and SampleTime
outputs were emitted. Percentiles below are SampleTime values in microseconds
per operation; throughput is reported separately in operations per
microsecond.

## Environment and workload metadata

| Item | Evidence host / workload |
| --- | --- |
| OS | Windows 11 Home, `10.0.26200` / build `26200` |
| CPU | Intel Core i9-13900H; 14 cores / 20 logical processors |
| RAM | 33,968,349,184 bytes |
| Storage | `E:` fixed NTFS volume; host reports NVMe SSD media; volume-to-device mapping not isolated |
| JDK / JVM | Microsoft OpenJDK `21.0.12`, 64-bit OpenJDK Server VM `21.0.12+8-LTS` |
| JVM arguments | none (`<none>` in JMH) |
| GC | G1 GC, JDK default; not independently isolated |
| Netty | `4.2.17.Final` |
| Netty allocator | `PooledByteBufAllocator.DEFAULT` |
| JMH | `1.37` |
| Forks / threads | `1` / `1` |
| Warmup / measurement | `1 x 1 s` / `2 x 1 s` |
| WAL durability | `SYNC_EACH_APPEND`; `force(true)` is included in append timing |
| WAL segment parameters | `4,128` and `65,536` bytes |
| Pipeline | capacity `1024`, wait mode `BLOCKING`, one producer/one consumer |
| Append command vector | One SubmitLimit command per `walAppendForce` invocation; record payload is 52 bytes |
| Admission vectors | Explicit `SUBMIT` and `CANCEL` parameters; Submit record is 52 bytes and Cancel record is 28 bytes |
| Loopback vector | Alternating SubmitLimit / CancelOrder; one request in flight; request frames are 56 / 32 bytes |
| Response vectors | Local `COMMAND` and `MATCH` Protocol v1 responses |

For a fresh one-record WAL invocation, the 32-byte segment header plus one
Submit/Cancel record occupies 84/60 logical bytes respectively and one segment.
The loopback sample run recorded 4,301 sequential operations in the raw JSON;
the alternating vector therefore contained 2,151 Submit and 2,150 Cancel
records (172,052 record bytes before segment headers). Operation counts and
physical segment totals are run-specific for time-based JMH iterations and are
not treated as fixed capacity claims.

## Full Java 21 matrix

The values below are the successful run recorded in
`benchmark-results/phase7-durable-full.json`. `Mean`, P50, P95, P99 and P999
are SampleTime observations in `us/op`. The WAL append tails show substantial
host/filesystem variance and the small sample counts are retained rather than
hidden.

| Workload | Parameters | Samples | Mean | P50 | P95 | P99 | P999 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `appendPlusPublish` | Submit / 4,128 | 940 | 1,191.463 | 1,140.736 | 1,454.080 | 1,730.089 | 6,176.768 |
| `appendPlusPublish` | Submit / 65,536 | 921 | 1,217.116 | 1,175.552 | 1,541.939 | 1,906.811 | 6,381.568 |
| `appendPlusPublish` | Cancel / 4,128 | 139 | 13,104.960 | 12,337.152 | 15,679.488 | 24,510.464 | 24,707.072 |
| `appendPlusPublish` | Cancel / 65,536 | 936 | 1,206.890 | 1,161.216 | 1,490.944 | 1,930.322 | 6,193.152 |
| `localResultWrite` | COMMAND | 57,553 | 0.429 | 0.300 | 0.400 | 2.200 | 17.174 |
| `localResultWrite` | MATCH | 54,791 | 0.433 | 0.300 | 0.500 | 2.200 | 14.212 |
| `loopbackSequentialRoundTrip` | alternating Submit/Cancel | 4,301 | 690.606 | 344.064 | 541.594 | 733.102 | 5,375.492 |
| `walAppendForce` | 4,128 | 7 | 673,516.105 | 1,452.032 | 3,644,850.176 | 3,644,850.176 | 3,644,850.176 |
| `walAppendForce` | 65,536 | 27 | 139,876.504 | 1,214.464 | 1,920,991.232 | 2,139,095.040 | 2,139,095.040 |

Throughput observations from the same run were approximately:

| Workload | Throughput |
| --- | ---: |
| `appendPlusPublish` Submit / 4,128 | `0.001 ops/us` |
| `appendPlusPublish` Submit / 65,536 | `0.001 ops/us` |
| `appendPlusPublish` Cancel / 4,128 | `0.001 ops/us` |
| `appendPlusPublish` Cancel / 65,536 | `0.001 ops/us` |
| `localResultWrite` COMMAND / MATCH | `2.909` / `2.751 ops/us` |
| `loopbackSequentialRoundTrip` | `0.003 ops/us` |
| `walAppendForce` 4,128 / 65,536 | `0.001` / `0.001 ops/us` |

The full matrix completed successfully. JMH emitted its normal warning that
these values are data requiring experimental interpretation; no optimization
decision is made from this run.

## Claim boundary and limitations

- `walAppendForce` is a WAL component measurement, not client latency or
  end-to-end request latency.
- `appendPlusPublish` ends at pipeline admission; it does not wait for engine
  completion or response delivery.
- `localResultWrite` measures local response encoding only. It is not network
  write latency, client receipt or acknowledgement.
- `loopbackSequentialRoundTrip` is one local connection with one request in
  flight. It is not concurrent-client capacity, Internet latency or exchange
  throughput.
- `SYNC_EACH_APPEND` is the correctness default, but the benchmark does not
  prove hardware power-loss safety. No durable client ACK, exactly-once,
  reconnect, Snapshot or online Recovery claim is made.
- `BUFFERED` is not used by the live composition and is not silently promoted
  by benchmark results.
- The large synchronous-WAL tails and run-specific loopback outliers are
  recorded evidence, not production SLOs. Filesystem cache, Windows scheduling,
  one fork and short measurement windows limit inference.
- Phase 7 remains an engineering/component baseline. Network/WAL integration,
  Snapshot, online Recovery, production optimization and Product Release remain
  separately governed.

## Evidence status

| Check | Result |
| --- | --- |
| Benchmark source compile / Checkstyle | PASS; 0 violations |
| `mvn -pl benchmark -am test` | PASS; core 158 tests, 0 failures |
| Shaded benchmark package | PASS |
| Java 21 smoke/full matrix | PASS |
| Raw JSON | local and ignored by Git |
| Production optimization claim | none |
