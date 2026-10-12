package com.example.visualfeedtask.ui.feed

import com.example.visualfeedtask.data.model.Story
import com.example.visualfeedtask.data.repository.StoryRepository
import com.example.visualfeedtask.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `starts loading and exposes empty cache when subscribed`() = runTest {
        val viewModel = FeedViewModel(FakeStoryRepository())
        assertEquals(FeedUiState.Loading, viewModel.uiState.value)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        assertEquals(FeedUiState.Ready(emptyList()), viewModel.uiState.value)
    }

    @Test
    fun `repository updates become ui state while subscribed`() = runTest {
        val repository = FakeStoryRepository()
        val viewModel = FeedViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        val story = Story(1, "Kotlin news", "author", 42, 100, null, "/files/1.png")
        repository.stories.value = listOf(story)
        runCurrent()
        assertEquals(FeedUiState.Ready(listOf(story)), viewModel.uiState.value)

        repository.stories.value = emptyList()
        runCurrent()
        assertEquals(FeedUiState.Ready(emptyList()), viewModel.uiState.value)
    }
}

private class FakeStoryRepository : StoryRepository {
    val stories = MutableStateFlow<List<Story>>(emptyList())
    override fun observeStories(): Flow<List<Story>> = stories
}
