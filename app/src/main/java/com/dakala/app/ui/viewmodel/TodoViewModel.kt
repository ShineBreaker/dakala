package com.dakala.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dakala.app.data.org.OrgNote
import com.dakala.app.data.org.OrgTodoStates
import com.dakala.app.data.org.identity
import com.dakala.app.data.repository.OrgTodoRepository
import com.dakala.app.widget.UsageWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Org 待办界面状态
 */
sealed interface TodoUiState {
    /** 加载中 */
    data object Loading : TodoUiState

    /** 未选择 org 文件 */
    data object NoFile : TodoUiState

    /** 正常显示 */
    data class Ready(
        val notes: List<OrgNote>,
        val states: OrgTodoStates,
        val fileName: String?
    ) : TodoUiState

    /** 读取失败；permissionError=true 时提示重新选择文件 */
    data class Error(val message: String, val permissionError: Boolean) : TodoUiState
}

/**
 * Org 待办 ViewModel
 *
 * 管理用户指定的 org 文件的待办列表读取、状态切换和新增。
 */
@HiltViewModel
class TodoViewModel @Inject constructor(
    application: Application,
    private val orgRepository: OrgTodoRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TodoViewModel"
    }

    private val _uiState = MutableStateFlow<TodoUiState>(TodoUiState.Loading)
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    private val _orgFileUri = MutableStateFlow<String?>(null)
    val orgFileUri: StateFlow<String?> = _orgFileUri.asStateFlow()

    private val _isWriting = MutableStateFlow(false)
    val isWriting: StateFlow<Boolean> = _isWriting.asStateFlow()

    init {
        refresh()
    }

    /**
     * 重新读取 org 文件
     */
    fun refresh() {
        viewModelScope.launch {
            _orgFileUri.value = orgRepository.getOrgFileUri()
            when (val result = orgRepository.readTodos()) {
                is OrgTodoRepository.ReadResult.Success ->
                    _uiState.value = TodoUiState.Ready(
                        notes = result.file.notes,
                        states = result.file.states,
                        fileName = result.fileName
                    )
                OrgTodoRepository.ReadResult.NoFile ->
                    _uiState.value = TodoUiState.NoFile
                is OrgTodoRepository.ReadResult.Error ->
                    _uiState.value = TodoUiState.Error(result.message, result.permissionError)
            }
        }
    }

    /**
     * 设置 org 文件（SAF 选择结果）
     */
    fun setOrgFile(uri: Uri) {
        viewModelScope.launch {
            orgRepository.setOrgFile(uri)
            _orgFileUri.value = uri.toString()
            refresh()
        }
    }

    /**
     * 切换条目完成状态
     */
    fun toggleNote(note: OrgNote) {
        if (_isWriting.value) return
        viewModelScope.launch {
            _isWriting.value = true
            try {
                val ok = orgRepository.toggleNoteAtLine(note.lineIndex, note.identity)
                if (!ok) {
                    Log.w(TAG, "条目行已失效: line=${note.lineIndex}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "切换待办状态失败", e)
                _uiState.value = TodoUiState.Error(e.message ?: "写入失败", e is SecurityException)
            } finally {
                _isWriting.value = false
            }
            reloadAfterWrite()
        }
    }

    /**
     * 追加新待办到文件末尾
     */
    fun addTodo(title: String) {
        if (_isWriting.value || title.isBlank()) return
        viewModelScope.launch {
            _isWriting.value = true
            try {
                orgRepository.appendTodo(title.trim())
            } catch (e: Exception) {
                Log.e(TAG, "追加待办失败", e)
                _uiState.value = TodoUiState.Error(e.message ?: "写入失败", e is SecurityException)
            } finally {
                _isWriting.value = false
            }
            reloadAfterWrite()
        }
    }

    /**
     * 更新条目可编辑字段（标题/状态/优先级/tags/SCHEDULED/DEADLINE）
     *
     * @param title 新标题；null 表示不修改
     * @param state 新状态关键字；null 表示不修改
     * @param priority 新优先级；null 保持、"" 清除
     * @param tags 新 tags；null 保持、空表清除
     * @param scheduled 新 SCHEDULED 时间戳；null 保持、"" 清除
     * @param deadline 新 DEADLINE 时间戳；null 保持、"" 清除
     */
    fun updateTodo(
        note: OrgNote,
        title: String?,
        state: String?,
        priority: String? = null,
        tags: List<String>? = null,
        scheduled: String? = null,
        deadline: String? = null
    ) {
        if (_isWriting.value) return
        viewModelScope.launch {
            _isWriting.value = true
            try {
                val ok = orgRepository.updateTodo(
                    note.lineIndex, title, state, priority, tags, scheduled, deadline
                )
                if (!ok) {
                    Log.w(TAG, "条目行已失效: line=${note.lineIndex}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新待办失败", e)
                _uiState.value = TodoUiState.Error(e.message ?: "写入失败", e is SecurityException)
            } finally {
                _isWriting.value = false
            }
            reloadAfterWrite()
        }
    }

    /**
     * 删除条目（标题 + planning + 正文，直到下一个标题）
     */
    fun deleteTodo(note: OrgNote) {
        if (_isWriting.value) return
        viewModelScope.launch {
            _isWriting.value = true
            try {
                val ok = orgRepository.deleteTodo(note.lineIndex)
                if (!ok) {
                    Log.w(TAG, "条目行已失效: line=${note.lineIndex}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除待办失败", e)
                _uiState.value = TodoUiState.Error(e.message ?: "写入失败", e is SecurityException)
            } finally {
                _isWriting.value = false
            }
            reloadAfterWrite()
        }
    }

    /**
     * 写操作后统一收口：重读文件 + 通知小部件重绘
     */
    private fun reloadAfterWrite() {
        refresh()
        UsageWidgetProvider.updateWidget(getApplication())
    }
}
