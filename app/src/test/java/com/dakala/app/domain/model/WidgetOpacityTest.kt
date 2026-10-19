package com.dakala.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetOpacityTest {

    @Test
    fun parse_returns_default_for_null_or_garbage() {
        assertEquals(WidgetOpacity.DEFAULT, WidgetOpacity.parse(null))
        assertEquals(WidgetOpacity.DEFAULT, WidgetOpacity.parse(""))
        assertEquals(WidgetOpacity.DEFAULT, WidgetOpacity.parse("abc"))
        assertEquals(WidgetOpacity.DEFAULT, WidgetOpacity.parse("8.5"))
    }

    @Test
    fun parse_returns_default_for_out_of_range() {
        assertEquals(WidgetOpacity.DEFAULT, WidgetOpacity.parse("-1"))
        assertEquals(WidgetOpacity.DEFAULT, WidgetOpacity.parse("101"))
    }

    @Test
    fun parse_accepts_boundary_values() {
        assertEquals(WidgetOpacity(0), WidgetOpacity.parse("0"))
        assertEquals(WidgetOpacity(100), WidgetOpacity.parse("100"))
    }

    @Test
    fun toAlpha_scales_percent_to_255() {
        assertEquals(0, WidgetOpacity(0).toAlpha())
        assertEquals(255, WidgetOpacity(100).toAlpha())
        assertEquals(127, WidgetOpacity(50).toAlpha())
        assertEquals(216, WidgetOpacity(85).toAlpha())
    }

    @Test
    fun format_round_trips_through_parse() {
        val opacity = WidgetOpacity(60)
        assertEquals(opacity, WidgetOpacity.parse(opacity.format()))
    }
}
