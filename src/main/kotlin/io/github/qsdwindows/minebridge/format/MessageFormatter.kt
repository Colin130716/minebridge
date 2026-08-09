/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.format

import io.github.qsdwindows.minebridge.config.FormattingConfig
import io.github.qsdwindows.minebridge.matterbridge.IncomingMessage
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/** 将 IncomingMessage 格式化为 MC 聊天组件：灰[平台] + 金用户名 + 白正文。 */
object MessageFormatter {

    fun format(message: IncomingMessage, config: FormattingConfig): Component {
        val platform = resolvePlatform(message)
        val username = FormatCodeStripper.strip(message.username ?: "?")
        val text = FormatCodeStripper.strip(message.text ?: "")

        val prefix = if (config.showPlatformPrefix) {
            "${config.prefixFormat.replace("%platform%", platform)} "
        } else {
            ""
        }

        return Component.literal("")
            .append(Component.literal(prefix).withStyle(ChatFormatting.GRAY))
            .append(Component.literal("$username: ").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE))
    }

    private fun resolvePlatform(message: IncomingMessage): String =
        message.protocol
            ?.takeIf { it.isNotBlank() }
            ?: message.account?.substringBefore('.')
            ?: "unknown"
}
