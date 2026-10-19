package com.dakala.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTimeTest {

    @Test
    fun parse_returns_default_for_null() {
        assertEquals(NotificationTime.DEFAULT, NotificationTime.parse(null))
    }

    @Test
    fun parse_returns_default_for_blank_or_garbage() {
        assertEquals(NotificationTime.DEFAULT, NotificationTime.parse(""))
        assertEquals(NotificationTime.DEFAULT, NotificationTime.parse("abc"))
        assertEquals(NotificationTime.DEFAULT, NotificationTime.parse("8"))
        assertEquals(NotificationTime.DEFAULT, NotificationTime.parse("08:"))
    }

    @Test
    fun parse_returns_default_for_out_of_range_values() {
        assertEquals(NotificationTime.DEFAULT, NotificationTime.parse("24:00"))
        assertEquals(NotificationTime.DEFAULT, NotificationTime.parse("25:99"))
        assertEquals(NotificationTime.DEFAULT, NotificationTime.parse("-1:30"))
        assertEquals(NotificationTime.DEFAULT, NotificationTime.parse("12:60"))
    }

    @Test
    fun parse_accepts_single_digit_components() {
        assertEquals(NotificationTime(8, 5), NotificationTime.parse("8:5"))
    }

    @Test
    fun format_pads_to_two_digits() {
        assertEquals("08:05", NotificationTime(8, 5).format())
        assertEquals("22:00", NotificationTime(22, 0).format())
    }

    @Test
    fun format_round_trips_through_parse() {
        val time = NotificationTime(20, 30)
        assertEquals(time, NotificationTime.parse(time.format()))
    }
}
