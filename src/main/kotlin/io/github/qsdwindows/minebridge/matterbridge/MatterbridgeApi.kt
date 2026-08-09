/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import java.util.concurrent.CompletableFuture

/** Matterbridge REST API 抽象（便于测试替身）。 */
interface MatterbridgeApi {
    /** POST /api/message，异步；HTTP 200 → true。 */
    fun sendMessage(message: OutgoingMessage): CompletableFuture<Boolean>

    /** GET /api/messages，拉取新消息。 */
    fun fetchMessages(): List<IncomingMessage>

    /** GET /api/health，存活检查。 */
    fun healthCheck(): Boolean

    /**
     * GET /api/stream 长连接。内部起守护线程读取并回调 [onMessage]。
     * 返回 AutoCloseable，close() 中断读取并关闭底层连接。
     */
    fun openStream(onMessage: (IncomingMessage) -> Unit): AutoCloseable
}
