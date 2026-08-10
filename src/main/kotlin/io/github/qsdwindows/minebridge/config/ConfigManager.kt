/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/** 负责配置文件的加载与默认配置生成。 */
class ConfigManager(private val configPath: Path) {

    fun load(): MinebridgeConfig {
        if (!Files.exists(configPath)) {
            saveDefault()
        }
        return try {
            val text = Files.readString(configPath)
            val tables = TomlParser.parse(text)
            parseTables(tables)
        } catch (e: Exception) {
            LOGGER.warn("[Minebridge] Failed to read config {}: {}. Falling back to defaults", configPath, e.message)
            DEFAULT_CONFIG
        }
    }

    private fun parseTables(tables: Map<String, Map<String, Any?>>): MinebridgeConfig {
        val mb = tables["matterbridge"] ?: emptyMap()
        val br = tables["bridge"] ?: emptyMap()
        val fmt = tables["formatting"] ?: emptyMap()
        val ev = tables["events"] ?: emptyMap()

        return MinebridgeConfig(
            matterbridge = MatterbridgeConfig(
                baseUrl = mb.str("baseUrl") ?: DEFAULT_CONFIG.matterbridge.baseUrl,
                token = mb.str("token") ?: DEFAULT_CONFIG.matterbridge.token,
                gateway = mb.str("gateway") ?: DEFAULT_CONFIG.matterbridge.gateway,
            ),
            bridge = BridgeConfig(
                enabled = br.bool("enabled", DEFAULT_CONFIG.bridge.enabled),
                streamEnabled = br.bool("streamEnabled", DEFAULT_CONFIG.bridge.streamEnabled),
                pollIntervalSeconds = br.long("pollIntervalSeconds", DEFAULT_CONFIG.bridge.pollIntervalSeconds),
                reconnectDelaySeconds = br.long("reconnectDelaySeconds", DEFAULT_CONFIG.bridge.reconnectDelaySeconds),
                streamFailoverThreshold = br.int("streamFailoverThreshold", DEFAULT_CONFIG.bridge.streamFailoverThreshold),
            ),
            formatting = FormattingConfig(
                showPlatformPrefix = fmt.bool("showPlatformPrefix", DEFAULT_CONFIG.formatting.showPlatformPrefix),
                prefixFormat = fmt.str("prefixFormat") ?: DEFAULT_CONFIG.formatting.prefixFormat,
            ),
            events = EventsConfig(
                forwardChat = ev.bool("forwardChat", DEFAULT_CONFIG.events.forwardChat),
                forwardJoin = ev.bool("forwardJoin", DEFAULT_CONFIG.events.forwardJoin),
                forwardLeave = ev.bool("forwardLeave", DEFAULT_CONFIG.events.forwardLeave),
            ),
        )
    }

    fun saveDefault(): Path {
        Files.createDirectories(configPath.parent)
        Files.writeString(configPath, DEFAULT_CONFIG_TOML)
        return configPath
    }

    fun save(config: MinebridgeConfig): Path {
        Files.createDirectories(configPath.parent)
        Files.writeString(configPath, toToml(config))
        return configPath
    }

    private fun toToml(config: MinebridgeConfig): String = buildString {
        appendLine("# Minebridge 配置文件（由配置界面保存）")
        appendLine()
        appendLine("[matterbridge]")
        appendLine("baseUrl = \"${escape(config.matterbridge.baseUrl)}\"")
        appendLine("token = \"${escape(config.matterbridge.token)}\"")
        appendLine("gateway = \"${escape(config.matterbridge.gateway)}\"")
        appendLine()
        appendLine("[bridge]")
        appendLine("enabled = ${config.bridge.enabled}")
        appendLine("streamEnabled = ${config.bridge.streamEnabled}")
        appendLine("pollIntervalSeconds = ${config.bridge.pollIntervalSeconds}")
        appendLine("reconnectDelaySeconds = ${config.bridge.reconnectDelaySeconds}")
        appendLine("streamFailoverThreshold = ${config.bridge.streamFailoverThreshold}")
        appendLine()
        appendLine("[formatting]")
        appendLine("showPlatformPrefix = ${config.formatting.showPlatformPrefix}")
        appendLine("prefixFormat = \"${escape(config.formatting.prefixFormat)}\"")
        appendLine()
        appendLine("[events]")
        appendLine("forwardChat = ${config.events.forwardChat}")
        appendLine("forwardJoin = ${config.events.forwardJoin}")
        appendLine("forwardLeave = ${config.events.forwardLeave}")
    }

    private fun escape(value: String): String = buildString {
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    private fun Map<String, Any?>.str(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.bool(key: String, default: Boolean): Boolean = (this[key] as? Boolean) ?: default
    private fun Map<String, Any?>.long(key: String, default: Long): Long = (this[key] as? Long) ?: default
    private fun Map<String, Any?>.int(key: String, default: Int): Int = (this[key] as? Long)?.toInt() ?: default

    companion object {
        val DEFAULT_CONFIG: MinebridgeConfig = MinebridgeConfig()
        private val LOGGER: Logger = LoggerFactory.getLogger(ConfigManager::class.java)
    }
}
