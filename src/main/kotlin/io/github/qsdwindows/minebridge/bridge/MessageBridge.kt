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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

    private val LOGGER: Logger = LoggerFactory.getLogger(MessageBridge::class.java)

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

    /** server tick 分发：过滤自身回声 + event 过滤 + 去重 + 格式化 + 广播。 */
    fun onServerTick() {
        while (true) {
            val msg = incomingQueue.poll() ?: break
            // 只处理聊天类事件，忽略附件/系统等其他事件。
            // 注意：Matterbridge 从 xmpp/irc 等外部平台转发来的普通消息 event 为空/NULL，
            // 不应被当作未知事件丢弃，而应视为聊天内容。
            if (!isHandledEvent(msg)) continue
            // 过滤自己发出的消息回声（protocol=api 或 account 为 minecraft/api.*）
            if (isOwnEcho(msg)) continue
            // 防回环去重
            if (deduplicator.isDuplicate(msg.gateway, msg.username, msg.text ?: "")) continue
            val component = MessageFormatter.format(msg, config.formatting)
            server.playerList.players.forEach { it.sendSystemMessage(component) }
        }
    }

    /** 发送侧：记录去重摘要后异步发送，失败时记录 warn 日志。 */
    fun send(message: OutgoingMessage) {
        deduplicator.mark(message.gateway, message.username, message.text)
        api.sendMessage(message).thenAccept { ok ->
            if (!ok) {
                LOGGER.warn("[Minebridge] Failed to send message to gateway '{}'", message.gateway)
            }
        }
    }

    /** 运行状态快照（供 /minebridge status 命令展示与测试）。 */
    fun status(): BridgeStatus = BridgeStatus(
        enabled = config.bridge.enabled,
        streamEnabled = config.bridge.streamEnabled,
        receiveMode = if (pollerActive.get()) "poller" else "stream",
        streamFailures = streamFailures,
        onboardingQueueSize = incomingQueue.size,
        gateway = config.matterbridge.gateway,
        baseUrl = config.matterbridge.baseUrl,
    )

    /** 运行状态数据。 */
    data class BridgeStatus(
        val enabled: Boolean,
        val streamEnabled: Boolean,
        val receiveMode: String,
        val streamFailures: Int,
        val onboardingQueueSize: Int,
        val gateway: String,
        val baseUrl: String,
    )

    private fun isOwnEcho(msg: IncomingMessage): Boolean =
        msg.protocol == "api" ||
            msg.account?.startsWith("minecraft") == true ||
            msg.account?.startsWith("api.") == true

    companion object {
        private val CHAT_EVENTS = setOf("msg_create")

        /**
         * 入站消息是否应被处理（可被当作聊天/事件展示）。
         *
         * - `msg_create` 显式处理；
         * - **event 为空/NULL 且文本非空** 视为普通聊天消息（Matterbridge 从 xmpp/irc
         *   等外部桥转发来的消息 event 通常为空，仅 text/username 有效）；
         * - 其他事件（join/leave/附件/系统等）忽略——Matterbridge 原生不向 api 转发
         *   IRC/XMPP 的 join/leave，入站 join/leave 无意义。
         */
        internal fun isHandledEvent(msg: IncomingMessage): Boolean {
            val event = msg.event
            if (event.isNullOrBlank()) {
                // 空 event：仅当有实际文本内容时才作为聊天消息展示
                return !msg.text.isNullOrBlank()
            }
            return event in CHAT_EVENTS
        }
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
