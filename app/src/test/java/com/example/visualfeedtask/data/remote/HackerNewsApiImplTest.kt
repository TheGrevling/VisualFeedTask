package com.example.visualfeedtask.data.remote

import com.example.visualfeedtask.core.network.HttpClient
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.Strictness
import java.io.IOException
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class HackerNewsApiImplTest {
    private val gson = Gson().newBuilder().setStrictness(Strictness.STRICT).create()

    @Test
    fun `fetches top and new ID arrays and items from exact Hacker News URLs`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeHttpClient(dispatcher)
        val api = HackerNewsApiImpl(client, gson, dispatcher)
        client.responses["https://hacker-news.firebaseio.com/v0/topstories.json"] =
            "[9129911,9129199,9127761]"
        client.responses["https://hacker-news.firebaseio.com/v0/newstories.json"] = "[12,11]"
        client.responses["https://hacker-news.firebaseio.com/v0/item/9.json"] =
            """{"id":9,"type":"story","by":"alice","time":1234,"title":"A story","score":12}"""
        client.responses["https://hacker-news.firebaseio.com/v0/item/404.json"] = "null"

        assertEquals(listOf(9129911L, 9129199L, 9127761L), api.getTopStoryIds())
        assertEquals(listOf(12L, 11L), api.getNewStoryIds())
        val item = api.getItem(9L)
        assertEquals(9L, item?.id)
        assertEquals("alice", item?.author)
        assertEquals(1234L, item?.submittedAtEpochSeconds)
        assertNull(api.getItem(404L))
        assertEquals(client.responses.keys.toList(), client.requests)
    }

    @Test
    fun `returns all 500 IDs in API order without truncation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeHttpClient(dispatcher)
        val api = HackerNewsApiImpl(client, gson, dispatcher)
        val ids = (500L downTo 1L).toList()
        client.responses["https://hacker-news.firebaseio.com/v0/topstories.json"] = gson.toJson(ids)

        assertEquals(ids, api.getTopStoryIds())
    }

    @Test
    fun `propagates malformed and null ID responses rather than returning defaults`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = FakeHttpClient(dispatcher)
        val api = HackerNewsApiImpl(client, gson, dispatcher)
        for (json in listOf("null", """{"error":"unavailable"}""", "[invalid", "[1,null]")) {
            client.responses["https://hacker-news.firebaseio.com/v0/topstories.json"] = json
            try {
                api.getTopStoryIds()
                fail("Expected JSON failure for $json")
            } catch (expected: JsonParseException) {
                // Expected invalid response.
            }
        }
    }

    @Test
    fun `propagates HTTP client failures`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = object : HttpClient {
            override suspend fun get(url: String): String = throw IOException("Unavailable")
        }
        val api = HackerNewsApiImpl(client, gson, dispatcher)

        try {
            api.getTopStoryIds()
            fail("Expected network failure")
        } catch (failure: IOException) {
            assertEquals("Unavailable", failure.message)
        }
    }
}

private class FakeHttpClient(
    private val expectedDispatcher: CoroutineDispatcher,
) : HttpClient {
    val responses = linkedMapOf<String, String>()
    val requests = mutableListOf<String>()

    override suspend fun get(url: String): String {
        assertSame(expectedDispatcher, coroutineContext[ContinuationInterceptor])
        requests += url
        return responses.getValue(url)
    }
}
