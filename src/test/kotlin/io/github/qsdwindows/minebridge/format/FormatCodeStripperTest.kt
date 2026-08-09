package io.github.qsdwindows.minebridge.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatCodeStripperTest {

    @Test
    fun `strips all color and format codes`() {
        assertEquals("Hello", FormatCodeStripper.strip("§aHello"))
        assertEquals("Hello world", FormatCodeStripper.strip("§bHello §rworld"))
        assertEquals("Bold", FormatCodeStripper.strip("§lBold"))
    }

    @Test
    fun `uppercase codes also stripped`() {
        assertEquals("Hi", FormatCodeStripper.strip("§AHi"))
    }

    @Test
    fun `hex color codes stripped`() {
        assertEquals("Rainbow", FormatCodeStripper.strip("§x§F§F§0§0§0§0Rainbow"))
    }

    @Test
    fun `plain text unchanged`() {
        assertEquals("plain text 123", FormatCodeStripper.strip("plain text 123"))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", FormatCodeStripper.strip(""))
    }

    @Test
    fun `dangling code at end is removed`() {
        assertEquals("trail", FormatCodeStripper.strip("trail§"))
    }
}
