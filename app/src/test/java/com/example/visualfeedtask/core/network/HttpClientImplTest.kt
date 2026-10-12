package com.example.visualfeedtask.core.network

import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

class HttpClientImplTest {
    @get:Rule
    val server = MockWebServer()

    @Test
    fun `performs GET and returns the response body`() = runTest {
        server.enqueue(MockResponse().setBody("[9129911,9129199,9127761]"))
        val client = HttpClientImpl(OkHttpClient(), Dispatchers.IO)

        assertEquals(
            "[9129911,9129199,9127761]",
            client.get(server.url("/v0/topstories.json").toString()),
        )
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals("GET", request?.method)
        assertEquals("/v0/topstories.json", request?.path)
    }

    @Test
    fun `prepares request on injected dispatcher and restores caller thread`() = runTest {
        val requestThread = AtomicReference<Thread>()
        val transport = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun callStart(call: Call) {
                    requestThread.set(Thread.currentThread())
                }
            })
            .build()
        server.enqueue(MockResponse().setBody("[]"))
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val callerThread = Thread.currentThread()
            HttpClientImpl(transport, dispatcher).get(server.url("/").toString())

            assertNotSame(callerThread, requestThread.get())
            assertSame(callerThread, Thread.currentThread())
        }
    }

    @Test
    fun `propagates unsuccessful HTTP status instead of returning error body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Unavailable"))
        val client = HttpClientImpl(OkHttpClient(), Dispatchers.IO)

        try {
            client.get(server.url("/").toString())
            fail("Expected an HTTP failure")
        } catch (failure: HttpException) {
            assertEquals(503, failure.statusCode)
        }
    }

    @Test
    fun `propagates transport failures`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val transport = OkHttpClient.Builder().retryOnConnectionFailure(false).build()

        try {
            HttpClientImpl(transport, Dispatchers.IO).get(server.url("/").toString())
            fail("Expected a transport failure")
        } catch (failure: IOException) {
            assertNotSame(HttpException::class.java, failure.javaClass)
        }
    }

    @Test
    fun `caller cancellation cancels the in flight HTTP call`() = runTest {
        val cancelledCall = CompletableDeferred<Call>()
        val transport = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun canceled(call: Call) {
                    cancelledCall.complete(call)
                }
            })
            .build()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = HttpClientImpl(transport, Dispatchers.IO)
        val request = async { client.get(server.url("/").toString()) }
        withContext(Dispatchers.IO) {
            checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        }

        request.cancel()
        request.join()

        assertTrue(cancelledCall.await().isCanceled())
        assertTrue(request.isCancelled)
    }
}
