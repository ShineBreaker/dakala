package com.dakala.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class DayResetTimeTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun clockAt(instant: String) = Clock.fixed(Instant.parse(instant), zone)

    // 2026-10-28 02:00 北京时间 = 2026-10-27T18:00Z
    private val oct28_0200 = clockAt("2026-10-27T18:00:00Z")

    // 2026-10-28 05:00 北京时间 = 2026-10-27T21:00Z
    private val oct28_0500 = clockAt("2026-10-27T21:00:00Z")

    // 2026-10-28 22:00 北京时间 = 2026-10-28T14:00Z
    private val oct28_2200 = clockAt("2026-10-28T14:00:00Z")

    // 2026-10-28 23:30 北京时间 = 2026-10-28T15:30Z
    private val oct28_2330 = clockAt("2026-10-28T15:30:00Z")

    @Test
    fun dateKey_default_reset_matches_calendar_day() {
        // 默认 00:00：与自然日一致
        assertEquals(20261028, DayResetTime.DEFAULT.dateKey(oct28_0200))
        assertEquals(20261028, DayResetTime.DEFAULT.dateKey(oct28_2330))
    }

    @Test
    fun dateKey_early_morning_reset_keeps_yesterday() {
        // 重置 04:00：凌晨 2 点仍属于「昨天」的打卡周期
        val reset = DayResetTime.of(4, 0)
        assertEquals(20261027, reset.dateKey(oct28_0200))
    }

    @Test
    fun dateKey_early_morning_reset_rolls_after_reset() {
        // 重置 04:00：凌晨 5 点已进入新周期
        val reset = DayResetTime.of(4, 0)
        assertEquals(20261028, reset.dateKey(oct28_0500))
    }

    @Test
    fun dateKey_late_night_reset_labels_by_period_start() {
        // 重置 23:00：22 点属于「昨天 23 点开启」的周期，23:30 翻篇
        val reset = DayResetTime.of(23, 0)
        assertEquals(20261027, reset.dateKey(oct28_2200))
        assertEquals(20261028, reset.dateKey(oct28_2330))
    }

    @Test
    fun periodStartMillis_picks_latest_reset_before_now() {
        // 重置 04:00、此刻 10-28 02:00 → 周期起点为 10-27 04:00（北京）
        val reset = DayResetTime.of(4, 0)
        val expected = Instant.parse("2026-10-26T20:00:00Z").toEpochMilli()
        assertEquals(expected, reset.periodStartMillis(oct28_0200))
    }

    @Test
    fun periodStartMillis_same_day_when_after_reset() {
        // 重置 04:00、此刻 10-28 05:00 → 周期起点为 10-28 04:00（北京）
        val reset = DayResetTime.of(4, 0)
        val expected = Instant.parse("2026-10-27T20:00:00Z").toEpochMilli()
        assertEquals(expected, reset.periodStartMillis(oct28_0500))
    }

    @Test
    fun parse_falls_back_to_default_on_invalid_input() {
        assertEquals(DayResetTime.DEFAULT, DayResetTime.parse(null))
        assertEquals(DayResetTime.DEFAULT, DayResetTime.parse(""))
        assertEquals(DayResetTime.DEFAULT, DayResetTime.parse("abc"))
        assertEquals(DayResetTime.DEFAULT, DayResetTime.parse("25:00"))
        assertEquals(DayResetTime.DEFAULT, DayResetTime.parse("12:60"))
    }

    @Test
    fun parse_and_format_round_trip() {
        val time = DayResetTime.parse("04:30")
        assertEquals(4, time.hour)
        assertEquals(30, time.minute)
        assertEquals("04:30", time.format())
        assertEquals("23:00", DayResetTime.parse("23:00").format())
    }
}
