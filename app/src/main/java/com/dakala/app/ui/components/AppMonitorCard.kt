package com.dakala.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dakala.app.R
import com.dakala.app.domain.model.AppMonitorStatus
import com.dakala.app.ui.theme.DakalaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用监控项卡片组件
 *
 * 显示单个应用的监控状态，包括：
 * - 应用图标（已完成则灰度处理）
 * - 应用名称
 * - 今日打开状态
 * - 今日使用时长
 * - 完成进度
 *
 * @param status 应用监控状态
 * @param onAppClick 点击应用时的回调
 * @param onSettingsClick 点击设置按钮时的回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppMonitorCard(
    status: AppMonitorStatus,
    onAppClick: (String) -> Unit,
    onSettingsClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isCompleted = status.isCompleted

    // 应用图标（含完成态灰度化）：PackageManager 读取与位图处理放到 IO 线程，
    // 列表滚动期间不在组合线程做磁盘 IO 与像素遍历
    val displayIcon by produceState<Bitmap?>(
        initialValue = null,
        status.appItem.packageName,
        isCompleted
    ) {
        value = withContext(Dispatchers.IO) {
            val drawable = try {
                context.packageManager.getApplicationIcon(status.appItem.packageName)
            } catch (e: Exception) {
                context.getDrawable(android.R.drawable.sym_def_app_icon)
            }
            drawable?.let { drawableToBitmap(it) }?.let { bitmap ->
                if (isCompleted) toGrayscale(bitmap) else bitmap
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onAppClick(status.appItem.packageName) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                DakalaColors.getCompletedBackground()
            } else {
                DakalaColors.getIncompleteBackground()
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 应用图标：固定占位，解码完成前不挤压布局
            Box(modifier = Modifier.size(48.dp)) {
                displayIcon?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = status.appItem.appName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 应用信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // 应用名称
                Text(
                    text = status.appItem.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 状态信息
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 打开状态
                    Text(
                        text = if (status.isOpenedToday) {
                            stringResource(R.string.status_opened)
                        } else {
                            stringResource(R.string.status_not_opened)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.isOpenedToday) {
                            DakalaColors.Success
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 使用时长
                    Text(
                        text = status.todayDurationFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 进度条（M3 Expressive 波浪指示器）
                LinearWavyProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isCompleted) {
                        DakalaColors.Success
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 目标提示
                Text(
                    text = "目标: ${status.thresholdFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 设置按钮
            IconButton(
                onClick = { onSettingsClick(status.appItem.packageName) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置时长",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 将Bitmap转换为灰度图
 *
 * @param source 原始Bitmap
 * @return 灰度化的Bitmap
 */
private fun toGrayscale(source: Bitmap): Bitmap {
    val grayBitmap = Bitmap.createBitmap(
        source.width,
        source.height,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(grayBitmap)
    val paint = Paint()
    val colorMatrix = ColorMatrix().apply {
        setSaturation(0f)
    }
    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(source, 0f, 0f, paint)

    return grayBitmap
}

/**
 * 将Drawable转换为Bitmap
 *
 * @param drawable Drawable对象
 * @return Bitmap对象
 */
internal fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }

    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1),
        drawable.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)

    return bitmap
}

/**
 * 空状态组件
 *
 * 当没有监控应用时显示。
 *
 * @param message 提示信息
 * @param modifier 修饰符
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📋",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 分组标题组件
 *
 * @param title 标题
 * @param count 数量
 * @param modifier 修饰符
 */
@Composable
fun SectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Badge(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
