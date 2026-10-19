package com.dakala.app.data.org

/**
 * Org-mode 语法片段
 *
 * 解析（OrgParser）与写回（OrgWriter）共用的判定规则集中于此，
 * 避免同一语法在两处各有一份定义后各自漂移。
 */

/** Planning 行：前导空白 + SCHEDULED:|DEADLINE:|CLOSED: 开头 */
internal val PLANNING_LINE = Regex("^[ \\t]*(?:SCHEDULED|DEADLINE|CLOSED)[ \\t]*:")

/** 去掉行尾的 \r（兼容 CRLF 文件） */
internal fun String.stripEol(): String =
    if (endsWith("\r")) dropLast(1) else this
