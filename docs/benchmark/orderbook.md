# OrderBook Benchmark

## Status

Pending baseline implementation.

## Planned Comparisons

- Price-level insert
- Best bid and ask lookup
- Cancel by `OrderId`
- Matching across one price level
- Matching across multiple price levels
- Empty price-level cleanup

## Comparison Rule

All alternatives must process the same generated event stream and validate the same final state.
