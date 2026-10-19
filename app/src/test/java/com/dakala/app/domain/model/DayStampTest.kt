package com.dakala.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class DayStampTest {

    @Test
    fun of_changes_right_after_midnight() {
        val zone = ZoneId.of("Asia/Shanghai")
        // 同一本地日的 23:59:59 与次日的 00:00:01（UTC 表示）
        val beforeMidnight = Clock.fixed(Instant.parse("2026-10-19T15:59:59Z"), zone)
        val afterMidnight = Clock.fixed(Instant.parse("2026-10-19T16:00:01Z"), zone)

        assertEquals(20261019, DayStamp.of(beforeMidnight))
        assertEquals(20261020, DayStamp.of(afterMidnight))
    }

    @Test
    fun of_rolls_over_month_end() {
        val zone = ZoneId.of("Asia/Shanghai")
        assertEquals(
            20260131,
            DayStamp.of(Clock.fixed(Instant.parse("2026-01-31T15:59:59Z"), zone))
        )
        assertEquals(
            20260201,
            DayStamp.of(Clock.fixed(Instant.parse("2026-01-31T16:00:01Z"), zone))
        )
    }

    @Test
    fun of_encodes_single_digit_month_and_day() {
        assertEquals(
            20260109,
            DayStamp.of(Clock.fixed(Instant.parse("2026-01-09T01:00:00Z"), ZoneId.of("UTC")))
        )
    }
}
