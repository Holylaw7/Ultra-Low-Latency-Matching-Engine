# Phase 9 — TASK-20260823-039 / Full-Path JMH Baseline and JFR Profile

## Executive Status

| Item | Status |
| --- | --- |
| Phase | Phase 9 — System Qualification, Performance Characterization and Long-Run Reliability |
| Task | `TASK-20260823-039` — Full-path JMH baseline and JFR profile |
| Authorization | Phase 9 Blueprint approval; TASK-038 Evidence Gate PASS |
| Implementation | Additive `SystemQualificationBenchmark` under the existing benchmark module |
| Production source | Unchanged; frozen-path diff must remain `0` |
| Closure state | Completed / Evidence Gate PASS; TASK-040 is the next authorized task |
| Raw benchmark output | Local ignored `benchmark-results/` |
| Raw JFR output | Local ignored `profiler-results/` |
| Working tree policy | Pre-existing `.vscode/` remains untouched and untracked |

## Scope and Boundaries

`SystemQualificationBenchmark` measures two separate approved boundaries:

```text
Protocol v1 TCP client
        ↓
durable gateway / WAL-before-execute / Pipeline / MatchingEngine
        ↓
ordered response frames
```

and:

```text
closed WAL (+ optional Snapshot)
        ↓
RecoveryPlanner
        ↓
sequence convergence / recovered runtime
        ↓
listener-ready state
```

Fixture construction, WAL/Snapshot publication and teardown are outside the
measured operation. The benchmark does not call coordinator, pipeline or engine
methods directly from the full-path round-trip method.

No production source, production test, existing benchmark class, dependency,
Protocol/WAL/Snapshot format or runtime default is changed by this task.

## Benchmark Matrix

The declared JMH configuration is frozen before each run:

```text
Forks: 3
Threads: 1
Warmup: 5 iterations × 2 seconds
Measurement: 5 iterations × 5 seconds
Modes: Throughput and SampleTime
```

`durableProtocolRoundTrip` covers:

```text
workload = LIFECYCLE_MIX, CROSSING_MULTI_MATCH,
           RESTING_DEPTH, MEMORY_STEADY_STATE_V1
segmentSizeBytes = 4128, 65536
```

`recoveryBootstrapToListener` covers:

```text
recoveryMode = PURE_WAL, SNAPSHOT_THEN_WAL
commandCount = 256, 1024
segmentSizeBytes = 4128, 65536
```

The two methods therefore form a declared 16-combination matrix, with both
JMH modes recorded for each combination (32 result entries in the raw JSON).
`SYNC_EACH_APPEND` remains the live
durability mode. `BUFFERED` is not used for live acceptance by this benchmark.

## Smoke Evidence

The first implementation checkpoint passed Java 21 benchmark smoke runs for:

- durable Protocol v1 loopback (`LIFECYCLE_MIX`, 4,128-byte segments);
- durable Protocol v1 crossing workload (`CROSSING_MULTI_MATCH`, 65,536-byte
  segments);
- `PURE_WAL` bootstrap-to-listener (256 commands, 4,128-byte segments);
- `SNAPSHOT_THEN_WAL` bootstrap-to-listener (256 commands, 4,128-byte
  segments);
- one representative `-prof gc -prof jfr` run, which produced a JFR artifact.

Raw smoke JSON and JFR files remain ignored under `benchmark-results/` and
`profiler-results/`; they are not mixed with Phase 8 recovery evidence.

## Full Evidence

The completed unprofiled matrix contains 16 parameter combinations and 32
base JMH result entries (Throughput and SampleTime for each combination). It
was run with three forks, five two-second warmups, five five-second
measurements and one thread. The raw JSON retains all fork/iteration samples,
including outliers; no result was filtered.

| Evidence | Result |
| --- | --- |
| Raw matrix | `benchmark-results/task039-jmh-full.json` |
| Raw matrix SHA-256 | `8C8D05209FA903D13A3D3474142BAF59FA968602EA33A7BD44DCF1FC317A2EAF` |
| Base result entries | `32` (`16` combinations × `2` modes) |
| SampleTime observations | `1,420,841` total; per-combination range `11,575`–`173,723` |
| Throughput score range | `0.000172803075926201`–`0.00297275415168161 ops/us` |
| SampleTime P50 range | `395.264`–`5,128.192 us/op` |
| SampleTime P95 range | `652.288`–`9,994.240 us/op` |
| SampleTime P99 range | `826.368`–`11,075.584 us/op` |
| SampleTime P99.9 range | `2,452.094976`–`13,349.945344 us/op` |
| SampleTime max range | `9,060.352`–`329,777.152 us/op` |

The first unrestricted profiler invocation was interrupted before a complete
result because it expanded all recovery parameters; it is not used as
evidence. The completed representative profile below is the authoritative
GC/JFR lane.

An earlier preflight matrix invocation was also stopped after the deterministic
workload vector was corrected before any result was accepted. It is not part of
the declared evidence set; the raw matrix and all summaries below come only
from the corrected, complete invocation.

### Representative GC/JFR Profile

The representative profile fixes `recoveryMode=SNAPSHOT_THEN_WAL`,
`commandCount=1024` and `segmentSizeBytes=65536`. Because the workspace path
contains non-ASCII characters, collection used a temporary `X:` mapping and
JMH's `configName=profile;verbose=true` options. No other JMH instance was
running when `-Djmh.ignoreLock=true` was used to bypass a stale lock left by
the interrupted, non-evidence invocation.

```powershell
subst X: "E:\学习\Ultra-Low-Latency Matching Engine"
& 'E:\Java\microsoft-jdk-21\bin\java.exe' '-Djmh.ignoreLock=true' '-jar' `
  'X:\benchmark\target\matching-engine-benchmark-0.1.0-SNAPSHOT.jar' `
  'SystemQualificationBenchmark.recoveryBootstrapToListener' `
  '-p' 'recoveryMode=SNAPSHOT_THEN_WAL' '-p' 'commandCount=1024' `
  '-p' 'segmentSizeBytes=65536' '-wi' '5' '-w' '2s' '-i' '5' '-r' '5s' `
  '-f' '3' '-t' '1' '-foe' 'true' '-prof' 'gc' `
  '-prof' 'jfr:dir=X:\profiler-results\task039-jfr-representative-v2;configName=profile;verbose=true' `
  '-rf' 'json' '-rff' 'X:\benchmark-results\task039-profile-representative-v2.json'
subst X: /d
```

| Profile evidence | Result |
| --- | --- |
| JSON | `benchmark-results/task039-profile-representative-v2.json` |
| JSON SHA-256 | `92581E7AD1D9136186F43312C4725EFBD3B6B9840DC9B3908EB95A22A941DA68` |
| JFR files | `2` final recordings: `profiler-results/task039-jfr-representative-v2/*-Throughput-*/profile.jfr` and `*-SampleTime-*/profile.jfr`; the JMH output path was reported for each of the 3 fork sessions |
| Throughput GC allocation | `376.316 MB/sec` average; `1,284,474.677 B/op` |
| Throughput GC | `317` collections / `216 ms` aggregate |
| SampleTime | `N=24,371`, P50 `2,588.672 us/op`, P95 `3,567.616 us/op`, P99 `5,103.616 us/op`, P99.9 `8,168.407 us/op`, max `47,775.744 us/op` |
| SampleTime GC allocation | `386.654 MB/sec` average; `1,275,774.822 B/op` |
| SampleTime GC | `327` collections / `219 ms` aggregate |
| Throughput JFR SHA-256 | `F20B661CC06322FF206878A4066D974871A65B88EB8D68E32743563705A58B89` |
| SampleTime JFR SHA-256 | `7F513C23C8F54DFCC34DF877E5EECA3175814BAA93BA313BBC615CD956DAE345` |

The JFR/GC lane is observational and is not mixed with the unprofiled matrix
or used to authorize an optimization.

### Build and Frozen-Path Verification

`mvn verify` completed successfully with `195` core tests and `46`
qualification tests (the two existing environment-gated qualification tests
remain skipped), zero failures/errors and zero Checkstyle violations. The
tracked frozen production paths (`src/main/java/**`, `src/test/java/**`,
`core/pom.xml` and existing benchmark classes) have no diff. `git diff --check`
passes; `.vscode/` remains untracked and untouched.

The declared matrix is reproducible with:

```powershell
& 'E:\Java\microsoft-jdk-21\bin\java.exe' -jar `
  benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar `
  SystemQualificationBenchmark -wi 5 -w 2s -i 5 -r 5s -f 3 -t 1 `
  -foe true -rf json -rff benchmark-results/task039-jmh-full.json
```

The profiler lane is run separately with JMH's existing JDK profilers:

```powershell
& 'E:\Java\microsoft-jdk-21\bin\java.exe' -jar `
  benchmark/target/matching-engine-benchmark-0.1.0-SNAPSHOT.jar `
  SystemQualificationBenchmark.recoveryBootstrapToListener `
  -wi 5 -w 2s -i 5 -r 5s -f 3 -t 1 -foe true `
  -prof gc -prof jfr:dir=profiler-results/task039-jfr `
  -rf json -rff benchmark-results/task039-profile.json
```

The profile command is a separate observational lane; it is not mixed with
the three-fork latency matrix and cannot authorize an optimization.

### Environment

| Field | Recorded value |
| --- | --- |
| OS | Microsoft Windows 11 Home Chinese, `10.0.26200` |
| CPU | 13th Gen Intel Core i9-13900H; 14 cores / 20 logical processors |
| Storage | `E:` fixed NTFS volume; host reports NVMe media; device mapping not isolated |
| JDK / VM | Microsoft OpenJDK `21.0.12`, 64-bit OpenJDK Server VM |
| JVM arguments | none beyond the launcher command |
| GC | G1 GC (JDK default) |
| Estimated max heap | 7.91 GiB (`-XshowSettings:vm -version`) |
| JMH | `1.37` |
| Netty | existing project `4.2.17.Final` dependency set |
| Allocator | existing `PooledByteBufAllocator.DEFAULT` runtime configuration |

## Claim Boundary

All numbers in this report are component/local-host engineering observations.
They do not establish production throughput or latency, guaranteed P99/P999,
durable client acknowledgement, production RTO, availability SLA, hardware
power-loss safety, exactly-once semantics, capacity guarantees or Product
Release readiness. JFR and GC profiler output is observational only and does
not authorize production optimization or a default/configuration change.

## Evidence Gate

- [x] Declared 3-fork JMH matrix completes without omitted failures.
- [x] P50/P95/P99/P999/max, throughput and sample counts are retained.
- [x] Allocation and GC profiler evidence is recorded separately.
- [x] JFR profile artifacts and hashes are recorded.
- [x] Complete host/JDK/JVM/GC/heap/storage/Netty/allocator metadata is
  synchronized.
- [x] `mvn verify` and Checkstyle 0 pass.
- [x] `git diff --check` and frozen production-path audit pass.
- [x] `verifier`, `benchmark-reviewer` and `docs-auditor` report PASS.
- [x] Exact-SHA CI passes for the final evidence checkpoint.

Read-only reviewer results: `verifier PASS`, `benchmark-reviewer PASS` and
`docs-auditor PASS`.

The technical evidence checkpoint is commit `d003266` with Standard CI
`32707393196` PASS and Qualification Quick Lane `32707393200` PASS. The final
status/archive synchronization is documentation-only and must retain this
checkpoint as the benchmark evidence input; it does not move the baseline tag
or authorize Phase 9 Closure.

## Known Limitations

- Measurements are local-host observations on the recorded Java 21/JMH setup.
- Filesystem cache and operating-system scheduling are not isolated.
- JFR is a profile artifact, not a proof of absence of leaks or a production
  allocation/GC guarantee.
- Restart campaign evidence remains in TASK-038 and is not combined with these
  benchmark results.
- Production optimization, merge, `v0.8.0-engineering-baseline`, Phase 10 and
  Product Release remain unauthorized.
