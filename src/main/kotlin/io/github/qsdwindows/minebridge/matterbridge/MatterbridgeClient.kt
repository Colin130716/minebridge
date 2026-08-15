/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Copyright (c) 2026 qsdwindows
 */
package io.github.qsdwindows.minebridge.matterbridge

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/** 基于 JDK HttpClient 的 Matterbridge API 实现。 */
class MatterbridgeClient(
    baseUrl: String,
    private val token: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val gson: Gson = Gson(),
) : MatterbridgeApi {

    private val baseUrl: String = baseUrl.trimEnd('/')

    override fun sendMessage(message: OutgoingMessage): CompletableFuture<Boolean> {
        val request = newRequest("/message")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(message)))
            .build()
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenApply { it.statusCode() == 200 }
            .exceptionally { false }
    }

    override fun fetchMessages(): List<IncomingMessage> {
        val request = newRequest("/messages").GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return emptyList()
        val type = object : TypeToken<List<IncomingMessage>>() {}.type
        return gson.fromJson(response.body(), type) ?: emptyList()
    }

    override fun healthCheck(): Boolean {
        val request = newRequest("/health").GET().build()
        return try {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }

    override fun openStream(
        onMessage: (IncomingMessage) -> Unit,
        onOpened: (AutoCloseable) -> Unit,
    ) {
        val request = newRequest("/stream").GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw IOException("stream HTTP ${response.statusCode()}")
        }
        val input: InputStream = response.body()
        val closed = AtomicBoolean(false)
        val handle = AutoCloseable {
            closed.set(true)
            try {
                input.close()
            } catch (_: Exception) {
            }
        }
        onOpened(handle)
        try {
            val reader = input.bufferedReader()
            while (!closed.get()) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val msg = gson.fromJson(line, IncomingMessage::class.java) ?: continue
                onMessage(msg)
            }
        } finally {
            try {
                input.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun newRequest(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json, application/x-json-stream")
            .header("User-Agent", "minebridge/1.0.1")
            .timeout(Duration.ofSeconds(30))
}
