# Phase 5 WAL / Replay Component Benchmark

## Status

Completed as TASK-20260821-018 evidence. This is a component-level JMH
baseline for the approved persistence/replay implementation; it is not an
end-to-end durability, recovery or exchange-throughput claim.

## Method

`WalBenchmark` measures three separate boundaries:

- `walAppend`: one command append, with `SYNC_EACH_APPEND` and `BUFFERED`
  reported separately;
- `walScan`: strict closed-WAL segment scan and decode without engine replay;
- `walReplay`: strict closed-WAL scan followed by genesis `MatchingEngine`
  replay.

Each JMH state creates its own temporary WAL fixture outside the measured
operation, consumes the result through a `Blackhole`, and deletes only its own
temporary directory after the trial. The fixture is rebuilt for each fork and
parameter set so stale WAL bytes are not reused.

The full matrix was run with:

```text
java -jar benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar \
  WalBenchmark -wi 1 -i 1 -f 1 -w 1s -r 1s -t 1 -foe true \
  -rf json -rff benchmark-results/wal-full.json
```

Environment and configuration:

| Field | Value |
| --- | --- |
| JDK | OpenJDK 21.0.12, 64-bit Server VM |
| JMH | 1.37 |
| OS / storage | Windows workspace; local filesystem (device/cache not isolated) |
| Forks / threads | 1 / 1 |
| Warmup / measurement | 1 x 1 s / 1 x 1 s |
| Segment sizes | 4,128 and 65,536 bytes |
| Replay/scan command counts | 256 and 1,024 |
| Raw output | local ignored `benchmark-results/wal-full.json` |

## Recorded Results

Scores below are the single-fork component observations from the full matrix.
Throughput is `ops/us`; sample-time is `us/op`. The one-iteration setup is
intended as a reproducible baseline, not statistical production capacity.

### Append

| Durability | Segment | Throughput (ops/us) | Sample mean (us/op) |
| --- | ---: | ---: | ---: |
| SYNC_EACH_APPEND | 4,128 | 0.003542858 | 283.741 |
| SYNC_EACH_APPEND | 65,536 | 0.004224853 | 246.111 |
| BUFFERED | 4,128 | 0.120667710 | 4.796 |
| BUFFERED | 65,536 | 0.335894622 | 4.510 |

### Strict scan and offline replay

| Operation | Commands | Segment | Throughput (ops/us) | Sample mean (us/op) |
| --- | ---: | ---: | ---: | ---: |
| walScan | 256 | 4,128 | 0.003693626 | 276.955 |
| walScan | 256 | 65,536 | 0.007988130 | 129.520 |
| walScan | 1,024 | 4,128 | 0.001272279 | 783.633 |
| walScan | 1,024 | 65,536 | 0.006502601 | 154.410 |
| walReplay | 256 | 4,128 | 0.002958416 | 348.866 |
| walReplay | 256 | 65,536 | 0.005104278 | 205.167 |
| walReplay | 1,024 | 4,128 | 0.000998311 | 1,013.116 |
| walReplay | 1,024 | 65,536 | 0.002588236 | 397.084 |

The values are workload- and environment-specific. `BUFFERED` is not durable
throughput, and `SYNC_EACH_APPEND` does not establish a hardware power-loss
guarantee beyond the approved `FileChannel.force(true)` boundary.

## Verification and Claim Limits

- `mvn verify`: 113 tests passed, 0 failures, Maven reactor 3/3 SUCCESS;
  Checkstyle reported 0 violations.
- Benchmark smoke and full matrix completed successfully; the raw JSON remains
  local and ignored rather than committed.
- No result changes the `SYNC_EACH_APPEND` default or WAL format.
- Append timing excludes client acknowledgement, pipeline admission, trade
  publication and network I/O.
- Scan timing is not recovery time; replay timing is not crash-recovery time.
- Snapshot, online recovery, live pipeline/WAL integration, Network,
  replication, GC profiling and production optimization remain out of scope.
- Filesystem cache, Windows scheduling, one fork and one one-second sample
  limit inference. More rigorous performance work requires a separate
  evidence/optimization decision.
