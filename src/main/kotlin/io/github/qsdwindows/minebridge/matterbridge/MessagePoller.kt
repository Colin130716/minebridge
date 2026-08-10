/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import io.github.qsdwindows.minebridge.config.BridgeConfig
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GET /api/messages 轮询回退接收器。stream 不可用时由 MessageBridge 启动。
 *
 * 维护最近已处理消息 key 的环形缓冲：部分 Matterbridge 版本/配置下 /api/messages
 * 可能重复返回同一批历史消息，seen 集合确保同一批只投递一次。
 */
class MessagePoller(
    private val api: MatterbridgeApi,
    private val config: BridgeConfig,
    private val onMessage: (IncomingMessage) -> Unit,
) : AutoCloseable {

    private val running = AtomicBoolean(false)
    private var executor: ScheduledExecutorService? = null

    private val seenKeys: ArrayDeque<String> = ArrayDeque(SEEN_CAPACITY)
    private val seenIndex: MutableSet<String> = HashSet(SEEN_CAPACITY)

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

    internal fun pollOnce() {
        if (!running.get()) return
        try {
            api.fetchMessages().forEach { msg ->
                val key = msg.id ?: "${msg.gateway}|${msg.username}|${msg.text}"
                if (!markSeen(key)) return@forEach
                onMessage(msg)
            }
        } catch (_: Exception) {
            // 轮询失败静默，等待下一周期
        }
    }

    /** 记录已处理消息 key；若已存在返回 false（该批消息此前已投递过）。 */
    private fun markSeen(key: String): Boolean {
        if (key in seenIndex) return false
        seenIndex.add(key)
        seenKeys.addLast(key)
        if (seenKeys.size > SEEN_CAPACITY) {
            seenIndex.remove(seenKeys.removeFirst())
        }
        return true
    }

    override fun close() {
        running.set(false)
        executor?.shutdownNow()
        executor = null
    }

    companion object {
        private const val SEEN_CAPACITY = 200
    }
}
