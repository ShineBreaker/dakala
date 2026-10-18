package com.dakala.app.data.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class OrgWriterTest {

    private val now = LocalDateTime.of(2026, 10, 19, 14, 30)
    private val states = OrgTodoStates()

    @Test
    fun `toggle todo to done replaces keyword`() {
        val content = "* TODO 任务\n"
        val result = OrgWriter.toggleNote(content, 0, states, now)!!
        assertEquals("* DONE 任务\nCLOSED: [2026-10-19 Mon 14:30]\n", result)
    }

    @Test
    fun `toggle done to todo removes closed`() {
        val content = "* DONE 任务\nCLOSED: [2026-10-19 Mon 14:30]\n"
        val result = OrgWriter.toggleNote(content, 0, states, now)!!
        assertEquals("* TODO 任务\n", result)
    }

    @Test
    fun `toggle preserves priority tags and level`() {
        val content = "*** TODO [#A] 标题 :tag1:tag2:\n"
        val result = OrgWriter.toggleNote(content, 0, states, now)!!
        assertEquals("*** DONE [#A] 标题 :tag1:tag2:\nCLOSED: [2026-10-19 Mon 14:30]\n", result)
    }

    @Test
    fun `closed added to existing planning line`() {
        val content = "* TODO 任务\nSCHEDULED: <2026-10-20 Tue>\n"
        val result = OrgWriter.toggleNote(content, 0, states, now)!!
        assertEquals(
            "* DONE 任务\nCLOSED: [2026-10-19 Mon 14:30] SCHEDULED: <2026-10-20 Tue>\n",
            result
        )
    }

    @Test
    fun `toggle updates existing closed timestamp`() {
        val content = "* TODO 任务\nCLOSED: [2020-01-01 Wed 08:00] DEADLINE: <2026-10-25 Sun>\n"
        val result = OrgWriter.toggleNote(content, 0, states, now)!!
        assertEquals(
            "* DONE 任务\nCLOSED: [2026-10-19 Mon 14:30] DEADLINE: <2026-10-25 Sun>\n",
            result
        )
    }

    @Test
    fun `untoggle keeps other planning entries`() {
        val content = "* DONE 任务\nCLOSED: [2026-10-19 Mon 14:30] DEADLINE: <2026-10-25 Sun>\n"
        val result = OrgWriter.toggleNote(content, 0, states, now)!!
        assertEquals("* TODO 任务\nDEADLINE: <2026-10-25 Sun>\n", result)
    }

    @Test
    fun `toggle uses custom keywords`() {
        val custom = OrgTodoStates(
            todoKeywords = listOf("NEXT", "WAIT"),
            doneKeywords = listOf("DONE", "CANCELLED")
        )
        val content = "* NEXT 任务\n"
        val result = OrgWriter.toggleNote(content, 0, custom, now)!!
        assertEquals("* DONE 任务\nCLOSED: [2026-10-19 Mon 14:30]\n", result)
    }

    @Test
    fun `toggle on non-todo headline returns null`() {
        assertNull(OrgWriter.toggleNote("* 普通标题\n", 0, states, now))
    }

    @Test
    fun `toggle on invalid line returns null`() {
        assertNull(OrgWriter.toggleNote("* TODO 任务\n", 5, states, now))
        assertNull(OrgWriter.toggleNote("正文\n", 0, states, now))
    }

    @Test
    fun `toggle preserves extra spacing`() {
        val content = "*   TODO 任务\n"
        val result = OrgWriter.toggleNote(content, 0, states, now)!!
        assertEquals("*   DONE 任务\nCLOSED: [2026-10-19 Mon 14:30]\n", result)
    }

    @Test
    fun `toggle does not touch other lines`() {
        val content = "* TODO 任务\n正文不动\n* DONE 已完成\n"
        val result = OrgWriter.toggleNote(content, 0, states, now)!!
        assertEquals("* DONE 任务\nCLOSED: [2026-10-19 Mon 14:30]\n正文不动\n* DONE 已完成\n", result)
    }

    @Test
    fun `append note to empty file`() {
        assertEquals("* TODO 新任务\n", OrgWriter.appendNote("", "新任务", states))
    }

    @Test
    fun `append note to existing file`() {
        val content = "* TODO 旧任务\n"
        assertEquals(
            "* TODO 旧任务\n* TODO 新任务\n",
            OrgWriter.appendNote(content, "新任务", states)
        )
    }

    @Test
    fun `append note without trailing newline`() {
        val content = "* TODO 旧任务"
        assertEquals(
            "* TODO 旧任务\n* TODO 新任务\n",
            OrgWriter.appendNote(content, "新任务", states)
        )
    }

    @Test
    fun `append uses custom first keyword`() {
        val custom = OrgTodoStates(listOf("NEXT"), listOf("DONE"))
        assertEquals("* NEXT 新任务\n", OrgWriter.appendNote("", "新任务", custom))
    }

    @Test
    fun `append sanitizes newline in title`() {
        assertEquals("* TODO 一行 标题\n", OrgWriter.appendNote("", "一行\n标题", states))
    }

    @Test
    fun `closed insert matches indented planning style`() {
        val content = "* DONE 已完成\n  CLOSED: [2026-10-18 Sun 10:00]\n* TODO 任务\n"
        val result = OrgWriter.toggleNote(content, 2, states, now)!!
        assertEquals(
            "* DONE 已完成\n  CLOSED: [2026-10-18 Sun 10:00]\n* DONE 任务\n  CLOSED: [2026-10-19 Mon 14:30]\n",
            result
        )
    }
}
