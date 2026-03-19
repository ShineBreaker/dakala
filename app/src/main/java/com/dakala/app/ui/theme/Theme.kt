package com.dakala.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material 3 Expressive 配色方案
 * 
 * 定义应用的颜色主题，遵循Material 3 Expressive设计语言。
 * 使用温暖、活泼的色调，强调视觉冲击力。
 */

// 主色调
private val Primary = Color(0xFFFF6B35)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFFFFDBD1)
private val OnPrimaryContainer = Color(0xFF3D0D00)

// 次要色调
private val Secondary = Color(0xFF77574D)
private val OnSecondary = Color(0xFFFFFFFF)
private val SecondaryContainer = Color(0xFFFFDBD1)
private val OnSecondaryContainer = Color(0xFF2C150F)

// 第三色调
private val Tertiary = Color(0xFF695D2F)
private val OnTertiary = Color(0xFFFFFFFF)
private val TertiaryContainer = Color(0xFFF3E1A6)
private val OnTertiaryContainer = Color(0xFF221B00)

// 背景色
private val Background = Color(0xFFFFF8F5)
private val OnBackground = Color(0xFF231916)
private val Surface = Color(0xFFFFF8F5)
private val OnSurface = Color(0xFF231916)

// 表面变体
private val SurfaceVariant = Color(0xFFF5DDD6)
private val OnSurfaceVariant = Color(0xFF53433F)

// 轮廓色
private val Outline = Color(0xFF85736E)
private val OutlineVariant = Color(0xFFD8C2BB)

// 状态色
private val Success = Color(0xFF4CAF50)
private val Warning = Color(0xFFFF9800)
private val Error = Color(0xFFBA1A1A)
private val ErrorContainer = Color(0xFFFFDAD6)
private val OnErrorContainer = Color(0xFF410002)

// 特殊色
private val IncompleteBackground = Color(0xFFFFEBEE)
private val CompletedBackground = Color(0xFFE8F5E9)

/**
 * 浅色主题配色方案
 */
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

/**
 * 深色主题配色方案
 */
private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF5DED8),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF5DED8),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BB),
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

/**
 * 应用主题
 *
 * @param darkTheme 是否使用深色主题
 * @param dynamicColor 是否使用动态颜色（Material You），仅 Android 12+ 支持
 * @param content 内容
 */
@Composable
fun DakalaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * 扩展颜色定义 - 支持深色模式和动态颜色
 */
object DakalaColors {
    // 浅色模式颜色（fallback）
    val IncompleteBackgroundLight = Color(0xFFFFEBEE)
    val CompletedBackgroundLight = Color(0xFFE8F5E9)

    // 深色模式颜色（fallback）
    val IncompleteBackgroundDark = Color(0xFF4A1C1C)
    val CompletedBackgroundDark = Color(0xFF1B3D1F)

    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    val GrayscaleFilter = Color(0xFF808080)

    /**
     * 获取未完成背景色
     * 使用 MaterialTheme 的 errorContainer 颜色以支持动态颜色
     */
    @Composable
    fun getIncompleteBackground(): Color {
        return MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    }

    /**
     * 获取已完成背景色
     * 使用 MaterialTheme 的 primaryContainer 颜色以支持动态颜色
     */
    @Composable
    fun getCompletedBackground(): Color {
        return MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }
}
