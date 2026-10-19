# 改进候选清单

> 由《贡献与改进指南》（`docs/CONTRIBUTING.md`）维护：审计产出卡片，复核与验收写回同一张卡。
> 本文件是仓库内唯一候选清单；下轮审计先复核旧卡（重排重要度、剔除已被提交修掉的），再取前 8 张实施。

首轮审计：2026-10-19 —— 分区审计 5 个区域、产出 15 张卡，独立复核 15 张全部通过；8 张已实施并提交，其余 6 张列于下方，另 1 张已被本次提交修掉（见文末）。

## 后台层-1 | 重要度 1 | 状态：待复核

- **位置**：`app/src/main/java/com/dakala/app/DakalaApplication.kt:45`、`app/src/main/java/com/dakala/app/receiver/BootReceiver.kt:28`、`app/src/main/java/com/dakala/app/worker/NotificationWorker.kt:69,251-262`；写入侧 `app/src/main/java/com/dakala/app/ui/viewmodel/MainViewModel.kt:215-219`
- **问题**：界面改的提醒时间只写进设置表，从不重排已入队的任务；而 app 每次启动与开机都用无参调用（默认 22:00）、以 `ExistingWorkPolicy.REPLACE` 覆盖既有排期。用户把提醒改到 20:30，当天仍会在 22:00 响，且下次启动又被重置回 22:00——「设置通知时间」这条设置实际不生效。
- **目标形态与验收**：排期只认设置值：`scheduleDailyNotification` 去掉默认参数（或调用方一律显式传入读到的设置值），设置写入后立即重排；启动/开机改为「按设置值排期」，缺省回退仍是 22:00。验收：手工步骤——设为 20:30 → 杀进程重启 → `adb shell dumpsys jobscheduler | grep -i dakala`（或 WorkManager 的 unique work 记录）确认下次执行时间为 20:30 而非 22:00。
- **代码依据**：`NotificationWorker.kt:69` 签名带默认值 `hour: Int = 22, minute: Int = 0`；`:99-103` `enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)`；`DakalaApplication.kt:44-45` 注释即「调度默认通知（22:00）」+ 无参调用；`NotificationWorker.kt:255-261` 说明 worker 自身是读设置重排的——即只有这条路径认设置值。
- **收益**：locality（排期来源收敛为「设置值」一处）；修掉用户可见故障。
- **工作量**：小（3 个文件）。
- **改动风险**：中（实际提醒时刻会变，需确认设置缺失/非法时的回退仍是 22:00，且不产生重复排期）。
- **重要度**：中（用户可见：设置不生效，且每次启动被静默重置）。

**复核记录**：2026-10-19 独立复核 —— 通过（调用链核对：`scheduleDailyNotification` 的三个调用点无一传入用户设置值）。

## 界面层-1 | 重要度 2 | 状态：待复核

- **位置**：`app/src/main/java/com/dakala/app/ui/AppSelectionActivity.kt:152`（调用后立即 `onBack()`）；写入在 `app/src/main/java/com/dakala/app/ui/viewmodel/AppSelectionViewModel.kt:174-175`、循环 `:199-210`
- **问题**：保存监控选择是 fire-and-forget：点「保存」即 `saveSelectedApps()` 后立刻 `finish()`，ViewModel 随之 clear、`viewModelScope` 取消，循环内逐条 suspend 写库可能被截断成「只保存了前几个」；界面没有任何反馈，用户以为存好了。
- **目标形态与验收**：写入移出不随 UI 取消的作用域（`NonCancellable` 包裹，或收敛成 Repository 里的一次批量写入/事务），保存完成后由界面触发 `finish()`。验收：手工步骤——勾选 20 个应用→点保存→立刻从最近任务划掉进程→重开确认 20 个全在监控列表；批量写入落成 Repository 单一入口后，补一条 JVM 单测断言「全部写入成功或整体失败」。
- **代码依据**：`AppSelectionActivity.kt:151-154` 按钮 onClick 内先 `viewModel.saveSelectedApps()` 再 `onBack()`（`onBack` 即 `finish()`，见 `:47-53`）；`AppSelectionViewModel.kt:175` `viewModelScope.launch {`，`:199-203` 与 `:206-210` 循环内逐条 suspend 写库。
- **收益**：locality（保存语义收敛到一处）、可测性（事务化后可单测）；消除静默数据丢失。
- **工作量**：中（2-3 个文件）。
- **改动风险**：中（`finish()` 时机后移，需确认失败路径有提示、不卡住界面）。
- **重要度**：中（数据静默丢失，但只在保存后立刻退出时发生）。

**复核记录**：2026-10-19 独立复核 —— 通过（逐行读取 Activity 与 ViewModel，确认无等待、无回调）。

## 领域层-1 | 重要度 3 | 状态：待复核

- **位置**：`app/src/main/java/com/dakala/app/domain/model/AppMonitorStatus.kt:119-165`（`MonitorStatusGroup`）与 `:185-231`（`CustomCheckStatusGroup`）
- **问题**：两个分组类型是同一形状的两份拷贝，6 个派生属性逐字对应、只换名词；其中两组共 4 个属性（`totalApps`/`completionRate` 与 `totalItems`/`completionRate`）全仓零引用——接口面大于实际需要，且改一处语义必须记得改另一处。
- **目标形态与验收**：合并为一个泛型分组类型（如 `data class StatusGroup<T>(val incomplete: List<T>, val completed: List<T>)`），派生只保留真正被使用的成员，删除零引用的那批；调用方（`MainActivity.kt`、`CustomCheckComponents.kt`）改用统一命名。验收：JVM 单测保留 `AppMonitorStatusTest` 的既有断言（若断言落在被删属性上，改测保留的成员），并确认编译期就能发现旧属性名残留。
- **代码依据**：`:119-165` 与 `:185-231` 逐字对应——`:126` `hasIncompleteApps get() = incompleteApps.isNotEmpty()` 对 `:193` `hasIncompleteItems`；`:138` `isEmpty` 对 `:205`；`:144/150/156` `totalApps/completedCount/completionRate` 对 `:210/216/222` `totalItems/completedCount/completionRate`，公式同为 `completed / total`。零引用由 `grep -rn "completionRate|completedCount|totalApps|totalItems|todayDurationMinutes|thresholdMinutes" app/src/main/java` 核实：仅命中本文件定义处（`:34,:46,:144,:150,:156,:210,:216,:222`）。
- **收益**：leverage（调用方要学的属性从 6 个降到 3 个）、locality（分组规则只写一份，不会两组行为漂移）；删除的是投机性接口面（Speculative Generality）。
- **工作量**：中（3 个文件）。
- **改动风险**：低（纯结构收敛，无用户可见行为变化；需确认 `/codebase-check` 类删除项无隐藏调用方，已用 grep 核实）。
- **重要度**：中（挡住后续对分组语义的任何改动）。

**复核记录**：2026-10-19 独立复核 —— 通过（24 处成员逐行对照，零引用结论经全仓 grep 核实）。

## 界面层-2 | 重要度 4 | 状态：待复核

- **位置**：`app/src/main/java/com/dakala/app/ui/MainActivity.kt:186-189`（解析）、`:443-444`（格式化）；`app/src/main/java/com/dakala/app/ui/viewmodel/MainViewModel.kt:95-96,206,215-219`；`app/src/main/java/com/dakala/app/worker/NotificationWorker.kt:255-259`；`app/src/main/java/com/dakala/app/data/repository/AppUsageRepository.kt:231`
- **问题**：通知时间以裸字符串 `"HH:mm"` 穿过 ViewModel 直抵界面：解析、格式化、默认值 22:00 在三处各写一遍，存储格式泄露给 UI 与后台 worker，worker 还硬编码了字面量 key `"notification_time"` 而不是用 `AppSetting.KEY_NOTIFICATION_TIME`。
- **目标形态与验收**：新增值类型（如 `domain/model/NotificationTime.kt`：`hour`/`minute` + `parse(raw: String?)` 带 22:00 回退 + `format()`），`MainViewModel` 暴露 `StateFlow<NotificationTime>`、`setNotificationTime(NotificationTime)`，界面与 worker 共用该类型。验收：补 JVM 单测覆盖 `parse` 的非法输入（空串、`"25:99"`、`"8:5"`）与 `format()` 的补零；单测落在新接口上。
- **代码依据**：`MainActivity.kt:186-189` `notificationTime.split(":")` + `?: 22` / `?: 0`；`:443` `String.format(Locale.US, "%02d:%02d", h, m)` 后 `:444` 回写；`NotificationWorker.kt:255-259` 同一段 split + 同一组回退，`:255` 硬编码 key；`AppUsageRepository.kt:231` 第四处默认值 `?: "22:00"`；`MainViewModel.kt:95` `MutableStateFlow("22:00")` 是第五处。
- **收益**：locality（「HH:mm + 默认 22:00 + 容错」一处定义）、leverage（界面不再需要知道存储格式）、可测性（parse/format 为纯函数，可直接单测）。
- **工作量**：中（4-5 个文件）。
- **改动风险**：低-中（行为等价，但需保持设置表里的字符串格式不变，否则旧数据读不回来）。
- **重要度**：中（同一规则 5 处实现，改默认值必漏）。

**复核记录**：2026-10-19 独立复核 —— 通过（四处解析/格式化逐行读取，worker 侧硬编码 key 已核实）。

## 测试与文档-1 | 重要度 5 | 状态：待复核

- **位置**：`app/src/main/java/com/dakala/app/ui/viewmodel/MainViewModel.kt:375-380`、`app/src/main/java/com/dakala/app/data/repository/AppUsageRepository.kt:150-155`、`app/src/main/java/com/dakala/app/widget/UsageWidgetProvider.kt:629`、`app/src/main/java/com/dakala/app/widget/WidgetService.kt:269`
- **问题**：「今天是哪天」（`yyyyMMdd` 整数 + `Calendar.getInstance()`）在四个生产文件里各自重算一遍，跨天边界因此没有单一归属；而跨天判定正是最近两次修复（`b0c92f3`、`2cb6c8c`）的病根所在，今天仍无法写回归测试。
- **目标形态与验收**：抽出一个纯函数模块（如 `domain/model/DayStamp.kt`：`fun of(clock: Clock): Int` / `fun today()`），四处改调它；需要时可注入 `java.time.Clock`（JDK 内置，不新增依赖）。验收：补 JVM 单测——固定时钟在 `23:59:59` 与 `00:00:01` 两个时刻断言返回的日期键不同，覆盖跨天边界；`checkDateAndRefresh` 的跨天分支在注入 Clock 后即可回归。
- **代码依据**：四处同形，逐字核对——`MainViewModel.kt:375-380` `fun getCurrentDate(): Int { val calendar = Calendar.getInstance(); return calendar.get(YEAR) * 10000 + (calendar.get(MONTH) + 1) * 100 + calendar.get(DAY_OF_MONTH) }`；`AppUsageRepository.kt:150-155` 同体；`UsageWidgetProvider.kt:629-634` 同体（全限定 `java.util.Calendar`）；`WidgetService.kt:269-274` 同体。`grep -rn "getCurrentDate|getTodayDate" app/src/main/java` 命中数量与上述四处一致。
- **收益**：locality（日期边界只存在一处）、可测性（跨天分支首次可回归）；收益直接对应已发生过两次的缺陷。
- **工作量**：中（4-5 个文件）。
- **改动风险**：低（纯函数抽取，行为等价；需保持本地时区语义）。
- **重要度**：中（已知缺陷来源，且当前挡住回归测试）。

**复核记录**：2026-10-19 独立复核 —— 通过（四处函数体逐字对照，行号与实际一致）。

## 数据层-1 | 重要度 6 | 状态：待复核

- **位置**：`app/src/main/java/com/dakala/app/data/repository/AppUsageRepository.kt`（12 处 `@Suppress("unused")`）、`app/src/main/java/com/dakala/app/data/local/dao/AppDao.kt`
- **问题**：数据层公开面里有 20 余个成员没有任何调用方，其中 12 个正是用 `@Suppress("unused")` 压住告警的那些；接口面与实际使用面脱节，维护者无法从类型判断哪些是活接口，也无法用编译器发现死代码。
- **目标形态与验收**：逐成员确认无调用方后删除，去掉全部 `@Suppress("unused")`（让编译器重新具备发现死代码的能力）；确有未来用途的，重新按需加入而不是常驻。验收：先 `grep -rn "<成员名>" app/src/main` 逐个确认零引用（排除定义处），删除后 `./gradlew ktlintCheck test --no-configuration-cache` 与 `assembleDebug` 均为绿。
- **代码依据**：`AppUsageRepository.kt` 共 29 个公开方法，12 处 `@Suppress("unused")` 与 12 个零引用方法一一对应（`updateUsageRecord`、`getTodayUsageRecord(s)`、`cleanOldRecords` 等）；DAO 侧另有 6 个零引用查询、类级 3 个零引用成员。
- **收益**：leverage（接口面回到真实使用面）、locality（删除后编译器能继续守护死代码）；属 Speculative Generality 清理。
- **工作量**：中（2-3 个文件，但需逐个 grep 核实）。
- **改动风险**：低（删除零引用成员；DAO 删除需同步核对 Room 生成的实现，跑一次 `assembleDebug` 即可确认）。
- **重要度**：低（无用户可见影响，但持续拖慢后续每次改动）。

**复核记录**：2026-10-19 独立复核 —— 通过（逐名 grep 排除定义处后确认零引用）。

## 已被本次提交修掉的候选

| 原候选 | 位置 | 修它的提交 |
|--------|------|-----------|
| 完成判定 / 打开判定在 widget 与 worker 三处各写一遍 | `WidgetService.kt`、`UsageWidgetProvider.kt`、`NotificationWorker.kt` | `1b531d6`、`be56423` |
| 今日桶过滤谓词三份实现，锁在 Context 类内无法单测 | `UsageStatsUseCase.kt` | `17fcc62`（并新增 `UsageStatsUseCaseTest`） |
