package com.dakala.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.dakala.app.ui.theme.DarkColorScheme
import com.dakala.app.ui.theme.LightColorScheme

/**
 * 小部件调色板
 *
 * RemoteViews 无法引用 Compose 主题，本类把 [ColorScheme] 拍平成可直接
 * 传给 setTextColor/setColorFilter 等反射调用的具体色值（ARGB int）。
 *
 * 取色原则（见 docs/DESIGN.md）：API 31+ 与应用共用 Material You 动态
 * 调色板；API 29/30 回退到应用自带的静态明暗配色，两套色走同一个
 * ColorScheme 接口，调用方不感知差异。
 *
 * 可读性适配：背景半透明会透出壁纸吃掉对比度，因此——
 * 背景色在 surface 基础上向极端方向（深更黑/浅更白）偏移；
 * 次级文字不用 onSurfaceVariant（半透明底上太灰），用 onSurface
 * 混入少量 variant 的实体色；状态绿按明暗分档。
 */
data class WidgetPalette(
    @field:ColorInt val background: Int,
    @field:ColorInt val onSurface: Int,
    @field:ColorInt val secondary: Int,
    @field:ColorInt val accent: Int,
    @field:ColorInt val error: Int,
    @field:ColorInt val success: Int
) {
    companion object {
        /**
         * 浅色模式调色板；API 31+ 为动态取色，以下为静态配色
         */
        fun light(context: Context): WidgetPalette = fromColorScheme(
            if (supportsDynamicColor()) dynamicLightColorScheme(context) else LightColorScheme,
            isDark = false
        )

        /**
         * 深色模式调色板；API 31+ 为动态取色，以下为静态配色
         */
        fun dark(context: Context): WidgetPalette = fromColorScheme(
            if (supportsDynamicColor()) dynamicDarkColorScheme(context) else DarkColorScheme,
            isDark = true
        )

        /**
         * 按当前系统明暗模式解析的调色板
         *
         * 用于 API 29/30 单份 RemoteViews 构建，以及 RemoteViewsService
         * 工厂为列表项取色（数据在宿主当前模式下重新拉取）。
         */
        fun current(context: Context): WidgetPalette =
            if (isNightMode(context)) dark(context) else light(context)

        /**
         * 当前系统是否处于深色模式
         */
        fun isNightMode(context: Context): Boolean {
            val uiMode = context.resources.configuration.uiMode
            return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        private fun supportsDynamicColor(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        private fun fromColorScheme(scheme: ColorScheme, isDark: Boolean): WidgetPalette {
            val onSurface = scheme.onSurface.toArgb()
            return WidgetPalette(
                background = ColorUtils.blendARGB(
                    scheme.surface.toArgb(),
                    if (isDark) Color.BLACK else Color.WHITE,
                    BG_SHIFT
                ),
                onSurface = onSurface,
                secondary = ColorUtils.blendARGB(
                    onSurface,
                    scheme.onSurfaceVariant.toArgb(),
                    SECONDARY_SHIFT
                ),
                accent = scheme.primary.toArgb(),
                error = scheme.error.toArgb(),
                success = if (isDark) SUCCESS_DARK else SUCCESS_LIGHT
            )
        }

        /** 背景色向极端方向偏移比例：深色更黑、浅色更白，补偿半透明损失的对比 */
        private const val BG_SHIFT = 0.25f

        /** 次级文字混入 onSurfaceVariant 的比例：以 onSurface 为主保持亮度 */
        private const val SECONDARY_SHIFT = 0.3f

        /** 完成绿分档：深色模式用亮绿，浅色模式用深绿 */
        private const val SUCCESS_DARK = 0xFF66BB6A.toInt()
        private const val SUCCESS_LIGHT = 0xFF2E7D32.toInt()
    }
}
