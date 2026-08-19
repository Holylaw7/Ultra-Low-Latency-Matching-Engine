# Phase 2 Report - Structural Limit Matching ADR / Decision

## 1. Report Metadata

| Field | Value |
| --- | --- |
| Phase | `Phase 2 - Basic OrderBook` |
| Stage | `ADR / Decision - Structural Limit Matching` |
| Task | `TASK-20260819-004-basic-orderbook` |
| ADR | [`ADR-0008-structural-limit-matching.md`](../../docs/adr/ADR-0008-structural-limit-matching.md) |
| Report Date | `2026-08-19` |
| Stage Status | `Completed - Pending Human Approval` |
| Next Approval Gate | `Pending Human Approval` |

## 2. Objective

在进入 Structural Limit Matching 生产实现前，冻结输入状态、买卖交叉规则、
价格时间优先级、maker-price 语义、结构化结果边界、残量 resting 以及
active index 生命周期，并确保该决策与 ADR-0001、ADR-0005、ADR-0007、
任务方案和现有 OrderBook 架构一致。

## 3. Discovery Evidence

已审查：

- Phase 2 Sub-stage 1-3 实现和测试；
- `OrderBook`、`SideBook`、`PriceLevel`、`OrderQueue`、`OrderNode`；
- Phase 1 `Order`、`OrderStatus`、`Trade`、`Execution` 和值对象；
- ADR-0001、ADR-0005、ADR-0007；
- `order-book.md`、`matching-engine.md`、`AGENT_CONTEXT.md` 和任务方案；
- 当前分支、最近提交及工作区状态。

当前基线：

```text
HEAD: 2e17e41 feat(orderbook): add orderbook aggregate and active index
Branch: feature/domain-model
Working tree before this documentation change: clean
```

当前已有的 `OrderBook.applyExecution` 仅用于已知活动订单的状态同步，不负责
选择对手盘或产生结构化结果，可作为后续匹配实现的内部基础。

## 4. Proposed Decision

ADR-0008 提议：

- 新增 `OrderBook.matchLimit(Order)`；
- 返回不可变、按撮合顺序排列的 `List<MatchFragment>`；
- `MatchFragment` 只包含 maker/taker ID、maker price、成交数量及双方成交后
  剩余数量；
- 仅接受 `NEW` 状态的限价 incoming order；
- Buy 在 `incomingPrice >= bestAsk` 时交叉，Sell 在
  `incomingPrice <= bestBid` 时交叉；
- 以对手盘 best price 和同价 FIFO 顺序遍历；
- 成交价格始终为 resting maker order price；
- maker/taker 使用既有 `Order.applyExecution()` 生命周期；
- maker 完全成交时同步移除节点、价格层和 active index；
- incoming residual 仅在撮合结束后一次性进入自己的 side book；
- 不创建 `OrderBookMatch`、`Trade`、`Execution`、TradeId 或事件。

该 ADR 目前为 `Proposed`，尚未授权生产代码或生产测试实现。

## 5. Options and Decision Boundary

已记录并比较：

1. `OrderBook` 返回结构化 `MatchFragment`：提议采用；
2. 暴露可变 `OrderNode` / 队列遍历：拒绝；
3. 在 OrderBook 内创建 `Trade` / `Execution`：拒绝；
4. 当前阶段引入 `OrderBookMatch` 聚合：延期。

## 6. Scope

ADR 审批后允许：

- Structural Limit Matching；
- 单层、多层、双方向撮合；
- partial/full fill；
- maker-price；
- residual resting；
- 确定性和不变量测试。

仍禁止：

- Market Order、IOC/FOK、slippage；
- MatchingEngine；
- Trade/Execution 生成或发布；
- WAL、Snapshot、Recovery、Network、Disruptor；
- 性能替代结构和性能结论。

## 7. Verification Evidence

本阶段为文档与决策阶段，未修改生产代码、测试代码或构建配置，未执行
新增运行时测试。已完成只读架构/API 审查和文档一致性准备。

计划在 ADR 与任务方案获批后的实现阶段验证：

- 空对手盘和非交叉 resting；
- 单层 exact/partial/full；
- 多层 sweep；
- 同价 FIFO；
- 双方向 buy/sell；
- maker-price；
- residual resting；
- empty-level cleanup；
- active index 与数量不变量；
- 相同输入的 fragments 和最终状态确定性。

## 8. Approval Request

请 Human Developer 审批：

1. [`ADR-0008-structural-limit-matching.md`](../../docs/adr/ADR-0008-structural-limit-matching.md)；
2. 任务方案中 Structural Limit Matching 的范围、API 和验收标准；
3. 进入下一阶段：

```text
Implementation - Structural Limit Matching
```

在审批完成前保持：

```text
ADR-0008: Proposed
Production implementation: Not authorized
Structural matching tests: Not authorized
Next gate: Pending Human Approval
```
