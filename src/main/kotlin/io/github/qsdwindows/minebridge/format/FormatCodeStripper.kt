/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.format

/** 去除 Minecraft 颜色/格式码（§ 后跟任意字符，含 §x 十六进制颜色序列）。 */
object FormatCodeStripper {

    private val FORMAT_CODE = Regex("§.?")

    fun strip(input: String): String = input.replace(FORMAT_CODE, "")
}
