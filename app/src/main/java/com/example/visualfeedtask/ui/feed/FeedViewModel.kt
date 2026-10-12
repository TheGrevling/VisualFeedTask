package com.example.visualfeedtask.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.visualfeedtask.data.repository.StoryRepository
import com.example.visualfeedtask.data.model.Story
import com.example.visualfeedtask.data.model.StoryPageKey
import com.google.gson.JsonParseException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = mutableUiState.asStateFlow()
    private var loadJob: Job? = null
    private var nextPage: StoryPageKey? = null

    init {
        loadStories()
    }

    fun onIntent(intent: FeedIntent) {
        when (intent) {
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
                mutableUiState.value = state.copy(loadMoreFailed = true)
            } catch (failure: JsonParseException) {
                mutableUiState.value = state.copy(loadMoreFailed = true)
            }
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
                mutableUiState.value = FeedUiState.Ready(
                    stories = (existing + feed.stories).distinctBy { it.id },
                    isOffline = offline,
                    hasMore = key != null,
                )
                return
            }
        } while (key != null)
    }
}
