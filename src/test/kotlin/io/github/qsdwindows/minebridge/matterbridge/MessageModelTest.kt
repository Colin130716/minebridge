package io.github.qsdwindows.minebridge.matterbridge

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MessageModelTest {
    private val gson = Gson()

    @Test
    fun `serializes OutgoingMessage with all fields to expected JSON`() {
        val msg = OutgoingMessage(
            gateway = "mygateway",
            text = "hello",
            username = "alice",
            avatar = "http://a.png",
            event = "msg_create",
            account = "minecraft",
            protocol = "minecraft",
            channel = "main",
            userid = "uuid-123",
        )

        val json = gson.toJson(msg)

        assertEquals(
            "{\"gateway\":\"mygateway\",\"text\":\"hello\",\"username\":\"alice\",\"avatar\":\"http://a.png\"," +
                "\"event\":\"msg_create\",\"account\":\"minecraft\",\"protocol\":\"minecraft\",\"channel\":\"main\"," +
                "\"userid\":\"uuid-123\"}",
            json
        )
    }

    @Test
    fun `serializes OutgoingMessage with defaults when optional fields null`() {
        val msg = OutgoingMessage(gateway = "g", text = "t", username = "u")
        val json = gson.toJson(msg)
        assertEquals("{\"gateway\":\"g\",\"text\":\"t\",\"username\":\"u\"}", json)
    }

    @Test
    fun `deserializes IncomingMessage from matterbridge JSON`() {
        val json = """
            {
              "avatar": "https://gravatar.com/x.jpg",
              "event": "msg_create",
              "gateway": "mygateway",
              "text": "Testing, testing, 1-2-3.",
              "username": "alice",
              "account": "slack.myteam",
              "channel": "test-channel",
              "id": "slack 1541361213.030700",
              "parent_id": "slack 1541361213.030700",
              "protocol": "slack",
              "timestamp": "1541361213.030700",
              "userid": "U4MCXJKNC"
            }
        """.trimIndent()

        val msg = gson.fromJson(json, IncomingMessage::class.java)

        assertEquals("msg_create", msg.event)
        assertEquals("mygateway", msg.gateway)
        assertEquals("Testing, testing, 1-2-3.", msg.text)
        assertEquals("alice", msg.username)
        assertEquals("slack.myteam", msg.account)
        assertEquals("test-channel", msg.channel)
        assertEquals("slack 1541361213.030700", msg.id)
        assertEquals("slack 1541361213.030700", msg.parentId)
        assertEquals("slack", msg.protocol)
        assertEquals("1541361213.030700", msg.timestamp)
        assertEquals("U4MCXJKNC", msg.userid)
    }

    @Test
    fun `deserializes IncomingMessage with missing optional fields as null`() {
        val msg = gson.fromJson("{\"text\":\"hi\",\"username\":\"bob\"}", IncomingMessage::class.java)
        assertEquals("hi", msg.text)
        assertEquals("bob", msg.username)
        assertNull(msg.account)
        assertNull(msg.event)
        assertNull(msg.parentId)
    }
}
