package com.dakala.app.domain.model

import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Locale

/**
 * 每日重置时间值类型
 *
 * 「存储格式 HH:mm + 默认值 00:00 + 容错解析」这条规则的唯一定义处。
 * 打卡周期的日界不再固定在零点：每天 [hour]:[minute] 时刻翻篇，
 * 当前周期 = 最近一次重置点到下一次重置点。
 *
 * @property minutesOfDay 重置点距零点的分钟数（0-1439）
 */
@ConsistentCopyVisibility
data class DayResetTime private constructor(
    val minutesOfDay: Int
) {
    val hour: Int get() = minutesOfDay / 60
    val minute: Int get() = minutesOfDay % 60

    /**
     * 序列化为设置表的存储格式（"HH:mm"）
     */
    fun format(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    /**
     * 当前打卡周期的起点（最近一次重置点）
     *
     * 例：重置 04:00、此刻 02:30 → 周期起点为昨天 04:00；
     * 此刻 05:00 → 周期起点为今天 04:00。
     */
    fun currentPeriodStart(clock: Clock): ZonedDateTime {
        val now = ZonedDateTime.now(clock)
        val todayReset = LocalTime.of(hour, minute).atDate(now.toLocalDate()).atZone(clock.zone)
        return if (now.isBefore(todayReset)) todayReset.minusDays(1) else todayReset
    }

    /**
     * 当前打卡周期起点（毫秒时间戳）
     */
    fun periodStartMillis(clock: Clock): Long =
        currentPeriodStart(clock).toInstant().toEpochMilli()

    /**
     * 当前打卡周期的日期键（yyyyMMdd），取周期起点所在日期
     *
     * 重置 00:00 时即日历日；重置 04:00 时凌晨 2 点仍归「昨天」。
     */
    fun dateKey(clock: Clock): Int {
        val date = currentPeriodStart(clock).toLocalDate()
        return date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
    }

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        /**
         * 默认重置时间：每晚 00:00
         */
        val DEFAULT = DayResetTime(0)

        /**
         * 由距零点分钟数构造；越界值钳制到 0-1439
         */
        fun ofMinutes(minutesOfDay: Int): DayResetTime =
            DayResetTime(minutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1))

        /**
         * 由时分构造
         */
        fun of(hour: Int, minute: Int): DayResetTime = ofMinutes(hour * 60 + minute)

        /**
         * 从设置表的原始字符串解析；null、格式错误或数值越界均回退到默认值 00:00。
         */
        fun parse(raw: String?): DayResetTime {
            if (raw == null) return DEFAULT
            val parts = raw.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: return DEFAULT
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: return DEFAULT
            if (hour !in 0..23 || minute !in 0..59) return DEFAULT
            return of(hour, minute)
        }
    }
}
