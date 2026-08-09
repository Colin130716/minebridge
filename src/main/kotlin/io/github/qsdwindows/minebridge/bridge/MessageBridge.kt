/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.bridge

import io.github.qsdwindows.minebridge.config.MinebridgeConfig
import io.github.qsdwindows.minebridge.format.MessageFormatter
import io.github.qsdwindows.minebridge.matterbridge.IncomingMessage
import io.github.qsdwindows.minebridge.matterbridge.MatterbridgeApi
import io.github.qsdwindows.minebridge.matterbridge.MessageDeduplicator
import io.github.qsdwindows.minebridge.matterbridge.MessagePoller
import io.github.qsdwindows.minebridge.matterbridge.OutgoingMessage
import io.github.qsdwindows.minebridge.matterbridge.StreamListener
import net.minecraft.server.MinecraftServer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 桥接协调层：持有 stream 监听与轮询回退，维护线程安全入站队列，
 * 在 server tick 中把消息格式化为聊天组件并广播给全体在线玩家。
 */
class MessageBridge(
    private val server: MinecraftServer,
    private val config: MinebridgeConfig,
    private val api: MatterbridgeApi,
) : AutoCloseable {

    private val incomingQueue: ConcurrentLinkedQueue<IncomingMessage> = ConcurrentLinkedQueue()
    private val deduplicator = MessageDeduplicator()
    private val pollerActive = AtomicBoolean(false)
    private var streamFailures = 0
    private var streamListener: StreamListener? = null
    private var poller: MessagePoller? = null

    fun start() {
        if (!config.bridge.enabled) return
        val listener = StreamListener(
            api = api,
            config = config.bridge,
            onMessage = ::onIncoming,
            onOpened = ::onStreamOpened,
            onClosed = ::onStreamClosed,
        )
        streamListener = listener
        if (config.bridge.streamEnabled) {
            listener.start()
        } else {
            startPoller()
        }
    }

    /** 入站消息入队，由 onServerTick 在 server 线程消费。 */
    fun onIncoming(msg: IncomingMessage) {
        incomingQueue.add(msg)
    }

    /** server tick 分发：过滤自身回声 + 去重 + 格式化 + 广播。 */
    fun onServerTick() {
        while (true) {
            val msg = incomingQueue.poll() ?: break
            // 过滤自己发出的消息（account=minecraft）
            if (msg.account == "minecraft") continue
            // 防回环去重
            if (deduplicator.isDuplicate(msg.userid, msg.text ?: "")) continue
            val component = MessageFormatter.format(msg, config.formatting)
            server.playerList.players.forEach { it.sendSystemMessage(component) }
        }
    }

    /** 发送侧：记录去重摘要后异步发送。 */
    fun send(message: OutgoingMessage) {
        deduplicator.mark(message.userid, message.text)
        api.sendMessage(message)
    }

    private fun onStreamOpened() {
        streamFailures = 0
        stopPoller()
    }

    private fun onStreamClosed(error: Throwable?) {
        if (error == null) return // 正常 EOF，重连即可
        streamFailures++
        if (streamFailures >= config.bridge.streamFailoverThreshold) {
            startPoller()
        }
    }

    private fun startPoller() {
        if (pollerActive.compareAndSet(false, true)) {
            val p = MessagePoller(api, config.bridge, ::onIncoming)
            poller = p
            p.start()
        }
    }

    private fun stopPoller() {
        if (pollerActive.compareAndSet(true, false)) {
            poller?.close()
            poller = null
        }
    }

    override fun close() {
        streamListener?.close()
        stopPoller()
    }
}
