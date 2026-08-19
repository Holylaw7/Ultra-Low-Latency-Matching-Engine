# ADR-0009 - Performance Profiling Evidence for the Phase 2 OrderBook Baseline

## Status

Proposed

## Context

The Phase 2 OrderBook correctness verification, benchmark baseline and
documentation synchronization were approved by the Human Developer on
`2026-08-19`.

The approved component baseline is the existing TreeMap side books with
intrusive FIFO queues and an active `OrderId -> OrderNode` index. The recorded
JMH baseline includes one-level and 64-level matching workloads, but it does
not identify CPU, allocation, garbage-collection or JVM hot paths.

The next governed stage is profiling. Profiling must explain the observed
baseline before any optimization proposal is considered.

## Problem

Without a controlled profile, a performance change would be based on
assumption rather than evidence. The project needs a reproducible profiling
procedure that:

- uses the approved baseline implementation without changing production
  behavior;
- prioritizes the multi-level matching path while retaining a small set of
  supporting OrderBook operations;
- records enough environment and workload metadata to compare results;
- separates profile observations from optimization decisions; and
- stops before any production optimization or Phase 3 work.

## Options

### Option A - Run an unstructured profiler session

This is quick, but it makes workload, warmup, sampling and environment
comparison difficult. It is rejected because the evidence would not be
reproducible enough for an optimization decision.

### Option B - Use JFR as the required baseline profile

JFR is included with the approved Java 21 runtime and can record JVM-level CPU,
allocation and garbage-collection evidence without adding a project
dependency. A fixed JMH workload and recorded JVM configuration can be used as
the profile input.

### Option C - Require async-profiler for every profile

async-profiler can provide useful CPU and allocation views, but availability
and native setup vary by operating system. Making it mandatory would block the
first controlled profile on environments where it is not installed.

## Proposed Decision

Use a controlled JFR recording as the required first profiling experiment.
Use async-profiler only as an explicitly recorded supplementary experiment when
it is available and its command, version and output are captured.

The profiling experiment must:

1. Reuse the committed `OrderBookBaselineBenchmark` workloads and do not change
   production code or benchmark semantics.
2. Prioritize `multiLevelMatch` as the primary hotspot workload.
3. Include `oneLevelMatch`, `cancelByOrderId` and
   `cancelAndCleanEmptyLevel` as supporting workloads.
4. Record Java/JDK, JVM arguments, OS, CPU, core count, memory, JMH version,
   profiler version, fork/thread settings, warmup, measurement duration,
   workload shape and timestamp.
5. Store raw recordings under a local ignored profile-results directory. The
   profile report must reference the exact artifact paths and commands.
6. Report observations such as hot methods, allocation sites and GC activity
   without changing code or declaring an optimization benefit.
7. Produce a separate Optimization ADR / Decision proposal if the evidence
   supports a code, data-structure, allocation or JVM change.

## Scope Boundary

Authorized by this ADR proposal only after Human approval:

- profiling the existing Phase 2 OrderBook baseline;
- collecting JFR evidence;
- collecting optional async-profiler evidence when available;
- producing a profiling report and hotspot analysis.

Not authorized by this ADR:

- modifying production code, tests or benchmark semantics to improve results;
- introducing object pools, custom trees, SkipLists, radix indexes, off-heap
  storage, Disruptor, lock-free mutation or GC tuning;
- changing the workload to produce a better number;
- claiming production throughput, production P99 or million-orders-per-second
  performance;
- implementing an optimization;
- entering MatchingEngine, Trade/Execution, WAL, Network or Phase 3.

## Consequences

Positive consequences:

- the first profile is reproducible and tied to the approved baseline;
- JFR is available with the existing Java 21 environment;
- profiling observations remain separate from optimization approval;
- future optimization comparisons have a documented B0 baseline.

Costs and limits:

- JFR and optional async-profiler introduce measurement overhead;
- one machine and fixed micro-workloads do not represent end-to-end system
  behavior;
- a profile may identify a hotspot without proving that a proposed change is
  beneficial;
- allocation and GC evidence may require a separate profile configuration.

## Verification Plan

The profiling report must include:

- the exact command and timestamp;
- environment and JVM configuration;
- workload and JMH parameters;
- raw recording paths and tool versions;
- CPU hot methods or call paths;
- allocation and GC observations when collected;
- measurement limitations and overhead considerations;
- an explicit statement that no optimization was implemented.

The stage ends at a Human Approval gate. An optimization can begin only after a
separate ADR / Decision and task-plan approval.

## Decision Record

| Field | Value |
| --- | --- |
| Proposal date | `2026-08-19` |
| Reviewer | `Pending Human Developer review` |
| Decision date |  |
| Decision | `Proposed` |

## Relationship to Existing Decisions

ADR-0009 does not change ADR-0007 or ADR-0008. It defines only the evidence
collection boundary for the already approved Phase 2 OrderBook baseline.
Optimization, alternative data structures and Phase 3 remain separately
governed decisions.
