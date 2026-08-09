/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge

import io.github.qsdwindows.minebridge.bridge.MessageBridge
import io.github.qsdwindows.minebridge.config.ConfigManager
import io.github.qsdwindows.minebridge.event.ChatEventForwarder
import io.github.qsdwindows.minebridge.event.PlayerJoinLeaveForwarder
import io.github.qsdwindows.minebridge.matterbridge.MatterbridgeClient
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object MinebridgeMod : ModInitializer {
    const val MOD_ID: String = "minebridge"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    private var bridge: MessageBridge? = null

    override fun onInitialize() {
        LOGGER.info("[Minebridge] Initializing")

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val configDir = FabricLoader.getInstance().configDir
            val config = ConfigManager(configDir.resolve("minebridge.toml")).load()
            if (!config.bridge.enabled) {
                LOGGER.info("[Minebridge] Bridge disabled by config, skipping")
                return@register
            }

            val client = MatterbridgeClient(
                baseUrl = config.matterbridge.baseUrl,
                token = config.matterbridge.token,
            )

            val b = MessageBridge(server, config, client)
            b.start()
            bridge = b

            ChatEventForwarder(config, b::send).register()
            PlayerJoinLeaveForwarder(config, b::send).register()

            LOGGER.info(
                "[Minebridge] Bridge started: gateway={} baseUrl={}",
                config.matterbridge.gateway,
                config.matterbridge.baseUrl
            )
        }

        ServerTickEvents.END_SERVER_TICK.register { _ ->
            bridge?.onServerTick()
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            bridge?.close()
            bridge = null
            LOGGER.info("[Minebridge] Bridge stopped")
        }
    }
}
