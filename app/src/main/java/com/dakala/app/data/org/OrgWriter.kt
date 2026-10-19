package com.dakala.app.data.org

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Org 文件写回器
 *
 * 对文件做最小化行级修改，保留未涉及部分的原始字节：
 * - 切换待办状态：替换标题行中的状态关键字
 * - 标记完成时写入 `CLOSED: [yyyy-MM-dd EEE HH:mm]`（对齐 Emacs org-log-done=time 行为）
 * - 取消完成时移除 CLOSED 条目
 *
 * 暂不支持 repeater 位移（标记带 +1d 等重复器的条目时仍会直接置为 DONE）。
 */
object OrgWriter {

    private val STARS_PREFIX = Regex("^(\\*+[ \\t]+)")
    private val CLOSED_ENTRY = Regex("[ \\t]*CLOSED[ \\t]*:[ \\t]*\\[[^\\]\r\n]*\\][ \\t]*")
    private val LINE_INDENT = Regex("^[ \\t]*")

    private val CLOSED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm", Locale.US)
    private val TS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", Locale.US)
    private val TS_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * 生成 org 主动时间戳：`<2026-10-19 Mon>`，带时间为 `<2026-10-19 Mon 14:30>`
     */
    fun formatTimestamp(date: LocalDate, time: LocalTime? = null): String =
        "<" + date.format(TS_DATE_FORMAT) +
            (time?.let { " " + it.format(TS_TIME_FORMAT) } ?: "") + ">"

    /**
     * 切换指定行的待办状态（TODO ↔ DONE）
     *
     * 写回入口：状态工作流由 content 现场解析，保证与 content 同源。
     *
     * @param content 当前文件完整内容
     * @param lineIndex 目标标题行行号（0 起始）
     * @param now 当前时间（用于 CLOSED 时间戳，便于测试注入）
     * @return 修改后的文件内容；若该行已不是带状态的标题则返回 null
     */
    fun toggleNote(
        content: String,
        lineIndex: Int,
        now: LocalDateTime = LocalDateTime.now()
    ): String? = toggleNote(content, lineIndex, OrgParser.parse(content).states, now)

    /**
     * 切换指定行的待办状态（TODO ↔ DONE）
     *
     * 写回前会重新解析该行校验仍是合法待办标题，防止文件外部变更后误写。
     *
     * @param content 当前文件完整内容
     * @param lineIndex 目标标题行行号（0 起始）
     * @param states 状态工作流，需与 content 同源；单测与自定义工作流可显式传入
     * @param now 当前时间（用于 CLOSED 时间戳，便于测试注入）
     * @return 修改后的文件内容；若该行已不是带状态的标题则返回 null
     */
    fun toggleNote(
        content: String,
        lineIndex: Int,
        states: OrgTodoStates,
        now: LocalDateTime = LocalDateTime.now()
    ): String? {
        val lines = content.split('\n').toMutableList()
        if (lineIndex !in lines.indices) return null

        // 重新解析校验该行仍是待办标题
        val note = OrgParser.parseNote(lines, lineIndex, states) ?: return null

        val (body, eol) = lines[lineIndex].splitEol()
        val prefix = STARS_PREFIX.find(body)?.value ?: return null
        val rest = body.substring(prefix.length)
        if (!rest.startsWith(note.state)) return null

        val newState = if (note.isDone) states.defaultTodo else states.defaultDone
        val markingDone = !note.isDone
        lines[lineIndex] = prefix + newState + rest.substring(note.state.length) + eol

        if (markingDone) {
            insertClosed(lines, lineIndex, eol, now)
        } else {
            removeClosed(lines, lineIndex)
        }

        return lines.joinToString("\n")
    }

    /**
     * 在文件末尾追加一个新的待办条目
     *
     * @param title 待办标题（换行会被替换为空格）
     * @param level 标题层级，默认顶级
     */
    fun appendNote(content: String, title: String, states: OrgTodoStates, level: Int = 1): String {
        val headline = "*".repeat(level.coerceAtLeast(1)) + " " +
            states.defaultTodo + " " + title.replace('\n', ' ').trim()
        return when {
            content.isEmpty() -> headline + "\n"
            content.endsWith("\n") -> content + headline + "\n"
            else -> content + "\n" + headline + "\n"
        }
    }

    /**
     * 更新指定行待办的可编辑字段
     *
     * 可写字段：标题、状态、优先级、tags、SCHEDULED、DEADLINE。
     * 层级与星号前缀的原始间距保留；状态跨过完成边界时补写/移除
     * CLOSED 时间戳（与 toggleNote 同一记账逻辑）；正文与其他
     * planning 内容不受影响。
     *
     * 参数语义统一：null = 保持原值；空串/空列表 = 清除；
     * 其他值 = 设为该值。scheduled/deadline 接受 `<...>`、`[...]`
     * 原文或裸日期（自动包成 `<...>`）。
     *
     * @param content 当前文件完整内容
     * @param lineIndex 目标标题行行号（0 起始）
     * @param title 新标题（换行会被替换为空格）
     * @param state 新状态关键字，须属于 states.allKeywords
     * @param priority 新优先级字母（单字符，非字母返回 null）
     * @param tags 新 tags 列表
     * @param scheduled 新 SCHEDULED 时间戳
     * @param deadline 新 DEADLINE 时间戳
     * @param states 状态工作流，需与 content 同源
     * @param now 当前时间（用于 CLOSED 时间戳，便于测试注入）
     * @return 修改后的文件内容；行非法、非待办标题或字段值非法时返回 null
     */
    fun updateNote(
        content: String,
        lineIndex: Int,
        title: String? = null,
        state: String? = null,
        priority: String? = null,
        tags: List<String>? = null,
        scheduled: String? = null,
        deadline: String? = null,
        states: OrgTodoStates,
        now: LocalDateTime = LocalDateTime.now()
    ): String? {
        val lines = content.split('\n').toMutableList()
        if (lineIndex !in lines.indices) return null
        if (state != null && state !in states.allKeywords) return null
        if (priority != null && priority.isNotEmpty() && !priority.matches(Regex("[A-Za-z]"))) return null

        val note = OrgParser.parseNote(lines, lineIndex, states) ?: return null
        val (body, eol) = lines[lineIndex].splitEol()
        val prefix = STARS_PREFIX.find(body)?.value ?: return null

        // 重建标题行：前缀 + 状态 + [优先级] + 标题 + :tags:
        val newState = state ?: note.state
        val newTitle = title?.replace('\n', ' ')?.trim() ?: note.title
        val newPriority = if (priority == null) note.priority else priority.uppercase().ifEmpty { null }
        val newTags = tags ?: note.tags
        val parts = buildList {
            newPriority?.let { add("[#$it]") }
            if (newTitle.isNotEmpty()) add(newTitle)
            if (newTags.isNotEmpty()) add(":" + newTags.joinToString(":") + ":")
        }
        lines[lineIndex] = if (parts.isEmpty()) {
            prefix + newState + eol
        } else {
            prefix + newState + " " + parts.joinToString(" ") + eol
        }

        // planning 行：SCHEDULED / DEADLINE（CLOSED 由状态逻辑单独维护）
        updatePlanning(lines, lineIndex, scheduled, deadline)

        // done 边界跨越时维护 CLOSED 时间戳
        val nowDone = newState in states.doneKeywords
        if (note.isDone != nowDone) {
            if (nowDone) insertClosed(lines, lineIndex, eol, now) else removeClosed(lines, lineIndex)
        }

        return lines.joinToString("\n")
    }

    /**
     * 更新标题行后 planning 行中的 SCHEDULED / DEADLINE
     *
     * null = 保持原值；"" = 移除该条目；其他值 = 替换。
     * 行上 CLOSED 原样保留；全部条目清空时删除整行；
     * 无 planning 行且有新条目时在标题行后插入。
     */
    private fun updatePlanning(
        lines: MutableList<String>,
        lineIndex: Int,
        scheduled: String?,
        deadline: String?
    ) {
        if (scheduled == null && deadline == null) return
        val nextIndex = lineIndex + 1

        if (nextIndex < lines.size && PLANNING_LINE.containsMatchIn(lines[nextIndex].stripEol())) {
            val (body, eol) = lines[nextIndex].splitEol()
            var oldSched: String? = null
            var oldDead: String? = null
            var oldClosed: String? = null
            for (entry in PLANNING_ENTRY.findAll(body)) {
                when (entry.groupValues[1]) {
                    "SCHEDULED" -> oldSched = entry.groupValues[2]
                    "DEADLINE" -> oldDead = entry.groupValues[2]
                    "CLOSED" -> oldClosed = entry.groupValues[2]
                }
            }
            val parts = buildList {
                pickPlanning(scheduled, oldSched)?.let { add("SCHEDULED: $it") }
                pickPlanning(deadline, oldDead)?.let { add("DEADLINE: $it") }
                oldClosed?.let { add("CLOSED: $it") }
            }
            if (parts.isEmpty()) {
                lines.removeAt(nextIndex)
            } else {
                val indent = LINE_INDENT.find(body)?.value ?: ""
                lines[nextIndex] = indent + parts.joinToString(" ") + eol
            }
        } else if (!scheduled.isNullOrEmpty() || !deadline.isNullOrEmpty()) {
            val parts = buildList {
                scheduled.takeIf { !it.isNullOrEmpty() }?.let { add("SCHEDULED: ${normalizeTimestamp(it)}") }
                deadline.takeIf { !it.isNullOrEmpty() }?.let { add("DEADLINE: ${normalizeTimestamp(it)}") }
            }
            if (parts.isNotEmpty()) {
                val eol = lines[lineIndex].splitEol().second
                lines.add(nextIndex, detectPlanningIndent(lines) + parts.joinToString(" ") + eol)
            }
        }
    }

    /** null = 保持旧值；"" = 清除；其他 = 规范化为 org 时间戳 */
    private fun pickPlanning(new: String?, old: String?): String? = when {
        new == null -> old
        new.isEmpty() -> null
        else -> normalizeTimestamp(new)
    }

    /** 裸时间文本自动包成主动时间戳 `<...>`；已带 <> 或 [] 的原样返回 */
    private fun normalizeTimestamp(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("<") || t.startsWith("[")) t else "<$t>"
    }

    /**
     * 删除指定行的待办条目
     *
     * 删除范围：标题行 + 紧随的 planning 行 + 正文，直到下一个标题行
     * （任何层级）。更深层的子标题不受影响——它们是独立条目。
     *
     * @param content 当前文件完整内容
     * @param lineIndex 目标标题行行号（0 起始）
     * @param states 状态工作流，需与 content 同源
     * @return 修改后的文件内容；该行不是合法待办标题时返回 null
     */
    fun deleteNote(content: String, lineIndex: Int, states: OrgTodoStates): String? {
        val lines = content.split('\n').toMutableList()
        if (lineIndex !in lines.indices) return null
        OrgParser.parseNote(lines, lineIndex, states) ?: return null

        var end = lineIndex + 1
        while (end < lines.size && HEADLINE.find(lines[end].stripEol()) == null) {
            end++
        }
        lines.subList(lineIndex, end).clear()

        return lines.joinToString("\n")
    }

    /**
     * 在标题行后写入 CLOSED 时间戳
     *
     * 若已有 planning 行则将 CLOSED 置于条目最前（与 Emacs 输出一致）；
     * 否则在标题行后插入新的 planning 行，缩进风格参考文件中其他 planning 行。
     */
    private fun insertClosed(
        lines: MutableList<String>,
        lineIndex: Int,
        headlineEol: String,
        now: LocalDateTime
    ) {
        val closed = "CLOSED: [" + now.format(CLOSED_FORMAT) + "]"
        val nextIndex = lineIndex + 1

        if (nextIndex < lines.size && PLANNING_LINE.containsMatchIn(lines[nextIndex].stripEol())) {
            val (body, eol) = lines[nextIndex].splitEol()
            val indent = LINE_INDENT.find(body)?.value ?: ""
            val rest = body.replace(CLOSED_ENTRY, "").trim()
            lines[nextIndex] = indent + closed + (if (rest.isEmpty()) "" else " $rest") + eol
        } else {
            lines.add(nextIndex, detectPlanningIndent(lines) + closed + headlineEol)
        }
    }

    /**
     * 从 planning 行中移除 CLOSED 条目；若整行变空则删除该行
     */
    private fun removeClosed(lines: MutableList<String>, lineIndex: Int) {
        val nextIndex = lineIndex + 1
        if (nextIndex >= lines.size) return
        val (body, eol) = lines[nextIndex].splitEol()
        if (!PLANNING_LINE.containsMatchIn(body)) return

        val cleaned = body.replace(CLOSED_ENTRY, "").trim()
        if (cleaned.isEmpty()) {
            lines.removeAt(nextIndex)
        } else {
            val indent = LINE_INDENT.find(body)?.value ?: ""
            lines[nextIndex] = indent + cleaned + eol
        }
    }

    /** 参照文件中已有 planning 行的缩进风格（取第一条），默认无缩进 */
    private fun detectPlanningIndent(lines: List<String>): String {
        for (line in lines) {
            val body = line.stripEol()
            if (PLANNING_LINE.containsMatchIn(body)) {
                return LINE_INDENT.find(body)?.value ?: ""
            }
        }
        return ""
    }

    /** 拆分内容体与行尾 \r（兼容 CRLF） */
    private fun String.splitEol(): Pair<String, String> =
        if (endsWith("\r")) dropLast(1) to "\r" else this to ""
}
