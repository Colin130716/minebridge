/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.config

/**
 * 轻量 TOML 解析器（零依赖）。
 *
 * 支持：注释（#）、双引号字符串（含 \n \t \" \\ 转义）、布尔、整数、嵌套表（[a.b]）、
 * 行内注释。不支持数组/多行字符串/浮点（YAGNI）。
 *
 * 返回：表名 → (键 → 值)。表名为空字符串表示根表。
 */
object TomlParser {

    fun parse(text: String): Map<String, Map<String, Any?>> {
        val tables = LinkedHashMap<String, MutableMap<String, Any?>>()
        var currentTable = ""
        tables[currentTable] = LinkedHashMap()

        text.lineSequence().forEach { rawLine ->
            val line = stripComment(rawLine).trim()
            if (line.isEmpty()) return@forEach

            if (line.startsWith("[") && line.endsWith("]")) {
                currentTable = line.substring(1, line.length - 1).trim()
                tables.getOrPut(currentTable) { LinkedHashMap() }
            } else {
                val eq = line.indexOf('=')
                if (eq > 0) {
                    val key = line.substring(0, eq).trim()
                    val value = parseValue(line.substring(eq + 1).trim())
                    tables.getOrPut(currentTable) { LinkedHashMap() }[key] = value
                }
            }
        }
        // 剔除没有任何键的空表（含空输入时自动创建的空根表）
        tables.entries.removeIf { it.value.isEmpty() }
        return tables
    }

    /** 仅移除字符串外的 `#` 注释（字符串内的 `#` 按 TOML 规范保留）。 */
    private fun stripComment(line: String): String {
        var inString = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inString -> inString = true
                c == '"' && inString -> {
                    val prev = line.getOrNull(i - 1)
                    if (prev != '\\') inString = false
                }
                c == '#' && !inString -> return line.substring(0, i)
            }
            i++
        }
        return line
    }

    private fun parseValue(raw: String): Any? = when {
        raw.startsWith("\"") && raw.endsWith("\"") && raw.length >= 2 ->
            unescape(raw.substring(1, raw.length - 1))
        raw == "true" -> true
        raw == "false" -> false
        raw.toLongOrNull() != null -> raw.toLong()
        else -> raw
    }

    private fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    '\\' -> append('\\')
                    '"' -> append('"')
                    else -> {
                        append(c)
                        append(n)
                    }
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
