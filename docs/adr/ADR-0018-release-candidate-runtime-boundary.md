# ADR-0018: Release-Candidate Runtime Boundary

## Status

**Proposed — Pending Human Phase 10 Blueprint Approval**

No Phase 10 implementation, merge, release-candidate tag or Product Release is
authorized by this proposal.

## Context

`v0.8.0-engineering-baseline` contains a deterministic matching core, bounded
SPSC event pipeline, Command WAL v1, Protocol v1 gateway, WAL-before-execute
durable path, Snapshot v1 recovery bootstrap and recorded qualification
evidence. The verified components can be composed by tests, but the repository
does not yet expose a release-candidate application boundary with one explicit
configuration, startup, readiness, shutdown and packaging contract.

The next coherent step is not a new trading feature or an automatic Product
Release. It is to assemble the existing frozen capabilities into a reproducible
single-node runtime and qualify that composition without changing matching,
ordering, persistence, protocol or recovery semantics.

## Decisions

### D1 — Phase 10 is release-candidate runtime assembly

Phase 10 adds a real application entrypoint, immutable configuration,
composition-root lifecycle ownership, operational status, graceful shutdown,
packaging and assembled-runtime qualification. It does not declare the system
Production Ready and does not authorize Product Release.

### D2 — v0.8.0 semantics remain frozen

Domain, OrderBook, MatchingEngine, Pipeline, Protocol v1, WAL v1, Snapshot v1,
recovery modes, sequence ownership, single-session/one-in-flight behavior,
`SYNC_EACH_APPEND`, WAL-before-execute and listener-last recovery remain
unchanged. Additive runtime adapters may compose these components but may not
reinterpret them.

### D3 — Ownership is one explicit hierarchy

`ReleaseCandidateRuntime` owns exactly two direct children: the existing
`RecoverableDurableMatchingEngineTcpServer` and a new `ManagementServer`. The
Protocol server remains the sole owner of its `RecoverableDurableRuntime`,
Protocol boss/worker groups, listener and active session. The recovered runtime
remains the sole owner of `RecoveryLease`, WAL writer, Pipeline and durable
coordinator. The management server owns one separate one-thread Netty event-loop
group, its listener and its bounded connections.

The composition root never independently closes a transitive child. Each owner
closes only its direct children, exactly once. Existing constructors retain
their behavior; an additive Protocol-server constructor accepts the shared
availability predicate and a first-terminal-failure observer.

### D4 — Startup order is fixed and readiness is last

Startup is:

```text
parse and validate configuration
    -> create availability = STARTING / ready false
    -> start Protocol server with admission predicate false
       (acquire lease -> recover -> Pipeline/coordinator -> bind listener)
    -> start management listener
    -> atomically publish availability = READY / admission true
```

The Protocol server checks the same atomic availability predicate on session
activation and request admission. No request may be accepted before recovery
and both listeners are ready. Failure rolls back in reverse direct-child order:
management, then Protocol server; each child closes its own resources.

### D5 — Shutdown order is explicit and bounded

Shutdown atomically publishes `STOPPING / ready false`, closes the management
listener, calls the Protocol server's additive `stopAdmission()`, and then
`awaitInFlight(timeout)`. After completion or timeout, it calls the existing
Protocol-server shutdown, which closes the active session, recovered runtime,
Protocol event loops and their transitive resources. A timeout is a non-clean
terminal outcome; a durable command is not rolled back, and client receipt may
remain ambiguous. `stopAdmission()` and `awaitInFlight(Duration)` are the only
new Protocol lifecycle operations authorized by this decision.

A compatible `RecoverableDurableRuntime.shutdown(Duration)` overload is also
authorized so the Pipeline drain consumes the caller's remaining cooperative
deadline; existing `shutdown()` behavior remains compatible. The bound applies
to application-controlled drain and Netty termination. JDK/OS file close or
`force` calls are not preemptible, so Phase 10 makes no hard wall-clock or
process-kill guarantee for a blocked native storage operation.

The Protocol server's private runtime-close path is narrowly authorized to
forward its calculated remaining `Duration` to that overload. Its existing
public `shutdown()`/`shutdown(Duration)` signatures and ownership remain
unchanged; it must never expose or let the composition root close the transitive
runtime directly.

### D6 — Configuration is typed, immutable and fail-closed

The application accepts one UTF-8 strict-properties-v1 file selected through
`--config <path>` or `--config=<path>`. Unknown keys, duplicate semantic
settings, malformed values, unsafe defaults and invalid path relationships fail
before recovery or listener binding. Relative paths resolve against the
configuration-file directory. Effective configuration can be printed in a
sanitized form; no secret-bearing fields are introduced in this Phase.

Each non-blank/non-comment line is exactly `key=value`; keys and values are
trimmed, `#` and `!` start whole-line comments, and escapes, continuation lines,
key-only lines and duplicate keys are rejected. Built-in defaults are applied
first and the file is the only override layer. CLI flags select the file/action
but never override a configuration key. Environment variables, JVM system
properties, remote configuration and live reload are not precedence layers.
The exact key/default/range table is normative in the Phase 10 Blueprint.

### D7 — Network defaults are local and trusted

Protocol v1 and the management boundary default to loopback-only binds. Phase
10 makes no TLS, authentication, authorization or public-Internet safety claim.
An untrusted or non-loopback default, TLS/auth expansion or Protocol v2 requires
an Exception Gate and separate Human architecture approval.

### D8 — Operational status uses a bounded local protocol

Liveness, readiness, status and counters observe immutable runtime lifecycle
snapshots and monotonic boundary counters. They do not traverse or mutate live
OrderBook or MatchingEngine state. No metrics/logger/health path may become a
second producer, block the engine consumer or introduce an unbounded queue.

The management adapter is loopback-only and uses a Phase-10-local ASCII
request/canonical-JSON-line response protocol over TCP. A connection may send
exactly one of `LIVE\n`, `READY\n`, `STATUS\n` or `METRICS\n`, then receives one
response and is closed. Requests are at most 32 bytes, responses at most 2048
UTF-8 bytes, backlog is 16, concurrent connections are capped at 16 and the
default request timeout is 1000 ms. One explicitly owned Netty event-loop thread
serves this boundary. Unknown/oversized/multiple requests fail and close.

The canonical status schema version is 1 and contains, in fixed order:
`schemaVersion`, `state`, `live`, `ready`, `failureCode`, `protocolBound`,
`recoveryMode`, `acceptedCommands`, `terminalFailures` and `uptimeMillis`.
`METRICS` returns the same monotonic counters plus `managementRequests` and
`managementRejected`. No exception message, order, trade or path is exposed.
Existing Netty is reused; no new dependency is introduced.

### D9 — Exit outcomes are stable and scriptable

The application exposes these process outcomes:

| Code | Meaning |
| ---: | --- |
| `0` | clean shutdown |
| `2` | command-line or configuration rejection |
| `3` | storage, recovery or startup convergence failure |
| `4` | required listener bind failure |
| `5` | terminal runtime failure |
| `6` | bounded shutdown timeout |

Unhandled startup/runtime exceptions must map to the narrowest applicable
outcome and preserve the causal diagnostic without printing command payloads or
sensitive filesystem contents unnecessarily.

### D10 — Packaging is reproducible, not an installer

Phase 10 may produce one executable Java artifact named
`core/target/matching-engine-rc.jar` and recorded launch examples from the
existing Maven reactor. Packaging must not change runtime semantics or
embed machine-specific configuration. Containers, service managers,
orchestrators, deployment automation and signed distribution are deferred.

### D11 — Qualification exercises the assembled process

Evidence starts the packaged application as a child process, interacts only
through Protocol v1 and the management boundary, and verifies configuration,
startup, readiness, shutdown, restart and fail-closed behavior. The normative
full campaign is the Blueprint-defined `RC_ASSEMBLED_RUNTIME_V1` campaign;
component-only evidence cannot substitute for it.

### D12 — Performance remains characterization

Phase 10 records startup-to-ready, graceful-shutdown, response latency,
durability and management-path overhead on a recorded host. Results remain
environment-specific engineering evidence. A performance result cannot change
defaults or authorize optimization; production optimization requires a
separate ADR and Human approval.

### D13 — Existing evidence and baseline tags remain immutable

`v0.8.0-engineering-baseline` remains fixed. Phase 9 qualification artifacts
remain historical evidence and are not regenerated or reclassified. Phase 10
creates its own manifests, hashes and reports.

### D14 — Storage lifecycle expansion is deferred

WAL retention, compaction, truncation, archival and disk-quota policy remain
out of scope. The runtime must surface storage/configuration failures and stop
admission safely, but it must not invent deletion authority.

### D15 — Session and delivery semantics remain unchanged

Multiple active sessions, a second producer, request pipelining,
reconnect/deduplication, exactly-once delivery and ambiguous-result recovery
remain deferred. Runtime assembly must not hide the existing ambiguity after a
durable command when client receipt is unknown.

### D16 — Candidate tag is not Product Release

After TASK-041 through TASK-046, automated Evidence Gates, Sol High Closure
Review and Human Closure Approval, Phase 10 may propose an annotated
`v0.9.0-rc.1` tag on a verified master merge commit. That tag represents a
release-candidate engineering checkpoint, not `v0.9.0`, Production Ready or a
Product Release.

## Alternatives Considered

| Option | Benefit | Reason not selected now |
| --- | --- | --- |
| Release-candidate runtime assembly | Closes the operational composition gap around already-qualified components | Selected |
| WAL retention and compaction | Bounds long-term disk growth | Requires operational ownership and destructive retention policy first; deferred |
| Production performance optimization | Could reduce measured tails | No approved blocking hotspot; optimization must remain evidence-driven |
| Multi-session sequencing | Increases connectivity | Changes producer, identity, ordering and recovery architecture; separate Phase |
| Immediate Product Release | Produces a marketable release label | Security, operations and release claims are not yet authorized |

## Consequences

The repository gains a reproducible runtime boundary and operational evidence
without changing the trading core. Phase 10 deliberately exposes remaining
limitations rather than masking them: trusted-network use, single session,
unbounded WAL lifecycle, no HA, no exactly-once and no hardware power-loss
guarantee.

## Exception Gates

Stop and return for Human review if implementation requires:

- a change to matching, sequence, durability, protocol or recovery semantics;
- a new producer, hidden executor, unbounded queue or engine-state observer;
- a new dependency;
- TLS/auth, non-loopback-by-default exposure or Protocol v2;
- WAL deletion/retention authority;
- multiple sessions, reconnect/deduplication or exactly-once semantics;
- a configuration or management contract different from D6-D9;
- changing an accepted threshold or default to obtain a performance PASS;
- a Product Release or Production Ready claim.

## Approval Record

| Date | Reviewer | Decision | Scope |
| --- | --- | --- | --- |
| 2026-08-24 | Human Developer | Pending | ADR-0018 D1-D16 and Phase 10 Blueprint |
