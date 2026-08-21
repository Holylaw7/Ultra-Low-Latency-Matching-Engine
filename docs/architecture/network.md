# Phase 6 Network Boundary

## Implemented topology

```text
Netty EventLoop
    -> strict binary frame decoder / request validation
    -> single-session gateway
    -> bounded SPSC Disruptor pipeline
    -> frozen MatchingEngine
    -> Pipeline Result Handler
    -> Netty EventLoop scheduling
    -> ordered response encode / write / flush
```

The gateway permits one active session and one request in flight. Manual reads
provide server-side flow control; a `FULL` admission result is retryable and
consumes neither the client request ID nor the gateway-owned Command Sequence.
The pipeline consumer never mutates Netty connection state directly: result
handling is scheduled back to the channel's EventLoop.

## Identity domains

The implementation keeps these values independent:

```text
Client Request ID
    != Command Sequence
    != Disruptor Ring Sequence
    != EventSequence
    != TradeId
```

The gateway owns request correlation and candidate Command Sequence values;
only accepted publication advances the two counters. Ring sequence is
infrastructure metadata and is never exposed in protocol or matching results.

## Failure and acknowledgement semantics

Malformed frames fail closed. A second session, a second in-flight request, an
unexpected request ID, pipeline `FULL`, gateway failure and channel write
failure have explicit bounded outcomes. If a request is accepted and matching
executes but the response write fails or the connection closes, the client
outcome is ambiguous. Phase 6 provides no reconnect, retry deduplication,
exactly-once, rollback or client-receipt guarantee.

Local write completion is not a durable acknowledgement. The network adapter
does not publish to the command WAL and does not change Phase 5 recovery
authority.

## Frozen and deferred boundaries

Domain, OrderBook, MatchingEngine, WAL and Recovery remain frozen relative to
`v0.4.0-engineering-baseline`. Phase 6 uses only the Blueprint-authorized
additive pipeline terminal-failure observer; the Phase 4 execution,
backpressure and ownership semantics remain unchanged. Phase 6
does not add live WAL/Pipeline durability integration, multiple active sessions,
request pipelining, reconnect/session recovery, TLS/authentication, Snapshot,
online Recovery, native transport or Product Release. These require separate
ADR/Blueprint approval.

## Evidence boundary

The current evidence covers strict byte contracts, fragmented/coalesced input,
single-session loopback behavior, ordered result delivery, failure handling and
component-level codec/loopback JMH measurements. It does not establish a
durable network service, Internet security posture, concurrent-client scaling,
or production latency/throughput target.
