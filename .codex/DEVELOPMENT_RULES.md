# DEVELOPMENT_RULES - Matching Engine Development Rules

## 1. General Rules

### Rule 1 - Read Before Modify

修改任何代码之前必须：

1. 阅读相关源码
2. 阅读相关测试
3. 阅读相关 ADR
4. 检查 Git diff
5. 理解当前实现

禁止盲改。

### Rule 2 - Minimal Change

优先：

> 用最小修改解决问题。

禁止无关：

- 重构
- 格式化
- 文件移动
- API 修改
- 依赖升级

### Rule 3 - Test First

新增功能必须至少包含：

- Happy Path
- Boundary Case
- Invalid Input
- State Transition

核心撮合逻辑必须具备：

- Unit Test
- Property Test（适合时）
- Replay Test

---

## 2. Trading Semantics

撮合必须遵守：

### Price Priority

买单：最高价格优先。

卖单：最低价格优先。

### Time Priority

同价格：先进入订单优先。

### Partial Fill

订单允许部分成交。

### Fully Filled

剩余数量为 0 时：

```text
OrderStatus = FILLED
```

### Cancel

- 已成交订单不能 Cancel。
- 已取消订单的 Cancel 必须幂等。

---

## 3. Sequence

所有进入 Matching Engine 的事件必须拥有 Monotonic Sequence。

例如：

```text
1
2
3
4
5
```

禁止：

```text
10
8
9
```

Sequence 是以下能力的重要基础：

- Replay
- WAL
- Debug
- Determinism

---

## 4. OrderBook Rules

订单簿必须满足：

```text
BestBid < BestAsk
```

不存在交叉时。

撮合过程中：

```text
BestBid >= BestAsk
```

表示存在可成交订单。

撮合结束后必须恢复：

```text
BestBid < BestAsk
```

---

## 5. O(1) Cancel

禁止：

```text
遍历整个订单队列寻找 OrderId
```

应该：

```text
OrderId
    -> OrderNode
    -> prev / next
```

实现 O(1) 删除。

---

## 6. Memory Rules

Critical Path 中禁止不必要的：

```java
new Object()
new String()
String.format()
Boxing
Stream
Lambda allocation
```

禁止在高频路径中使用：

```java
Map<Long, Object>
```

除非 Benchmark 证明其合理。

优先：

- Primitive
- Arrays
- Intrusive structures
- Object reuse
- Pre-allocation

---

## 7. Concurrency Rules

默认原则：

> 一个 Symbol 的 OrderBook 由一个 Matching Thread 顺序修改。

禁止多个线程同时修改同一个 OrderBook。

并发应该发生在：

```text
Network
Ingress
Pipeline
Egress
Persistence
```

而不是：

```text
OrderBook mutation
```

---

## 8. False Sharing

共享热点变量必须考虑 Cache Line。

例如：

```java
volatile long sequence;
```

如果存在多个线程频繁修改，必须评估：

- Padding
- `@Contended`
- Memory Layout

但是：

> 不允许为了“看起来高级”而盲目使用 padding。

必须 Benchmark。

---

## 9. Lock-free Rules

禁止因为 Lock-free 看起来更高级而使用 Lock-free。

必须回答：

1. 为什么需要？
2. Lock 版本性能是多少？
3. Lock-free 版本性能是多少？
4. 延迟尾部是否改善？
5. CPU 是否增加？
6. Complexity 是否增加？

---

## 10. WAL Rules

WAL 必须：

- 顺序写
- 有 sequence
- 可检测 corruption
- 可 replay
- 可恢复

禁止：

> WAL 成为不可验证的黑盒。

---

## 11. Recovery Rules

Recovery 必须验证：

```text
Original State == Recovered State
```

至少比较：

- Order count
- Order state
- Quantity
- Price
- Sequence
- Trade count
- State hash

---

## 12. Determinism Rules

相同：

```text
Input Events
```

必须产生：

```text
Same Trades
Same OrderBook
Same State Hash
```

禁止依赖以下因素作为业务顺序依据：

- `System.currentTimeMillis()`
- Thread scheduling
- Hash iteration order
- Random UUID
- Concurrent race

---

## 13. Benchmark Rules

Benchmark 不允许：

- 手工修改结果
- 删除失败结果
- 只跑一次
- 只报告最好结果
- 忽略 JVM warmup
- 把 Debug 模式数据当正式数据

JMH Benchmark 必须：

- Warmup
- Measurement
- Fork
- 明确参数

---

## 14. Performance Optimization Rules

任何优化都必须留下：

```text
Before
After
Delta
```

例如：

```text
Before:
850K ops/s

After:
1.12M ops/s

Improvement:
31.7%
```

如果性能下降，必须记录原因。

---

## 15. Code Quality

禁止：

- God Class
- 巨型方法
- 隐式状态
- 静态全局变量
- 魔法数字
- 魔法字符串

核心代码必须优先可读性。

---

## 16. Dependency Rules

第三方依赖必须：

- 有明确理由
- 有稳定版本
- License 可接受
- 不重复造轮子

核心 OrderBook 不允许依赖大型框架。

---

## 17. Git Rules

每个逻辑完整功能一个 Commit。

推荐：

```text
feat(orderbook): implement price level
feat(match): implement limit order matching
feat(wal): add sequential wal
perf(orderbook): reduce cancel allocation
test(recovery): verify deterministic replay
docs(benchmark): add orderbook benchmark
```

禁止使用以下无意义 Commit Message：

```text
update
fix
test
aaa
final
```

---

## 18. Definition of Done

一个任务只有满足以下条件才算完成：

- Code complete
- Tests complete
- Build passed
- Relevant benchmark passed（如果涉及性能）
- Git diff reviewed
- No obvious regression

---

## 19. Failure Handling

如果测试失败，不要立即修改测试。

首先：

1. 分析失败
2. 判断实现错误还是测试错误
3. 找到 Root Cause
4. 修复实现
5. 再运行测试

---

## 20. Architecture Change

以下变化必须暂停并报告：

- 改变 Matching Model
- 改变 OrderBook 核心结构
- 改变 WAL Format
- 改变 Event Ordering
- 引入新的 Persistence Model
- 引入分布式架构
- 改变核心 Protocol

Codex 不得自行完成架构级重构。

---

## 21. AI-Assisted Development Rule

Codex 生成的任何代码都必须：

> 被人类能够解释。

因此不要生成：

- 无法解释的复杂 Lock-free Algorithm
- 无法证明正确的内存优化
- 无意义的 Unsafe Hack
- 为 Benchmark 特制的代码路径

如果 Codex 无法解释一个优化：

> 不应该合并。

