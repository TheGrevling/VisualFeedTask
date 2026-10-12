package com.example.visualfeedtask.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.visualfeedtask.data.di.ImageGenerationDisabled
import com.example.visualfeedtask.data.repository.StoryRepository
import com.example.visualfeedtask.data.repository.StoryThumbnailRepository
import com.example.visualfeedtask.data.model.Story
import com.example.visualfeedtask.data.model.StoryPageKey
import com.google.gson.JsonParseException
import com.google.mediapipe.framework.MediaPipeException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
    private val thumbnailRepository: StoryThumbnailRepository,
    @ImageGenerationDisabled private val imageGenerationDisabled: Boolean = false,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = mutableUiState.asStateFlow()
    private var loadJob: Job? = null
    private var nextPage: StoryPageKey? = null
    private val completedThumbnails = mutableSetOf<Long>()
    private val thumbnailMutex = Mutex()

    init {
        loadStories()
    }

    fun onIntent(intent: FeedIntent) {
        when (intent) {
            is FeedIntent.RequestThumbnail -> requestThumbnail(intent.storyId)
            is FeedIntent.RetryThumbnail -> requestThumbnail(intent.storyId, retry = true)
            is FeedIntent.ThumbnailLoadFailed -> {
                completedThumbnails.remove(intent.storyId)
                mutableUiState.update { state ->
                    if (state is FeedUiState.Ready) {
                        state.copy(failedThumbnails = state.failedThumbnails + intent.storyId)
                    } else state
                }
            }
            FeedIntent.ReachedBottom -> {
                val state = mutableUiState.value as? FeedUiState.Ready ?: return
                if (state.hasMore && !state.loadMoreFailed) loadNextPage(state)
            }
            FeedIntent.Retry -> {
                val state = mutableUiState.value
                if (state is FeedUiState.Ready && state.loadMoreFailed) loadNextPage(state)
                else if (state == FeedUiState.Error) loadStories()
            }
        }
    }

    private fun requestThumbnail(storyId: Long, retry: Boolean = false) {
        if (imageGenerationDisabled) return
        val state = mutableUiState.value as? FeedUiState.Ready ?: return
        val story = state.stories.firstOrNull { it.id == storyId } ?: return
        if (storyId in state.loadingThumbnails || storyId in state.queuedThumbnails) return
        if (!retry && (storyId in completedThumbnails || storyId in state.failedThumbnails)) return
        mutableUiState.value = state.copy(
            queuedThumbnails = state.queuedThumbnails + storyId,
            failedThumbnails = state.failedThumbnails - storyId,
        )
        viewModelScope.launch {
            try {
                // Fair FIFO lock: one thumbnail generates while the others stay queued.
                val path = thumbnailMutex.withLock {
                    mutableUiState.update { current ->
                        if (current is FeedUiState.Ready) current.copy(
                            queuedThumbnails = current.queuedThumbnails - storyId,
                            loadingThumbnails = current.loadingThumbnails + storyId,
                        ) else current
                    }
                    thumbnailRepository.getThumbnail(story)
                }
                completedThumbnails += storyId
                mutableUiState.update { current ->
                    if (current is FeedUiState.Ready) current.copy(
                        stories = current.stories.map {
                            if (it.id == storyId) it.copy(thumbnailPath = path) else it
                        },
                        loadingThumbnails = current.loadingThumbnails - storyId,
                        failedThumbnails = current.failedThumbnails - storyId,
                    ) else current
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                markThumbnailFailed(storyId)
            } catch (failure: MediaPipeException) {
                markThumbnailFailed(storyId)
            } catch (failure: IllegalArgumentException) {
                markThumbnailFailed(storyId)
            } catch (failure: IllegalStateException) {
                markThumbnailFailed(storyId)
            }
        }
    }

    private fun markThumbnailFailed(storyId: Long) {
        mutableUiState.update { current ->
            if (current is FeedUiState.Ready) current.copy(
                queuedThumbnails = current.queuedThumbnails - storyId,
                loadingThumbnails = current.loadingThumbnails - storyId,
                failedThumbnails = current.failedThumbnails + storyId,
            ) else current
        }
    }

    fun loadStories() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            mutableUiState.value = FeedUiState.Loading
            try {
                loadPage(null, emptyList(), isOffline = false)
            } catch (failure: IOException) {
                mutableUiState.value = FeedUiState.Error
            } catch (failure: JsonParseException) {
                mutableUiState.value = FeedUiState.Error
            }
        }

    }

    private fun loadNextPage(state: FeedUiState.Ready) {
        if (loadJob?.isActive == true) return
        val page = nextPage ?: return
        mutableUiState.value = state.copy(isLoadingMore = true, loadMoreFailed = false)
        loadJob = viewModelScope.launch {
            try {
                loadPage(page, state.stories, state.isOffline)
            } catch (failure: IOException) {
                markLoadMoreFailed()
            } catch (failure: JsonParseException) {
                markLoadMoreFailed()
            }
        }
    }

    private fun markLoadMoreFailed() {
        mutableUiState.update { current ->
            if (current is FeedUiState.Ready) {
                current.copy(isLoadingMore = false, loadMoreFailed = true)
            } else current
        }
    }

    private suspend fun loadPage(
        page: StoryPageKey?,
        existing: List<Story>,
        isOffline: Boolean,
    ) {
        var key = page
        var offline = isOffline
        do {
            val feed = storyRepository.getStories(key)
            offline = offline || feed.isOffline
            key = feed.nextPage
            // Skip batches containing only missing/deleted/non-story items so scrolling can continue.
            if (feed.stories.isNotEmpty() || key == null) {
                nextPage = key
                mutableUiState.update { current ->
                    val ready = current as? FeedUiState.Ready
                    (ready ?: FeedUiState.Ready(
                        emptyList(), imageGenerationEnabled = !imageGenerationDisabled,
                    )).copy(
                        stories = ((ready?.stories ?: existing) + feed.stories).distinctBy { it.id },
                        isOffline = offline,
                        hasMore = key != null,
                        isLoadingMore = false,
                        loadMoreFailed = false,
                    )
                }
                return
            }
        } while (key != null)
    }
}
