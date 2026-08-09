package io.github.qsdwindows.minebridge.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TomlParserTest {
    @Test
    fun `parses nested tables with comments and all supported types`() {
        val toml = """
            # header comment
            [matterbridge]
            baseUrl = "http://localhost:4242/api"
            token = "secret-token"
            gateway = "mygateway"

            [bridge]
            enabled = true
            pollIntervalSeconds = 2
        """.trimIndent()

        val result = TomlParser.parse(toml)

        assertEquals("http://localhost:4242/api", result["matterbridge"]?.get("baseUrl"))
        assertEquals("secret-token", result["matterbridge"]?.get("token"))
        assertEquals("mygateway", result["matterbridge"]?.get("gateway"))
        assertEquals(true, result["bridge"]?.get("enabled"))
        assertEquals(2L, result["bridge"]?.get("pollIntervalSeconds"))
    }

    @Test
    fun `inline comments after values are stripped`() {
        val toml = "gateway = \"gw\"  # comment here"
        assertEquals("gw", TomlParser.parse(toml)[""]?.get("gateway"))
    }

    @Test
    fun `escaped quotes and backslashes in strings`() {
        val toml = "token = \"a\\\"b\\\\c\""
        assertEquals("a\"b\\c", TomlParser.parse(toml)[""]?.get("token"))
    }

    @Test
    fun `booleans and integers parse to typed values`() {
        val toml = "a = true\nb = false\nc = 42"
        val result = TomlParser.parse(toml)[""]!!
        assertTrue(result["a"] as Boolean)
        assertFalse(result["b"] as Boolean)
        assertEquals(42L, result["c"])
    }

    @Test
    fun `empty input returns map with empty root table`() {
        assertTrue(TomlParser.parse("").isEmpty())
    }
}
