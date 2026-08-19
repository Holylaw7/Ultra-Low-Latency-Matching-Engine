# MASTER_PROMPT - Ultra-Low-Latency Matching Engine

> 项目名称：Ultra-Low-Latency Matching Engine
> 项目定位：单机高性能、低延迟、确定性撮合引擎
> 主要语言：Java 21
> 开发模式：Human-led Architecture + Codex-assisted Engineering
> 当前阶段：Phase 1 - Domain Model（Governance Alignment）
>
> 本文件是 Codex 在本项目中的最高优先级项目级行为规范。
> 每次启动新的 Codex 会话时，必须首先阅读：
>
> 1. `.codex/MASTER_PROMPT.md`
> 2. `.codex/DEVELOPMENT_RULES.md`
> 3. `.codex/AGENT_CONTEXT.md`
> 4. `tasks/README.md`
> 5. 所有 `tasks/active/` 下与当前任务相关的方案
>
> 然后检查当前 Git 状态、项目结构、构建状态、已有测试和任务方案状态。
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

### 6.1 方案先行

任何开发任务必须先在 `tasks/active/` 创建任务方案，方案至少包含：

- Task ID 和标题
- 背景、目标和非目标
- 需求与验收标准
- 当前实现和影响范围
- 设计方案与候选方案
- 文件变更计划
- 测试计划
- Benchmark / Profile 计划
- 风险与回滚方案
- Git 提交计划
- 当前开发阶段、下一审批门禁和阶段报告记录方式

任务状态必须遵循：

```text
Proposed
    -> Approved
    -> In Progress
    -> Completed
```

方案处于 `Proposed` 状态时：

- Codex 可以阅读代码、补充方案和执行只读验证。
- Codex 不得修改生产代码、测试代码、构建配置或运行时行为。
- 方案涉及架构、协议、事件顺序、WAL、Snapshot 或恢复策略时，必须明确标记需要 Human 决策。

### 6.2 决策与 ADR 关联

任务方案的 `Decision` 小节必须明确记录 ADR 关联：

- 识别出需要长期保留的技术决策时，必须先创建或更新 `docs/adr/` 下状态为 `Proposed` 的 ADR 草案。
- ADR 草案必须先记录 Context、Problem、Options、Proposed Decision、Scope Boundary、Consequences 和验证计划，再进行技术决策或审批。
- Human Review 和技术决策只能发生在 ADR 草案存在之后，结果必须回写 ADR 状态。
- 任务方案必须记录 ADR 的相对路径、编号、标题和当前状态。
- ADR 状态与审批结果一致后，任务方案才能获批并进入实现。
- 任务方案中的决策必须与 ADR 中的 `Decision` 内容一致；任务方案负责执行范围，ADR 负责长期决策记录。
- 如果明确不需要 ADR，必须写明 `ADR: Not required` 及不需要的理由，不能留空或只勾选 `No architecture change`。
- ADR 路径失效、状态不一致或决策发生变化时，必须先同步任务方案和 ADR，再继续实现。

只有 Human 明确审批后，任务才能进入 `Approved`，随后才允许开始实现。开始实现时将状态改为 `In Progress`，完成验收、测试、文档和 Git 提交后改为 `Completed`，并将方案移动到 `tasks/completed/`。

如果实现范围、设计、验收标准或风险发生变化，必须先更新任务方案并重新审批。不得通过代码先行的方式绕过方案审批。

### 6.3 阶段报告与逐步审批

任务必须按风险和交付边界划分为若干开发阶段，至少区分：

```text
ADR / Decision
    -> Task Approval
    -> Implementation
    -> Verification
    -> Documentation and Synchronization
```

每个阶段完成后，Codex 必须：

1. 输出并在任务方案的 `Phase Reports and Approval Gates` 中记录阶段报告。
2. 报告目标、实际完成内容、文件范围、验证证据、偏差、风险、限制和下一阶段提案。
3. 将下一审批门禁设为 `Pending Human Approval` 并停止执行下一阶段。
4. 等待 Human 明确批准，并记录日期、审批人、决定、约束和备注。

只有审批记录完成后才能进入下一阶段。审批被拒绝、增加约束或发现范围变化时，必须先更新任务方案；如果影响技术决策，必须先创建或更新 ADR，再重新申请审批。

阶段完成后的文档、ADR、任务方案、规范和 `AGENT_CONTEXT.md` 必须同步；同步未完成时不能将任务标记为 `Completed`。

每次任务：

1. 理解当前架构
2. 找到并确认 `tasks/active/` 中对应的已审批方案
3. 找到最小修改范围
4. 实现
5. 编写或更新测试
6. 执行相关测试
7. 检查性能影响
8. 检查 Git diff
9. 输出阶段报告并等待 Human 审批
10. 审批通过后进入下一阶段
11. 更新任务状态、文档和上下文
12. 总结结果

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

---

## 13. 软件工程交付流程

所有开发任务必须遵循以下生命周期：

```text
需求确认
    -> 创建 ADR 草案
    -> Human 决策和审批
    -> 范围与验收标准
    -> 任务方案审批
    -> 最小实现
    -> 阶段报告
    -> Human 审批
    -> 测试与静态检查
    -> 阶段报告
    -> Human 审批
    -> 文档与上下文同步
    -> 阶段报告
    -> Human 审批
    -> Git Commit
    -> 状态确认
```

每个任务开始前必须明确：

- 目标
- 非目标
- 输入与输出
- 验收标准
- 决策及其对应的 ADR 文档，或明确记录不需要 ADR 的理由
- 影响范围
- 风险
- 需要执行的验证命令
- 阶段划分、阶段报告位置和每个阶段的审批门禁

需求不明确、验收标准冲突或涉及架构变化时，Codex 必须先报告，不得自行扩大范围。

实现时必须：

- 保持模块边界清晰
- 优先复用现有抽象和项目约定
- 将业务逻辑与 Infrastructure 分离
- 避免在同一变更中混合功能、无关重构和格式化
- 保持 API、数据格式和事件顺序的兼容性，除非已有明确决策
- 对失败路径、边界条件和资源释放进行处理

交付前必须完成：

- 相关测试
- 构建和静态检查
- 代码差异审查
- 相关 Benchmark 或 Profile
- 文档和 `AGENT_CONTEXT.md` 更新
- 每个完成阶段的报告和 Human 审批记录
- ADR、任务方案、规范和 `AGENT_CONTEXT.md` 的最终同步
- Git 提交前后状态确认

---

## 14. Git 工作流与状态确认

Git 是项目变更的唯一追踪来源。任何代码、测试、配置或文档修改都必须通过 Git 管理。

### 14.1 会话开始检查

每次会话开始必须执行并理解：

```bash
git status --short --branch
git branch --show-current
git log --oneline --decorate -5
git diff --stat
git diff --cached --stat
```

如果存在未提交修改：

- 先区分当前任务修改与既有修改
- 不得覆盖、撤销或重置用户已有修改
- 不得把无关修改混入当前提交
- 若既有修改影响当前任务，必须先报告风险

### 14.2 修改前检查

开始编辑前必须确认：

- 当前分支正确
- 工作区状态已记录
- 目标文件属于当前任务范围
- 不存在未处理的冲突
- 依赖、构建和测试基线已知

### 14.3 分支规则

除初始化或明确授权外，不直接在稳定分支上开发。

分支命名必须使用以下前缀之一：

```text
feature/<short-description>
fix/<short-description>
perf/<short-description>
test/<short-description>
refactor/<short-description>
docs/<short-description>
chore/<short-description>
```

分支必须只承载一个逻辑主题。不得通过创建大量无意义分支规避整理变更。

### 14.4 暂存与 Diff 审查

禁止无审查地执行全量暂存作为默认流程。优先显式指定相关文件：

```bash
git add <file1> <file2>
git status --short
git diff --cached --stat
git diff --cached --check
git diff --cached
```

暂存区审查必须确认：

- 没有密钥、口令、Token、个人配置或敏感数据
- 没有构建产物、临时文件或 IDE 文件
- 没有无关格式化和重命名
- 没有删除测试或降低校验强度
- 变更与任务目标一致

### 14.5 提交前门禁

提交前必须根据变更范围执行：

```bash
git diff --check
git diff --cached --check
```

并执行适用的：

- 编译
- 单元测试
- 集成测试
- 静态分析
- 格式检查
- 相关 Benchmark
- 相关 Recovery / Replay 测试

任何门禁失败时，不得提交“先提交再修复”的半成品。若确需提交未完成工作，必须明确标记并先报告。

### 14.6 提交规范

每个 Commit 必须：

- 只包含一个逻辑完整变更
- 能够独立解释修改原因
- 通过适用的验证
- 使用 Conventional Commits 风格
- 不包含无关修改

格式：

```text
<type>(<scope>): <imperative summary>
```

允许的 `type`：

```text
feat
fix
perf
test
refactor
docs
build
ci
chore
```

示例：

```text
feat(orderbook): implement price level
fix(match): preserve time priority after partial fill
perf(orderbook): reduce cancel allocation
test(recovery): verify deterministic replay
docs(benchmark): record baseline methodology
```

禁止使用以下无意义提交信息：

```text
update
fix
test
aaa
final
temp
```

除非用户明确要求，Codex 不得执行：

- `git reset --hard`
- `git checkout -- <path>`
- `git restore <path>`
- `git clean`
- `git commit --amend`
- `git rebase`
- 强制推送

这些操作可能破坏已有工作或历史，必须先获得明确授权。

### 14.7 提交后确认

每次提交完成后必须执行：

```bash
git status --short --branch
git log -1 --oneline --decorate
git show --stat --oneline HEAD
```

除非存在明确说明的未完成修改，提交后的工作区必须干净。

最终报告必须包含：

- 当前分支
- Commit hash
- Commit message
- 提交包含的文件或逻辑范围
- 执行过的验证命令
- 验证结果
- 是否存在未提交修改

### 14.8 推送与发布

未经用户明确要求，不执行 `git push`。

推送前必须确认：

- 本地工作区干净
- 当前分支和远端目标正确
- 最新提交已通过本地验证
- 没有敏感文件
- 提交历史和变更范围合理

Release Tag 必须建立在已验证的稳定提交上，并记录：

- 版本号
- 变更摘要
- 测试结果
- Benchmark 结果
- 已知限制

---

## 15. Codex 会话完成标准

一次开发任务只有同时满足以下条件才算完成：

1. 需求范围明确。
2. 实现与现有架构一致。
3. 相关测试已新增或更新。
4. 构建和适用的质量门禁通过。
5. 性能相关结论有 Benchmark 或 Profile 证据。
6. 代码 Diff 已审查。
7. 每个开发阶段都有阶段报告，且进入下一阶段前已有 Human 审批。
8. 文档和 `AGENT_CONTEXT.md` 已同步。
9. Git Commit 已完成或明确说明未提交原因。
10. 提交后 Git 状态已确认。
11. 最终报告完整记录变更、验证、风险和下一步。
