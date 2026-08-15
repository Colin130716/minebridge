/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 配置文件热重载监听器（轻量轮询 mtime，跨平台，零依赖）。
 *
 * 后台线程定期检查 [configPath] 的 lastModified；变化时回调 [onChange]。
 * 首次 start 时记录基线，不立即触发。close 时停止线程。
 */
class ConfigWatcher(
    private val configPath: Path,
    private val onChange: () -> Unit,
    private val pollMillis: Long = 1000L,
) : AutoCloseable {

    private val LOGGER: Logger = LoggerFactory.getLogger(ConfigWatcher::class.java)
    private val running = AtomicBoolean(true)
    private var thread: Thread? = null
    private var lastModified: Long =
        if (Files.exists(configPath)) Files.getLastModifiedTime(configPath).toMillis() else -1L

    fun start() {
        if (!running.get()) return
        val t = Thread(::loop, "minebridge-config-watcher")
        t.isDaemon = true
        thread = t
        t.start()
    }

    private fun loop() {
        while (running.get()) {
            try {
                val current = if (Files.exists(configPath)) Files.getLastModifiedTime(configPath).toMillis() else -1L
                if (current != lastModified && lastModified != -1L && current != -1L) {
                    LOGGER.info("[Minebridge] Config file changed, triggering reload")
                    onChange()
                }
                lastModified = current
            } catch (e: Exception) {
                LOGGER.debug("[Minebridge] Config watcher error: {}", e.message)
            }
            sleepQuietly(pollMillis)
        }
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            running.set(false)
        }
    }

    override fun close() {
        running.set(false)
        thread?.interrupt()
    }
}
