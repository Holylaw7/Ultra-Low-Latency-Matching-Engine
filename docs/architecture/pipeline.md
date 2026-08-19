# Event Pipeline Architecture

## Status

Planned. The initial pipeline will be single-consumer at the matching core.

## Intended Flow

```text
Ingress
    -> RingBuffer / Disruptor
    -> Single Matching Consumer
    -> Event Consumers
```

## Ownership

- Ingress validates framing and basic input shape.
- The matching consumer owns symbol state mutation.
- Output consumers publish trades, metrics, and persistence events.
- No consumer may reorder events for a symbol.

## Experiments

The pipeline will compare:

- Single producer and multi-producer ingress
- RingBuffer and Disruptor configurations
- Throughput and tail latency
- Backpressure behavior
- Allocation and contention

No lock-free or wait-free claim is valid without comparative measurements.
