# Phase 2 Report - Implementation Structural Limit Matching

## 1. Report Metadata

| Field | Value |
| --- | --- |
| Phase | `Phase 2 - Basic OrderBook` |
| Stage | `Implementation - Structural Limit Matching` |
| Task | `TASK-20260819-004-basic-orderbook` |
| ADR | [`ADR-0008-structural-limit-matching.md`](../../docs/adr/ADR-0008-structural-limit-matching.md) |
| Report Date | `2026-08-19` |
| Stage Status | `Completed - Pending Human Approval` |
| Next Approval Gate | `Pending Human Approval` |

## 2. Objective

在已批准的 ADR-0008 范围内完成确定性的 Structural Limit Matching：

- 仅处理 `NEW` 限价 incoming order；
- 按价格优先和同价 FIFO 遍历对手盘；
- 使用 resting maker price；
- 支持单层、多层、partial/full fill；
- 在 sweep 完成后一次性 resting incoming residual；
- 返回不可变、按撮合顺序排列的 `MatchFragment`；
- 保持 Order、PriceLevel、active index 和 Best Price 状态一致。

本阶段不进入 MatchingEngine、Trade/Execution、事件、持久化、网络或性能优化。

## 3. Implementation Summary

已完成：

- 新增不可变 `MatchFragment` record，不暴露 `OrderNode`，不携带
  `TradeId`、事件 Sequence、时间戳或事件对象。
- 新增 `OrderBook.matchLimit(Order)`。
- 实现 Buy/Sell crossing：

  ```text
  Buy:  incomingPrice >= bestAsk
  Sell: incomingPrice <= bestBid
  ```

- 按 best opposite price 和既有队列 head 实现 price-time priority。
- 每个 fragment 使用 resting maker order price。
- maker 和 taker 通过既有受控 domain execution transition 同步状态。
- maker 完全成交时移除 queue node、空价格层和 active index entry。
- incoming residual 在完整 sweep 后只调用一次 `add`，进入自己的价格层尾部。
- 返回 `List.copyOf(fragments)`，调用方不能修改结果顺序或内容。

## 4. Verification Coverage

测试覆盖：

| Case | Evidence |
| --- | --- |
| Empty opposite side | incoming limit rests, returns no fragments |
| No crossing | incoming limit rests on own side |
| Exact fill | one maker-price fragment and both orders filled |
| Partial maker fill | maker remains at queue head with reduced quantity |
| Taker residual | residual rests at incoming price |
| Residual FIFO | residual appends after existing same-price orders |
| Multi-level buy sweep | best ask to worse asks |
| Multi-level sell sweep | best bid to worse bids |
| Same-price FIFO | earlier maker is consumed first |
| Last maker removal | empty level and active entry are removed |
| Invalid input | null, market, terminal and duplicate-active-id rejected |
| Determinism | identical ordered inputs produce equal fragments and state |
| Quantity invariant | level total equals queued order residual sum |

## 5. Changed Files

Production and tests:

- `src/main/java/com/ultralatency/matching/orderbook/MatchFragment.java`
- `src/main/java/com/ultralatency/matching/orderbook/OrderBook.java`
- `src/test/java/com/ultralatency/matching/orderbook/OrderBookTest.java`

Synchronization:

- `.codex/MASTER_PROMPT.md`
- `.codex/AGENT_CONTEXT.md`
- `docs/architecture/order-book.md`
- `docs/architecture/matching-engine.md`
- `tasks/active/TASK-20260819-004-basic-orderbook.md`
- this phase report

ADR-0008 remains the approved decision source. No new architecture decision
was introduced during implementation.

## 6. Verification Evidence

```text
mvn verify
BUILD SUCCESS
Tests: 45
Failures: 0
Errors: 0
Skipped: 0
Checkstyle: 0 violations
```

Additional repository checks:

```text
git diff --check
PASS
```

No benchmark or profile was executed. This stage produces correctness evidence
only; it does not authorize throughput, latency, allocation, GC or P99
performance conclusions.

## 7. Scope and Domain Boundary

No changes were made to Phase 1 `Order`, `OrderStatus`, `Trade`, `Execution` or
their value objects. The implementation does not create Trade/Execution
values, TradeIds, event sequences or publication events.

The following remain out of scope:

- Market Order, IOC, FOK and slippage policy；
- MatchingEngine orchestration；
- WAL, Snapshot, Recovery and Network；
- Disruptor, locks, concurrent iteration and callbacks；
- Custom Tree, SkipList, Radix, Price Array and off-heap layouts；
- benchmark-driven optimization.

Shade Plugin overlap warnings remain known benchmark packaging technical debt
and were not changed by this stage.

## 8. Approval Request

请求 Human Developer 审查并批准本实现阶段。

当前状态：

```text
Implementation - Structural Limit Matching:
    Completed - Pending Human Approval

Next Stage:
    Verification

Authorization:
    Verification is not authorized until Human Approval is recorded
```

审批通过后，下一阶段应单独输出 Verification 报告；在此之前不开始
Verification、Benchmark 或任何 Phase 3 实现。
