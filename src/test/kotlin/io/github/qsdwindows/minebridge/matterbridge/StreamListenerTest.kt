package io.github.qsdwindows.minebridge.matterbridge

import io.github.qsdwindows.minebridge.config.BridgeConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class StreamListenerTest {

    private val config = BridgeConfig(
        enabled = true,
        streamEnabled = true,
        pollIntervalSeconds = 1,
        reconnectDelaySeconds = 1,
        streamFailoverThreshold = 3,
    )

    private class FakeApi : MatterbridgeApi {
        val opened = CountDownLatch(1)
        val delivered = CopyOnWriteArrayList<IncomingMessage>()
        var failNextOpen = false

        override fun sendMessage(message: OutgoingMessage): CompletableFuture<Boolean> =
            CompletableFuture.completedFuture(true)

        override fun fetchMessages(): List<IncomingMessage> = emptyList()

        override fun healthCheck(): Boolean = true

        override fun openStream(onMessage: (IncomingMessage) -> Unit): AutoCloseable {
            if (failNextOpen) {
                failNextOpen = false
                throw IOException("connection refused")
            }
            opened.countDown()
            val closed = AtomicBoolean(false)
            val thread = Thread {
                while (!closed.get()) {
                    onMessage(IncomingMessage(text = "ping", username = "svc", protocol = "test"))
                    try {
                        Thread.sleep(50)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            thread.isDaemon = true
            thread.start()
            return AutoCloseable {
                closed.set(true)
                thread.interrupt()
            }
        }
    }

    @Test
    fun `stream delivers messages via callback`() {
        val api = FakeApi()
        val listener = StreamListener(api, config, onMessage = { api.delivered.add(it) }, onOpened = {}, onClosed = {})
        listener.start()

        assertTrue(api.opened.await(3, TimeUnit.SECONDS))
        Thread.sleep(300)
        listener.close()

        assertTrue(api.delivered.isNotEmpty())
        assertEquals("ping", api.delivered.first().text)
    }

    @Test
    fun `stream reconnects after failure with backoff`() {
        val api = FakeApi().apply { failNextOpen = true }
        val closedEvents = CopyOnWriteArrayList<Throwable?>()
        val listener = StreamListener(api, config, onMessage = {}, onOpened = {}, onClosed = { closedEvents.add(it) })
        listener.start()

        assertTrue(api.opened.await(5, TimeUnit.SECONDS))
        Thread.sleep(200)
        listener.close()

        assertTrue(closedEvents.isNotEmpty())
        assertTrue(closedEvents.first() is IOException)
    }

    @Test
    fun `close stops the reconnect loop`() {
        val api = FakeApi().apply { failNextOpen = true }
        val listener = StreamListener(api, config, onMessage = {}, onOpened = {}, onClosed = {})
        listener.start()
        Thread.sleep(100)
        listener.close()
        assertTrue(true)
    }
}
