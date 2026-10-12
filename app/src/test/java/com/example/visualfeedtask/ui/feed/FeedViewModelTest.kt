package com.example.visualfeedtask.ui.feed

import com.example.visualfeedtask.data.model.Story
import com.example.visualfeedtask.data.model.StoryFeed
import com.example.visualfeedtask.data.model.StoryPageKey
import com.example.visualfeedtask.data.repository.StoryRepository
import com.example.visualfeedtask.testing.MainDispatcherRule
import com.google.gson.JsonParseException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val story = Story(1, "Kotlin news", "author", 42, 100, null, "/files/1.png")

    @Test
    fun `starts loading then requests repository stories without requiring a subscriber`() = runTest {
        val repository = FakeStoryRepository()
        val viewModel = FeedViewModel(repository)
        assertEquals(FeedUiState.Loading, viewModel.uiState.value)

        repository.result = StoryFeed(listOf(story))
        runCurrent()

        assertEquals(1, repository.calls)
        assertEquals(FeedUiState.Ready(listOf(story)), viewModel.uiState.value)
    }

    @Test
    fun `exposes cached stories with offline status`() = runTest {
        val repository = FakeStoryRepository().apply {
            result = StoryFeed(listOf(story), isOffline = true)
        }
        val viewModel = FeedViewModel(repository)
        runCurrent()

        assertEquals(FeedUiState.Ready(listOf(story), isOffline = true), viewModel.uiState.value)
    }

    @Test
    fun `network and parsing errors become error state and can be retried`() = runTest {
        for (failure in listOf(IOException("Offline"), JsonParseException("Invalid"))) {
            val repository = FakeStoryRepository().apply { error = failure }
            val viewModel = FeedViewModel(repository)
            runCurrent()
            assertEquals(FeedUiState.Error, viewModel.uiState.value)

            repository.error = null
            repository.result = StoryFeed(listOf(story))
            viewModel.onIntent(FeedIntent.Retry)
            runCurrent()

            assertEquals(2, repository.calls)
            assertEquals(FeedUiState.Ready(listOf(story)), viewModel.uiState.value)
        }
    }

    @Test
    fun `does not start duplicate loads while a request is running`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeStoryRepository().apply { this.gate = gate }
        val viewModel = FeedViewModel(repository)
        runCurrent()
        viewModel.loadStories()
        runCurrent()
        assertEquals(1, repository.calls)

        gate.complete(Unit)
        runCurrent()
        assertEquals(FeedUiState.Ready(emptyList()), viewModel.uiState.value)
    }

    @Test
    fun `cancellation is not reported as a loading error`() = runTest {
        val repository = FakeStoryRepository().apply { error = CancellationException("Cancelled") }
        val viewModel = FeedViewModel(repository)
        runCurrent()

        assertEquals(FeedUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `reached bottom appends a page and suppresses duplicate in flight loads`() = runTest {
        val ids = (1L..45L).toList()
        val firstPage = StoryPageKey(ids, offset = 20)
        val secondPage = firstPage.copy(offset = 40)
        val nextStory = story.copy(id = 2, title = "Second story")
        val gate = CompletableDeferred<Unit>()
        val repository = FakeStoryRepository().apply {
            result = StoryFeed(listOf(story), nextPage = firstPage)
            pageResults[firstPage] = StoryFeed(listOf(nextStory), nextPage = secondPage)
            pageResults[secondPage] = StoryFeed(listOf(story.copy(id = 3)), nextPage = null)
            pageGate = gate
        }
        val viewModel = FeedViewModel(repository)
        runCurrent()

        viewModel.onIntent(FeedIntent.ReachedBottom)
        runCurrent()
        viewModel.onIntent(FeedIntent.ReachedBottom)
        runCurrent()

        assertEquals(2, repository.calls)
        assertEquals(listOf(null, firstPage), repository.requestedPages)
        assertEquals(
            FeedUiState.Ready(listOf(story), hasMore = true, isLoadingMore = true),
            viewModel.uiState.value,
        )

        gate.complete(Unit)
        runCurrent()
        assertEquals(
            FeedUiState.Ready(listOf(story, nextStory), hasMore = true),
            viewModel.uiState.value,
        )

        viewModel.onIntent(FeedIntent.ReachedBottom)
        runCurrent()
        assertEquals(
            FeedUiState.Ready(listOf(story, nextStory, story.copy(id = 3)), hasMore = false),
            viewModel.uiState.value,
        )
        viewModel.onIntent(FeedIntent.ReachedBottom)
        runCurrent()
        assertEquals(3, repository.calls)
        assertEquals(listOf(null, firstPage, secondPage), repository.requestedPages)
    }

    @Test
    fun `retry preserves loaded stories and retries the failed page`() = runTest {
        val page = StoryPageKey((1L..45L).toList(), offset = 20)
        val nextStory = story.copy(id = 2, title = "Second story")
        val repository = FakeStoryRepository().apply {
            result = StoryFeed(listOf(story), nextPage = page)
            pageErrors[page] = IOException("Offline")
            pageResults[page] = StoryFeed(listOf(nextStory), nextPage = page.copy(offset = 40))
        }
        val viewModel = FeedViewModel(repository)
        runCurrent()

        viewModel.onIntent(FeedIntent.ReachedBottom)
        runCurrent()
        assertEquals(
            FeedUiState.Ready(listOf(story), hasMore = true, loadMoreFailed = true),
            viewModel.uiState.value,
        )

        repository.pageErrors.remove(page)
        viewModel.onIntent(FeedIntent.Retry)
        runCurrent()

        assertEquals(
            FeedUiState.Ready(listOf(story, nextStory), hasMore = true),
            viewModel.uiState.value,
        )
        assertEquals(listOf(null, page, page), repository.requestedPages)
    }

    @Test
    fun `empty page advances to next page until it finds stories or exhausts`() = runTest {
        val emptyPage = StoryPageKey((1L..45L).toList(), offset = 20)
        val populatedPage = emptyPage.copy(offset = 40)
        val lastStory = story.copy(id = 41, title = "Last story")
        val repository = FakeStoryRepository().apply {
            result = StoryFeed(listOf(story), nextPage = emptyPage)
            pageResults[emptyPage] = StoryFeed(emptyList(), nextPage = populatedPage)
            pageResults[populatedPage] = StoryFeed(listOf(lastStory), nextPage = null)
        }
        val viewModel = FeedViewModel(repository)
        runCurrent()

        viewModel.onIntent(FeedIntent.ReachedBottom)
        runCurrent()

        assertEquals(
            FeedUiState.Ready(listOf(story, lastStory), hasMore = false),
            viewModel.uiState.value,
        )
        assertEquals(listOf(null, emptyPage, populatedPage), repository.requestedPages)
    }
}

private class FakeStoryRepository : StoryRepository {
    var result = StoryFeed(emptyList())
    var error: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    var pageGate: CompletableDeferred<Unit>? = null
    val pageResults = mutableMapOf<StoryPageKey, StoryFeed>()
    val pageErrors = mutableMapOf<StoryPageKey, Exception>()
    val requestedPages = mutableListOf<StoryPageKey?>()
    var calls = 0

    override suspend fun getStories(page: StoryPageKey?): StoryFeed {
        calls++
        requestedPages += page
        gate?.await()
        if (page != null) pageGate?.await()
        if (page != null) pageErrors[page]?.let { throw it }
        error?.let { throw it }
        return page?.let(pageResults::get) ?: result
    }
}
