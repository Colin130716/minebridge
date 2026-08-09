/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

/** POST /api/message 请求体（Matterbridge API 0.1.0-oas3）。gateway/text/username 必填。 */
data class OutgoingMessage(
    val gateway: String,
    val text: String,
    val username: String,
    val avatar: String? = null,
    val event: String? = null,
    val account: String? = null,
    val protocol: String? = null,
    val channel: String? = null,
    val userid: String? = null,
    val extra: Any? = null,
)
