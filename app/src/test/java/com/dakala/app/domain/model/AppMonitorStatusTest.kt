package com.dakala.app.domain.model

import com.dakala.app.data.local.entity.AppItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMonitorStatusTest {

    @Test
    fun isOpenedToday_returns_false_when_duration_is_zero() {
        val appItem = AppItem(
            packageName = "com.test.app",
            appName = "Test App",
            durationThreshold = 600
        )
        val status = AppMonitorStatus(appItem, todayDurationSeconds = 0)
        assertFalse(status.isOpenedToday)
    }

    @Test
    fun isOpenedToday_returns_true_when_duration_greater_than_zero() {
        val appItem = AppItem(
            packageName = "com.test.app",
            appName = "Test App",
            durationThreshold = 600
        )
        val status = AppMonitorStatus(appItem, todayDurationSeconds = 60)
        assertTrue(status.isOpenedToday)
    }

    @Test
    fun isCompleted_returns_false_when_duration_below_threshold() {
        val appItem = AppItem(
            packageName = "com.test.app",
            appName = "Test App",
            durationThreshold = 600
        )
        val status = AppMonitorStatus(appItem, todayDurationSeconds = 300)
        assertFalse(status.isCompleted)
    }

    @Test
    fun isCompleted_returns_true_when_duration_equals_threshold() {
        val appItem = AppItem(
            packageName = "com.test.app",
            appName = "Test App",
            durationThreshold = 600
        )
        val status = AppMonitorStatus(appItem, todayDurationSeconds = 600)
        assertTrue(status.isCompleted)
    }

    @Test
    fun remainingSeconds_returns_correct_value() {
        val appItem = AppItem(
            packageName = "com.test.app",
            appName = "Test App",
            durationThreshold = 600
        )
        val status = AppMonitorStatus(appItem, todayDurationSeconds = 300)
        assertEquals(300, status.remainingSeconds)
    }

    @Test
    fun progress_returns_correct_ratio() {
        val appItem = AppItem(
            packageName = "com.test.app",
            appName = "Test App",
            durationThreshold = 600
        )
        val status = AppMonitorStatus(appItem, todayDurationSeconds = 300)
        assertEquals(0.5f, status.progress, 0.01f)
    }

    @Test
    fun formatDuration_returns_correct_format() {
        assertEquals("5分钟30秒", AppMonitorStatus.formatDuration(330))
        assertEquals("10分钟", AppMonitorStatus.formatDuration(600))
        assertEquals("45秒", AppMonitorStatus.formatDuration(45))
        assertEquals("0秒", AppMonitorStatus.formatDuration(0))
    }
}