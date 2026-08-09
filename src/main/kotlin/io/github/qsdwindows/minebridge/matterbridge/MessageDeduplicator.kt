/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import java.util.ArrayDeque

/**
 * 防回环去重器：记录最近已发送消息的摘要（userid|text），
 * 收到 IncomingMessage 时若命中则丢弃，避免 MC→Matterbridge→MC 回声。
 */
class MessageDeduplicator(private val capacity: Int = 50) {

    private val entries: ArrayDeque<String> = ArrayDeque(capacity)
    private val index: MutableSet<String> = HashSet(capacity)

    /** 记录一条已发送消息；若摘要已存在返回 false（重复）。 */
    fun mark(userid: String?, text: String): Boolean {
        val key = keyOf(userid, text)
        if (index.contains(key)) return false
        index.add(key)
        entries.addLast(key)
        if (entries.size > capacity) {
            index.remove(entries.removeFirst())
        }
        return true
    }

    /** 查询摘要是否在最近已发送记录中。 */
    fun isDuplicate(userid: String?, text: String): Boolean = index.contains(keyOf(userid, text))

    private fun keyOf(userid: String?, text: String): String = "${userid ?: ""}|$text"
}
