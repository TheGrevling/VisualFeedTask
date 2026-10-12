package com.example.visualfeedtask.data.repository

import com.example.visualfeedtask.data.local.ThumbnailDao
import com.example.visualfeedtask.data.model.Story
import com.example.visualfeedtask.data.thumbnail.ThumbnailGenerator
import java.io.IOException
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LocalStoryThumbnailRepositoryTest {
    private val story = Story(7, "Story", "alice", 42, 100, null, null)

    @Test
    fun `persists path on IO before exposing the generated image`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = mutableListOf<String>()
        val generator = object : ThumbnailGenerator {
            override suspend fun generateThumbnail(storyId: Long, title: String): String {
                assertEquals(story.id, storyId)
                assertEquals(story.title, title)
                events += "generate"
                return "/files/7.jpg"
            }
        }
        val dao = object : ThumbnailDao {
            override suspend fun setThumbnailPath(storyId: Long, path: String): Int {
                assertSame(dispatcher, currentCoroutineContext()[ContinuationInterceptor])
                assertEquals(story.id, storyId)
                assertEquals("/files/7.jpg", path)
                events += "persist"
                return 1
            }
        }
        val path = LocalStoryThumbnailRepository(generator, dao, dispatcher).getThumbnail(story)
        events += "return"
        assertEquals("/files/7.jpg", path)
        assertEquals(listOf("generate", "persist", "return"), events)
    }

    @Test
    fun `missing story does not expose unpersisted path`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val generator = object : ThumbnailGenerator {
            override suspend fun generateThumbnail(storyId: Long, title: String) = "/files/7.jpg"
        }
        val dao = object : ThumbnailDao {
            override suspend fun setThumbnailPath(storyId: Long, path: String) = 0
        }
        try {
            LocalStoryThumbnailRepository(generator, dao, dispatcher).getThumbnail(story)
            error("Expected persistence failure")
        } catch (failure: IOException) {
            assertEquals("Cannot save thumbnail for missing story 7", failure.message)
        }
    }

    @Test
    fun `generation failure and cancellation do not write to Room`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        for (failure in listOf(IOException("Inference"), CancellationException("Cancelled"))) {
            val generator = object : ThumbnailGenerator {
                override suspend fun generateThumbnail(storyId: Long, title: String): String =
                    throw failure
            }
            val dao = object : ThumbnailDao {
                override suspend fun setThumbnailPath(storyId: Long, path: String): Int =
                    error("Must not persist a failed generation")
            }
            try {
                LocalStoryThumbnailRepository(generator, dao, dispatcher).getThumbnail(story)
                error("Expected failure")
            } catch (actual: Exception) {
                assertSame(failure, actual)
            }
        }
    }
}
