/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.bridge

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 每个玩家是否接收 Minebridge 转发入站消息的显示偏好（默认开启）。
 *
 * 仅在实际关闭时记录一条 false 项；开启即移除（回到默认 true）。
 * 线程安全（ConcurrentHashMap），命令与 tick 可在不同线程访问。
 */
class DisplayPrefs {

    private val prefs: ConcurrentHashMap<UUID, Boolean> = ConcurrentHashMap()

    /** 设置某玩家是否接收入站消息显示。 */
    fun setEnabled(uuid: UUID, enabled: Boolean) {
        if (enabled) prefs.remove(uuid) else prefs[uuid] = false
    }

    /** 某玩家是否接收入站消息显示（默认开启）。 */
    fun isEnabled(uuid: UUID): Boolean = prefs[uuid] != false

    /** 当前已显式关闭显示的玩家数（便于测试/调试）。 */
    fun disabledCount(): Int = prefs.size
}
