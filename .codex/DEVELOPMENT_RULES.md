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

---

## 22. 软件工程生命周期

所有任务必须按照以下顺序推进：

```text
Requirement
    -> Scope
    -> Design
    -> Implementation
    -> Verification
    -> Review
    -> Documentation
    -> Commit
```

### 22.1 需求确认

开始实现前必须明确：

- 任务目标
- 非目标
- 输入与输出
- 验收标准
- 影响模块
- 风险
- 验证命令

如果需求存在歧义、冲突或可能改变架构，必须先报告并等待决策。

### 22.2 设计

设计必须说明：

- 现有实现
- 问题根因
- 候选方案
- 选择理由
- 兼容性影响
- 性能影响
- 测试策略
- 回滚或恢复方式

以下情况必须创建或更新 ADR：

- 改变核心数据结构
- 改变并发模型
- 改变事件顺序
- 改变 WAL 或 Snapshot 格式
- 引入或替换关键依赖
- 改变网络协议
- 改变持久化或恢复策略

### 22.3 实现

实现必须：

- 遵循现有模块边界
- 使用项目既有命名和异常处理方式
- 保持方法职责单一
- 显式处理边界条件和失败路径
- 避免无关重构和格式化
- 保持 API 和数据格式兼容，除非已有架构决策
- 不隐藏状态变化

业务逻辑不得下沉到网络、持久化、Metrics 或其他 Infrastructure 层。

### 22.4 验证

验证范围必须与风险匹配：

- 纯函数或领域规则：Unit Test
- 模块交互：Integration Test
- 网络、WAL、Recovery：System / End-to-End Test
- 确定性：Replay Test 和 State Hash Test
- 性能：JMH、JFR 或 async-profiler
- 失败恢复：Crash / Corruption / Chaos Test

测试必须覆盖：

- 正常路径
- 边界条件
- 非法输入
- 状态转换
- 重复操作
- 失败和恢复路径

不得通过删除、跳过、注释测试或降低断言来消除失败。

### 22.5 Review

提交前必须逐项检查：

- 需求是否完整实现
- 是否引入无关修改
- 是否破坏现有 API 或交易语义
- 是否存在异常吞噬、资源泄漏或线程安全问题
- 是否有不必要的分配、锁和 I/O
- 测试是否证明关键行为
- 文档是否与实现一致

---

## 23. 代码质量与安全门禁

### 23.1 代码质量

必须保持：

- 明确的模块边界
- 可读的命名
- 小而明确的方法
- 显式的状态转换
- 可测试的依赖
- 一致的异常语义

禁止：

- 无理由的静态全局状态
- 隐式线程切换
- 静默吞掉异常
- 通过返回 `null` 隐藏失败
- 复制粘贴形成的重复业务规则
- 为了测试而加入生产专用旁路

### 23.2 输入与资源安全

必须验证：

- 数值范围
- 数量和价格精度
- OrderId 唯一性
- Sequence 连续性
- 网络消息长度
- WAL Record 长度和 CRC
- 文件路径和资源边界

禁止提交：

- 密钥
- Token
- 密码
- 真实业务数据
- 本机绝对路径
- 个人 IDE 配置

### 23.3 依赖和配置

新增或升级依赖必须记录：

- 版本
- 用途
- License
- 安全风险
- 是否可以移除

依赖版本必须可重复解析。构建配置不得依赖开发者本机的隐式环境。

---

## 24. 构建、测试与 CI 门禁

项目必须至少具备以下质量门禁：

- 编译
- 单元测试
- 集成测试
- 静态检查或格式检查
- 相关 Benchmark
- CI 构建

每次提交前必须执行适用命令，并记录命令和结果。

测试失败处理流程：

```text
Observe Failure
    -> Reproduce
    -> Identify Root Cause
    -> Fix Implementation or Test
    -> Re-run Focused Test
    -> Re-run Regression Suite
```

不得只运行失败的单个测试后直接提交。

测试必须尽量：

- 可重复
- 独立
- 无外部状态依赖
- 无时间和线程调度依赖
- 失败时提供足够上下文

出现 Flaky Test 时必须记录并处理，不得长期忽略。

---

## 25. Git 仓库管理规则

Git 是项目变更的唯一追踪来源。任何代码、测试、配置或文档变更都必须进入 Git 历史。

### 25.1 每次会话开始

必须执行：

```bash
git status --short --branch
git branch --show-current
git log --oneline --decorate -5
git diff --stat
git diff --cached --stat
```

必须先理解未提交修改，再开始编辑。

如果发现不是当前任务产生的修改：

- 不得覆盖
- 不得重置
- 不得擅自暂存
- 不得混入当前提交
- 如果影响当前任务，必须报告

### 25.2 修改前和修改后

修改前记录：

- 当前分支
- 工作区是否干净
- HEAD Commit
- 当前任务范围

修改后必须检查：

```bash
git status --short
git diff --stat
git diff --check
git diff
```

### 25.3 分支策略

稳定分支只接受已验证的完整变更。

开发分支命名使用：

```text
feature/<short-description>
fix/<short-description>
perf/<short-description>
test/<short-description>
refactor/<short-description>
docs/<short-description>
chore/<short-description>
```

一个分支只处理一个逻辑主题。不得把多个无关任务合并到同一提交。

### 25.4 暂存区审查

优先显式暂存目标文件：

```bash
git add <file1> <file2>
git status --short
git diff --cached --stat
git diff --cached --check
git diff --cached
```

暂存前必须确认：

- 没有敏感信息
- 没有构建产物
- 没有临时文件和 IDE 文件
- 没有无关格式化
- 没有误删文件
- 没有删除或弱化测试

### 25.5 Commit 规范

每个 Commit 必须：

- 逻辑完整
- 可独立构建或验证
- 只包含一个主题
- 说明修改意图
- 使用 Conventional Commits

格式：

```text
<type>(<scope>): <imperative summary>
```

允许类型：

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

禁止使用：

```text
update
fix
test
aaa
final
temp
```

除非得到明确授权，不得执行：

- `git reset --hard`
- `git checkout -- <path>`
- `git restore <path>`
- `git clean`
- `git commit --amend`
- `git rebase`
- 强制推送

### 25.6 提交前门禁

提交前必须执行：

```bash
git diff --check
git diff --cached --check
```

并执行所有适用的构建、测试、静态检查、Benchmark 和 Recovery 验证。

门禁失败时不得提交半成品。确需保存未完成工作时，必须先报告状态和风险。

### 25.7 提交后确认

提交完成后必须执行：

```bash
git status --short --branch
git log -1 --oneline --decorate
git show --stat --oneline HEAD
```

默认要求提交后工作区干净。

最终报告必须包含：

- 分支
- Commit hash
- Commit message
- 变更范围
- 验证命令
- 验证结果
- 未提交修改

### 25.8 Push、Merge 和 Release

未经明确授权不得执行 `git push`。

Push 或 Merge 前必须确认：

- 工作区干净
- 分支目标正确
- 本地验证通过
- 提交历史可读
- 不包含敏感或临时文件

Release Tag 只能建立在已验证提交上，并必须记录版本、变更、验证结果、Benchmark 结果和已知限制。

---

## 26. 文档和上下文维护

以下内容必须与代码同步：

- README
- Architecture 文档
- ADR
- Benchmark 报告
- Recovery 文档
- `AGENT_CONTEXT.md`

重大设计、性能或恢复结论必须记录：

- 背景
- 假设
- 实验方法
- 数据
- 结论
- 限制

不得把未经验证的目标、推测或宣传性数字写成事实。

---

## 27. Definition of Done

任务只有同时满足以下条件才算完成：

- 需求范围明确
- 代码实现完成
- 相关测试完成
- 构建通过
- 静态或格式检查通过
- 相关 Benchmark 通过（如果涉及性能）
- Git Diff 已审查
- 文档已同步
- Commit 已完成或未提交原因已说明
- 提交后 Git 状态已确认
- 没有明显回归
