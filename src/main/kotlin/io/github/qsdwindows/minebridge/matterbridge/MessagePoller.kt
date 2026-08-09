/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import io.github.qsdwindows.minebridge.config.BridgeConfig
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** GET /api/messages 轮询回退接收器。stream 不可用时由 MessageBridge 启动。 */
class MessagePoller(
    private val api: MatterbridgeApi,
    private val config: BridgeConfig,
    private val onMessage: (IncomingMessage) -> Unit,
) : AutoCloseable {

    private val running = AtomicBoolean(false)
    private var executor: ScheduledExecutorService? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val ex = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "minebridge-poller").apply { isDaemon = true }
        }
        executor = ex
        ex.scheduleWithFixedDelay(
            { pollOnce() },
            0,
            config.pollIntervalSeconds,
            TimeUnit.SECONDS,
        )
    }

    private fun pollOnce() {
        if (!running.get()) return
        try {
            api.fetchMessages().forEach(onMessage)
        } catch (_: Exception) {
            // 轮询失败静默，等待下一周期
        }
    }

    override fun close() {
        running.set(false)
        executor?.shutdownNow()
        executor = null
    }
}
