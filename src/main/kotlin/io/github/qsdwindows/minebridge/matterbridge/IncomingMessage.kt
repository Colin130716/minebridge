/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import com.google.gson.annotations.SerializedName

/** GET /api/messages 与 /api/stream 返回的消息对象。 */
data class IncomingMessage(
    val id: String? = null,
    @SerializedName("parent_id") val parentId: String? = null,
    val text: String? = null,
    val username: String? = null,
    val account: String? = null,
    val protocol: String? = null,
    val channel: String? = null,
    val event: String? = null,
    val gateway: String? = null,
    val timestamp: String? = null,
    val userid: String? = null,
    val avatar: String? = null,
    val extra: Any? = null,
)
