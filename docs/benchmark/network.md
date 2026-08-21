# Phase 6 Network Benchmark Evidence

## Status

Phase 6 network benchmark evidence is complete for the approved component and
sequential loopback scope. It is a local-host engineering baseline, not a
production or durable-acknowledgement claim.

## Workloads

`NetworkBenchmark` contains three fixed JMH workloads:

- `requestDecode`: strict binary frame decoding for a deterministic alternating
  `SubmitLimitCommand` / `CancelOrderCommand` request mix;
- `responseEncode`: fixed `CommandResult`, `MatchResult` and `Error` response
  encoding;
- `loopbackSequentialRoundTrip`: one persistent loopback TCP connection with
  exactly one request in flight, alternating Submit/Cancel commands, and a
  complete ordered response read before the next request.

Each workload validates its vectors or response correlation, so the benchmark
does not use a dead-code-only shortcut. The raw JMH CSV is local and ignored:
`benchmark-results/phase6-network-smoke-jdk21.csv`.

## Reproduction command

The benchmark must run on Java 21. On the evidence host:

```powershell
mvn -pl benchmark -am -DskipTests package
& 'E:\Java\microsoft-jdk-21\bin\java.exe' -jar `
  benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar `
  NetworkBenchmark -wi 1 -i 2 -f 1 -t 1 `
  -rff benchmark-results/phase6-network-full-jdk21.csv -rf csv
```

The first attempt with a Java 17 runtime was rejected by the Java 21 class-file
version; it is not evidence. The values below are from the successful Java 21
full matrix run. A shorter one-iteration smoke run is also retained locally as
`benchmark-results/phase6-network-smoke-jdk21.csv`.

## Environment and workload metadata

| Item | Evidence host |
| --- | --- |
| OS | Windows 11 Home `10.0.26200` |
| CPU | Intel Core i9-13900H; 14 cores / 20 logical processors |
| RAM | 33,968,349,184 bytes |
| JDK/JVM | Microsoft OpenJDK `21.0.12`, 64-bit Server VM |
| JVM path | `E:\Java\microsoft-jdk-21` |
| GC | G1 (`UseG1GC=true`) |
| JVM arguments | none beyond the launcher command |
| Netty | `4.2.17.Final`; `netty-transport` + `netty-codec` |
| Allocator | `PooledByteBufAllocator.DEFAULT` |
| JMH | `1.37` |
| Warmup / measurement | `1 x 1s` / `1 x 1s` |
| Forks / threads | `1` / `1` |
| Request mix | alternating SubmitLimit / CancelOrder |
| Request sizes | Submit `56` bytes; Cancel `32` bytes |
| Command result size | `40` bytes |
| Loopback in-flight bound | exactly one request |

## Java 21 full-matrix evidence

The full run used one fork, one thread, one warmup iteration and two one-second
measurement iterations. Values are observations from this host and fixed
workload; each codec range spans its six Submit/Cancel × response-vector
parameter combinations.

| Workload | Throughput | P50 | P99 | P999 |
| --- | ---: | ---: | ---: | ---: |
| `requestDecode` (six vectors) | `7.004`–`7.869 ops/us` | `0.1`–`0.2 us` | `0.2`–`0.3 us` | `4.729`–`9.871 us` |
| `responseEncode` (six vectors) | `4.691`–`5.333 ops/us` | `0.2 us` | `1.8`–`1.9 us` | `14.367`–`19.660 us` |
| `loopbackSequentialRoundTrip` | `0.017 ops/us` | `50.048 us` | `169.108 us` | `260.719 us` |

The full loopback SampleTime P9999 was `1818.456 us` and the maximum was
`10158.080 us`. Codec throughput and SampleTime values vary by fixed vector;
the ranges record the observed combinations rather than inventing an aggregate.

The earlier Java 21 smoke run observed loopback P50/P99/P999 of
`58.24 / 199.828480 / 327.168 us`; it is retained as a smoke sanity check, not
mixed into the full-matrix summary.

## Claim boundary

- Codec measurements are parser/encoder component evidence, not TCP latency.
- Loopback is a local sequential round-trip component baseline, not Internet
  latency, concurrent-client throughput or production exchange performance.
- A local write completion is not a durable acknowledgement or proof of client
  receipt.
- Phase 6 does not measure live WAL integration, durability, reconnect,
  deduplication, Snapshot, online Recovery or TLS/security.
- Benchmark numbers do not authorize a wait-strategy, allocator, transport,
  protocol or durability-default change; `BLOCKING` remains the approved
  correctness default.

## Evidence status

| Evidence | Result |
| --- | --- |
| Benchmark package / Checkstyle | PASS |
| Java 21 JMH smoke | PASS |
| Java 21 JMH full matrix | PASS |
| Fixed Submit/Cancel decode and encode vectors | PASS |
| Sequential loopback complete-result validation | PASS |
| Raw artifact | local/ignored; summary committed here |
| Production optimization claim | none |
