/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.config

data class MatterbridgeConfig(
    val baseUrl: String = "http://localhost:4242/api",
    val token: String = "your-bearer-token",
    val gateway: String = "mygateway",
)

data class BridgeConfig(
    val enabled: Boolean = true,
    val streamEnabled: Boolean = true,
    val pollIntervalSeconds: Long = 2,
    val reconnectDelaySeconds: Long = 5,
    val streamFailoverThreshold: Int = 3,
)

data class FormattingConfig(
    val showPlatformPrefix: Boolean = true,
    val prefixFormat: String = "[%platform%]",
)

data class EventsConfig(
    val forwardChat: Boolean = true,
    val forwardJoin: Boolean = true,
    val forwardLeave: Boolean = true,
)

data class MinebridgeConfig(
    val matterbridge: MatterbridgeConfig = MatterbridgeConfig(),
    val bridge: BridgeConfig = BridgeConfig(),
    val formatting: FormattingConfig = FormattingConfig(),
    val events: EventsConfig = EventsConfig(),
)

/** 首次启动自动生成的默认配置内容。 */
val DEFAULT_CONFIG_TOML: String = """
    # Minebridge 配置文件
    # 修改后需重启服务器生效（首版不做热重载）。

    [matterbridge]
    baseUrl = "http://localhost:4242/api"   # Matterbridge API 基地址（含 /api）
    token = "your-bearer-token"             # Bearer token
    gateway = "mygateway"                   # matterbridge.toml 中的网关名

    [bridge]
    enabled = true                          # 总开关
    streamEnabled = true                    # 优先使用 /api/stream 长连接
    pollIntervalSeconds = 2                 # 轮询回退间隔（秒）
    reconnectDelaySeconds = 5               # stream 重连基础延迟（指数退避）
    streamFailoverThreshold = 3             # stream 连续失败 N 次后切换轮询

    [formatting]
    showPlatformPrefix = true               # 是否显示 [平台] 前缀
    prefixFormat = "[%platform%]"           # 前缀格式（%platform% 为占位符）

    [events]
    forwardChat = true                      # 转发聊天
    forwardJoin = true                      # 转发加入
    forwardLeave = true                     # 转发离开
""".trimIndent()
