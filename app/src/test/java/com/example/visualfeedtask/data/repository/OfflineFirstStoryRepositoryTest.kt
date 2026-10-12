package com.example.visualfeedtask.data.repository

import com.example.visualfeedtask.core.network.HttpException
import com.example.visualfeedtask.data.local.StoryDao
import com.example.visualfeedtask.data.local.StoryEntity
import com.example.visualfeedtask.data.local.toStory
import com.example.visualfeedtask.data.model.StoryPageKey
import com.example.visualfeedtask.data.remote.HackerNewsApi
import com.example.visualfeedtask.data.remote.HackerNewsItemDto
import com.google.gson.JsonParseException
import java.io.IOException
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OfflineFirstStoryRepositoryTest {
    private val cached = StoryEntity(7, "Story 7", "alice", 12, 1234, null, "/files/7.png")

    @Test
    fun `fetches top stories in API order and persists before returning preserving thumbnails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeStoryDao(listOf(cached))
        val api = FakeHackerNewsApi(dispatcher).apply {
            ids = listOf(9L, 7L, 9L)
            items = ids.associateWith { item(it) }
        }

        val feed = OfflineFirstStoryRepository(dao, api, dispatcher).getStories()

        assertEquals(listOf(9L, 7L), feed.stories.map { it.id })
        assertFalse(feed.isOffline)
        assertEquals("/files/7.png", feed.stories.last().thumbnailPath)
        assertEquals(feed.stories, dao.lastUpsert.map { it.toStory() })
        assertEquals(listOf(9L, 7L), api.requestedIds)
    }

    @Test
    fun `filters null deleted dead and non story items`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val api = FakeHackerNewsApi(dispatcher).apply {
            ids = listOf(1L, 2L, 3L, 4L, 5L)
            items = mapOf(
                1L to null,
                2L to item(2).copy(deleted = true),
                3L to item(3).copy(dead = true),
                4L to item(4).copy(type = "job"),
                5L to item(5),
            )
        }

        val feed = OfflineFirstStoryRepository(FakeStoryDao(), api, dispatcher).getStories()

        assertEquals(listOf(5L), feed.stories.map { it.id })
    }

    @Test
    fun `uses cached stories when top IDs or an item cannot be fetched`() = runTest {
        for (failAtItem in listOf(false, true)) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val dao = FakeStoryDao(listOf(cached))
            val api = FakeHackerNewsApi(dispatcher).apply {
                ids = listOf(1L, 2L)
                items = ids.associateWith { item(it) }
                if (failAtItem) itemFailure = IOException("Offline")
                else idsFailure = IOException("Offline")
            }

            val feed = OfflineFirstStoryRepository(dao, api, dispatcher).getStories()

            assertEquals(listOf(cached.toStory()), feed.stories)
            assertTrue(feed.isOffline)
            assertEquals(0, dao.upsertCount)
        }
    }

    @Test
    fun `offline without cached stories propagates network failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val api = FakeHackerNewsApi(dispatcher).apply { idsFailure = IOException("Offline") }

        try {
            OfflineFirstStoryRepository(FakeStoryDao(), api, dispatcher).getStories()
            fail("Expected offline failure")
        } catch (failure: IOException) {
            assertEquals("Offline", failure.message)
        }
    }

    @Test
    fun `HTTP errors invalid JSON and cancellation do not become offline success`() = runTest {
        for (error in listOf(HttpException(503), JsonParseException("Invalid"), CancellationException("Cancelled"))) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val api = FakeHackerNewsApi(dispatcher).apply { idsFailure = error }
            val dao = FakeStoryDao(listOf(cached))

            try {
                OfflineFirstStoryRepository(dao, api, dispatcher).getStories()
                fail("Expected ${error.javaClass.simpleName}")
            } catch (failure: HttpException) {
                assertEquals(error.message, failure.message)
            } catch (failure: JsonParseException) {
                assertEquals(error.message, failure.message)
            } catch (failure: CancellationException) {
                assertEquals(error.message, failure.message)
            }
            assertEquals(0, dao.upsertCount)
        }
    }

    @Test
    fun `bounds concurrent item requests and preserves order despite completion order`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val api = FakeHackerNewsApi(dispatcher).apply {
            ids = (1L..30L).toList()
            items = ids.associateWith { item(it) }
            delayed = true
        }

        val feed = OfflineFirstStoryRepository(FakeStoryDao(), api, dispatcher).getStories()

        assertEquals(api.ids.take(20), feed.stories.map { it.id })
        assertEquals(api.ids.take(20), api.requestedIds)
        assertTrue(api.maxConcurrent in 2..8)
    }

    @Test
    fun `requests exactly twenty IDs per page and fetches top IDs only once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ids = (1L..45L).toList()
        val api = FakeHackerNewsApi(dispatcher).apply {
            this.ids = ids
            items = ids.associateWith { item(it) }
        }
        val repository = OfflineFirstStoryRepository(FakeStoryDao(), api, dispatcher)

        val first = repository.getStories()
        assertEquals((1L..20L).toList(), first.stories.map { it.id })
        assertEquals(StoryPageKey(ids, offset = 20), first.nextPage)
        assertEquals((1L..20L).toList(), api.requestedIds)
        assertEquals(1, api.topIdsCallCount)

        val second = repository.getStories(first.nextPage)
        assertEquals((21L..40L).toList(), second.stories.map { it.id })
        assertEquals(StoryPageKey(ids, offset = 40), second.nextPage)
        assertEquals((1L..40L).toList(), api.requestedIds)
        assertEquals(1, api.topIdsCallCount)

        val final = repository.getStories(second.nextPage)
        assertEquals((41L..45L).toList(), final.stories.map { it.id })
        assertEquals(null, final.nextPage)
        assertEquals((1L..45L).toList(), api.requestedIds)
        assertEquals(1, api.topIdsCallCount)
    }

    @Test
    fun `offline initial fallback pages cached IDs twenty at a time`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cachedStories = (1L..45L).map { id ->
            StoryEntity(id, "Story $id", "alice", 42, 1234, null, null)
        }
        val api = FakeHackerNewsApi(dispatcher).apply {
            idsFailure = IOException("Offline")
        }
        val repository = OfflineFirstStoryRepository(FakeStoryDao(cachedStories), api, dispatcher)

        val first = repository.getStories()
        assertEquals((1L..20L).toList(), first.stories.map { it.id })
        assertTrue(first.isOffline)
        assertEquals(StoryPageKey(cachedStories.map { it.id }, 20, isOffline = true), first.nextPage)
        assertTrue(api.requestedIds.isEmpty())

        val second = repository.getStories(first.nextPage)
        assertEquals((21L..40L).toList(), second.stories.map { it.id })
        assertTrue(second.isOffline)
        assertEquals(StoryPageKey(cachedStories.map { it.id }, 40, isOffline = true), second.nextPage)

        val final = repository.getStories(second.nextPage)
        assertEquals((41L..45L).toList(), final.stories.map { it.id })
        assertTrue(final.isOffline)
        assertEquals(null, final.nextPage)
        assertTrue(api.requestedIds.isEmpty())
        assertEquals(1, api.topIdsCallCount)
    }

    @Test
    fun `later page failure retries the same IDs without skipping the failed page`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ids = (1L..45L).toList()
        val api = FakeHackerNewsApi(dispatcher).apply {
            this.ids = ids
            items = ids.associateWith { item(it) }
        }
        val repository = OfflineFirstStoryRepository(FakeStoryDao(), api, dispatcher)
        val first = repository.getStories()
        val failedPage = checkNotNull(first.nextPage)
        api.itemFailure = IOException("Offline")

        try {
            repository.getStories(failedPage)
            fail("Expected uncached page failure")
        } catch (failure: IOException) {
            assertEquals("Offline", failure.message)
        }
        assertEquals((1L..20L).toList(), api.requestedIds.take(20))
        val failedAttempt = api.requestedIds.drop(20)
        assertTrue(failedAttempt.isNotEmpty())
        assertTrue(failedAttempt.all { it in 21L..40L })
        assertEquals(StoryPageKey(ids, offset = 20), failedPage)

        api.itemFailure = null
        val retried = repository.getStories(failedPage)

        assertEquals((21L..40L).toList(), retried.stories.map { it.id })
        assertEquals(StoryPageKey(ids, offset = 40), retried.nextPage)
        assertEquals(failedAttempt + (21L..40L).toList(), api.requestedIds.drop(20))
        assertEquals(1, api.topIdsCallCount)
    }

    @Test
    fun `later page transport failure uses cached stories only for a fully cached page`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ids = (1L..45L).toList()
        val cachedStories = ids.map { id ->
            StoryEntity(id, "Story $id", "alice", 42, 1234, null, null)
        }
        val api = FakeHackerNewsApi(dispatcher).apply {
            this.ids = ids
            items = ids.associateWith { item(it) }
        }
        val dao = FakeStoryDao(cachedStories)
        val repository = OfflineFirstStoryRepository(dao, api, dispatcher)
        val first = repository.getStories()
        val secondPage = checkNotNull(first.nextPage)
        api.itemFailure = IOException("Offline")

        val offlinePage = repository.getStories(secondPage)

        assertEquals((21L..40L).toList(), offlinePage.stories.map { it.id })
        assertTrue(offlinePage.isOffline)
        assertEquals(StoryPageKey(ids, offset = 40), offlinePage.nextPage)
        assertEquals(1, dao.upsertCount)

        api.itemFailure = null
        val retried = repository.getStories(secondPage)
        assertEquals((21L..40L).toList(), retried.stories.map { it.id })
        assertEquals(StoryPageKey(ids, offset = 40), retried.nextPage)
        assertEquals(1, api.topIdsCallCount)
    }

    @Test
    fun `changed titles discard stale generated thumbnail paths`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val api = FakeHackerNewsApi(dispatcher).apply {
            ids = listOf(7L)
            items = mapOf(7L to item(7).copy(title = "Changed"))
        }

        val feed = OfflineFirstStoryRepository(FakeStoryDao(listOf(cached)), api, dispatcher).getStories()

        assertEquals(null, feed.stories.single().thumbnailPath)
    }
}

private fun item(id: Long) = HackerNewsItemDto(
    id, "story", "Story $id", "alice", 42, 1234, null, false, false,
)

private class FakeStoryDao(initial: List<StoryEntity> = emptyList()) : StoryDao {
    private val stories = MutableStateFlow(initial)
    var upsertCount = 0
    var lastUpsert = emptyList<StoryEntity>()
    override fun observeStories(): Flow<List<StoryEntity>> = stories

    override suspend fun upsertStories(stories: List<StoryEntity>) {
        upsertCount++
        lastUpsert = stories
        this.stories.value = (this.stories.value.associateBy { it.id } +
            stories.associateBy { it.id }).values.toList()
    }
}

private class FakeHackerNewsApi(private val dispatcher: CoroutineDispatcher) : HackerNewsApi {
    var ids = emptyList<Long>()
    var items = emptyMap<Long, HackerNewsItemDto?>()
    var idsFailure: Exception? = null
    var itemFailure: IOException? = null
    val requestedIds = mutableListOf<Long>()
    var delayed = false
    var topIdsCallCount = 0
    private var concurrent = 0
    var maxConcurrent = 0

    override suspend fun getTopStoryIds(): List<Long> {
        assertSame(dispatcher, coroutineContext[ContinuationInterceptor])
        topIdsCallCount++
        idsFailure?.let { throw it }
        return ids
    }

    override suspend fun getNewStoryIds(): List<Long> = error("Must fetch Top stories")

    override suspend fun getItem(id: Long): HackerNewsItemDto? {
        assertSame(dispatcher, coroutineContext[ContinuationInterceptor])
        requestedIds += id
        itemFailure?.let { throw it }
        concurrent++
        maxConcurrent = maxOf(maxConcurrent, concurrent)
        try {
            if (delayed) delay(31L - id)
            return items[id]
        } finally {
            concurrent--
        }
    }
}
