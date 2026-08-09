/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.event

import io.github.qsdwindows.minebridge.config.MinebridgeConfig
import io.github.qsdwindows.minebridge.matterbridge.OutgoingMessage
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.network.ServerGamePacketListenerImpl

/** 将玩家加入/离开事件转发到 Matterbridge（event=join/leave，username 固定 Minecraft）。 */
class PlayerJoinLeaveForwarder(
    private val config: MinebridgeConfig,
    private val send: (OutgoingMessage) -> Unit,
) {
    fun register() {
        ServerPlayConnectionEvents.JOIN.register(
            ServerPlayConnectionEvents.Join { handler: ServerGamePacketListenerImpl, _, _ ->
                if (!config.events.forwardJoin) return@Join
                val name = handler.player.gameProfile.name
                send(
                    OutgoingMessage(
                        gateway = config.matterbridge.gateway,
                        text = "$name joined the game",
                        username = "Minecraft",
                        event = "join",
                        account = "minecraft",
                        protocol = "minecraft",
                        channel = "main",
                        userid = handler.player.stringUUID,
                    )
                )
            }
        )
        ServerPlayConnectionEvents.DISCONNECT.register(
            ServerPlayConnectionEvents.Disconnect { handler: ServerGamePacketListenerImpl, _ ->
                if (!config.events.forwardLeave) return@Disconnect
                val name = handler.player.gameProfile.name
                send(
                    OutgoingMessage(
                        gateway = config.matterbridge.gateway,
                        text = "$name left the game",
                        username = "Minecraft",
                        event = "leave",
                        account = "minecraft",
                        protocol = "minecraft",
                        channel = "main",
                        userid = handler.player.stringUUID,
                    )
                )
            }
        )
    }
}
