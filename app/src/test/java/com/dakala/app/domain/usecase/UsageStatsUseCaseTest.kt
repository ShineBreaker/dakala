package com.dakala.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageStatsUseCaseTest {

    private val windowStart = 1_700_000_000_000L
    private val windowEnd = windowStart + 24 * 60 * 60 * 1000L

    private fun resumed(pkg: String, cls: String?, at: Long) =
        ForegroundEvent(pkg, cls, at, ForegroundEventKind.RESUMED)

    private fun paused(pkg: String, cls: String?, at: Long) =
        ForegroundEvent(pkg, cls, at, ForegroundEventKind.PAUSED)

    @Test
    fun foregroundSeconds_sums_resume_pause_pair() {
        val events = listOf(
            resumed("com.test.app", "Main", windowStart + 1_000L),
            paused("com.test.app", "Main", windowStart + 61_000L)
        )

        assertEquals(60, events.foregroundSeconds("com.test.app", windowStart, windowEnd))
    }

    @Test
    fun foregroundSeconds_sums_multiple_sessions() {
        val events = listOf(
            resumed("com.test.app", "Main", windowStart + 1_000L),
            paused("com.test.app", "Main", windowStart + 61_000L),
            resumed("com.test.app", "Main", windowStart + 601_000L),
            paused("com.test.app", "Main", windowStart + 901_000L)
        )

        assertEquals(360, events.foregroundSeconds("com.test.app", windowStart, windowEnd))
    }

    @Test
    fun foregroundSeconds_counts_when_in_foreground_at_window_start() {
        // 窗口起点前已 RESUME（重置点前打开的应用）：窗口内部分照常计入
        val events = listOf(
            resumed("com.test.app", "Main", windowStart - 60_000L),
            paused("com.test.app", "Main", windowStart + 120_000L)
        )

        assertEquals(120, events.foregroundSeconds("com.test.app", windowStart, windowEnd))
    }

    @Test
    fun foregroundSeconds_counts_whole_window_when_foreground_throughout() {
        // 起点前 RESUME、窗口内无任何后续事件且仍在用：整段窗口计入
        val events = listOf(
            resumed("com.test.app", "Main", windowStart - 60_000L)
        )

        assertEquals(
            (windowEnd - windowStart).toInt() / 1000,
            events.foregroundSeconds("com.test.app", windowStart, windowEnd)
        )
    }

    @Test
    fun foregroundSeconds_pre_window_activity_switch_counts_to_end() {
        // 起点前 RESUME A，窗口内换页 RESUME B 后再退出：整段窗口应连续计入
        val events = listOf(
            resumed("com.test.app", "A", windowStart - 60_000L),
            resumed("com.test.app", "B", windowStart + 60_000L),
            paused("com.test.app", "B", windowStart + 120_000L),
            paused("com.test.app", "A", windowStart + 180_000L)
        )

        assertEquals(180, events.foregroundSeconds("com.test.app", windowStart, windowEnd))
    }

    @Test
    fun foregroundSeconds_counts_until_end_when_still_foreground() {
        val events = listOf(
            resumed("com.test.app", "Main", windowEnd - 30_000L)
        )

        assertEquals(30, events.foregroundSeconds("com.test.app", windowStart, windowEnd))
    }

    @Test
    fun foregroundSeconds_tolerates_out_of_order_activity_switch() {
        // Activity 切换可能 RESUME 新页先于 PAUSE 旧页到达；
        // 任一类名仍 resumed 即视为前台，整段连续计入
        val events = listOf(
            resumed("com.test.app", "A", windowStart + 1_000L),
            resumed("com.test.app", "B", windowStart + 61_000L),
            paused("com.test.app", "A", windowStart + 62_000L),
            paused("com.test.app", "B", windowStart + 121_000L)
        )

        assertEquals(120, events.foregroundSeconds("com.test.app", windowStart, windowEnd))
    }

    @Test
    fun foregroundSeconds_excludes_events_outside_window() {
        val events = listOf(
            resumed("com.test.app", "Main", windowStart - 120_000L),
            paused("com.test.app", "Main", windowStart - 60_000L),
            resumed("com.test.app", "Main", windowStart + 1_000L),
            paused("com.test.app", "Main", windowStart + 61_000L)
        )

        assertEquals(60, events.foregroundSeconds("com.test.app", windowStart, windowEnd))
    }

    @Test
    fun foregroundSeconds_ignores_other_packages() {
        val events = listOf(
            resumed("com.other.app", "Main", windowStart + 1_000L),
            paused("com.other.app", "Main", windowStart + 61_000L)
        )

        assertEquals(0, events.foregroundSeconds("com.test.app", windowStart, windowEnd))
    }

    @Test
    fun foregroundSeconds_returns_zero_without_events() {
        assertEquals(0, emptyList<ForegroundEvent>()
            .foregroundSeconds("com.test.app", windowStart, windowEnd))
    }

    @Test
    fun foregroundSeconds_stray_pause_is_ignored() {
        // 无对应 RESUME 的 PAUSE 不计时：起点前台状态由回溯事件回放得出，
        // 窗口内孤立的 PAUSE 不能凭空断定起点时在前台
        val events = listOf(
            paused("com.test.app", "Main", windowStart + 60_000L),
            paused("com.test.app", "Ghost", windowStart + 120_000L)
        )

        assertEquals(0, events.foregroundSeconds("com.test.app", windowStart, windowEnd))
    }
}
