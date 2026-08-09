/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object MinebridgeMod : ModInitializer {
    const val MOD_ID: String = "minebridge"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        LOGGER.info("[Minebridge] Initialized (v1.0.0)")
    }
}
