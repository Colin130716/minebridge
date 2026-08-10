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
     * GET /api/stream 长连接，**阻塞语义**：在调用线程内同步读取直到 EOF/异常。
     *
     * - 连接建立成功后先回调 [onOpened]（参数为可关闭句柄，用于从外部中断读取）
     * - 逐行解析 JSON 并回调 [onMessage]
     * - 连接失败或 HTTP 非 200 时抛 [java.io.IOException]
     * - EOF 时正常返回
     */
    fun openStream(
        onMessage: (IncomingMessage) -> Unit,
        onOpened: (AutoCloseable) -> Unit,
    )
}
