package com.dakala.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import com.dakala.app.R
import com.dakala.app.data.local.dao.AppSettingDao
import com.dakala.app.data.local.entity.AppSetting
import com.dakala.app.data.org.NoteIdentity
import com.dakala.app.data.org.OrgParser
import com.dakala.app.data.org.OrgTodoFile
import com.dakala.app.data.org.OrgWriter
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Org 待办文件仓库
 *
 * 通过 SAF（Storage Access Framework）访问用户指定的 org 文件：
 * - 文件 URI 持久化在 app_settings 表（OpenDocument 返回的 content:// URI）
 * - 读取时整体解析，写入时先读后写（read-modify-write）
 * - 每次操作都重新读文件，外部（Emacs/syncthing 等）修改在下次刷新即生效
 *
 * 参考 Orgzly DocumentRepo 的 SAF 访问方式，但面向单文件而非目录树。
 */
class OrgTodoRepository(
    private val context: Context,
    private val appSettingDao: AppSettingDao
) {
    companion object {
        private const val TAG = "OrgTodoRepository"
        private const val BOM = '\uFEFF'

        /** 跨实例写锁：UI 与小组件两个入口并发 read-modify-write 时，防止后写者用旧全文覆盖先完成者 */
        private val WRITE_LOCK = Mutex()
    }

    /**
     * Org 文件读取结果
     */
    sealed interface ReadResult {
        /** 解析成功 */
        data class Success(val file: OrgTodoFile, val fileName: String?) : ReadResult

        /** 尚未选择 org 文件 */
        data object NoFile : ReadResult

        /** 读取失败；permissionError=true 表示权限失效需重新选择文件 */
        data class Error(val message: String, val permissionError: Boolean = false) : ReadResult
    }

    // ==================== 文件选择 ====================

    /**
     * 获取已设置的 org 文件 URI
     */
    suspend fun getOrgFileUri(): String? {
        return appSettingDao.getSetting(AppSetting.KEY_ORG_FILE_URI)
    }

    /**
     * 设置 org 文件 URI 并持久化访问权限
     *
     * 应在 OpenDocument/CreateDocument 返回后调用。
     */
    suspend fun setOrgFile(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // 部分 provider 不支持持久授权，降级为临时权限
            Log.w(TAG, "无法持久化文件权限: $uri", e)
        }
        appSettingDao.setSetting(AppSetting(AppSetting.KEY_ORG_FILE_URI, uri.toString()))
        Log.d(TAG, "设置 org 文件: $uri")
    }

    /**
     * 清除 org 文件设置
     */
    suspend fun clearOrgFile() {
        appSettingDao.deleteSetting(AppSetting.KEY_ORG_FILE_URI)
    }

    /**
     * 获取 org 文件的可读路径（用于设置页显示）
     *
     * SAF document URI 转 documentId（如 `primary:Documents/a/b.org`），
     * 渲染为 `内部存储/Documents/a/b.org`；其他卷标保留原卷 id。
     * 非 documents provider（syncthing 等）回退到 URI 路径或显示名。
     */
    suspend fun getOrgFilePath(): String? {
        val uri = currentUri() ?: return null
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val sep = docId.indexOf(':')
            if (sep > 0) {
                val volume = docId.substring(0, sep)
                val rest = docId.substring(sep + 1)
                val volumeLabel = if (volume == "primary") {
                    context.getString(R.string.storage_primary)
                } else {
                    volume
                }
                "$volumeLabel/$rest"
            } else {
                docId
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法解析文件路径: $uri", e)
            uri.lastPathSegment ?: queryDisplayName(uri)
        }
    }

    // ==================== 读取 ====================

    /**
     * 读取并解析 org 文件中的待办列表
     */
    suspend fun readTodos(): ReadResult {
        val uri = currentUri() ?: return ReadResult.NoFile
        return try {
            val content = readContent(uri)
            ReadResult.Success(OrgParser.parse(content), queryDisplayName(uri))
        } catch (e: SecurityException) {
            Log.e(TAG, "org 文件权限失效", e)
            ReadResult.Error(e.message ?: "权限失效", permissionError = true)
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "org 文件不存在", e)
            ReadResult.Error(e.message ?: "文件不存在", permissionError = true)
        } catch (e: IOException) {
            Log.e(TAG, "读取 org 文件失败", e)
            ReadResult.Error(e.message ?: "读取失败")
        } catch (e: Exception) {
            Log.e(TAG, "解析 org 文件失败", e)
            ReadResult.Error(e.message ?: "解析失败")
        }
    }

    // ==================== 写入 ====================

    /**
     * 切换指定行的待办状态
     *
     * @param expected 快照时的条目身份指纹（UI 列表/小部件渲染数据提取），
     *   行号因外部修改平移且指纹不符时拒绝写入
     * @return true 写入成功；false 目标行已失效（文件被外部修改）
     * @throws IOException / SecurityException 读写失败时抛出
     */
    @Throws(IOException::class, SecurityException::class)
    suspend fun toggleNoteAtLine(lineIndex: Int, expected: NoteIdentity? = null): Boolean {
        WRITE_LOCK.withLock {
            val uri = currentUri() ?: return false
            val content = readContent(uri)
            val states = OrgParser.parse(content).states
            val newContent = OrgWriter.toggleNote(content, lineIndex, states, expected = expected)
                ?: return false
            writeContent(uri, newContent)
            Log.d(TAG, "切换待办状态: line=$lineIndex")
        }
        return true
    }

    /**
     * 在文件末尾追加新待办
     */
    @Throws(IOException::class, SecurityException::class)
    suspend fun appendTodo(title: String) {
        WRITE_LOCK.withLock {
            val uri = currentUri() ?: return
            val content = readContent(uri)
            val states = OrgParser.parse(content).states
            writeContent(uri, OrgWriter.appendNote(content, title, states))
            Log.d(TAG, "追加待办: $title")
        }
    }

    /**
     * 更新指定行待办的可编辑字段（标题/状态/优先级/tags/SCHEDULED/DEADLINE）
     *
     * 参数语义同 [OrgWriter.updateNote]：null 保持、空值清除、其他值替换。
     *
     * @return true 写入成功；false 目标行已失效（文件被外部修改）或字段非法
     * @throws IOException / SecurityException 读写失败时抛出
     */
    @Throws(IOException::class, SecurityException::class)
    suspend fun updateTodo(
        lineIndex: Int,
        title: String? = null,
        state: String? = null,
        priority: String? = null,
        tags: List<String>? = null,
        scheduled: String? = null,
        deadline: String? = null
    ): Boolean {
        WRITE_LOCK.withLock {
            val uri = currentUri() ?: return false
            val content = readContent(uri)
            val states = OrgParser.parse(content).states
            val newContent = OrgWriter.updateNote(
                content, lineIndex, title, state, priority, tags, scheduled, deadline, states
            ) ?: return false
            writeContent(uri, newContent)
            Log.d(TAG, "更新待办: line=$lineIndex")
        }
        return true
    }

    /**
     * 删除指定行的待办条目
     *
     * @return true 写入成功；false 目标行已失效（文件被外部修改）
     * @throws IOException / SecurityException 读写失败时抛出
     */
    @Throws(IOException::class, SecurityException::class)
    suspend fun deleteTodo(lineIndex: Int): Boolean {
        WRITE_LOCK.withLock {
            val uri = currentUri() ?: return false
            val content = readContent(uri)
            val states = OrgParser.parse(content).states
            val newContent = OrgWriter.deleteNote(content, lineIndex, states) ?: return false
            writeContent(uri, newContent)
            Log.d(TAG, "删除待办: line=$lineIndex")
        }
        return true
    }

    // ==================== 私有方法 ====================

    private suspend fun currentUri(): Uri? {
        return getOrgFileUri()?.let { runCatching { it.toUri() }.getOrNull() }
    }

    private fun readContent(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("无法打开文件: $uri")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }.removePrefix(BOM.toString())
    }

    private fun writeContent(uri: Uri, content: String) {
        // "wt" 模式截断后写入；个别 provider 不支持时退回 "w"
        val stream = try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (e: FileNotFoundException) {
            context.contentResolver.openOutputStream(uri, "w")
        } ?: throw FileNotFoundException("无法写入文件: $uri")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取文件名失败", e)
            null
        }
    }
}
