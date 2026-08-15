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
import java.util.function.Supplier

/**
 * `/minebridge` 命令：status / reload / send。
 * 需要玩家 op 权限（level 2）。
 */
object MinebridgeCommand {

    /** 提供当前桥接状态快照；未就绪时返回 null。 */
    private var statusProvider: (() -> BridgeStatus?)? = null

    /** 触发配置热重载；返回是否成功。 */
    private var reloadAction: (() -> Boolean)? = null

    /** 发送一条测试消息到网关（同 Minebridge 玩家聊天格式）。 */
    private var sendAction: ((String) -> Unit)? = null

    /** 装配命令逻辑（由 MinebridgeMod 在 onInitialize 时调用，绑定回调）。 */
    fun setup(
        status: () -> BridgeStatus?,
        reload: () -> Boolean,
        send: (String) -> Unit,
    ) {
        statusProvider = status
        reloadAction = reload
        sendAction = send
    }

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("minebridge")
                    .requires { it.permissions().hasPermission(Permissions.COMMANDS_MODERATOR) }
                    .executes { ctx ->
                        sendFeedback(ctx, "Minebridge: 使用 /minebridge <status|reload|send <text>>")
                        Command.SINGLE_SUCCESS
                    }
                    .then(
                        Commands.literal("status")
                            .executes { ctx -> executeStatus(ctx) }
                    )
                    .then(
                        Commands.literal("reload")
                            .executes { ctx -> executeReload(ctx) }
                    )
                    .then(
                        Commands.literal("send")
                            .then(
                                Commands.argument("text", StringArgumentType.greedyString())
                                    .executes { ctx -> executeSend(ctx) }
                            )
                    )
            )
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

    private fun sendFeedback(ctx: CommandContext<CommandSourceStack>, text: String) {
        ctx.source.sendSuccess(Supplier { Component.literal(text) }, true)
    }
}
