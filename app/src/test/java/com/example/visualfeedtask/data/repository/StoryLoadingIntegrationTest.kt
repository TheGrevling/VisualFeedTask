package com.example.visualfeedtask.data.repository

import com.example.visualfeedtask.core.network.HttpClientImpl
import com.example.visualfeedtask.data.local.StoryDao
import com.example.visualfeedtask.data.local.StoryEntity
import com.example.visualfeedtask.data.remote.HackerNewsApiImpl
import com.example.visualfeedtask.testing.MainDispatcherRule
import com.example.visualfeedtask.ui.feed.FeedUiState
import com.example.visualfeedtask.ui.feed.FeedViewModel
import com.google.gson.Gson
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StoryLoadingIntegrationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val server = MockWebServer()

    @Test
    fun `viewModel fetches via repository and HTTP then a new viewModel displays saved stories offline`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v0/topstories.json" -> MockResponse().setBody("[9,7]")
                "/v0/item/9.json", "/v0/item/7.json" -> {
                    val id = if (request.path == "/v0/item/9.json") 9 else 7
                    MockResponse().setBody(
                        """{"id":$id,"type":"story","title":"Story $id","by":"alice","score":42,"time":1234}""",
                    )
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        val offline = AtomicBoolean(false)
        val transport = OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (offline.get()) throw IOException("Network unavailable")
                val request = chain.request()
                assertEquals("hacker-news.firebaseio.com", request.url.host)
                chain.proceed(request.newBuilder().url(server.url(request.url.encodedPath)).build())
            }
            .build()
        val dao = IntegrationStoryDao()
        val api = HackerNewsApiImpl(HttpClientImpl(transport, Dispatchers.IO), Gson(), Dispatchers.IO)
        val repository = OfflineFirstStoryRepository(dao, api, Dispatchers.IO)

        val thumbnails = StoryThumbnailRepository { "/files/${it.id}.jpg" }
        val onlineViewModel = FeedViewModel(repository, thumbnails)
        val online = onlineViewModel.uiState.first { it is FeedUiState.Ready } as FeedUiState.Ready
        assertEquals(listOf(9L, 7L), online.stories.map { it.id })
        assertFalse(online.isOffline)
        assertEquals(3, server.requestCount)

        offline.set(true)
        val offlineViewModel = FeedViewModel(repository, thumbnails)
        val saved = offlineViewModel.uiState.first { it is FeedUiState.Ready } as FeedUiState.Ready
        assertTrue(saved.isOffline)
        assertEquals(online.stories, saved.stories)
        assertEquals(3, server.requestCount)
    }
}

private class IntegrationStoryDao : StoryDao {
    private val savedStories = MutableStateFlow<List<StoryEntity>>(emptyList())

    override fun observeStories(): Flow<List<StoryEntity>> = savedStories

    override suspend fun upsertStories(stories: List<StoryEntity>) {
        savedStories.value = stories
    }
}
