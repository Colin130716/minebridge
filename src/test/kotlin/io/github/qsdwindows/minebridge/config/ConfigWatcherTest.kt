/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ConfigWatcherTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `triggers onChange when config file modified`() {
        val configPath = tempDir.resolve("minebridge.toml")
        Files.writeString(configPath, "a=1\n")
        // 确保基线 mtime 与后续修改不同
        Thread.sleep(20)

        val latch = CountDownLatch(1)
        val watcher = ConfigWatcher(configPath, onChange = { latch.countDown() }, pollMillis = 50L)
        watcher.start()

        try {
            // 修改文件内容（会更新 lastModified）
            Files.writeString(configPath, "a=2\n")
            assertTrue(
                latch.await(3, TimeUnit.SECONDS),
                "onChange should fire after config file is modified"
            )
        } finally {
            watcher.close()
        }
    }

    @Test
    fun `does not fire before any modification`() {
        val configPath = tempDir.resolve("minebridge.toml")
        Files.writeString(configPath, "a=1\n")
        Thread.sleep(20)

        val counter = AtomicInteger(0)
        val watcher = ConfigWatcher(configPath, onChange = { counter.incrementAndGet() }, pollMillis = 50L)
        watcher.start()
        try {
            Thread.sleep(200)
            assertEquals(0, counter.get(), "no callback before file changes")
        } finally {
            watcher.close()
        }
    }

    @Test
    fun `close stops further callbacks`() {
        val configPath = tempDir.resolve("minebridge.toml")
        Files.writeString(configPath, "a=1\n")
        Thread.sleep(20)

        val counter = AtomicInteger(0)
        val watcher = ConfigWatcher(configPath, onChange = { counter.incrementAndGet() }, pollMillis = 50L)
        watcher.start()
        watcher.close()

        Files.writeString(configPath, "a=3\n")
        Thread.sleep(200)
        assertEquals(0, counter.get(), "no callback after close")
    }
}
