# 贡献与改进指南

本仓库的代码改进按固定循环执行：**分区审计 → 独立复核 → 最小实施 → 门禁 → 提交**。
每条候选、每次改动都照本文件走，不另立流程。

## 1. 分区审计

按下列分区逐区扫描，找出结构摩擦点并产出候选卡：

| 分区 | 覆盖路径 |
|------|----------|
| 领域层 | `app/src/main/java/com/dakala/app/domain/`（model、usecase） |
| 数据层 | `app/src/main/java/com/dakala/app/data/`（local、repository、org） |
| 界面层 | `app/src/main/java/com/dakala/app/ui/`（Activity、ViewModel、components） |
| 后台层 | `app/src/main/java/com/dakala/app/worker/`、`receiver/`、`widget/` |
| 测试与文档 | `app/src/test/`、`docs/`、`README.md`、`AGENTS.md` |

描述问题时用现成词汇，不自造术语：

- 结构问题用 `~/.agents/skills/codebase-design/SKILL.md` 的深模块词汇：module、interface、depth、seam、adapter、leverage、locality。
- 坏味道按 `~/.agents/skills/code-review/SKILL.md` 第 3 节的 Fowler 基线清单逐条对照（Duplicated Code、Shotgun Surgery、Divergent Change、Speculative Generality、Middle Man、Primitive Obsession 等 12 条）。

## 2. 候选卡

每条候选写成一张卡片，八个字段缺一不可：

- **位置**：`文件路径:行号`；同类重复点在多处出现时逐个列出。
- **问题**：一到三句，写可观测的危害（用户可见故障、无法写测试、改一处要动 N 处），不写「不优雅」。
- **目标形态**：改完之后的结构：谁调用谁、接口长什么样。
- **代码依据**：支撑判断的当前代码片段、行为或提交记录。
- **收益**：标注命中的项 —— leverage（调用方少学多少）、locality（改动是否收敛到一处）、可测性（测试能否跨接口写）。
- **工作量**：小 / 中 / 大，附涉及文件数。
- **改动风险**：行为是否等价、边界在哪。
- **重要度**：排序依据（如「缺陷来源」「挡住回归测试」）。

示例（已实施，`git show 17fcc62` 可核对）：

- **位置**：`domain/usecase/UsageStatsUseCase.kt`（修复前 5 个公开方法、其中 3 个零调用）
- **问题**：零调用方法留在接口上；每个入口重复「取系统服务 + 算今日区间 + queryUsageStats」样板；「今日桶过滤」有三份实现且条件不一致，锁在 private + Android 类型之后，JVM 单测无法触及。
- **目标形态**：今日桶过滤抽成纯函数，查询入口收敛为一个。
- **代码依据**：三份过滤各写各的边界条件，其中两份与第三份不同。
- **收益**：locality（跨天边界收敛为一处）、leverage（调用方少学一套样板）、可测性（JVM 单测可覆盖），三项全中。
- **工作量**：中（2-3 个文件）。
- **改动风险**：低（纯重构，行为等价）。
- **重要度**：高（已知缺陷来源，且此前无法回归测试）。

按重要度排序后超出本轮预算的候选，保留在清单里并标「已跳过」+ 原因；下轮审计先复核旧清单，不重复提出。

## 3. 独立复核

实施前，把候选卡交给**没参与提出**的独立上下文（新的 sub-agent 会话或独立审查者）核对三问：

1. 位置与代码依据真实吗（打开文件核对）？
2. 问题描述属实吗，有无夸大？
3. 已被最近提交修掉了吗（`git log --oneline -- <path>` 核对）？

结论三态：**通过 / 驳回 / 修正后通过**。驳回必须写明原因留档。

## 4. 最小实施

只做候选卡描述的改动：

- 不顺手重命名、格式化或清理无关代码；移除因本次改动而废弃的导入、变量、函数。
- 不借清理之名扩大范围：一个候选一次提交。
- 不夹带新依赖、权限、常驻开销。

## 5. 门禁

```bash
./gradlew ktlintCheck test --no-configuration-cache   # 每次改动前必须绿；不绿不提交
./gradlew assembleDebug --no-configuration-cache      # 交付前（合并 main / 发版前）
```

- `--no-configuration-cache` 不可省略（AGP/WorkManager 兼容性，见 `AGENTS.md`）。
- 门禁失败就地修复，不绕过、不推迟。

## 6. 提交

约定式提交；规范来源为本机全局 `~/.config/git/gitmessage`。

```
<type>(<scope>): <subject>

<正文：为什么改>

Co-authored-by: <宿主> (<模型>) <noreply@...>
```

- **type** 取值：feat / fix / refactor / perf / style / chore / build / ci / docs / test / revert。
- **subject**：动词开头、祈使语气、全小写、结尾不加句号。
- **scope**：单文件改动用文件名，多文件改动用模块名。
- **body**：空一行再写，写「为什么改」（动机、前提、取舍），不重复 subject。
- **AI 协作署名**：AI 参与时补 `Co-authored-by:` 尾注；NAME 按当轮 runtime 写「宿主 (模型)」，EMAIL 用该宿主的 noreply 地址；不要照抄历史提交里的署名快照。

## 克制原则

- 不新增依赖、权限、常驻开销。
- 不借清理之名扩大范围：每行改动可追溯到一张候选卡。
- 删除文件用 `trash-put`（本机冻结 `rm`），不物理删除。

## 取舍判据：删除测试

决定一个模块该留、该加深还是该删时，设想删掉它：

- 复杂度**消失**（它只是转发）→ 浅模块，候选写「删除或合并」。
- 复杂度**摊回 N 个调用方**（locality 消失）→ 值得保留，候选写「加深接口、收敛调用方」。

配套两条（源自 `codebase-design`）：

- 一个 adapter 只是假设的缝，两个 adapter 才是真的缝：不为单一实现引入 seam。
- 接口就是测试面：改完后测试写在新接口上，不绕到接口后面测内部。

## 真实先例

| 提交 | 分区 | 做了什么 |
|------|------|----------|
| `17fcc62` | 领域层 | 收敛使用统计查询样板，抽出可单测的今日桶过滤 |
| `1b531d6` | 数据层 | 统一小组件与通知路径的未完成打卡判定 |
| `be56423` | 后台层 | 抽出未完成打卡查询用例，统一小组件时长文案 |
| `fdfb89b` | 后台层 | 统一通知重排，覆盖全部成功路径 |
| `156a4aa` | 数据层 | 收敛 org 写回入口，合并解析与写回的重复语法定义 |
| `ba42f06` | 界面层 | 修正 `isLoading` 死分支 |
| `a7f36c5` | 测试与文档 | 补记跨天刷新修复的真实改动 |
