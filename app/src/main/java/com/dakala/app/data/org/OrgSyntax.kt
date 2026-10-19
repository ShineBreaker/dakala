package com.dakala.app.data.org

import java.time.LocalDate
import java.time.LocalTime

/**
 * Org-mode 语法片段
 *
 * 解析（OrgParser）与写回（OrgWriter）共用的判定规则集中于此，
 * 避免同一语法在两处各有一份定义后各自漂移。
 */

/** Planning 行：前导空白 + SCHEDULED:|DEADLINE:|CLOSED: 开头 */
internal val PLANNING_LINE = Regex("^[ \\t]*(?:SCHEDULED|DEADLINE|CLOSED)[ \\t]*:")

/** 标题行：一个或多个星号 + 空白 + 内容（内容可为空，不要求带状态关键字） */
internal val HEADLINE = Regex("^(\\*+)[ \\t]+(.*)$")

/** Planning 行中的单个条目：KEY: <...> 或 KEY: [...] */
internal val PLANNING_ENTRY =
    Regex("(SCHEDULED|DEADLINE|CLOSED)[ \\t]*:[ \\t]*([\\[<][^\\]>\r\n]*[]>])")

/** 去掉行尾的 \r（兼容 CRLF 文件） */
internal fun String.stripEol(): String =
    if (endsWith("\r")) dropLast(1) else this

private val TS_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
private val TS_TIME = Regex("\\d{1,2}:\\d{2}")

/**
 * 从 org 时间戳原文提取日期与可选时间
 *
 * `<2026-10-19 Mon 14:30>` → (2026-10-19, 14:30)
 * `[2026-10-19 Mon]` → (2026-10-19, null)
 */
fun parseOrgTimestamp(raw: String): Pair<LocalDate, LocalTime?>? {
    val inner = raw.removePrefix("<").removePrefix("[").removeSuffix(">").removeSuffix("]")
    val parts = inner.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val date = parts.firstOrNull { TS_DATE.matches(it) }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return null
    val time = parts.firstOrNull { TS_TIME.matches(it) }
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    return date to time
}
