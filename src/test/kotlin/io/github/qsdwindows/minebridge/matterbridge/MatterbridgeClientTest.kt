package io.github.qsdwindows.minebridge.matterbridge

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class MatterbridgeClientTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val receivedBodies = CopyOnWriteArrayList<String>()
    private val receivedAuth = CopyOnWriteArrayList<String?>()

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api") { exchange -> handle(exchange) }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/api"
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        receivedAuth.add(exchange.requestHeaders.getFirst("Authorization"))
        try {
            when {
                path.endsWith("/health") -> respond(exchange, 200, "OK")
                path.endsWith("/message") -> {
                    receivedBodies.add(String(exchange.requestBody.readAllBytes()))
                    respond(exchange, 200, """{"username":"api"}""")
                }
                path.endsWith("/messages") -> respond(
                    exchange, 200,
                    """[{"text":"m1","username":"u1","protocol":"discord"},{"text":"m2","username":"u2","account":"tg.bot"}]"""
                )
                path.endsWith("/stream") -> {
                    exchange.responseHeaders.add("Content-Type", "application/x-json-stream")
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.use { out ->
                        out.write("""{"text":"s1","username":"streamer","protocol":"irc"}""".toByteArray())
                        out.write('\n'.code)
                        out.flush()
                        // keep open briefly so client can read; then close
                        Thread.sleep(200)
                    }
                }
                else -> respond(exchange, 404, "not found")
            }
        } catch (e: IOException) {
            // client closed connection; expected during close()
        }
    }

    private fun respond(exchange: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun client() = MatterbridgeClient(baseUrl = baseUrl, token = "secret-token")

    @Test
    fun `sendMessage posts JSON with bearer auth and returns true on 200`() {
        val result = client().sendMessage(OutgoingMessage(gateway = "gw", text = "hi", username = "alice"))
            .get(5, TimeUnit.SECONDS)

        assertTrue(result)
        assertEquals("Bearer secret-token", receivedAuth.first())
        assertTrue(receivedBodies.first().contains("\"gateway\":\"gw\""))
        assertTrue(receivedBodies.first().contains("\"text\":\"hi\""))
    }

    @Test
    fun `healthCheck returns true on 200`() {
        assertTrue(client().healthCheck())
    }

    @Test
    fun `fetchMessages parses incoming array`() {
        val messages = client().fetchMessages()
        assertEquals(2, messages.size)
        assertEquals("m1", messages[0].text)
        assertEquals("discord", messages[0].protocol)
        assertEquals("tg.bot", messages[1].account)
    }

    @Test
    fun `openStream delivers line-delimited messages then closes`() {
        val received = CopyOnWriteArrayList<IncomingMessage>()
        val client = client()

        val handle = client.openStream { received.add(it) }
        Thread.sleep(500)
        handle.close()

        assertTrue(received.isNotEmpty())
        assertEquals("s1", received.first().text)
        assertEquals("irc", received.first().protocol)
    }

    @Test
    fun `sendMessage returns false on non-200`() {
        val bad = MatterbridgeClient(baseUrl = "http://127.0.0.1:${server.address.port}/wrong", token = "t")
        val result = bad.sendMessage(OutgoingMessage(gateway = "g", text = "t", username = "u"))
            .get(5, TimeUnit.SECONDS)
        assertFalse(result)
    }
}
