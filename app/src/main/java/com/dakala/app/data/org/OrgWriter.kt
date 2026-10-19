package com.dakala.app.data.org

import java.time.LocalDateTime
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
