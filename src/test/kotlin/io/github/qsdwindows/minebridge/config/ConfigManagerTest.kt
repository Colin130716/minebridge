package io.github.qsdwindows.minebridge.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigManagerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `creates default config when file missing`() {
        val path = tempDir.resolve("minebridge.toml")
        val config = ConfigManager(path).load()

        assertTrue(Files.exists(path), "default config file should be written")
        assertEquals("http://localhost:4242/api", config.matterbridge.baseUrl)
        assertEquals("mygateway", config.matterbridge.gateway)
        assertTrue(config.bridge.enabled)
        assertTrue(config.bridge.streamEnabled)
        assertEquals(2L, config.bridge.pollIntervalSeconds)
        assertEquals(5L, config.bridge.reconnectDelaySeconds)
        assertEquals(3, config.bridge.streamFailoverThreshold)
        assertTrue(config.formatting.showPlatformPrefix)
        assertEquals("[%platform%]", config.formatting.prefixFormat)
        assertTrue(config.events.forwardChat)
        assertTrue(config.events.forwardJoin)
        assertTrue(config.events.forwardLeave)
    }

    @Test
    fun `loads values from existing config file`() {
        val path = tempDir.resolve("minebridge.toml")
        Files.writeString(
            path,
            """
            [matterbridge]
            baseUrl = "http://127.0.0.1:9999/api"
            token = "tok123"
            gateway = "prod-gw"

            [bridge]
            enabled = true
            streamEnabled = false
            pollIntervalSeconds = 7
            reconnectDelaySeconds = 9
            streamFailoverThreshold = 5

            [formatting]
            showPlatformPrefix = false
            prefixFormat = "<%platform%>"

            [events]
            forwardChat = false
            forwardJoin = true
            forwardLeave = false
            """.trimIndent()
        )

        val config = ConfigManager(path).load()

        assertEquals("http://127.0.0.1:9999/api", config.matterbridge.baseUrl)
        assertEquals("tok123", config.matterbridge.token)
        assertEquals("prod-gw", config.matterbridge.gateway)
        assertFalse(config.bridge.streamEnabled)
        assertEquals(7L, config.bridge.pollIntervalSeconds)
        assertEquals(9L, config.bridge.reconnectDelaySeconds)
        assertEquals(5, config.bridge.streamFailoverThreshold)
        assertFalse(config.formatting.showPlatformPrefix)
        assertEquals("<%platform%>", config.formatting.prefixFormat)
        assertFalse(config.events.forwardChat)
        assertTrue(config.events.forwardJoin)
        assertFalse(config.events.forwardLeave)
    }

    @Test
    fun `missing keys fall back to defaults`() {
        val path = tempDir.resolve("minebridge.toml")
        Files.writeString(path, "[matterbridge]\nbaseUrl = \"http://x/api\"\n")

        val config = ConfigManager(path).load()

        assertEquals("http://x/api", config.matterbridge.baseUrl)
        assertEquals("your-bearer-token", config.matterbridge.token)
        assertTrue(config.bridge.enabled)
    }

    @Test
    fun `saveDefault returns path to written file`() {
        val path = tempDir.resolve("sub").resolve("minebridge.toml")
        val written = ConfigManager(path).saveDefault()
        assertEquals(path, written)
        assertTrue(Files.exists(path))
    }
}
