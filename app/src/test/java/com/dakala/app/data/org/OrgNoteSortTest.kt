package com.dakala.app.data.org

import org.junit.Assert.assertEquals
import org.junit.Test

class OrgNoteSortTest {

    private fun note(
        lineIndex: Int,
        isDone: Boolean,
        closed: String? = null
    ) = OrgNote(
        lineIndex = lineIndex,
        level = 1,
        state = if (isDone) "DONE" else "TODO",
        isDone = isDone,
        priority = null,
        title = "note-$lineIndex",
        closed = closed
    )

    @Test
    fun `incomplete notes stay in file order before done ones`() {
        val notes = listOf(
            note(0, isDone = true, closed = "[2026-10-21 Wed 10:00]"),
            note(1, isDone = false),
            note(2, isDone = false),
            note(3, isDone = true, closed = "[2026-10-19 Mon 08:00]")
        )

        assertEquals(listOf(1, 2, 0, 3), notes.sortedForDisplay().map { it.lineIndex })
    }

    @Test
    fun `done notes are ordered by completion time newest first`() {
        val notes = listOf(
            note(0, isDone = true, closed = "[2026-10-19 Mon 08:00]"),
            note(1, isDone = true, closed = "[2026-10-21 Wed 10:00]"),
            note(2, isDone = true, closed = "[2026-10-20 Tue 22:30]")
        )

        assertEquals(listOf(1, 2, 0), notes.sortedForDisplay().map { it.lineIndex })
    }

    @Test
    fun `done notes without closed timestamp go last`() {
        val notes = listOf(
            note(0, isDone = true),
            note(1, isDone = true, closed = "[2026-10-18 Sun 09:00]"),
            note(2, isDone = true)
        )

        assertEquals(listOf(1, 0, 2), notes.sortedForDisplay().map { it.lineIndex })
    }

    @Test
    fun `closed date without time is parsed`() {
        val notes = listOf(
            note(0, isDone = true, closed = "[2026-10-20 Tue]"),
            note(1, isDone = true, closed = "[2026-10-21 Wed]")
        )

        assertEquals(listOf(1, 0), notes.sortedForDisplay().map { it.lineIndex })
    }
}
