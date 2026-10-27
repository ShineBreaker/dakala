package com.dakala.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 滚轮选择器（单列）
 *
 * 类系统计时器的滚轮控件：LazyColumn + 吸附惯性滚动，停靠项即选中项。
 * 列表首尾各垫 [visibleItemCount]/2 个空行，使首末项也能滚到中间；
 * 行下标 = 值下标 + 垫行数，因此 initialFirstVisibleItemIndex 传
 * selectedIndex 即可把初值摆正到中心。
 *
 * @param items 候选文本列表
 * @param selectedIndex 当前选中下标
 * @param onSelectedIndexChange 滚动停靠后回调选中下标
 * @param modifier 修饰符
 * @param enabled false 时禁用滚动并整体降透明度（如「跟随全局」态）
 * @param visibleItemCount 可见行数（奇数），中间行为选中行
 * @param itemHeight 单行高度
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visibleItemCount: Int = 3,
    itemHeight: Dp = 44.dp
) {
    val padCount = visibleItemCount / 2
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(items.indices)
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 视口中心所在的条目下标（行下标减去垫行数）
    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                selectedIndex
            } else {
                val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val row = visible.minByOrNull { item ->
                    abs(item.offset + item.size / 2 - center)
                }
                ((row?.index ?: 0) - padCount).coerceIn(items.indices)
            }
        }
    }

    // 滚动吸附落定后上报
    LaunchedEffect(centeredIndex) {
        if (centeredIndex != selectedIndex) {
            onSelectedIndexChange(centeredIndex)
        }
    }

    // 外部改值（预设快捷项）时滚到目标项
    LaunchedEffect(selectedIndex) {
        if (centeredIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItemCount)
            .clip(RoundedCornerShape(12.dp))
    ) {
        // 选中行高亮带（垫在列表下）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    RoundedCornerShape(10.dp)
                )
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            userScrollEnabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            items(count = items.size + padCount * 2) { row ->
                val index = row - padCount
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (index in items.indices) {
                        Text(
                            text = items[index],
                            style = if (index == centeredIndex) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            color = when {
                                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                index == centeredIndex -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 时刻滚轮（时 : 分）
 *
 * @param hour 当前小时（0-23）
 * @param minute 当前分钟（0-59）
 * @param onHourChange 小时变化回调
 * @param onMinuteChange 分钟变化回调
 * @param modifier 修饰符
 * @param enabled false 时两轮都禁用
 */
@Composable
fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val hours = remember { (0..23).map { "%02d".format(it) } }
    val minutes = remember { (0..59).map { "%02d".format(it) } }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelPicker(
            items = hours,
            selectedIndex = hour.coerceIn(0, 23),
            onSelectedIndexChange = onHourChange,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        WheelUnitLabel("时")
        WheelPicker(
            items = minutes,
            selectedIndex = minute.coerceIn(0, 59),
            onSelectedIndexChange = onMinuteChange,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        WheelUnitLabel("分")
    }
}

/**
 * 时长滚轮（时 : 分 : 秒）
 *
 * @param hour 当前小时（0-23）
 * @param minute 当前分钟（0-59）
 * @param second 当前秒（0-59）
 * @param onHourChange 小时变化回调
 * @param onMinuteChange 分钟变化回调
 * @param onSecondChange 秒变化回调
 * @param modifier 修饰符
 */
@Composable
fun DurationWheelPicker(
    hour: Int,
    minute: Int,
    second: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onSecondChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hours = remember { (0..23).map { "%02d".format(it) } }
    val minutes = remember { (0..59).map { "%02d".format(it) } }
    val seconds = remember { (0..59).map { "%02d".format(it) } }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelPicker(
            items = hours,
            selectedIndex = hour.coerceIn(0, 23),
            onSelectedIndexChange = onHourChange,
            modifier = Modifier.weight(1f)
        )
        WheelUnitLabel("时")
        WheelPicker(
            items = minutes,
            selectedIndex = minute.coerceIn(0, 59),
            onSelectedIndexChange = onMinuteChange,
            modifier = Modifier.weight(1f)
        )
        WheelUnitLabel("分")
        WheelPicker(
            items = seconds,
            selectedIndex = second.coerceIn(0, 59),
            onSelectedIndexChange = onSecondChange,
            modifier = Modifier.weight(1f)
        )
        WheelUnitLabel("秒")
    }
}

@Composable
private fun WheelUnitLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}
