/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.qsdwindows.minebridge.bridge.MessageBridge.BridgeStatus
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.Permissions
import java.util.UUID
import java.util.function.Supplier

/**
 * `/minebridge` 命令：status / reload / send / display。
 * - status / reload / send：需要 op 权限（COMMANDS_MODERATOR）
 * - display：所有玩家可用（控制是否显示 Minebridge 转发的入站消息）
 */
object MinebridgeCommand {

    /** 提供当前桥接状态快照；未就绪时返回 null。 */
    private var statusProvider: (() -> BridgeStatus?)? = null

    /** 触发配置热重载；返回是否成功。 */
    private var reloadAction: (() -> Boolean)? = null

    /** 发送一条测试消息到网关（同 Minebridge 玩家聊天格式）。 */
    private var sendAction: ((String) -> Unit)? = null

    /** 查询某玩家是否显示入站消息。 */
    private var isDisplayEnabled: ((UUID) -> Boolean)? = null

    /** 设置某玩家是否显示入站消息。 */
    private var setDisplayEnabled: ((UUID, Boolean) -> Unit)? = null

    /** 装配命令逻辑（由 MinebridgeMod 在 onInitialize 时调用，绑定回调）。 */
    fun setup(
        status: () -> BridgeStatus?,
        reload: () -> Boolean,
        send: (String) -> Unit,
        isDisplayEnabled: (UUID) -> Boolean,
        setDisplayEnabled: (UUID, Boolean) -> Unit,
    ) {
        statusProvider = status
        reloadAction = reload
        sendAction = send
        this.isDisplayEnabled = isDisplayEnabled
        this.setDisplayEnabled = setDisplayEnabled
    }

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val root = Commands.literal("minebridge")
                .executes { ctx ->
                    sendFeedback(ctx, "Minebridge: 使用 /minebridge <status|reload|send <text>|display [on|off]>")
                    Command.SINGLE_SUCCESS
                }
                // 需要 op 权限的子命令，逐个 requires（根命令对所有人可见）
                .then(
                    Commands.literal("status")
                        .requires { it.permissions().hasPermission(Permissions.COMMANDS_MODERATOR) }
                        .executes { ctx -> executeStatus(ctx) }
                )
                .then(
                    Commands.literal("reload")
                        .requires { it.permissions().hasPermission(Permissions.COMMANDS_MODERATOR) }
                        .executes { ctx -> executeReload(ctx) }
                )
                .then(
                    Commands.literal("send")
                        .requires { it.permissions().hasPermission(Permissions.COMMANDS_MODERATOR) }
                        .then(
                            Commands.argument("text", StringArgumentType.greedyString())
                                .executes { ctx -> executeSend(ctx) }
                        )
                )
                // display：所有玩家可用
                .then(
                    Commands.literal("display")
                        .executes { ctx -> executeDisplayToggle(ctx) }
                        .then(
                            Commands.literal("on")
                                .executes { ctx -> executeDisplaySet(ctx, true) }
                        )
                        .then(
                            Commands.literal("off")
                                .executes { ctx -> executeDisplaySet(ctx, false) }
                        )
                )
            dispatcher.register(root)
        }
    }

    private fun executeStatus(ctx: CommandContext<CommandSourceStack>): Int {
        val status = statusProvider?.invoke()
        if (status == null) {
            sendFeedback(ctx, "Minebridge: 桥未启动（配置禁用或服务器未就绪）")
            return Command.SINGLE_SUCCESS
        }
        sendFeedback(
            ctx,
            "Minebridge status: gateway=${status.gateway} baseUrl=${status.baseUrl} " +
                "enabled=${status.enabled} receiveMode=${status.receiveMode} " +
                "streamFailures=${status.streamFailures} queue=${status.onboardingQueueSize}"
        )
        return Command.SINGLE_SUCCESS
    }

    private fun executeReload(ctx: CommandContext<CommandSourceStack>): Int {
        val ok = reloadAction?.invoke() ?: false
        sendFeedback(ctx, if (ok) "Minebridge: 配置已热重载" else "Minebridge: 重载失败（见服务器日志）")
        return Command.SINGLE_SUCCESS
    }

    private fun executeSend(ctx: CommandContext<CommandSourceStack>): Int {
        val text = StringArgumentType.getString(ctx, "text")
        val action = sendAction
        if (action == null) {
            sendFeedback(ctx, "Minebridge: 桥未启动，无法发送")
            return Command.SINGLE_SUCCESS
        }
        action(text)
        sendFeedback(ctx, "Minebridge: 测试消息已发送")
        return Command.SINGLE_SUCCESS
    }

    private fun executeDisplayToggle(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            sendFeedback(ctx, "Minebridge: 该命令仅玩家可用")
            return Command.SINGLE_SUCCESS
        }
        val current = isDisplayEnabled?.invoke(player.uuid) ?: true
        val next = !current
        setDisplayEnabled?.invoke(player.uuid, next)
        sendFeedback(ctx, "Minebridge: 已${if (next) "开启" else "关闭"}转发消息显示")
        return Command.SINGLE_SUCCESS
    }

    private fun executeDisplaySet(ctx: CommandContext<CommandSourceStack>, enabled: Boolean): Int {
        val player = ctx.source.player ?: run {
            sendFeedback(ctx, "Minebridge: 该命令仅玩家可用")
            return Command.SINGLE_SUCCESS
        }
        setDisplayEnabled?.invoke(player.uuid, enabled)
        sendFeedback(ctx, "Minebridge: 已${if (enabled) "开启" else "关闭"}转发消息显示")
        return Command.SINGLE_SUCCESS
    }

    private fun sendFeedback(ctx: CommandContext<CommandSourceStack>, text: String) {
        ctx.source.sendSuccess(Supplier { Component.literal(text) }, true)
    }
}
