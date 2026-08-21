# DEVELOPMENT_RULES - Matching Engine Development Rules

本文件只定义实现、测试、性能、安全和 Git 工程规则。治理规则由
`.codex/MASTER_PROMPT.md` 管理，当前状态由 `.codex/AGENT_CONTEXT.md`
管理，获批工作和阶段状态由 `tasks/` 管理。发生冲突时必须停止并按该
事实源边界完成同步。

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

以下未被已批准 ADR / Phase Blueprint 明确覆盖的变化必须暂停并报告：

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

#### 决策步骤的 ADR 记录要求

任务方案的 `Decision` 小节必须包含一个 `ADR Linkage` 小节，并明确记录：

| Field | Required Content |
| --- | --- |
| ADR | `docs/adr/ADR-NNNN-title.md`，或 `Not required` |
| Status | ADR 当前状态，例如 `Proposed`、`Accepted`、`Accepted with constraints` |
| Decision Summary | 与 ADR `Decision` 小节一致的摘要 |
| Scope Boundary | 该决策允许和禁止的实现范围 |

规则：

1. 识别出需要长期保留的技术决策后，必须先创建或更新 ADR 草案，并将状态设为 `Proposed`。
2. ADR 草案必须先记录 Context、Problem、Options、Proposed Decision、Scope Boundary、Consequences 和验证计划，再进行技术决策或审批。
3. Human Review 和技术决策只能发生在 ADR 草案存在之后；Phase Blueprint Approval 可以一次批准其决策矩阵中明确列出的 ADR，决策结果必须回写 ADR 状态。
4. ADR 状态与直接审批或 Blueprint 继承审批一致后，任务方案才能获批并进入实现。
5. 任务方案必须链接具体 ADR，不能只写“ADR required”。
6. ADR 与任务方案的决策内容必须一致；若不一致，先暂停实现并同步两者。
7. 不需要 ADR 时，必须写 `ADR: Not required`，并说明这是局部实现、不改变架构、协议、数据格式或运行时语义。
8. ADR 发生变更时，必须在任务方案的 `Implementation Log` 中记录变更原因、影响和重新验证结果。

上面的顺序适用于每一个独立的技术决策。ADR 不是实现完成后的总结，而是决策前的输入、评审依据和长期记录。

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

### 22.6 Phase Blueprint、执行检查点与审批

一个任务必须按风险和交付边界划分为若干开发阶段。至少应区分：

```text
ADR / Decision
    -> Task Approval
    -> Implementation
    -> Verification
    -> Benchmark / Profile（适用时）
    -> Documentation and Synchronization
    -> Completion
```

新的多任务 Phase 默认先建立完整 `Phase Blueprint`，一次冻结 ADR、Task、
阶段、验收、测试、Git、回滚和文档计划。Human Blueprint Approval 对其中
明确列出的范围授予执行权限；阶段可以包含多个原子开发动作，但不得跨越
未批准的 Blueprint 边界。

每个阶段完成后，Codex 必须：

1. 输出并记录阶段报告，至少包含：
   - 本阶段目标和实际完成内容
   - 修改文件和范围
   - 测试、构建、Benchmark 或其他证据
   - 与已批准方案的偏差
   - 新增风险、限制和未验证内容
   - 下一阶段是否仍在 Blueprint 授权范围内
2. 在 `tasks/reports/` 创建或更新 Blueprint 指定的 Task / evidence checkpoint 报告，并在任务方案中记录位置和状态。多个已授权子阶段可以共享累计报告，避免重复 Phase 背景。
3. 运行测试、构建、静态检查、Diff、Commit、Push 和 CI 等 Blueprint 证据门禁。
4. 检查是否触发 Exception Gate。

所有证据门禁通过且下一阶段已被 Blueprint 明确授权时，可以直接继续，
无需例行 Human 审批。以下情况必须停止并等待 Human：

- Blueprint 明确声明的人工门禁；
- ADR 冲突、范围扩张、公共 API 破坏或格式/语义变化；
- 新关键依赖或实质性实现策略变化；
- 验证失败暴露架构问题，或需要弱化验收标准；
- Phase Closure、Release 或破坏性 Git 操作；
- 下一动作未被 Blueprint 明确列出。

发生 Exception Gate 时，必须记录日期、影响、候选方案和所需决定；涉及
技术决策时先更新 ADR 与 Blueprint，再获得 Human 批准。未使用 Blueprint
的独立任务继续采用逐阶段 Human Approval。

任务完成阶段必须额外执行文档同步：

- ADR、任务方案、规范、架构/Benchmark/Recovery 文档和 `AGENT_CONTEXT.md` 的状态、决策摘要、范围边界和验证结果一致。
- 同步完成前，任务不能标记为 `Completed`。
- 同步完成后，在阶段报告和最终报告中记录同步范围及结果。

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
git log --oneline --decorate -10
git diff --stat
git diff --cached --stat
git remote -v
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

已获批且完成的逻辑阶段，可以按 `.codex/MASTER_PROMPT.md` 执行正常、非破坏性的 `git push` 作为仓库同步步骤。执行前必须确认 remote、目标分支、工作区、验证结果和提交历史；执行后记录远程分支和可观察的 CI 状态。

以下操作始终需要 Human 明确授权：force push、共享历史改写、删除远程分支、删除标签、修改默认或保护分支、Release 发布。禁止向默认分支 force push。

Push 或 Merge 前必须确认：

- 工作区干净
- 分支目标正确
- 本地验证通过
- 提交历史可读
- 不包含敏感或临时文件

Release Tag 只能建立在已验证提交上，并必须记录版本、变更、验证结果、Benchmark 结果和已知限制。

### 25.9 Artifact Policy

应提交可审查、可复现的摘要证据：Benchmark 报告、Profiling 摘要、
Architecture、ADR、复现命令和小型确定性 fixture。通常忽略 `target/`、
原始 JFR、较大的 profiler dump、临时 Benchmark 输出、IDE metadata 和
本地日志。

未提交的原始证据必须在已提交报告中记录路径、生成命令、摘要和限制。
不得提交 secret、token、password 或未明确授权的大型生成物。

---

## 26. 文档和上下文维护

以下内容必须与代码同步：

- README
- Architecture 文档
- ADR
- Benchmark 报告
- Recovery 文档
- `AGENT_CONTEXT.md`

`AGENT_CONTEXT.md` 是紧凑的当前状态索引，不是项目日记。它必须维护 Project Progress、当前 Phase/Task/Stage/Approval、Branch、HEAD 基线、Remote Sync、CI、Next Gate、关键证据链接和真实风险；详细历史保留在 ADR、Task、Stage Report 和 Git 中，不得整段复制。

每个任务的决策步骤必须与对应 ADR 同步。任务完成前必须确认：

- ADR 草案在技术决策和任务审批之前已经存在。
- 任务方案的 ADR 链接可访问。
- ADR 状态和任务审批状态一致。
- 任务方案的决策摘要与 ADR 的决策内容一致。
- ADR 已列入 Git Diff 审查范围。
- 如果不需要 ADR，任务方案中已记录明确理由。
- Blueprint 要求的 Task / evidence checkpoint 报告完整。
- Human Blueprint、Exception 和 Closure 审批按实际触发情况记录完整。
- 文档与 `AGENT_CONTEXT.md` 已完成同步。

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
- Blueprint 要求的 Task / evidence checkpoint 报告完整
- Human Blueprint、Exception 和 Closure 审批按实际触发情况完整
- 文档已同步
- `AGENT_CONTEXT.md` 已同步
- 仓库已按 Git policy 同步，或已明确记录 remote/CI 不可用
- Commit 已完成或未提交原因已说明
- 提交后 Git 状态已确认
- 没有明显回归

---

## 28. Task 工作区与方案先行

### 28.1 唯一方案工作区

`tasks/` 是项目开发方案的唯一工作区。任何需要修改以下内容的任务，都必须先在 `tasks/active/` 创建任务方案：

- 生产代码
- 测试代码
- 构建配置
- Benchmark
- Profile 配置
- 网络协议
- WAL、Snapshot 或 Recovery
- 项目文档
- 运行时行为

任务方案模板位于：

```text
tasks/TEMPLATE.md
```

多任务 Phase 的 Blueprint 模板位于：

```text
tasks/PHASE_BLUEPRINT_TEMPLATE.md
```

工作区结构：

```text
tasks/
├── README.md
├── TEMPLATE.md
├── PHASE_BLUEPRINT_TEMPLATE.md
├── blueprints/
├── active/
├── completed/
└── archive/
```

### 28.2 方案内容要求

方案必须在实现前明确：

- Task ID、标题、负责人和相关阶段
- 背景、目标和非目标
- 需求、输入、输出和验收标准
- 当前实现与影响范围
- 设计方案、候选方案和选择理由
- 是否需要 ADR 或 Human 架构决策
- 计划修改的文件和模块
- Unit、Integration、System、Recovery 或 Replay 测试计划
- Benchmark、Profile、数据集和指标计划
- 风险、兼容性和回滚方案
- 验证命令和 Git 提交计划
- 当前开发阶段、下一审批门禁和阶段报告记录方式
- Phase Blueprint 路径、继承授权范围和 Exception Gate（适用时）

方案不完整时，任务不得进入实现阶段。

### 28.3 状态流转

任务状态只能按以下流程流转：

```text
Proposed
    -> Approved
    -> In Progress
    -> Completed
```

任务也可以在明确记录原因后进入：

```text
Proposed / Approved / In Progress
    -> Cancelled
```

状态含义：

- `Proposed`：方案已创建，等待直接 Human 审批或 Phase Blueprint Approval。
- `Approved`：方案已通过直接审批或可追溯 Blueprint 继承审批，允许开始实现。
- `In Progress`：已开始实现、测试或 Benchmark。
- `Completed`：验收标准已满足，变更已提交并完成状态确认。
- `Cancelled`：任务被明确取消，且取消原因已记录。

任务处于 `In Progress` 时，仍必须维护当前阶段和下一门禁。Blueprint 模式下，下一门禁可以是已授权检查点、Exception Gate 或 Phase Closure；不得把每个子阶段自动改成 `Pending Human Approval`。严格门禁模式或触发异常时，下一门禁必须标记为 `Pending Human Approval`。

### 28.4 审批门禁

`Proposed` 状态未获直接 Human 确认或明确 Blueprint 继承审批前：

- 可以阅读代码和文档。
- 可以执行只读命令、构建基线和补充方案。
- 不得修改生产代码、测试代码、构建配置或运行时行为。
- 不得创建用于绕过审批的临时实现。

只有方案进入 `Approved` 后，才能将状态改为 `In Progress` 并开始开发。
Human Phase Blueprint Approval 是其中明确列出 Task 的有效审批来源；Task
必须链接 Blueprint 并记录继承范围。未列出的 Task 仍需独立 Human 审批。

以下变更必须在方案中明确标记并等待 Human 决策：

- 改变核心架构
- 改变 Matching Model
- 改变 OrderBook 核心结构
- 改变事件顺序或 Sequence 语义
- 改变网络协议
- 改变 WAL、Snapshot 或 Recovery 格式
- 引入或替换关键第三方依赖
- 扩大原定任务范围

进入任何需要技术决策的阶段前，必须确认相关 ADR 草案已经存在，并通过
直接 Human Review 或 Blueprint 决策矩阵完成审批；不得先做决定、先写
实现，再补 ADR。

### 28.5 实现期间管理

实现过程中必须持续更新任务方案：

- 开始实现时记录 `In Progress`。
- 设计发生变化时先更新方案，再继续实现。
- 新增文件、测试、Benchmark 和文档必须与方案的文件变更计划一致。
- 发现新的风险、限制或性能回归时必须记录。
- 不得将未验证的目标写成已达成的结论。
- 每个阶段完成时更新 Blueprint 指定的累计报告、Task 日志和证据状态。
- 证据门禁通过、无 Exception Gate 且下一阶段已在 Blueprint 中授权时，直接推进。
- Blueprint 人工门禁、Exception Gate 或 Phase Closure 到达时，设为 `Pending Human Approval` 并停止。

### 28.6 完成、归档和恢复

任务完成前必须：

- 满足全部验收标准。
- 执行适用的测试、构建、静态检查和 Benchmark。
- 审查 Git Diff 和暂存区 Diff。
- 更新相关文档和 `AGENT_CONTEXT.md`。
- 确认 ADR、任务方案、阶段报告、规范和 `AGENT_CONTEXT.md` 的内容已同步。
- 确认 Blueprint 要求的 Task / evidence checkpoint 报告齐全。
- 确认 Blueprint、Exception 和 Closure 的 Human 审批记录按实际触发情况齐全。
- 完成一个逻辑完整的 Conventional Commit。
- 执行提交后 Git 状态确认。

完成后：

1. 将任务状态改为 `Completed`。
2. 将任务方案从 `tasks/active/` 移动到 `tasks/completed/`。
3. 在 `Implementation Log` 中记录结果和验证命令。
4. 长期历史方案可移动到 `tasks/archive/`，不得无记录删除。

每次新会话必须先读取 `tasks/README.md` 和相关 `tasks/active/` 方案，确认当前任务状态后才能编辑文件。
