/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.client

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.qsdwindows.minebridge.config.ConfigManager
import net.fabricmc.loader.api.FabricLoader

/**
 * Mod Menu 集成（可选依赖：仅在安装了 modmenu + cloth-config 时加载）。
 * 该 entrypoint 由 Mod Menu 自身调用，modmenu 缺失时本类不会被加载。
 */
class MinebridgeModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent ->
        val path = FabricLoader.getInstance().configDir.resolve("minebridge.toml")
        val manager = ConfigManager(path)
        val config = manager.load()
        ClothConfigScreen.create(parent, config) { newConfig ->
            manager.save(newConfig)
        }
    }
}
