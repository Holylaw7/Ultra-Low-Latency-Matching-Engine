# MASTER_PROMPT - Ultra-Low-Latency Matching Engine

> 项目名称：Ultra-Low-Latency Matching Engine
> 项目定位：单机高性能、低延迟、确定性撮合引擎
> 主要语言：Java 21
> 开发模式：Human-led Architecture + Codex-assisted Engineering
> 当前阶段：Project Bootstrap
>
> 本文件是 Codex 在本项目中的最高优先级项目级行为规范。
> 每次启动新的 Codex 会话时，必须首先阅读：
>
> 1. `.codex/MASTER_PROMPT.md`
> 2. `.codex/DEVELOPMENT_RULES.md`
> 3. `.codex/AGENT_CONTEXT.md`
>
> 然后检查当前 Git 状态、项目结构、构建状态和已有测试。
> 不允许跳过上下文恢复直接修改核心代码。

> **未经测量，不做优化；未经验证，不下结论；无法解释，不合并实现。**

---

## 1. 项目使命

本项目不是普通的交易业务系统，也不是 CRUD 项目。

目标是从零构建一个：

> **Single-Node Ultra-Low-Latency Matching Engine**

重点研究：

- Limit Order Book
- Price-Time Priority
- 高性能订单处理
- 单线程确定性撮合
- Lock-free / Low-contention Pipeline
- Disruptor / RingBuffer
- CPU Cache Locality
- False Sharing
- Object Allocation
- JVM GC
- Netty
- WAL
- Crash Recovery
- Deterministic Replay
- JMH Benchmark
- async-profiler / JFR 性能分析

项目必须能够解释：

1. 为什么 OrderBook 采用当前设计？
2. 为什么撮合核心采用单线程或 Actor 模型？
3. 为什么外围使用 RingBuffer？
4. 如何做到 Cancel O(1)？
5. 如何减少 GC？
6. 如何降低 CPU Cache Miss？
7. 如何处理 False Sharing？
8. 如何保证撮合结果 Deterministic？
9. 崩溃后如何通过 WAL 恢复？
10. Benchmark 数据如何产生？
11. 性能瓶颈通过什么 Profile 工具定位？
12. 每一次性能优化是否有可重复的实验数据？

---

## 2. 核心设计原则

### 2.1 正确性优先于性能

任何优化都不能破坏：

- Price-Time Priority
- Order Sequence
- Partial Fill
- Cancel Semantics
- Order State
- Trade Generation
- WAL Ordering
- Recovery Correctness

禁止为了 Benchmark 数字牺牲交易语义。

### 2.2 Determinism 优先

相同输入事件序列必须得到：

- 相同订单簿
- 相同成交序列
- 相同订单状态
- 相同 sequence
- 相同最终 state hash

禁止依赖：

- 当前时间作为撮合顺序
- HashMap 随机遍历顺序
- 非确定性并发竞争
- 未定义线程调度结果

### 2.3 Critical Path 极简

撮合核心 Critical Path 中禁止无必要的：

- `synchronized`
- `ReentrantLock`
- blocking I/O
- 网络 I/O
- 数据库访问
- Redis
- MQ
- 日志打印
- 大量对象创建
- String 拼接
- JSON 序列化

核心路径应该尽量接近：

```text
Order Event
    -> Validate
    -> OrderBook
    -> Match
    -> Trade
    -> State Update
```

### 2.4 Benchmark 驱动优化

禁止：

> “我觉得这样会更快。”

必须遵循：

```text
Baseline
    -> Benchmark
    -> Profile
    -> Identify Bottleneck
    -> Form Hypothesis
    -> Optimize
    -> Benchmark
    -> Compare
    -> Keep/Revert
```

任何性能优化必须有数据支持。

---

## 3. 系统总体架构

目标架构：

```text
Client
    |
    v
Netty
    |
    v
Decoder
    |
    v
Ingress
    |
    v
RingBuffer / Disruptor
    |
    v
Matching Engine
    |
    v
OrderBook
    |
    v
Trade Event
    |
    v
Event Consumer
    |
    +---- WAL
    +---- Output
    +---- Metrics
```

核心撮合：

```text
Matching Engine
    |
    +---- Bid Side
    +---- Ask Side
```

订单簿必须实现：

- Price Priority
- Time Priority
- O(1) Cancel
- Efficient Best Bid/Ask
- Partial Fill
- Empty Price Level Cleanup

---

## 4. 技术栈

默认技术栈：

- Java 21
- Maven
- JUnit 5
- JMH
- Netty
- LMAX Disruptor
- Java NIO
- JFR
- async-profiler
- GitHub Actions

除非存在明确技术原因，不得随意增加第三方依赖。

任何新增依赖必须说明：

1. 为什么需要
2. 是否可以自行实现
3. 对性能和维护性的影响
4. License 是否合适

---

## 5. 开发阶段

必须按照以下阶段推进。

### Phase 0 - Project Bootstrap

建立：

- Maven 项目
- Java 21
- Git
- CI
- 基础测试框架
- Benchmark 模块
- 文档结构

### Phase 1 - Domain Model

实现：

- Order
- OrderType
- Side
- OrderStatus
- Trade
- Execution
- OrderId
- Price
- Quantity
- Sequence

首先保证模型正确。

### Phase 2 - Basic OrderBook

实现：

- BidBook
- AskBook
- PriceLevel
- OrderQueue

支持：

- Add
- Cancel
- Best Bid
- Best Ask
- Match

### Phase 3 - Matching Engine

实现：

- Limit Order
- Market Order
- Partial Fill
- Multiple Price Levels
- Price-Time Priority

建立完整的 correctness test suite。

### Phase 4 - High Performance OrderBook

依次研究：

1. TreeMap
2. Custom Tree
3. SkipList
4. Intrusive Linked List
5. OrderId Index
6. Cache-friendly layout

每一个版本必须 Benchmark。
禁止无数据重构。

### Phase 5 - Event Pipeline

引入：

- RingBuffer
- Disruptor
- Single Producer / Multi Producer 对比
- Single Consumer

研究：

- Lock contention
- Throughput
- Latency
- Backpressure

### Phase 6 - Network Layer

使用 Netty 实现：

- TCP Server
- Binary Protocol
- Decoder
- Encoder
- Connection Management

禁止 JSON 作为核心性能路径协议。
可以提供 JSON 或文本协议用于 Debug。

### Phase 7 - WAL

实现：

- Sequential WAL
- Sequence Number
- CRC
- Append
- Flush Policy
- Segment
- Recovery

必须保证：

> WAL Replay 后状态与崩溃前状态一致。

### Phase 8 - Snapshot + Recovery

实现：

```text
Snapshot
    -> WAL Position
    -> Restart
    -> Snapshot Load
    -> WAL Replay
    -> State Verification
```

### Phase 9 - Deterministic Replay

输入：

```text
orders.log
```

输出：

- trades
- order states
- order book
- state hash

相同日志必须得到相同结果。

### Phase 10 - Performance Engineering

使用：

- JMH
- JFR
- async-profiler
- GC logs
- Linux perf（如果环境支持）

重点分析：

- CPU cycles
- Allocation rate
- GC
- Branch prediction
- Cache locality
- Lock contention
- False sharing
- Context switching

### Phase 11 - Benchmark Suite

至少包含：

- Benchmark A：Pure Matching
- Benchmark B：OrderBook
- Benchmark C：RingBuffer Pipeline
- Benchmark D：WAL
- Benchmark E：Recovery
- Benchmark F：TCP End-to-End

### Phase 12 - Chaos / Failure Testing

测试：

- Process crash
- WAL partial write
- Duplicate Order
- Duplicate Cancel
- Invalid Order
- Sequence Gap
- Corrupted WAL
- Recovery

### Phase 13 - Release

最终必须具备：

- 完整测试
- Benchmark
- Performance Report
- Architecture Document
- ADR
- Recovery Document
- README
- CI
- Release Tag

---

## 6. Agent 工作方式

Codex 不应该一次性实现整个系统。

必须采用：

> Small Step -> Test -> Review -> Commit

每次任务：

1. 理解当前架构
2. 找到最小修改范围
3. 实现
4. 编写或更新测试
5. 执行相关测试
6. 检查性能影响
7. 检查 Git diff
8. 总结结果

---

## 7. Codex 禁止行为

禁止：

- 未经确认修改整体架构
- 大规模重构
- 删除测试来让 Build 通过
- 修改 Benchmark 数据
- 编造性能数据
- 删除失败 Benchmark
- 用 Mock 冒充真实测试
- 用注释解释错误实现
- 为了测试通过修改正确性规则
- 引入不必要依赖
- 把业务逻辑塞进 Infrastructure
- 在 Critical Path 中加入日志
- 用 `synchronized` 解决所有并发问题
- 用线程数量堆吞吐
- 声称性能目标已经达成而没有 Benchmark 证据

---

## 8. 性能数据可信度原则

所有性能数据必须包含：

- CPU
- CPU Core
- RAM
- OS
- JVM
- JDK Version
- JVM Arguments
- Benchmark Duration
- Warmup
- Threads
- Dataset Size
- Order Distribution
- Price Distribution

Benchmark 报告必须区分：

- Throughput
- P50
- P95
- P99
- P999
- Allocation Rate
- GC
- CPU Usage

禁止只报告平均延迟。

---

## 9. 工程质量

代码必须：

- 可读
- 可维护
- 有测试
- 有 Benchmark
- 有 ADR

文档必须覆盖：

- Architecture
- Design Decisions
- Benchmark
- Recovery
- Performance Analysis

每一个重大技术决策必须能够回答：

> Why?

---

## 10. 人类决策权

Human 是：

- Architecture Owner
- Product Owner
- Performance Target Owner
- Final Reviewer

Codex 是：

- Implementation Agent
- Test Agent
- Refactoring Agent
- Benchmark Agent
- Documentation Agent

Codex 不得自行决定：

- 核心架构
- 性能指标
- 发布标准
- 删除重大功能
- 改变交易规则

遇到架构级问题必须先报告。

---

## 11. 每次任务结束必须输出

### Implementation Summary

- 修改内容
- 修改原因

### Tests

- 执行命令
- 测试数量
- 成功或失败

### Benchmark

如果涉及性能：

- Baseline
- Current
- Improvement
- Regression

### Risks

- 已知风险
- 未验证内容

### Next Step

- 建议下一步

---

## 12. 最终成功标准

项目完成不以代码量为标准。

最终必须证明：

1. 撮合语义正确
2. OrderBook 正确
3. Cancel 高效
4. Critical Path 低分配
5. Pipeline 高吞吐
6. WAL 可恢复
7. Replay Deterministic
8. Crash Recovery 正确
9. Benchmark 可重复
10. 性能优化有证据

最终目标：

> Build a technically credible, measurable, deterministic, single-node ultra-low-latency matching engine.

