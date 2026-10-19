package com.dakala.app.domain.model

import java.util.Locale

/**
 * 通知时间值类型
 *
 * 「存储格式 HH:mm + 默认值 22:00 + 容错解析」这条规则的唯一定义处：
 * 设置表的字符串格式不越过本类型泄露给界面或后台排期。
 *
 * @property hour 小时（0-23）
 * @property minute 分钟（0-59）
 */
data class NotificationTime(
    val hour: Int,
    val minute: Int
) {
    /**
     * 序列化为设置表的存储格式（"HH:mm"）
     */
    fun format(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    companion object {
        /**
         * 设置缺失或非法时的回退值
         */
        val DEFAULT = NotificationTime(22, 0)

        /**
         * 从设置表的原始字符串解析；null、格式错误或数值越界均回退到默认值 22:00。
         */
        fun parse(raw: String?): NotificationTime {
            if (raw == null) return DEFAULT
            val parts = raw.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: return DEFAULT
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: return DEFAULT
            if (hour !in 0..23 || minute !in 0..59) return DEFAULT
            return NotificationTime(hour, minute)
        }
    }
}
