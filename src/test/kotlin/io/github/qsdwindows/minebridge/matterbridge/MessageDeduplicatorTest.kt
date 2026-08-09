package io.github.qsdwindows.minebridge.matterbridge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageDeduplicatorTest {

    @Test
    fun `mark returns true for new messages`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark("uuid-1", "hello"))
        assertTrue(dedup.mark("uuid-1", "world"))
    }

    @Test
    fun `mark returns false for duplicate`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark("uuid-1", "hello"))
        assertFalse(dedup.mark("uuid-1", "hello"))
    }

    @Test
    fun `same text from different users is distinct`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark("uuid-1", "hello"))
        assertTrue(dedup.mark("uuid-2", "hello"))
    }

    @Test
    fun `isDuplicate detects previously marked messages`() {
        val dedup = MessageDeduplicator()
        dedup.mark("uuid-1", "hello")
        assertTrue(dedup.isDuplicate("uuid-1", "hello"))
        assertFalse(dedup.isDuplicate("uuid-1", "nope"))
        assertFalse(dedup.isDuplicate("uuid-2", "hello"))
    }

    @Test
    fun `null userid handled`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark(null, "hello"))
        assertTrue(dedup.isDuplicate(null, "hello"))
        assertFalse(dedup.isDuplicate(null, "other"))
    }

    @Test
    fun `oldest entry evicted when capacity exceeded`() {
        val dedup = MessageDeduplicator(capacity = 3)
        assertTrue(dedup.mark("u", "a"))
        assertTrue(dedup.mark("u", "b"))
        assertTrue(dedup.mark("u", "c"))
        assertTrue(dedup.mark("u", "d")) // evicts "a"
        assertFalse(dedup.isDuplicate("u", "a"))
        assertTrue(dedup.isDuplicate("u", "b"))
        assertTrue(dedup.isDuplicate("u", "c"))
        assertTrue(dedup.isDuplicate("u", "d"))
    }
}
