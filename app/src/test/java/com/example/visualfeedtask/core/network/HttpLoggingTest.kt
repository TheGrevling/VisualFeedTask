package com.example.visualfeedtask.core.network

import com.example.visualfeedtask.core.di.NetworkModule
import java.io.IOException
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

class HttpLoggingTest {
    @get:Rule
    val server = MockWebServer()

    private val messages = mutableListOf<String>()
    private val interceptor = NetworkModule.provideHttpLoggingInterceptor(
        HttpLoggingInterceptor.Logger { messages += it },
    )

    @Test
    fun `logs URL status headers and body without consuming response or exposing credentials`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("X-Request-Id", "request-123")
                .setHeader("Set-Cookie", "private-session")
                .setBody("[9129911,9129199]"),
        )
        val url = server.url("/v0/topstories.json")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer private-token")
            .header("Cookie", "private-cookie")
            .build()

        NetworkModule.provideOkHttpClient(interceptor).newCall(request).execute().use {
            assertEquals("[9129911,9129199]", it.body?.string())
        }

        val log = messages.joinToString("\n")
        assertTrue(log.contains("--> GET $url"))
        assertTrue(log.contains("<-- 200"))
        assertTrue(log.contains("X-Request-Id: request-123", ignoreCase = true))
        assertTrue(log.contains("Content-Type: application/json", ignoreCase = true))
        assertTrue(log.contains("[9129911,9129199]"))
        assertFalse(log.contains("private-token"))
        assertFalse(log.contains("private-cookie"))
        assertFalse(log.contains("private-session"))
    }

    @Test
    fun `logs failed transport calls`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val client = NetworkModule.provideOkHttpClient(interceptor).newBuilder()
            .retryOnConnectionFailure(false)
            .build()

        try {
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            fail("Expected a transport failure")
        } catch (expected: IOException) {
            assertTrue(messages.any { it.contains("HTTP FAILED") })
        }
    }

    @Test
    fun `disabled logging emits no messages`() {
        interceptor.level = HttpLoggingInterceptor.Level.NONE
        server.enqueue(MockResponse().setBody("[]"))

        NetworkModule.provideOkHttpClient(interceptor)
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute().close()

        assertTrue(messages.isEmpty())
    }
}
