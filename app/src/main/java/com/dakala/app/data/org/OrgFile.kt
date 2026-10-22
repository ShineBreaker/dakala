package com.dakala.app.data.org

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Org 文件中的 TODO 状态工作流
 *
 * 对应 org-mode 的 `#+TODO:` / `#+SEQ_TODO:` 设置，例如：
 * `#+SEQ_TODO: TODO(t) NEXT(n) | DONE(d) CANCELLED(c)`
 *
 * @property todoKeywords 未完成状态关键字（按顺序）
 * @property doneKeywords 完成状态关键字（按顺序）
 */
data class OrgTodoStates(
    val todoKeywords: List<String> = listOf("TODO"),
    val doneKeywords: List<String> = listOf("DONE")
) {
    /** 所有已识别状态关键字 */
    val allKeywords: List<String>
        get() = todoKeywords + doneKeywords

    /** 默认切换目标：未完成状态 */
    val defaultTodo: String
        get() = todoKeywords.firstOrNull() ?: "TODO"

    /** 默认切换目标：完成状态 */
    val defaultDone: String
        get() = doneKeywords.firstOrNull() ?: "DONE"
}

/**
 * Org 文件中的一个待办条目（带有状态关键字的标题行）
 *
 * @property lineIndex 标题行在文件中的行号（0 起始），作为写回时的定位标识
 * @property level 标题层级（星号数量）
 * @property state 状态关键字原文（如 "TODO"、"NEXT"、"DONE"），可能为未识别关键字但此处只存已识别的
 * @property isDone 是否为完成状态
 * @property priority 优先级（"A"/"B"/"C"），无则为 null
 * @property title 标题文本（已剥离状态、优先级、tags）
 * @property tags 标签列表
 * @property scheduled SCHEDULED 时间戳原文（如 "<2026-10-19 Mon>"）
 * @property deadline DEADLINE 时间戳原文
 * @property closed CLOSED 时间戳原文
 */
data class OrgNote(
    val lineIndex: Int,
    val level: Int,
    val state: String,
    val isDone: Boolean,
    val priority: String?,
    val title: String,
    val tags: List<String> = emptyList(),
    val scheduled: String? = null,
    val deadline: String? = null,
    val closed: String? = null
)

/**
 * 解析后的 Org 文件
 *
 * @property title 文件标题（#+TITLE:）
 * @property states TODO 状态工作流
 * @property notes 所有带状态关键字的条目，按文件顺序
 */
data class OrgTodoFile(
    val title: String?,
    val states: OrgTodoStates,
    val notes: List<OrgNote>
) {
    val incompleteNotes: List<OrgNote>
        get() = notes.filter { !it.isDone }

    val doneNotes: List<OrgNote>
        get() = notes.filter { it.isDone }
}

/**
 * 完成时间：由 CLOSED 时间戳解析；无 CLOSED 或格式不可解析时为 null
 */
fun OrgNote.completionTime(): LocalDateTime? =
    closed?.let { parseOrgTimestamp(it) }?.let { (date, time) ->
        date.atTime(time ?: LocalTime.MIN)
    }

/**
 * 列表展示顺序：未完成在前（保持文件内顺序），已完成在后按完成时间从晚到早；
 * 无 CLOSED 的已完成条目排在最后，组内保持文件内顺序。
 */
fun List<OrgNote>.sortedForDisplay(): List<OrgNote> {
    val (done, incomplete) = partition { it.isDone }
    val orderedDone = done.sortedWith(
        compareBy<OrgNote> { it.completionTime() == null }
            .thenByDescending { it.completionTime() ?: LocalDateTime.MIN }
            .thenBy { it.lineIndex }
    )
    return incomplete + orderedDone
}
