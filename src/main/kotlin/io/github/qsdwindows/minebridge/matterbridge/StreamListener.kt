/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import io.github.qsdwindows.minebridge.config.BridgeConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * GET /api/stream 长连接监听器。独立守护线程循环读取，
 * 断线后按指数退避自动重连（base = reconnectDelaySeconds，倍增上限 5 次）。
 *
 * [onOpened] 每次成功建立连接时回调；[onClosed] 每次连接中断/EOF 时回调（参数为异常或 null）。
 */
class StreamListener(
    private val api: MatterbridgeApi,
    private val config: BridgeConfig,
    private val onMessage: (IncomingMessage) -> Unit,
    private val onOpened: () -> Unit,
    private val onClosed: (Throwable?) -> Unit,
) : AutoCloseable {

    private val running = AtomicBoolean(true)
    private val currentStream = AtomicReference<AutoCloseable?>(null)

    fun start() {
        val thread = Thread(::runLoop, "minebridge-stream")
        thread.isDaemon = true
        thread.start()
    }

    private fun runLoop() {
        var attempt = 0
        while (running.get()) {
            try {
                val handle = api.openStream(onMessage)
                currentStream.set(handle)
                attempt = 0
                onOpened()
                // 阻塞等待：openStream 内部线程在连接关闭后返回
                try {
                    Thread.sleep(Long.MAX_VALUE)
                } catch (_: InterruptedException) {
                    // 被 close() 中断，退出
                }
                currentStream.set(null)
                if (running.get()) onClosed(null)
            } catch (e: Exception) {
                currentStream.set(null)
                if (running.get()) onClosed(e)
            }
            if (!running.get()) return
            attempt++
            val delaySeconds = config.reconnectDelaySeconds * (1L shl attempt.coerceAtMost(5))
            sleepQuietly(delaySeconds * 1000L)
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
        currentStream.get()?.close()
    }
}
