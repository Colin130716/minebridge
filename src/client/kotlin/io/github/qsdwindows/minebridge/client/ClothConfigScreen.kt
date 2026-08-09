/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.client

import io.github.qsdwindows.minebridge.config.MinebridgeConfig
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** 基于 Cloth Config 的配置界面构建器。 */
object ClothConfigScreen {

    fun create(parent: Screen?, config: MinebridgeConfig, onSave: (MinebridgeConfig) -> Unit): Screen {
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Minebridge"))

        val entry = builder.entryBuilder()
        var draft = config

        val matterbridge = builder.getOrCreateCategory(Component.literal("Matterbridge"))
        matterbridge.addEntry(
            entry.startStrField(Component.literal("Base URL"), draft.matterbridge.baseUrl)
                .setDefaultValue("http://localhost:4242/api")
                .setSaveConsumer { draft = draft.copy(matterbridge = draft.matterbridge.copy(baseUrl = it)) }
                .build()
        )
        matterbridge.addEntry(
            entry.startStrField(Component.literal("Token"), draft.matterbridge.token)
                .setDefaultValue("your-bearer-token")
                .setSaveConsumer { draft = draft.copy(matterbridge = draft.matterbridge.copy(token = it)) }
                .build()
        )
        matterbridge.addEntry(
            entry.startStrField(Component.literal("Gateway"), draft.matterbridge.gateway)
                .setDefaultValue("mygateway")
                .setSaveConsumer { draft = draft.copy(matterbridge = draft.matterbridge.copy(gateway = it)) }
                .build()
        )

        val bridge = builder.getOrCreateCategory(Component.literal("Bridge"))
        bridge.addEntry(
            entry.startBooleanToggle(Component.literal("Enabled"), draft.bridge.enabled)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(bridge = draft.bridge.copy(enabled = it)) }
                .build()
        )
        bridge.addEntry(
            entry.startBooleanToggle(Component.literal("Stream Enabled"), draft.bridge.streamEnabled)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(bridge = draft.bridge.copy(streamEnabled = it)) }
                .build()
        )
        bridge.addEntry(
            entry.startIntField(Component.literal("Poll Interval (s)"), draft.bridge.pollIntervalSeconds.toInt())
                .setDefaultValue(2)
                .setSaveConsumer { draft = draft.copy(bridge = draft.bridge.copy(pollIntervalSeconds = it.toLong())) }
                .build()
        )
        bridge.addEntry(
            entry.startIntField(Component.literal("Reconnect Delay (s)"), draft.bridge.reconnectDelaySeconds.toInt())
                .setDefaultValue(5)
                .setSaveConsumer { draft = draft.copy(bridge = draft.bridge.copy(reconnectDelaySeconds = it.toLong())) }
                .build()
        )
        bridge.addEntry(
            entry.startIntField(Component.literal("Stream Failover Threshold"), draft.bridge.streamFailoverThreshold)
                .setDefaultValue(3)
                .setSaveConsumer { draft = draft.copy(bridge = draft.bridge.copy(streamFailoverThreshold = it)) }
                .build()
        )

        val formatting = builder.getOrCreateCategory(Component.literal("Formatting"))
        formatting.addEntry(
            entry.startBooleanToggle(Component.literal("Show Platform Prefix"), draft.formatting.showPlatformPrefix)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(formatting = draft.formatting.copy(showPlatformPrefix = it)) }
                .build()
        )
        formatting.addEntry(
            entry.startStrField(Component.literal("Prefix Format"), draft.formatting.prefixFormat)
                .setDefaultValue("[%platform%]")
                .setSaveConsumer { draft = draft.copy(formatting = draft.formatting.copy(prefixFormat = it)) }
                .build()
        )

        val events = builder.getOrCreateCategory(Component.literal("Events"))
        events.addEntry(
            entry.startBooleanToggle(Component.literal("Forward Chat"), draft.events.forwardChat)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(events = draft.events.copy(forwardChat = it)) }
                .build()
        )
        events.addEntry(
            entry.startBooleanToggle(Component.literal("Forward Join"), draft.events.forwardJoin)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(events = draft.events.copy(forwardJoin = it)) }
                .build()
        )
        events.addEntry(
            entry.startBooleanToggle(Component.literal("Forward Leave"), draft.events.forwardLeave)
                .setDefaultValue(true)
                .setSaveConsumer { draft = draft.copy(events = draft.events.copy(forwardLeave = it)) }
                .build()
        )

        builder.setSavingRunnable { onSave(draft) }
        return builder.build()
    }
}
