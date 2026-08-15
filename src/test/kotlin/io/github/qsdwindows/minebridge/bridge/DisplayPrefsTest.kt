/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DisplayPrefsTest {

    private val uuid1 = UUID.randomUUID()
    private val uuid2 = UUID.randomUUID()

    @Test
    fun `defaults to enabled for unknown players`() {
        val prefs = DisplayPrefs()
        assertTrue(prefs.isEnabled(uuid1))
        assertEquals(0, prefs.disabledCount())
    }

    @Test
    fun `disable then enable toggles state`() {
        val prefs = DisplayPrefs()
        prefs.setEnabled(uuid1, false)
        assertFalse(prefs.isEnabled(uuid1))

        prefs.setEnabled(uuid1, true)
        assertTrue(prefs.isEnabled(uuid1))
        assertEquals(0, prefs.disabledCount())
    }

    @Test
    fun `per player state is independent`() {
        val prefs = DisplayPrefs()
        prefs.setEnabled(uuid1, false)
        assertFalse(prefs.isEnabled(uuid1))
        assertTrue(prefs.isEnabled(uuid2))
        assertEquals(1, prefs.disabledCount())
    }
}
