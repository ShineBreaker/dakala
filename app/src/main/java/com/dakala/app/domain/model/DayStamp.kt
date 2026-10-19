package com.dakala.app.domain.model

import java.time.Clock
import java.time.LocalDate

/**
 * 日期键（yyyyMMdd 整数）
 *
 * 「今天是哪天」的唯一计算处。这条计算此前在 MainViewModel、AppUsageRepository、
 * UsageWidgetProvider、WidgetService 各写一份，跨天边界没有单一归属——
 * 而它正是历次「跨天不刷新」缺陷的病根。
 */
object DayStamp {

    /**
     * 按给定时钟取日期键；测试用固定时钟覆盖跨零点边界
     */
    fun of(clock: Clock): Int {
        val date = LocalDate.now(clock)
        return date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
    }

    /**
     * 今天（系统默认时区）
     */
    fun today(): Int = of(Clock.systemDefaultZone())
}
