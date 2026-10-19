package com.dakala.app.data.org

/**
 * 轻量 Org-mode 待办解析器
 *
 * 只解析待办相关语法（参考 orgzly/org-java 的语义，按 org-mode 公开格式实现）：
 * - 标题行：`* TODO [#A] 标题 :tag1:tag2:`
 * - Planning 行（紧随标题行）：`SCHEDULED: <...> DEADLINE: <...> CLOSED: [...]`
 * - 文件内设置：`#+TODO:` / `#+SEQ_TODO:` / `#+TYP_TODO:` 自定义状态关键字
 *
 * 不解析正文、抽屉、区块等其他元素。
 */
object OrgParser {

    // 标题行：一个或多个星号 + 空白 + 内容（内容可为空）
    private val HEADLINE = Regex("^(\\*+)[ \\t]+(.*)$")

    // 优先级：[#X] 后跟空白或行尾
    private val PRIORITY = Regex("^\\[#([A-Za-z])\\](?:[ \\t]+(.*))?$")

    // 行尾 tags：空白 + :tag1:tag2:
    private val TAGS_SUFFIX = Regex("^(.*?)[ \\t]+:(\\S+):[ \\t]*$")

    // 整行仅为 tags（空标题）
    private val TAGS_ONLY = Regex("^:(\\S+):[ \\t]*$")

    // Planning 行中的单个条目：KEY: <...> 或 KEY: [...]
    private val PLANNING_ENTRY =
        Regex("(SCHEDULED|DEADLINE|CLOSED)[ \\t]*:[ \\t]*([\\[<][^\\]>\r\n]*[]>])")

    // 文件内设置：#+KEY: value
    private val FILE_SETTING = Regex("^#\\+([A-Za-z0-9_]+):[ \\t]*(.*?)[ \\t]*$")

    private val TODO_SETTING_KEYS = setOf("TODO", "SEQ_TODO", "TYP_TODO")

    /**
     * 解析 org 文件内容
     *
     * @param content 文件完整文本
     * @return 解析结果，包含状态工作流和所有带状态的条目
     */
    fun parse(content: String): OrgTodoFile {
        val lines = content.split('\n')
        val states = parseStates(lines)
        val title = parseTitle(lines)
        val notes = mutableListOf<OrgNote>()

        for (i in lines.indices) {
            val note = parseNote(lines, i, states) ?: continue
            notes.add(note)
        }

        return OrgTodoFile(title = title, states = states, notes = notes)
    }

    /**
     * 解析指定行的标题（用于写回前的行内容校验）
     *
     * @return null 如果该行不是标题行
     */
    internal fun parseNote(lines: List<String>, index: Int, states: OrgTodoStates): OrgNote? {
        val match = HEADLINE.matchEntire(lines[index].stripEol()) ?: return null
        val level = match.groupValues[1].length
        var rest = match.groupValues[2]

        // 状态关键字：首个空白分隔的 token
        var state: String? = null
        val firstToken = rest.takeWhile { it != ' ' && it != '\t' }
        if (firstToken.isNotEmpty() && firstToken in states.allKeywords) {
            state = firstToken
            rest = rest.removePrefix(firstToken).trimStart()
        }

        // 优先级：[#X]
        var priority: String? = null
        PRIORITY.matchEntire(rest)?.let { m ->
            priority = m.groupValues[1]
            rest = m.groupValues[2]
        }

        // 行尾 tags
        var tags = emptyList<String>()
        TAGS_ONLY.matchEntire(rest)?.let { m ->
            tags = splitTags(m.groupValues[1])
            rest = ""
        } ?: TAGS_SUFFIX.matchEntire(rest)?.let { m ->
            tags = splitTags(m.groupValues[2])
            rest = m.groupValues[1]
        }

        // Planning 行（必须紧随标题行）
        var scheduled: String? = null
        var deadline: String? = null
        var closed: String? = null
        val nextIndex = index + 1
        if (nextIndex < lines.size && PLANNING_LINE.containsMatchIn(lines[nextIndex].stripEol())) {
            for (entry in PLANNING_ENTRY.findAll(lines[nextIndex])) {
                when (entry.groupValues[1]) {
                    "SCHEDULED" -> scheduled = entry.groupValues[2]
                    "DEADLINE" -> deadline = entry.groupValues[2]
                    "CLOSED" -> closed = entry.groupValues[2]
                }
            }
        }

        // 只收录带有已识别状态关键字的标题
        val keyword = state ?: return null
        return OrgNote(
            lineIndex = index,
            level = level,
            state = keyword,
            isDone = keyword in states.doneKeywords,
            priority = priority,
            title = rest.trim(),
            tags = tags,
            scheduled = scheduled,
            deadline = deadline,
            closed = closed
        )
    }

    /**
     * 解析文件内 TODO 关键字设置
     *
     * `#+TODO:`/`#+SEQ_TODO:`/`#+TYP_TODO:` 格式：`TODO(t) NEXT(n) | DONE(d)`，
     * `|` 前为未完成状态，后为完成状态；无 `|` 时最后一个关键字视为完成状态。
     * 可有多行 = 多个工作流，此处合并为单一状态集合。
     */
    private fun parseStates(lines: List<String>): OrgTodoStates {
        val todo = linkedSetOf("TODO")
        val done = linkedSetOf("DONE")
        var foundCustom = false

        for (line in lines) {
            val m = FILE_SETTING.matchEntire(line.stripEol()) ?: continue
            val key = m.groupValues[1].uppercase()
            if (key !in TODO_SETTING_KEYS) continue
            if (!foundCustom) {
                // 遇到自定义设置后丢弃默认值，避免污染
                todo.clear()
                done.clear()
                foundCustom = true
            }
            val (t, d) = parseWorkflowValue(m.groupValues[2])
            todo.addAll(t)
            done.addAll(d)
        }

        return OrgTodoStates(
            todoKeywords = todo.toList(),
            doneKeywords = done.toList()
        )
    }

    /**
     * 解析单个工作流值，如 "TODO(t) NEXT(n) | DONE(d) CANCELLED(c)"
     */
    private fun parseWorkflowValue(value: String): Pair<List<String>, List<String>> {
        val bar = value.indexOf('|')
        val todoPart = if (bar >= 0) value.substring(0, bar) else value
        val donePart = if (bar >= 0) value.substring(bar + 1) else ""

        val todos = parseKeywords(todoPart)
        val dones = parseKeywords(donePart).toMutableList()

        if (bar < 0 && todos.isNotEmpty()) {
            // 无 | 时最后一个关键字为完成状态
            dones.add(todos.last())
            return todos.dropLast(1) to dones
        }
        return todos to dones
    }

    /**
     * 解析关键字列表，剥离 `(x)` 快捷键标记，丢弃纯标记 token
     */
    private fun parseKeywords(part: String): List<String> {
        return part.split(Regex("[ \\t]+"))
            .map { it.substringBefore('(') }
            .filter { it.isNotEmpty() }
    }

    private fun parseTitle(lines: List<String>): String? {
        for (line in lines) {
            val m = FILE_SETTING.matchEntire(line.stripEol()) ?: continue
            if (m.groupValues[1].equals("TITLE", ignoreCase = true)) {
                return m.groupValues[2].ifEmpty { null }
            }
        }
        return null
    }

    private fun splitTags(raw: String): List<String> =
        raw.split(':').filter { it.isNotEmpty() }
}
