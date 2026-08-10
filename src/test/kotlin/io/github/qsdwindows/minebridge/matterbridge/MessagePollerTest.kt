package io.github.qsdwindows.minebridge.matterbridge

import io.github.qsdwindows.minebridge.config.BridgeConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

class MessagePollerTest {

    private val config = BridgeConfig(
        enabled = true,
        streamEnabled = true,
        pollIntervalSeconds = 1,
        reconnectDelaySeconds = 1,
        streamFailoverThreshold = 3,
    )

    /** batch 可变：可在同一 poller 的多次轮询间模拟消息增量。 */
    private class FakeApi(var batch: List<IncomingMessage>) : MatterbridgeApi {
        override fun sendMessage(message: OutgoingMessage): CompletableFuture<Boolean> =
            CompletableFuture.completedFuture(true)

        override fun fetchMessages(): List<IncomingMessage> = batch

        override fun healthCheck(): Boolean = true

        override fun openStream(
            onMessage: (IncomingMessage) -> Unit,
            onOpened: (AutoCloseable) -> Unit,
        ) = throw UnsupportedOperationException("poller test does not use stream")
    }

    @Test
    fun `repeated poll of same batch delivers only once`() {
        val batch = listOf(
            IncomingMessage(id = "discord 1", text = "m1", username = "u1", protocol = "discord"),
            IncomingMessage(id = "discord 2", text = "m2", username = "u2", protocol = "discord"),
        )
        val delivered = CopyOnWriteArrayList<IncomingMessage>()
        val poller = MessagePoller(FakeApi(batch), config) { delivered.add(it) }
        poller.start()

        poller.pollOnce()
        poller.pollOnce()
        poller.pollOnce()
        poller.close()

        assertEquals(2, delivered.size, "same batch must not be redelivered")
        assertEquals("m1", delivered[0].text)
        assertEquals("m2", delivered[1].text)
    }

    @Test
    fun `new messages appended to batch are delivered`() {
        val api = FakeApi(
            listOf(IncomingMessage(id = "discord 1", text = "old", username = "u1", protocol = "discord"))
        )
        val delivered = CopyOnWriteArrayList<IncomingMessage>()
        val poller = MessagePoller(api, config) { delivered.add(it) }
        poller.start()

        poller.pollOnce()
        poller.pollOnce()
        assertEquals(1, delivered.size, "old batch delivered once")

        api.batch = listOf(
            IncomingMessage(id = "discord 1", text = "old", username = "u1", protocol = "discord"),
            IncomingMessage(id = "discord 2", text = "new", username = "u2", protocol = "discord"),
        )
        poller.pollOnce()
        poller.close()

        assertEquals(2, delivered.size, "only the new message should be delivered")
        assertEquals("new", delivered[1].text)
    }

    @Test
    fun `messages without id fall back to gateway-username-text key`() {
        val batch = listOf(
            IncomingMessage(text = "hi", username = "alice", gateway = "mygateway", protocol = "discord"),
        )
        val delivered = CopyOnWriteArrayList<IncomingMessage>()
        val poller = MessagePoller(FakeApi(batch), config) { delivered.add(it) }
        poller.start()

        poller.pollOnce()
        poller.pollOnce()
        poller.close()

        assertEquals(1, delivered.size, "id-less message deduped by fallback key")
    }

    @Test
    fun `pollOnce before start is a no-op`() {
        val batch = listOf(IncomingMessage(id = "x", text = "m", username = "u", protocol = "d"))
        val delivered = CopyOnWriteArrayList<IncomingMessage>()
        val poller = MessagePoller(FakeApi(batch), config) { delivered.add(it) }
        poller.pollOnce()
        assertEquals(0, delivered.size)
        poller.close()
    }
}
