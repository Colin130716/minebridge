/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.event

import io.github.qsdwindows.minebridge.config.MinebridgeConfig
import io.github.qsdwindows.minebridge.format.FormatCodeStripper
import io.github.qsdwindows.minebridge.matterbridge.OutgoingMessage
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.atomic.AtomicReference

/** 将玩家聊天转发到 Matterbridge（event=msg_create，去 § 码）。config 用引用以便热重载。 */
class ChatEventForwarder(
    private val configRef: AtomicReference<MinebridgeConfig>,
    private val send: (OutgoingMessage) -> Unit,
) {
    fun register() {
        ServerMessageEvents.CHAT_MESSAGE.register(
            ServerMessageEvents.ChatMessage { message: PlayerChatMessage, sender: ServerPlayer, _ ->
                val config = configRef.get()
                if (!config.events.forwardChat) return@ChatMessage
                send(
                    OutgoingMessage(
                        gateway = config.matterbridge.gateway,
                        text = FormatCodeStripper.strip(message.signedContent()),
                        username = sender.gameProfile.name,
                        event = "msg_create",
                        account = "minecraft",
                        protocol = "minecraft",
                        channel = "main",
                        userid = sender.stringUUID,
                    )
                )
            }
        )
    }
}
