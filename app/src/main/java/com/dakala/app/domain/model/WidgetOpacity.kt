package com.dakala.app.domain.model

/**
 * 小部件背景透明度值类型
 *
 * 「百分比 0-100 + 默认值 90% + 容错解析」这条规则的唯一定义处：
 * 设置表的字符串格式不越过本类型泄露给界面或小部件渲染。
 *
 * @property percent 不透明度百分比，0（全透明）到 100（不透明）
 */
data class WidgetOpacity(
    val percent: Int
) {
    init {
        require(percent in 0..100) { "percent must be in 0..100, was $percent" }
    }

    /**
     * 换算为 Drawable/View 使用的 alpha 值（0-255）
     */
    fun toAlpha(): Int = percent * 255 / 100

    /**
     * 序列化为设置表的存储格式（百分比整数字符串）
     */
    fun format(): String = percent.toString()

    companion object {
        /**
         * 设置缺失或非法时的回退值：90% 不透明
         *
         * 取 90 而非更低：背景色已极端化补偿，但过低透明度下花壁纸
         * 仍会显著降低文字对比度，默认值优先保可读性。
         */
        val DEFAULT = WidgetOpacity(90)

        /**
         * 从设置表的原始字符串解析；null、非数字或越界均回退到默认值。
         */
        fun parse(raw: String?): WidgetOpacity {
            val percent = raw?.toIntOrNull() ?: return DEFAULT
            if (percent !in 0..100) return DEFAULT
            return WidgetOpacity(percent)
        }
    }
}
