package com.dakala.app.data.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrgParserTest {

    @Test
    fun `parse simple todo headline`() {
        val file = OrgParser.parse("* TODO 买东西\n")
        assertEquals(1, file.notes.size)
        val note = file.notes[0]
        assertEquals(0, note.lineIndex)
        assertEquals(1, note.level)
        assertEquals("TODO", note.state)
        assertEquals("买东西", note.title)
        assertTrue(!note.isDone)
    }

    @Test
    fun `parse done state`() {
        val file = OrgParser.parse("* DONE 已完成任务\n")
        assertEquals("DONE", file.notes[0].state)
        assertTrue(file.notes[0].isDone)
    }

    @Test
    fun `headline without state is not a todo`() {
        val file = OrgParser.parse("* 普通标题\n** 子标题\n")
        assertTrue(file.notes.isEmpty())
    }

    @Test
    fun `bare star line is not a headline`() {
        val file = OrgParser.parse("*\n** \n")
        assertTrue(file.notes.isEmpty())
    }

    @Test
    fun `parse priority and tags`() {
        val file = OrgParser.parse("* TODO [#A] 重要任务 :work:urgent:\n")
        val note = file.notes[0]
        assertEquals("A", note.priority)
        assertEquals("重要任务", note.title)
        assertEquals(listOf("work", "urgent"), note.tags)
    }

    @Test
    fun `parse nested levels`() {
        val file = OrgParser.parse("* TODO 父\n** TODO 子\n*** DONE 孙\n")
        assertEquals(listOf(1, 2, 3), file.notes.map { it.level })
    }

    @Test
    fun `parse planning line`() {
        val content = "* TODO 任务\nSCHEDULED: <2026-10-20 Tue> DEADLINE: <2026-10-25 Sun>\n"
        val note = OrgParser.parse(content).notes[0]
        assertEquals("<2026-10-20 Tue>", note.scheduled)
        assertEquals("<2026-10-25 Sun>", note.deadline)
        assertNull(note.closed)
    }

    @Test
    fun `planning line must follow headline directly`() {
        val content = "* TODO 任务\n\nSCHEDULED: <2026-10-20 Tue>\n"
        val note = OrgParser.parse(content).notes[0]
        assertNull(note.scheduled)
    }

    @Test
    fun `custom todo keywords from file settings`() {
        val content = "#+TODO: TODO NEXT | DONE CANCELLED\n* NEXT 进行中\n* CANCELLED 取消\n"
        val file = OrgParser.parse(content)
        assertEquals(listOf("TODO", "NEXT"), file.states.todoKeywords)
        assertEquals(listOf("DONE", "CANCELLED"), file.states.doneKeywords)
        assertEquals("NEXT", file.notes[0].state)
        assertTrue(!file.notes[0].isDone)
        assertTrue(file.notes[1].isDone)
    }

    @Test
    fun `todo keywords without bar use last as done`() {
        val content = "#+TODO: TODO DONE\n* DONE 完成\n"
        val file = OrgParser.parse(content)
        assertEquals(listOf("TODO"), file.states.todoKeywords)
        assertEquals(listOf("DONE"), file.states.doneKeywords)
    }

    @Test
    fun `keyword fast access marks stripped`() {
        val content = "#+SEQ_TODO: TODO(t) WAIT(w@/!) | DONE(d!)\n* WAIT 等待\n"
        val file = OrgParser.parse(content)
        assertEquals(listOf("TODO", "WAIT"), file.states.todoKeywords)
        assertEquals(listOf("DONE"), file.states.doneKeywords)
        assertEquals("WAIT", file.notes[0].state)
    }

    @Test
    fun `unrecognized keyword stays in title`() {
        val file = OrgParser.parse("* MAYBE 待定\n")
        assertTrue(file.notes.isEmpty())
    }

    @Test
    fun `parse title setting`() {
        val file = OrgParser.parse("#+TITLE: 我的待办\n* TODO 任务\n")
        assertEquals("我的待办", file.title)
    }

    @Test
    fun `tags without preceding space stay in title`() {
        val file = OrgParser.parse("* TODO 标题foo:bar:\n")
        val note = file.notes[0]
        assertEquals("标题foo:bar:", note.title)
        assertTrue(note.tags.isEmpty())
    }

    @Test
    fun `content after todo ignored`() {
        val content = "* TODO 任务\n这是正文\n- 列表项\n* DONE 另一任务\n"
        val file = OrgParser.parse(content)
        assertEquals(2, file.notes.size)
    }

    @Test
    fun `crlf line endings`() {
        val file = OrgParser.parse("* TODO 任务\r\n* DONE 完成\r\n")
        assertEquals(2, file.notes.size)
        assertEquals("任务", file.notes[0].title)
    }

    @Test
    fun `state with only tags and no title`() {
        val file = OrgParser.parse("* TODO :tag:\n")
        val note = file.notes[0]
        assertEquals("", note.title)
        assertEquals(listOf("tag"), note.tags)
    }
}
