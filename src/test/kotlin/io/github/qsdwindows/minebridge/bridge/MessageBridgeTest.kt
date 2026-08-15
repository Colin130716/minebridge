/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.bridge

import io.github.qsdwindows.minebridge.matterbridge.IncomingMessage
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageBridgeTest {

    @Test
    fun `explicit chat event msg_create is handled`() {
        assertTrue(
            MessageBridge.isHandledEvent(
                IncomingMessage(event = "msg_create", text = "hello", username = "alice")
            )
        )
    }

    @Test
    fun `join and leave events from external bridges are ignored`() {
        // Matterbridge 原生不向 api 转发 IRC/XMPP 的 join/leave，入站 join/leave 无意义 → 忽略
        assertFalse(MessageBridge.isHandledEvent(IncomingMessage(event = "join", text = "x joined")))
        assertFalse(MessageBridge.isHandledEvent(IncomingMessage(event = "leave", text = "x left")))
    }

    @Test
    fun `external platform message with empty event and text is handled`() {
        // Matterbridge 从 xmpp/irc 转发来的普通消息 event 为空，但有文本 → 应显示
        assertTrue(
            MessageBridge.isHandledEvent(
                IncomingMessage(event = "", text = "hi from xmpp", username = "bob", protocol = "xmpp")
            )
        )
    }

    @Test
    fun `external platform message with null event and text is handled`() {
        assertTrue(
            MessageBridge.isHandledEvent(
                IncomingMessage(event = null, text = "hi from irc", username = "carol", protocol = "irc")
            )
        )
    }

    @Test
    fun `empty event with no text is ignored`() {
        // stream 欢迎事件 api_connected 之类的空正文消息不应展示
        assertFalse(MessageBridge.isHandledEvent(IncomingMessage(event = "", text = "")))
        assertFalse(MessageBridge.isHandledEvent(IncomingMessage(event = null, text = null)))
    }

    @Test
    fun `other non-chat events are ignored`() {
        assertFalse(MessageBridge.isHandledEvent(IncomingMessage(event = "attach_create", text = "file.png")))
        assertFalse(MessageBridge.isHandledEvent(IncomingMessage(event = "api_connected", text = "")))
    }
}
