package com.dakala.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageStatsUseCaseTest {

    private val todayStart = 1_700_000_000_000L
    private val todayEnd = todayStart + 24 * 60 * 60 * 1000L

    @Test
    fun latestDurationSeconds_excludes_bucket_spanning_midnight() {
        // 跨天桶（周/月/年）不能算作今日使用，否则会把昨天的时长显示到今天
        val buckets = listOf(
            UsageBucket(
                packageName = "com.test.app",
                firstTimeStamp = todayStart - 3_600_000L,
                lastTimeStamp = todayStart + 600_000L,
                totalTimeInForegroundMs = 3_600_000L
            )
        )

        assertEquals(0, buckets.latestDurationSeconds("com.test.app", todayStart, todayEnd))
    }

    @Test
    fun latestDurationSeconds_excludes_bucket_ending_after_range() {
        val buckets = listOf(
            UsageBucket(
                packageName = "com.test.app",
                firstTimeStamp = todayStart,
                lastTimeStamp = todayEnd + 1,
                totalTimeInForegroundMs = 600_000L
            )
        )

        assertEquals(0, buckets.latestDurationSeconds("com.test.app", todayStart, todayEnd))
    }

    @Test
    fun latestDurationSeconds_picks_newest_bucket_of_today() {
        val buckets = listOf(
            UsageBucket("com.test.app", todayStart, todayStart + 60_000L, 120_000L),
            UsageBucket("com.test.app", todayStart + 3_600_000L, todayStart + 3_660_000L, 300_000L)
        )

        assertEquals(300, buckets.latestDurationSeconds("com.test.app", todayStart, todayEnd))
    }

    @Test
    fun latestDurationSeconds_returns_zero_when_newest_bucket_has_no_usage() {
        // 最新今日桶时长为0时保持0，不回落取更早的非空桶
        val buckets = listOf(
            UsageBucket("com.test.app", todayStart, todayStart + 60_000L, 600_000L),
            UsageBucket("com.test.app", todayStart + 3_600_000L, todayStart + 3_660_000L, 0L)
        )

        assertEquals(0, buckets.latestDurationSeconds("com.test.app", todayStart, todayEnd))
    }

    @Test
    fun latestDurationSeconds_ignores_other_packages() {
        val buckets = listOf(
            UsageBucket("com.other.app", todayStart, todayStart + 60_000L, 600_000L)
        )

        assertEquals(0, buckets.latestDurationSeconds("com.test.app", todayStart, todayEnd))
    }
}
