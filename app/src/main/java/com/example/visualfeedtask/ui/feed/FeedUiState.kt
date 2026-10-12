package com.example.visualfeedtask.ui.feed

import com.example.visualfeedtask.data.model.Story

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Ready(
        val stories: List<Story>,
        val isOffline: Boolean = false,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
        val queuedThumbnails: Set<Long> = emptySet(),
        val loadingThumbnails: Set<Long> = emptySet(),
        val failedThumbnails: Set<Long> = emptySet(),
        val imageGenerationEnabled: Boolean = true,
    ) : FeedUiState
    data object Error : FeedUiState
}
