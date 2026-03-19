package com.dakala.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.dakala.app.data.local.entity.CustomCheckItem
import com.dakala.app.domain.model.CustomCheckStatus
import com.dakala.app.domain.model.CustomCheckStatusGroup
import java.io.InputStream

/**
 * 自定义打卡列表组件
 */
@Composable
fun CustomCheckList(
    statusGroup: CustomCheckStatusGroup,
    onCheckToggle: (Int, Boolean) -> Unit,
    onEdit: (CustomCheckItem) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (statusGroup.isEmpty) {
        EmptyState(
            message = "暂无自定义打卡项\n点击下方按钮添加",
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 未完成区域
            if (statusGroup.hasIncompleteItems) {
                item {
                    SectionHeader(
                        title = "未完成",
                        count = statusGroup.incompleteItems.size
                    )
                }
                items(statusGroup.incompleteItems) { status ->
                    CustomCheckCard(
                        status = status,
                        onCheckToggle = { onCheckToggle(status.item.id, true) },
                        onEdit = { onEdit(status.item) },
                        onDelete = { onDelete(status.item.id) }
                    )
                }
            }

            // 已完成区域
            if (statusGroup.hasCompletedItems) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(
                        title = "已完成",
                        count = statusGroup.completedItems.size
                    )
                }
                items(statusGroup.completedItems) { status ->
                    CustomCheckCard(
                        status = status,
                        onCheckToggle = { onCheckToggle(status.item.id, false) },
                        onEdit = { onEdit(status.item) },
                        onDelete = { onDelete(status.item.id) }
                    )
                }
            }
        }
    }
}

/**
 * 自定义打卡卡片组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCheckCard(
    status: CustomCheckStatus,
    onCheckToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompleted = status.isCompleted
    val context = LocalContext.current

    // 展开菜单状态
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (status.item.iconType == "image") {
                    // 显示图片图标
                    val bitmap = remember(status.item.iconData) {
                        try {
                            val uri = status.item.iconData.toUri()
                            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                            inputStream?.use { BitmapFactory.decodeStream(it) }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = status.item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: Text(
                        text = status.item.name.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.headlineMedium
                    )
                } else {
                    // 显示emoji图标
                    Text(
                        text = status.item.iconData,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 名称
            Text(
                text = status.item.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            // 打钩按钮 - 未完成显示空心圆，已完成显示实心勾
            IconButton(onClick = onCheckToggle) {
                Icon(
                    imageVector = if (isCompleted) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = if (isCompleted) "取消完成" else "标记完成",
                    tint = if (isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(28.dp)
                )
            }

            // 更多操作菜单
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "更多操作"
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            expanded = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 添加/编辑自定义打卡项对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomCheckDialog(
    item: CustomCheckItem? = null,
    onConfirm: (String, String, String) -> Unit, // name, iconType, iconData
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(item?.name ?: "") }
    var iconType by remember { mutableStateOf(item?.iconType ?: "emoji") }
    var iconData by remember { mutableStateOf(item?.iconData ?: "✅") }
    var customEmoji by remember { mutableStateOf("") }

    // 图片选择器
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    if (item?.iconType == "image" && selectedImageUri == null) {
        selectedImageUri = try {
            item.iconData.toUri()
        } catch (e: Exception) {
            null
        }
    }

    // 图片裁剪启动器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            iconType = "image"
            iconData = it.toString()
        }
    }

    // 预设emoji列表
    val presetEmojis = listOf(
        "✅", "⭐", "🎯", "💪", "📚", "🏃", "💧", "🍎", "🧘", "💤",
        "📝", "🎵", "🎨", "💊", "🥗", "🏋️", "🚴", "🏊", "🧹", "🌱",
        "☀️", "🌙", "❤️", "🔥", "⏰", "📱", "💻", "📖", "✏️", "🎮"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "添加自定义打卡" else "编辑自定义打卡") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 名称输入
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如：喝水、运动、阅读") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 图标类型选择
                Text(
                    text = "选择图标类型",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = iconType == "emoji",
                        onClick = { iconType = "emoji" },
                        label = { Text("Emoji") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = iconType == "image",
                        onClick = { iconType = "image" },
                        label = { Text("图片") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 根据类型显示不同的选择界面
                if (iconType == "emoji") {
                    // 自定义emoji输入
                    OutlinedTextField(
                        value = customEmoji,
                        onValueChange = {
                            if (it.length <= 2) { // 限制emoji输入长度
                                customEmoji = it
                                if (it.isNotEmpty()) {
                                    iconData = it
                                }
                            }
                        },
                        label = { Text("自定义Emoji") },
                        placeholder = { Text("输入任意emoji") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 预设emoji网格
                    Text(
                        text = "或选择预设图标",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.height(120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(presetEmojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (iconData == emoji && iconType == "emoji") {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .clickable {
                                        iconData = emoji
                                        iconType = "emoji"
                                        customEmoji = ""
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                } else {
                    // 图片选择
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 显示已选择的图片预览
                        selectedImageUri?.let { uri ->
                            val bitmap = remember(uri) {
                                try {
                                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                                    inputStream?.use { BitmapFactory.decodeStream(it) }
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "已选图片",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(16.dp)
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (selectedImageUri != null) "更换图片" else "选择图片")
                        }

                        if (selectedImageUri != null) {
                            TextButton(
                                onClick = {
                                    selectedImageUri = null
                                    iconData = "✅"
                                    iconType = "emoji"
                                }
                            ) {
                                Text("移除图片")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && iconData.isNotBlank()) {
                        onConfirm(name, iconType, iconData)
                    }
                },
                enabled = name.isNotBlank() && iconData.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 删除确认对话框
 */
@Composable
fun DeleteConfirmDialog(
    itemName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("确定要删除「$itemName」吗？") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}