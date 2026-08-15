/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge

import io.github.qsdwindows.minebridge.bridge.MessageBridge
import io.github.qsdwindows.minebridge.command.MinebridgeCommand
import io.github.qsdwindows.minebridge.config.ConfigManager
import io.github.qsdwindows.minebridge.config.ConfigWatcher
import io.github.qsdwindows.minebridge.config.MinebridgeConfig
import io.github.qsdwindows.minebridge.event.ChatEventForwarder
import io.github.qsdwindows.minebridge.event.PlayerJoinLeaveForwarder
import io.github.qsdwindows.minebridge.matterbridge.MatterbridgeClient
import io.github.qsdwindows.minebridge.matterbridge.OutgoingMessage
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

object MinebridgeMod : ModInitializer {
    const val MOD_ID: String = "minebridge"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    private var bridge: MessageBridge? = null
    private var currentServer: MinecraftServer? = null
    private var configWatcher: ConfigWatcher? = null
    private var forwardersRegistered = false

    /** 当前生效配置（转发器从这读取，热重载时替换）。 */
    private val configRef: AtomicReference<MinebridgeConfig> = AtomicReference()

    private lateinit var configPath: Path

    override fun onInitialize() {
        LOGGER.info("[Minebridge] Initializing")

        val configDir = FabricLoader.getInstance().configDir
        configPath = configDir.resolve("minebridge.toml")

        // 装配命令（回调绑定；命令注册本身不依赖 server 生命周期）
        MinebridgeCommand.setup(
            status = { bridge?.status() },
            reload = { reloadFromCommand() },
            send = { sendTestMessage(it) },
            isDisplayEnabled = { uuid -> bridge?.isDisplayEnabled(uuid) ?: true },
            setDisplayEnabled = { uuid, enabled -> bridge?.setDisplayEnabled(uuid, enabled) },
        )
        MinebridgeCommand.register()

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            currentServer = server
            val config = ConfigManager(configPath).load()
            configRef.set(config)
            if (config.bridge.enabled) {
                startBridge(server, config)
            } else {
                LOGGER.info("[Minebridge] Bridge disabled by config, skipping")
            }
            registerForwarders()
            startConfigWatcher()
        }

        ServerTickEvents.END_SERVER_TICK.register { _ ->
            bridge?.onServerTick()
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            configWatcher?.close()
            configWatcher = null
            bridge?.close()
            bridge = null
            currentServer = null
            forwardersRegistered = false
            LOGGER.info("[Minebridge] Bridge stopped")
        }
    }

    /** 新建并启动桥（更新 bridge 引用与 configRef）。 */
    private fun startBridge(server: MinecraftServer, config: MinebridgeConfig) {
        val client = MatterbridgeClient(
            baseUrl = config.matterbridge.baseUrl,
            token = config.matterbridge.token,
        )
        val b = MessageBridge(server, config, client)
        b.start()
        bridge = b

        LOGGER.info(
            "[Minebridge] Bridge started: gateway={} baseUrl={}",
            config.matterbridge.gateway,
            config.matterbridge.baseUrl
        )
    }

    /**
     * 注册聊天/加入/离开事件转发器（只做一次；重载时复用）。
     * send 走 [forward] 动态解析当前 bridge，避免重载后指向已关闭的旧桥。
     */
    private fun registerForwarders() {
        if (forwardersRegistered) return
        forwardersRegistered = true
        ChatEventForwarder(configRef, ::forward).register()
        PlayerJoinLeaveForwarder(configRef, ::forward).register()
    }

    /** 把出站消息交给当前生效的 bridge 发送（转发器 send 回调）。 */
    private fun forward(message: OutgoingMessage) {
        bridge?.send(message)
    }

    /** 配置热重载：关闭旧桥、用新配置重建。必须在 server 线程调用。 */
    private fun reloadBridge(): Boolean {
        val server = currentServer ?: return false
        if (!::configPath.isInitialized) return false
        return try {
            val newConfig = ConfigManager(configPath).load()

            // 关闭旧桥（stream/poller 线程）
            bridge?.close()
            bridge = null

            // 更新转发器读取的配置引用
            configRef.set(newConfig)

            if (newConfig.bridge.enabled) {
                startBridge(server, newConfig)
            } else {
                LOGGER.info("[Minebridge] Bridge disabled after reload, stopped")
            }
            LOGGER.info(
                "[Minebridge] Config reloaded: gateway={} enabled={}",
                newConfig.matterbridge.gateway,
                newConfig.bridge.enabled
            )
            true
        } catch (e: Exception) {
            LOGGER.error("[Minebridge] Config reload failed", e)
            // 用旧配置尝试恢复
            val fallback = configRef.get()
            if (bridge == null && fallback.bridge.enabled) {
                startBridge(server, fallback)
            }
            false
        }
    }

    /** 文件监听线程回调：调度到 server 线程执行重载。 */
    private fun onConfigFileChanged() {
        val server = currentServer
        if (server != null && server.isRunning) {
            server.execute { reloadBridge() }
        }
    }

    /** 命令触发的重载（命令回调本身在 server 线程，直接执行）。 */
    private fun reloadFromCommand(): Boolean = reloadBridge()

    /** 命令发送测试消息到网关（以管理员名义，event=msg_create）。 */
    private fun sendTestMessage(text: String) {
        val b = bridge ?: return
        val config = configRef.get()
        b.send(
            OutgoingMessage(
                gateway = config.matterbridge.gateway,
                text = text,
                username = "[server]",
                event = "msg_create",
                account = "minecraft",
                protocol = "minecraft",
                channel = "main",
            )
        )
    }

    private fun startConfigWatcher() {
        configWatcher?.close()
        val watcher = ConfigWatcher(configPath, ::onConfigFileChanged)
        watcher.start()
        configWatcher = watcher
    }
}
