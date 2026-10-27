# 改进候选清单

> 由《贡献与改进指南》（`docs/CONTRIBUTING.md`）维护：审计产出卡片，复核与验收写回同一张卡。
> 本文件是仓库内唯一候选清单；下轮审计先复核旧卡（重排重要度、剔除已被提交修掉的），再取前 8 张实施。

首轮审计：2026-10-19 —— 分区审计 5 个区域、产出 15 张卡，独立复核 15 张全部通过；8 张当日实施。剩余 6 张于同日第二轮全部实施完毕（见下），清单暂无待办。

## 界面层-3 | 重要度 - | 状态：已实施（5a21f85）

> 来源：用户需求（非审计产出）——时长输入控件难用、打卡日界需支持自定义重置点。

- **位置**：`app/src/main/java/com/dakala/app/ui/components/Dialogs.kt`（DurationThresholdDialog 双文本框）；`app/src/main/java/com/dakala/app/domain/usecase/UsageStatsUseCase.kt`（自然日桶）；`domain/model/DayStamp.kt`（yyyyMMdd 日界）；`data/local/entity/AppItem.kt`（无重置点字段）
- **问题**：时长设置靠两个数字文本框输入分/秒，难用且易出界；「今日」被钉死在自然日零点，`queryUsageStats` 的日桶无法表达「每晚 12 点以外时刻翻篇」的打卡周期（用户常用 23:00、04:00）。
- **目标形态与验收**：滚轮控件（LazyColumn + 吸附）替代文本输入；`DayResetTime` 值对象承载「HH:mm 解析 + 周期起点 + 周期日期键」三规则；使用统计改走 `queryEvents` 事件流按各应用重置点聚合；全局默认重置时间入 `app_settings`、应用维度可空覆盖（null=跟随全局）。验收：`DayResetTimeTest`（跨重置点日期键/周期起点/容错解析）+ `UsageStatsUseCaseTest`（事件聚合：窗口起点前已前台、乱序、孤立 PAUSE、仍在用计到窗口末）；手工：设置页改默认重置时间、应用卡片齿轮进二级对话框选 23:00/04:00 预设或「跟随全局」。
- **代码依据**：旧 `DurationThresholdDialog` 用两个 `OutlinedTextField`；`DayStamp.today()` 在 Repository/Widget×2/ViewModel 四处定死日界；`latestDurationSeconds` 按自然日过滤桶。
- **收益**：locality（重置语义收敛到 `DayResetTime` 一处，自定义打卡/小部件/使用记录同口径）；可测性（`foregroundSeconds` 纯函数首次覆盖跨边界会话）；用户可见（滚轮 + 23:00/04:00 重置）。
- **工作量**：大（约 20 个文件）。
- **改动风险**：中（使用统计从日桶换事件流，跨零点行为经单测锚定；DB v3→v4 沿用 destructive fallback；通知语义不变——通知时间与重置时间是两个独立设置）。
- **重要度**：用户提出（交互痛点 + 数据模型缺角）。

**复核记录**：用户直接需求，按原话验收：滚轮控件、默认 00:00、可单应用覆盖、23:00/04:00 常用项 —— 全部落实。
**验收记录**：2026-10-28 —— 目标形态达成：`DayResetTime`（ofMinutes/of/parse 容错回退 00:00 + currentPeriodStart/dateKey）；`AppItem.resetTimeMinutes` 可空覆盖 + `KEY_DEFAULT_RESET_TIME` 全局键；`UsageStatsUseCase` 改 `queryEvents` + `foregroundSeconds`（事件回溯覆盖「重置点前打开未退出」）；`WheelPicker`（时分/时分秒两种）+ `ResetTimeDialog` + `AppCheckSettingsDialog`；`DayStamp` 删除。验证：新增/改写 `DayResetTimeTest` 8 条、`UsageStatsUseCaseTest` 11 条、`AppMonitorStatusTest` 小时格式化 3 条断言；`./gradlew ktlintCheck test assembleDebug --no-configuration-cache` 全绿。

## 后台层-1 | 重要度 1 | 状态：已实施（1f7e734）

- **位置**：`app/src/main/java/com/dakala/app/DakalaApplication.kt:45`、`app/src/main/java/com/dakala/app/receiver/BootReceiver.kt:28`、`app/src/main/java/com/dakala/app/worker/NotificationWorker.kt:69,251-262`；写入侧 `app/src/main/java/com/dakala/app/ui/viewmodel/MainViewModel.kt`
- **问题**：界面改的提醒时间只写进设置表，从不重排已入队的任务；而 app 每次启动与开机都用无参调用（默认 22:00）、以 `ExistingWorkPolicy.REPLACE` 覆盖既有排期。用户把提醒改到 20:30，当天仍会在 22:00 响，且下次启动又被重置回 22:00——「设置通知时间」这条设置实际不生效。
- **目标形态与验收**：排期只认设置值：`scheduleDailyNotification` 去掉默认参数，设置写入后立即重排；启动/开机改为「按设置值排期」，缺省回退仍是 22:00。
- **代码依据**：`NotificationWorker.kt` 签名带默认值 `hour: Int = 22, minute: Int = 0`；`enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, ...)`；`DakalaApplication`/`BootReceiver` 均无参调用。
- **收益**：locality（排期来源收敛为「设置值」一处）；修掉用户可见故障。
- **工作量**：小（3 个文件）。
- **改动风险**：中（实际提醒时刻会变，需确认设置缺失/非法时的回退仍是 22:00，且不产生重复排期）。
- **重要度**：中（用户可见：设置不生效，且每次启动被静默重置）。

**复核记录**：2026-10-19 独立复核 —— 通过（调用链核对：`scheduleDailyNotification` 的三个调用点无一传入用户设置值）。
**验收记录**：2026-10-19 —— 目标形态达成：`scheduleDailyNotification(context, hour, minute)` 无默认参数（漏传即编译失败），新增 `scheduleFromSettings`（同步读设置）作为启动/开机入口，`setNotificationTime` 写入后立即按新值重排；回退路径统一走 `NotificationTime.parse`（缺省 22:00）。验证：`./gradlew ktlintCheck test --no-configuration-cache` 绿。手工项（设 20:30 → 杀进程重启 → WorkManager 中下次执行应为 20:30）留待统一测试。

## 界面层-1 | 重要度 2 | 状态：已实施（7b6b0d3）

- **位置**：`app/src/main/java/com/dakala/app/ui/AppSelectionActivity.kt:152`（调用后立即 `onBack()`）；写入在 `app/src/main/java/com/dakala/app/ui/viewmodel/AppSelectionViewModel.kt`
- **问题**：保存监控选择是 fire-and-forget：点「保存」即 `saveSelectedApps()` 后立刻 `finish()`，ViewModel 随之 clear、`viewModelScope` 取消，循环内逐条 suspend 写库可能被截断成「只保存了前几个」；界面没有任何反馈，用户以为存好了。
- **目标形态与验收**：写入移出不随 UI 取消的作用域，且整批原子生效。
- **代码依据**：按钮 onClick 内先 `viewModel.saveSelectedApps()` 再 `onBack()`（即 `finish()`）；`saveSelectedApps` 内 `viewModelScope.launch` + 循环逐条 suspend 写库。
- **收益**：locality（保存语义收敛到一处）、可测性（事务化）；消除静默数据丢失。
- **工作量**：中（3 个文件）。
- **改动风险**：中（保存路径行为等价，已监控项不覆盖时长阈值）。
- **重要度**：中（数据静默丢失，但只在保存后立刻退出时发生）。

**复核记录**：2026-10-19 独立复核 —— 通过（逐行读取 Activity 与 ViewModel，确认无等待、无回调）。
**验收记录**：2026-10-19 —— 目标形态达成：新增 `AppItemDao.syncMonitoredApps` `@Transaction` 默认方法承载整批「移除未选 + 插入新增」（原子生效，已监控项保持原样），ViewModel 用 `withContext(NonCancellable)` 包住调用，界面销毁后写入仍完成。验证：`./gradlew ktlintCheck test assembleDebug --no-configuration-cache` 绿（Room KSP 接受该事务方法）。手工项（勾选 20 个应用 → 保存 → 立刻杀进程 → 重开确认全在）留待统一测试；JVM 侧断言「全成或全败」需 Robolectric，项目无此基建，未做。

## 领域层-1 | 重要度 3 | 状态：已实施（4049944）

- **位置**：`app/src/main/java/com/dakala/app/domain/model/AppMonitorStatus.kt`（原 `MonitorStatusGroup` 与 `CustomCheckStatusGroup`）
- **问题**：两个分组类型是同一形状的两份拷贝，6 个派生属性逐字对应、只换名词；其中两组共 4 个属性（`totalApps`/`completionRate` 与 `totalItems`/`completionRate`）全仓零引用——接口面大于实际需要，且改一处语义必须记得改另一处。
- **目标形态与验收**：合并为一个泛型分组类型，派生只保留真正被使用的成员；调用方改用统一命名；残留旧属性名应编译失败。
- **代码依据**：两段定义逐字对应（grep 全仓零引用核实）。
- **收益**：leverage（调用方要学的属性从 6 个降到 3 个）、locality（分组规则只写一份）。
- **工作量**：中（4 个文件）。
- **改动风险**：低（纯结构收敛，无行为变化）。
- **重要度**：中（挡住后续对分组语义的任何改动）。

**复核记录**：2026-10-19 独立复核 —— 通过（成员逐行对照，零引用结论经全仓 grep 核实）。
**验收记录**：2026-10-19 —— 目标形态达成：合并为 `StatusGroup<T>`（incomplete/completed + hasIncomplete/hasCompleted/isEmpty，与实施前用点一一对应），删除零引用的 total*/completedCount/completionRate 与 `AppMonitorStatus.todayDurationMinutes`/`thresholdMinutes`，净删 86 行；MainViewModel、MainActivity、CustomCheckComponents 全部改用统一命名。验证：grep 旧名清零 + 门禁绿（残留旧名即编译失败，删除由编译器守护）。

## 界面层-2 | 重要度 4 | 状态：已实施（6b849e1）

- **位置**：`MainActivity`（解析与格式化）、`MainViewModel`（透传）、`NotificationWorker`（二次解析 + 硬编码 key）、`AppUsageRepository`（默认值）
- **问题**：通知时间以裸字符串 `"HH:mm"` 穿过 ViewModel 直抵界面：解析、格式化、默认值 22:00 在五处各写一遍，存储格式泄露给 UI 与后台 worker。
- **目标形态与验收**：值类型 + 纯函数 parse/format，存储格式不越过数据层；补 JVM 单测覆盖非法输入。
- **代码依据**：五处实现逐行读取（含 worker 硬编码 `"notification_time"` 字面量 key）。
- **收益**：locality（规则一处定义）、leverage（界面不再知道存储格式）、可测性（纯函数可直接单测）。
- **工作量**：中（6 个文件）。
- **改动风险**：低-中（行为等价，保持设置表字符串格式不变）。
- **重要度**：中（同一规则 5 处实现，改默认值必漏）。

**复核记录**：2026-10-19 独立复核 —— 通过（四处解析/格式化逐行读取，worker 侧硬编码 key 已核实）。
**验收记录**：2026-10-19 —— 目标形态达成：新增 `domain/model/NotificationTime`（parse 容错 + format + DEFAULT 回退），Repository 对外只暴露该类型，界面与 worker 消费类型本身，worker 改用 `AppSetting.KEY_NOTIFICATION_TIME` 常量；行为等价（解析回退顺序与原实现一致）。验证：新增 `NotificationTimeTest` 7 条断言（null/空串/垃圾/越界回退、单数字分量、format 补零、往返一致）全绿。

## 测试与文档-1 | 重要度 5 | 状态：已实施（121bb23）

- **位置**：`MainViewModel`、`AppUsageRepository`、`UsageWidgetProvider`、`WidgetService`（四份 `getTodayDate`/`getCurrentDate` 拷贝）
- **问题**：「今天是哪天」（`yyyyMMdd` 整数 + `Calendar.getInstance()`）在四个生产文件里各自重算一遍，跨天边界没有单一归属——而跨天判定正是 `b0c92f3`/`2cb6c8c` 两次修复的病根，且当时无法写回归测试。
- **目标形态与验收**：纯函数模块（可注入 `Clock`），四处改调它；补跨零点单测。
- **代码依据**：四处函数体逐字相同（grep 核实）。
- **收益**：locality（日期边界只存在一处）、可测性（跨天分支首次可回归）。
- **工作量**：中（6 个文件）。
- **改动风险**：低（纯函数抽取，行为等价，保持本地时区语义）。
- **重要度**：中（已知缺陷来源，且挡住回归测试）。

**复核记录**：2026-10-19 独立复核 —— 通过（四处函数体逐字对照，行号与实际一致）。
**验收记录**：2026-10-19 —— 目标形态达成：新增 `domain/model/DayStamp`（`of(clock)` 纯函数 + `today()`，java.time 无新依赖），四处调用点全部改走它，四份私有拷贝删除。验证：新增 `DayStampTest` 3 条（同一本地日 23:59:59 与次日 00:00:01 日期键不同、月末翻月、单数字月日编码）全绿。Clock 尚未注入 ViewModel（无 Robolectric 基建，注入了也测不到，未做——接缝已就位）。

## 数据层-1 | 重要度 6 | 状态：已实施（4403fb0）

- **位置**：`app/src/main/java/com/dakala/app/data/repository/AppUsageRepository.kt`、`app/src/main/java/com/dakala/app/data/local/dao/AppDao.kt`、`MainViewModel`
- **问题**：数据层公开面里 20 余个成员没有任何调用方，其中 12 个正是用 `@Suppress("unused")` 压住告警的那些；接口面与实际使用面脱节，编译器无法发现死代码。
- **目标形态与验收**：逐成员确认零调用后删除，`@Suppress("unused")` 清零；门禁与 assembleDebug 为绿。
- **代码依据**：逐名 grep（排除定义文件）确认零引用。
- **收益**：leverage（接口面回到真实使用面）；属 Speculative Generality 清理。
- **工作量**：中（3 个文件）。
- **改动风险**：低（删除零引用成员）。
- **重要度**：低（无用户可见影响，但持续拖慢后续每次改动）。

**复核记录**：2026-10-19 独立复核 —— 通过（逐名 grep 排除定义处后确认零引用）。
**验收记录**：2026-10-19 —— 目标形态达成：删除 AppUsageRepository 15 个零调用公开方法、MainViewModel 9 个零调用成员、连带 16 个仅被它们使用的 DAO 方法，`@Suppress("unused")` 全部清零（仅剩 3 处 DEPRECATION），净删约 400 行。验证：删除前逐名 grep 零引用；`./gradlew ktlintCheck test assembleDebug --no-configuration-cache` 全绿。实施中曾误删小组件仍在用的 `CustomCheckRecordDao.getRecordsByDate`，编译前自查发现并恢复。

## 已被首轮提交修掉的候选

| 原候选 | 位置 | 修它的提交 |
|--------|------|-----------|
| 完成判定 / 打开判定在 widget 与 worker 三处各写一遍 | `WidgetService.kt`、`UsageWidgetProvider.kt`、`NotificationWorker.kt` | `1b531d6`、`be56423` |
| 今日桶过滤谓词三份实现，锁在 Context 类内无法单测 | `UsageStatsUseCase.kt` | `17fcc62`（并新增 `UsageStatsUseCaseTest`） |

## 后续可提的新候选（本轮发现，未立卡）

- `NotificationWorker.cancelNotification`（`NotificationWorker.kt`）：全仓零调用，属同一类投机性接口面，下轮审计确认后可删。
