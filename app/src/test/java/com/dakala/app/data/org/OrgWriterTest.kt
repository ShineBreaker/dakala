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

    @Test
    fun `update note replaces title only`() {
        val content = "* TODO [#A] 旧标题 :tag1:\n"
        val result = OrgWriter.updateNote(content, 0, title = "新标题", states = states, now = now)!!
        assertEquals("* TODO [#A] 新标题 :tag1:\n", result)
    }

    @Test
    fun `update note changes state and writes closed`() {
        val content = "** TODO 任务\n"
        val result = OrgWriter.updateNote(content, 0, state = "DONE", states = states, now = now)!!
        assertEquals("** DONE 任务\nCLOSED: [2026-10-19 Mon 14:30]\n", result)
    }

    @Test
    fun `update note to todo removes closed`() {
        val content = "* DONE 任务\nCLOSED: [2026-10-19 Mon 14:30]\n"
        val result = OrgWriter.updateNote(content, 0, state = "TODO", states = states, now = now)!!
        assertEquals("* TODO 任务\n", result)
    }

    @Test
    fun `update note same done side keeps closed`() {
        val custom = OrgTodoStates(listOf("TODO"), listOf("DONE", "CANCELLED"))
        val content = "* DONE 任务\nCLOSED: [2026-10-18 Sun 10:00]\n"
        val result = OrgWriter.updateNote(content, 0, state = "CANCELLED", states = custom, now = now)!!
        assertEquals("* CANCELLED 任务\nCLOSED: [2026-10-18 Sun 10:00]\n", result)
    }

    @Test
    fun `update note rejects unknown state`() {
        assertNull(OrgWriter.updateNote("* TODO 任务\n", 0, state = "NOPE", states = states, now = now))
    }

    @Test
    fun `update note on non-todo returns null`() {
        assertNull(OrgWriter.updateNote("* 普通标题\n", 0, title = "x", states = states, now = now))
    }

    @Test
    fun `update note preserves spacing prefix`() {
        val content = "*   TODO 任务\n"
        val result = OrgWriter.updateNote(content, 0, title = "改", states = states, now = now)!!
        assertEquals("*   TODO 改\n", result)
    }

    @Test
    fun `update note sanitizes newline in title`() {
        val content = "* TODO 任务\n"
        val result = OrgWriter.updateNote(content, 0, title = "一行\n标题", states = states, now = now)!!
        assertEquals("* TODO 一行 标题\n", result)
    }

    @Test
    fun `update note sets and clears priority`() {
        val content = "* TODO 任务\n"
        assertEquals(
            "* TODO [#B] 任务\n",
            OrgWriter.updateNote(content, 0, priority = "B", states = states, now = now)
        )
        assertEquals(
            "* TODO 任务\n",
            OrgWriter.updateNote("* TODO [#A] 任务\n", 0, priority = "", states = states, now = now)
        )
        assertNull(OrgWriter.updateNote(content, 0, priority = "XY", states = states, now = now))
    }

    @Test
    fun `update note replaces and clears tags`() {
        val content = "* TODO 任务 :old:\n"
        assertEquals(
            "* TODO 任务 :a:b:\n",
            OrgWriter.updateNote(content, 0, tags = listOf("a", "b"), states = states, now = now)
        )
        assertEquals(
            "* TODO 任务\n",
            OrgWriter.updateNote(content, 0, tags = emptyList(), states = states, now = now)
        )
    }

    @Test
    fun `update note adds scheduled to note without planning`() {
        val content = "* TODO 任务\n正文\n"
        val result = OrgWriter.updateNote(
            content, 0, scheduled = "<2026-10-20 Tue>", states = states, now = now
        )!!
        assertEquals("* TODO 任务\nSCHEDULED: <2026-10-20 Tue>\n正文\n", result)
    }

    @Test
    fun `update note rewrites planning keeping closed`() {
        val content = "* DONE 任务\n  SCHEDULED: <2026-10-18 Sun> CLOSED: [2026-10-19 Mon 10:00]\n"
        val result = OrgWriter.updateNote(
            content, 0, scheduled = "", deadline = "2026-10-21", states = states, now = now
        )!!
        assertEquals(
            "* DONE 任务\n  DEADLINE: <2026-10-21> CLOSED: [2026-10-19 Mon 10:00]\n",
            result
        )
    }

    @Test
    fun `update note removing last planning entry deletes line`() {
        val content = "* TODO 任务\nSCHEDULED: <2026-10-18 Sun>\n正文\n"
        val result = OrgWriter.updateNote(
            content, 0, scheduled = "", states = states, now = now
        )!!
        assertEquals("* TODO 任务\n正文\n", result)
    }

    @Test
    fun `update note formats timestamp`() {
        assertEquals(
            "<2026-10-19 Mon>",
            OrgWriter.formatTimestamp(java.time.LocalDate.of(2026, 10, 19))
        )
        assertEquals(
            "<2026-10-19 Mon 08:05>",
            OrgWriter.formatTimestamp(java.time.LocalDate.of(2026, 10, 19), java.time.LocalTime.of(8, 5))
        )
    }

    @Test
    fun `delete note removes headline and planning and body`() {
        val content = "* TODO 保留\n* DONE 删除\n  CLOSED: [2026-10-18 Sun 10:00]\n正文行\n* TODO 保留2\n"
        val result = OrgWriter.deleteNote(content, 1, states)!!
        assertEquals("* TODO 保留\n* TODO 保留2\n", result)
    }

    @Test
    fun `delete note keeps child headlines`() {
        val content = "* TODO 父\n** TODO 子\n"
        val result = OrgWriter.deleteNote(content, 0, states)!!
        assertEquals("** TODO 子\n", result)
    }

    @Test
    fun `delete note at end of file`() {
        val content = "* TODO 保留\n* TODO 删除\n正文"
        val result = OrgWriter.deleteNote(content, 1, states)!!
        assertEquals("* TODO 保留", result)
    }

    @Test
    fun `delete note on non-todo returns null`() {
        assertNull(OrgWriter.deleteNote("* 普通标题\n", 0, states))
        assertNull(OrgWriter.deleteNote("* TODO 任务\n", 5, states))
    }
}
