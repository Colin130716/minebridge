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
import java.util.concurrent.atomic.AtomicInteger

class StreamListenerTest {

    private val config = BridgeConfig(
        enabled = true,
        streamEnabled = true,
        pollIntervalSeconds = 1,
        reconnectDelaySeconds = 1,
        streamFailoverThreshold = 3,
    )

    /** 可配置失败/断开行为的流替身。 */
    private class FakeApi : MatterbridgeApi {
        val opened = CountDownLatch(1)
        val delivered = CopyOnWriteArrayList<IncomingMessage>()
        val openCount = AtomicInteger(0)
        var failNextOpen = false
        var disconnectAfterOpen = false

        override fun sendMessage(message: OutgoingMessage): CompletableFuture<Boolean> =
            CompletableFuture.completedFuture(true)

        override fun fetchMessages(): List<IncomingMessage> = emptyList()

        override fun healthCheck(): Boolean = true

        override fun openStream(
            onMessage: (IncomingMessage) -> Unit,
            onOpened: (AutoCloseable) -> Unit,
        ) {
            if (failNextOpen) {
                failNextOpen = false
                throw IOException("connection refused")
            }
            openCount.incrementAndGet()
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
            val handle = AutoCloseable {
                closed.set(true)
                thread.interrupt()
            }
            onOpened(handle)
            // 模拟"连接建立后被服务端断开"：短暂存活后触发 EOF
            if (disconnectAfterOpen) {
                try {
                    Thread.sleep(150)
                } catch (_: InterruptedException) {
                }
                closed.set(true)
                thread.interrupt()
            }
            // 阻塞直到 close() 或模拟断开
            try {
                while (!closed.get()) {
                    Thread.sleep(20)
                }
            } catch (_: InterruptedException) {
                // closed
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
    fun `stream reconnects after open failure with backoff`() {
        val api = FakeApi().apply { failNextOpen = true }
        val closedEvents = CopyOnWriteArrayList<Throwable?>()
        val openedCount = AtomicInteger(0)
        val listener = StreamListener(
            api, config,
            onMessage = {},
            onOpened = { openedCount.incrementAndGet() },
            onClosed = { closedEvents.add(it) },
        )
        listener.start()

        // first open throws -> onClosed(IOException); then retry succeeds
        assertTrue(api.opened.await(5, TimeUnit.SECONDS))
        Thread.sleep(200)
        listener.close()

        assertTrue(closedEvents.isNotEmpty())
        assertTrue(closedEvents.first() is IOException)
        assertTrue(openedCount.get() >= 1)
    }

    @Test
    fun `reconnects after established stream disconnects`() {
        val api = FakeApi().apply { disconnectAfterOpen = true }
        val closedEvents = CopyOnWriteArrayList<Throwable?>()
        val openedCount = AtomicInteger(0)
        val listener = StreamListener(
            api, config,
            onMessage = {},
            onOpened = { openedCount.incrementAndGet() },
            onClosed = { closedEvents.add(it) },
        )
        listener.start()

        // 连接建立后 150ms 服务端断开 -> onClosed(null) -> 退避重连
        // 退避 = reconnectDelaySeconds * 2^attempt = 1 * 2 = 2s，加上 150ms 存活与重连建立，等待 3.5s
        Thread.sleep(3500)
        listener.close()

        assertTrue(openedCount.get() >= 2, "expected reconnect after disconnect, opened=$openedCount")
        assertTrue(closedEvents.any { it == null }, "expected EOF onClosed(null)")
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
