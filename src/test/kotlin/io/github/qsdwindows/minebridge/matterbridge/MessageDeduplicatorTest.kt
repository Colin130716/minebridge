package io.github.qsdwindows.minebridge.matterbridge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageDeduplicatorTest {

    @Test
    fun `mark returns true for new messages`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark("gw", "alice", "hello"))
        assertTrue(dedup.mark("gw", "alice", "world"))
    }

    @Test
    fun `mark returns false for duplicate`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark("gw", "alice", "hello"))
        assertFalse(dedup.mark("gw", "alice", "hello"))
    }

    @Test
    fun `same text from different users is distinct`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark("gw", "alice", "hello"))
        assertTrue(dedup.mark("gw", "bob", "hello"))
    }

    @Test
    fun `same user and text on different gateways is distinct`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark("gw1", "alice", "hello"))
        assertTrue(dedup.mark("gw2", "alice", "hello"))
    }

    @Test
    fun `isDuplicate detects previously marked messages`() {
        val dedup = MessageDeduplicator()
        dedup.mark("gw", "alice", "hello")
        assertTrue(dedup.isDuplicate("gw", "alice", "hello"))
        assertFalse(dedup.isDuplicate("gw", "alice", "nope"))
        assertFalse(dedup.isDuplicate("gw", "bob", "hello"))
    }

    @Test
    fun `null gateway and username handled`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.mark(null, null, "hello"))
        assertTrue(dedup.isDuplicate(null, null, "hello"))
        assertFalse(dedup.isDuplicate(null, null, "other"))
    }

    @Test
    fun `oldest entry evicted when capacity exceeded`() {
        val dedup = MessageDeduplicator(capacity = 3)
        assertTrue(dedup.mark("gw", "u", "a"))
        assertTrue(dedup.mark("gw", "u", "b"))
        assertTrue(dedup.mark("gw", "u", "c"))
        assertTrue(dedup.mark("gw", "u", "d")) // evicts "a"
        assertFalse(dedup.isDuplicate("gw", "u", "a"))
        assertTrue(dedup.isDuplicate("gw", "u", "b"))
        assertTrue(dedup.isDuplicate("gw", "u", "c"))
        assertTrue(dedup.isDuplicate("gw", "u", "d"))
    }
}
