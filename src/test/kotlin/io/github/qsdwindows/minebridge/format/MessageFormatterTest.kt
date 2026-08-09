package io.github.qsdwindows.minebridge.format

import io.github.qsdwindows.minebridge.config.FormattingConfig
import io.github.qsdwindows.minebridge.matterbridge.IncomingMessage
import net.minecraft.ChatFormatting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageFormatterTest {

    private val defaultConfig = FormattingConfig(showPlatformPrefix = true, prefixFormat = "[%platform%]")

    @Test
    fun `formats with platform prefix from protocol field`() {
        val msg = IncomingMessage(text = "hello", username = "alice", protocol = "telegram")
        val component = MessageFormatter.format(msg, defaultConfig)
        assertEquals("[telegram] alice: hello", component.string)
    }

    @Test
    fun `platform falls back to account before dot`() {
        val msg = IncomingMessage(text = "hi", username = "bob", account = "slack.myteam")
        val component = MessageFormatter.format(msg, defaultConfig)
        assertEquals("[slack] bob: hi", component.string)
    }

    @Test
    fun `platform unknown when neither protocol nor account`() {
        val msg = IncomingMessage(text = "hi", username = "bob")
        val component = MessageFormatter.format(msg, defaultConfig)
        assertEquals("[unknown] bob: hi", component.string)
    }

    @Test
    fun `no prefix when disabled`() {
        val msg = IncomingMessage(text = "hi", username = "bob", protocol = "discord")
        val component = MessageFormatter.format(
            msg,
            FormattingConfig(showPlatformPrefix = false, prefixFormat = "[%platform%]")
        )
        assertEquals("bob: hi", component.string)
    }

    @Test
    fun `custom prefix format replaces placeholder`() {
        val msg = IncomingMessage(text = "hi", username = "bob", protocol = "irc")
        val component = MessageFormatter.format(
            msg,
            FormattingConfig(showPlatformPrefix = true, prefixFormat = "<%platform%>")
        )
        assertEquals("<irc> bob: hi", component.string)
    }

    @Test
    fun `strips format codes injected in text and username`() {
        val msg = IncomingMessage(text = "sneaky §ktext", username = "§cattacker", protocol = "x")
        val component = MessageFormatter.format(msg, defaultConfig)
        assertEquals("[x] attacker: sneaky text", component.string)
    }

    @Test
    fun `applies gray gold white colors`() {
        val msg = IncomingMessage(text = "t", username = "u", protocol = "p")
        val component = MessageFormatter.format(msg, defaultConfig)
        val style = component.siblings[0].style
        assertTrue(style.color != null)
        assertEquals(ChatFormatting.GRAY.color, style.color!!.value)
    }
}
