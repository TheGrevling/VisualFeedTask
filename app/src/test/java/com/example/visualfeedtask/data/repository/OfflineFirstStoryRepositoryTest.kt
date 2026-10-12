package com.example.visualfeedtask.data.repository

import com.example.visualfeedtask.data.local.StoryDao
import com.example.visualfeedtask.data.local.StoryEntity
import com.example.visualfeedtask.data.model.Story
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineFirstStoryRepositoryTest {
    @Test
    fun `maps cached entities to application models including persisted thumbnail path`() = runTest {
        val dao = FakeStoryDao()
        val repository = OfflineFirstStoryRepository(dao, StandardTestDispatcher(testScheduler))
        assertEquals(emptyList<Story>(), repository.observeStories().first())

        dao.upsertStories(
            listOf(StoryEntity(7, "A story", "alice", 12, 1234, "https://example.com", "/files/7.png"))
        )

        assertEquals(
            listOf(Story(7, "A story", "alice", 12, 1234, "https://example.com", "/files/7.png")),
            repository.observeStories().first(),
        )
    }
}

private class FakeStoryDao : StoryDao {
    private val stories = MutableStateFlow<List<StoryEntity>>(emptyList())
    override fun observeStories(): Flow<List<StoryEntity>> = stories

    override suspend fun upsertStories(stories: List<StoryEntity>) {
        this.stories.value = (this.stories.value.associateBy { it.id } +
            stories.associateBy { it.id }).values.toList()
    }
}
