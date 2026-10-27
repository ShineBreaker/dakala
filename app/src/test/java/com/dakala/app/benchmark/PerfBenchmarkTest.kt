package com.dakala.app.benchmark

import com.dakala.app.data.local.entity.AppItem
import com.dakala.app.data.org.OrgParser
import com.dakala.app.data.org.OrgWriter
import com.dakala.app.domain.model.appMonitorStatuses
import com.dakala.app.domain.usecase.ForegroundEvent
import com.dakala.app.domain.usecase.ForegroundEventKind
import com.dakala.app.domain.usecase.foregroundSeconds
import java.io.File
import java.time.LocalDateTime
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 性能基准（改进前后对比用）
 *
 * 协议：输入全部由固定种子构造（java.util.Random 的算法有 Java 规范保证，跨版本可复现）；
 * 每项先预热 5 次再测量 9 次，取中位数（纳秒），以 "name=纳秒数" 逐行追加到
 * build/bench/results.txt（同时打印到测试 stdout），每次运行首个写入者先清空该文件。
 * 不做任何耗时断言——本测试恒通过，只测量并落盘。
 *
 * 运行方式：`./gradlew :app:testDebugUnitTest --no-configuration-cache
 * --tests "com.dakala.app.benchmark.PerfBenchmarkTest"`。
 * 注意不能对 `test` 聚合任务传 `--tests`（AGP 中它是 lifecycle 任务，不接收该选项）。
 *
 * 覆盖三个热点，各有两档数据规模（几百条 / 上千条）：
 * - OrgParser.parse：org 待办文件全量解析
 * - OrgWriter.toggleNote：打卡写回（行级改写；显式传入同源 states，
 *   全量 parse 的成本由 parse 基准单独承担）
 * - 使用时长→打卡聚合：foregroundSeconds 逐包事件聚合 + appMonitorStatuses
 *   映射与派生属性（GetIncompleteAppsUseCase 的纯 JVM 链路）
 */
class PerfBenchmarkTest {

    /** 中间结果汇：累积每次动作的返回值，防止 JIT 死码消除把测量对象优化成空转 */
    @Volatile
    private var sink = 0

    @Test
    fun bench_parse_org() {
        val medium = buildOrgSample(noteCount = 200, seed = SEED)
        val large = buildOrgSample(noteCount = 1000, seed = SEED + 1)
        // 固定输入的正确性校验（非耗时断言）：全部条目带已识别状态关键字，应全部收录
        assertEquals(200, OrgParser.parse(medium.content).notes.size)
        assertEquals(1000, OrgParser.parse(large.content).notes.size)

        medianNanos("parse_org_medium") { OrgParser.parse(medium.content).notes.size }
        medianNanos("parse_org_large") { OrgParser.parse(large.content).notes.size }
    }

    @Test
    fun bench_write_org() {
        val medium = buildOrgSample(noteCount = 200, seed = SEED)
        val large = buildOrgSample(noteCount = 1000, seed = SEED + 1)
        val states = OrgParser.parse(large.content).states
        // 校验写回目标行是合法待办标题（非耗时断言）
        assertNotNull(OrgWriter.toggleNote(medium.content, medium.toggleLineIndex, states, FIXED_NOW))

        medianNanos("write_toggle_medium") {
            OrgWriter.toggleNote(medium.content, medium.toggleLineIndex, states, FIXED_NOW)?.length ?: 0
        }
        medianNanos("write_toggle_large") {
            OrgWriter.toggleNote(large.content, large.toggleLineIndex, states, FIXED_NOW)?.length ?: 0
        }
    }

    @Test
    fun bench_aggregate_usage() {
        val medium = buildUsageSample(appCount = 60, seed = SEED)
        val large = buildUsageSample(appCount = 200, seed = SEED + 1)
        // 固定输入的正确性校验（非耗时断言）
        assertEquals(60, medium.apps.size)
        assertEquals(200, large.apps.size)
        assertTrue(medium.events.isNotEmpty())

        medianNanos("aggregate_usage_medium") { aggregateOnce(medium) }
        medianNanos("aggregate_usage_large") { aggregateOnce(large) }
    }

    // ---- 测量与结果落盘 ----

    /** 预热 [WARMUP] 次后测量 [MEASURE] 次，取中位数（纳秒）写入结果文件 */
    private fun medianNanos(name: String, action: () -> Int) {
        repeat(WARMUP) { sink += action() }
        val samples = LongArray(MEASURE)
        for (i in samples.indices) {
            val start = System.nanoTime()
            sink += action()
            samples[i] = System.nanoTime() - start
        }
        samples.sort()
        record(name, samples[samples.size / 2])
    }

    /** 模拟 GetIncompleteAppsUseCase 的纯 JVM 链路：逐包事件聚合 → 状态映射 → 派生属性与完成过滤 */
    private fun aggregateOnce(sample: UsageSample): Int {
        val durations = sample.apps.map { it.packageName }.associateWith { pkg ->
            sample.events.foregroundSeconds(pkg, sample.dayStart, sample.dayEnd)
        }
        val statuses = appMonitorStatuses(sample.apps, durations)
        var checksum = 0
        for (status in statuses) {
            checksum += if (status.isCompleted) {
                status.progress.toInt() + status.todayDurationFormatted.length
            } else {
                status.remainingSeconds + status.thresholdFormatted.length
            }
        }
        return checksum + statuses.count { !it.isCompleted }
    }

    // ---- 固定输入构造 ----

    /** 固定种子生成的 org 待办文件样本；toggleLineIndex 指向中间一条未完成条目的标题行 */
    private class OrgSample(val content: String, val toggleLineIndex: Int)

    private fun buildOrgSample(noteCount: Int, seed: Long): OrgSample {
        val random = Random(seed)
        val states = arrayOf("TODO", "NEXT", "DONE", "CANCELLED")
        val priorities = arrayOf("A", "B", "C")
        val weekdays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val sb = StringBuilder()
        val openLines = mutableListOf<Int>()
        sb.append("#+TITLE: Dakala Bench\n")
        sb.append("#+SEQ_TODO: TODO(t) NEXT(n) | DONE(d) CANCELLED(c)\n")
        sb.append('\n')
        var line = 3
        for (i in 0 until noteCount) {
            val level = if (random.nextInt(4) == 0) 2 else 1
            val state = states[random.nextInt(states.size)]
            sb.append("*".repeat(level)).append(' ').append(state)
            if (random.nextInt(10) < 3) {
                sb.append(" [#").append(priorities[random.nextInt(priorities.size)]).append(']')
            }
            sb.append(" 打卡条目 ").append(i)
            if (random.nextInt(10) < 4) {
                sb.append(" :tag").append(random.nextInt(8)).append(':')
                if (random.nextInt(2) == 0) sb.append("daily:")
            }
            sb.append('\n')
            if (state == "TODO" || state == "NEXT") openLines.add(line)
            line++
            // planning 行（约一半条目带）
            if (random.nextInt(2) == 0) {
                val day = (1 + random.nextInt(28)).toString().padStart(2, '0')
                val weekday = weekdays[random.nextInt(weekdays.size)]
                val hour = random.nextInt(24).toString().padStart(2, '0')
                val parts = mutableListOf<String>()
                if (random.nextInt(2) == 0) {
                    parts.add("SCHEDULED: <2026-10-$day $weekday ${hour}:30>")
                }
                if (random.nextInt(2) == 0) {
                    parts.add("DEADLINE: <2026-10-$day $weekday ${hour}:45>")
                }
                if ((state == "DONE" || state == "CANCELLED") && random.nextInt(2) == 0) {
                    parts.add("CLOSED: [2026-10-$day $weekday ${hour}:15]")
                }
                if (parts.isNotEmpty()) {
                    sb.append("  ").append(parts.joinToString(" ")).append('\n')
                    line++
                }
            }
            // 正文干扰行（0-2 行）
            repeat(random.nextInt(3)) {
                sb.append("  条目 ").append(i).append(" 的正文记录，补足一些文本长度以贴近真实文件内容。\n")
                line++
            }
        }
        return OrgSample(sb.toString(), openLines[openLines.size / 2])
    }

    /** 固定种子生成的使用时长聚合样本：受监控应用 + 混合事件（窗口内 / 窗口前 / 无关包名） */
    private class UsageSample(
        val apps: List<AppItem>,
        val events: List<ForegroundEvent>,
        val dayStart: Long,
        val dayEnd: Long
    )

    private fun buildUsageSample(appCount: Int, seed: Long): UsageSample {
        val random = Random(seed)
        val dayStart = 1_700_000_000_000L
        val dayEnd = dayStart + 18L * 3_600_000L
        val apps = (0 until appCount).map { i ->
            AppItem(
                id = i + 1,
                packageName = "com.bench.app$i",
                appName = "基准应用$i",
                isMonitored = true,
                durationThreshold = 300 + random.nextInt(1500),
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L
            )
        }
        val packages = apps.map { it.packageName }
        val events = mutableListOf<ForegroundEvent>()
        for (app in apps) {
            // 每个应用 4-7 对 RESUME/PAUSE：三分之二落在窗口内、其余在窗口前；
            // 十分之一换成别的包名
            repeat(4 + random.nextInt(4)) {
                val pkg = if (random.nextInt(10) == 0) packages[random.nextInt(packages.size)] else app.packageName
                val resumeAt = if (random.nextInt(3) > 0) {
                    dayStart + random.nextInt(64_000_000)
                } else {
                    dayStart - 7_200_000L - random.nextInt(3_600_000)
                }
                val pauseAt = resumeAt + 60_000L + random.nextInt(600_000)
                events.add(ForegroundEvent(pkg, "Main", resumeAt, ForegroundEventKind.RESUMED))
                events.add(ForegroundEvent(pkg, "Main", pauseAt, ForegroundEventKind.PAUSED))
            }
        }
        // 无关包名的干扰事件
        repeat(appCount * 2) {
            val t = dayStart + random.nextInt(64_000_000)
            val kind = if (random.nextInt(2) == 0) {
                ForegroundEventKind.RESUMED
            } else {
                ForegroundEventKind.PAUSED
            }
            events.add(ForegroundEvent("com.other.app${random.nextInt(1000)}", "Main", t, kind))
        }
        events.shuffle(random)
        return UsageSample(apps, events, dayStart, dayEnd)
    }

    companion object {
        private const val WARMUP = 5
        private const val MEASURE = 9
        private const val SEED = 42L
        private val FIXED_NOW = LocalDateTime.of(2026, 10, 20, 12, 0)
        private val fileLock = Any()
        private var cleared = false

        /** 追加一行 name=纳秒；每次运行的首个写入者先清空结果文件 */
        private fun record(name: String, nanos: Long) {
            val line = "$name=$nanos"
            // 同步打印到测试 stdout（JUnit 报告 system-out 可见），供直接解析输出的运行方式使用
            println(line)
            val file = File(benchDir(), "results.txt")
            synchronized(fileLock) {
                if (!cleared) {
                    file.parentFile?.mkdirs()
                    file.writeText("")
                    cleared = true
                }
                file.appendText(line + "\n")
            }
        }

        /** 定位 app/build/bench：兼容测试工作目录为模块目录或仓库根两种运行方式 */
        private fun benchDir(): File {
            val cwd = File(System.getProperty("user.dir") ?: ".")
            return when {
                File(cwd, "src/main").isDirectory -> File(cwd, "build/bench")
                File(cwd, "app/src/main").isDirectory -> File(cwd, "app/build/bench")
                else -> File(cwd, "build/bench")
            }
        }
    }
}
