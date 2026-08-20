# Phase 2 Report - Implementation Sub-stage 2

## 1. Report Metadata

| Field | Value |
| --- | --- |
| Phase | `Phase 2 - Basic OrderBook` |
| Stage | `Implementation - BidBook / AskBook` |
| Task | `TASK-20260819-004-basic-orderbook` |
| ADR | [`ADR-0007-basic-orderbook-structure-and-boundaries.md`](../../docs/adr/ADR-0007-basic-orderbook-structure-and-boundaries.md) |
| Report Date | `2026-08-19` |
| Stage Status | `Completed - Pending Human Approval` |
| Next Approval Gate | `Pending Human Approval` |

## 2. Objective

完成 Phase 2 的第二个实现子阶段，建立买卖两侧的
side-specific `TreeMap<Price, PriceLevel>` 索引、价格优先级、Best Price
读取和空价格层清理。不进入 `OrderBook` 聚合、active `OrderId` index 或
撮合实现。

## 3. Implemented Scope

- `BidBook` 使用降序价格索引，最高买价为 Best Bid。
- `AskBook` 使用升序价格索引，最低卖价为 Best Ask。
- 包内 `SideBook` 复用两侧的 add、cancel、成交后清理和 Best Price 缓存逻辑。
- 同价订单继续委托给 `PriceLevel`，保持 intrusive FIFO。
- `TreeMap` 保持价格层权威性，`bestLevel` 仅作为 Best Price 读取缓存。
- 取消、完全成交后立即删除空 `PriceLevel`，并刷新 Best Price。
- 部分成交更新价格层数量；完全成交移除节点并清理空层。
- 明确拒绝错误 side、Market Order 和 terminal order。

未实现：

- `OrderBook` 聚合；
- active `OrderId -> OrderNode` index；
- OrderBook 级 Add/Cancel 聚合；
- `OrderBookMatch`、单层/多层撮合；
- `Trade`、`Execution`、MatchingEngine、网络、持久化和 benchmark。

## 4. Changed Files

生产代码：

- `src/main/java/com/ultralatency/matching/orderbook/SideBook.java`
- `src/main/java/com/ultralatency/matching/orderbook/BidBook.java`
- `src/main/java/com/ultralatency/matching/orderbook/AskBook.java`

测试代码：

- `src/test/java/com/ultralatency/matching/orderbook/BidBookTest.java`
- `src/test/java/com/ultralatency/matching/orderbook/AskBookTest.java`

同步文档：

- `docs/architecture/order-book.md`
- `tasks/active/TASK-20260819-004-basic-orderbook.md`
- `.codex/AGENT_CONTEXT.md`
- `tasks/reports/PHASE-2-implementation-ordernode-queue-pricelevel.md`

## 5. Verification Evidence

```text
mvn -pl core -am test
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
Checkstyle: 0 violations
BUILD SUCCESS

mvn verify
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
Checkstyle: 0 violations
BUILD SUCCESS

git diff --check
PASS
```

测试覆盖了：

- Bid 高价优先；
- Ask 低价优先；
- 同价 FIFO；
- Best Bid / Best Ask；
- 非最佳和最佳价格层删除；
- 取消、完全成交后的空层清理；
- 部分成交数量更新；
- 空 book；
- 错误 side、Market Order、terminal order；
- foreign node 和重复取消的 no-op 语义。

未运行 benchmark。本子阶段没有产生吞吐、延迟、分配或 GC 结论。

## 6. Deviations and Risks

- 未修改 Phase 1 `Order`、值对象、Trade 或 Execution。
- 未引入第三方依赖。
- 没有超出 ADR-0007 已批准的 TreeMap side-book 范围。
- active `OrderId` index 尚未实现，因此跨 OrderBook 的全局唯一性和聚合取消
  仍由后续 `OrderBook` 子阶段负责。
- `SideBook` 是包内共享实现，不改变 `BidBook` / `AskBook` 的职责边界。
- Shade Plugin overlap warnings 仍为已知 benchmark packaging 技术债。

## 7. Approval Request

请 Human Developer 审批本实现子阶段，并授权下一子阶段：

```text
OrderBook aggregate
    -> active OrderId -> OrderNode index
    -> OrderBook-level add/cancel aggregation
```

在审批通过前，项目保持：

```text
Implementation Sub-stage 2: Completed
Next Gate: Pending Human Approval
OrderBook / active OrderId index: Not Authorized
Matching: Not Authorized
```
