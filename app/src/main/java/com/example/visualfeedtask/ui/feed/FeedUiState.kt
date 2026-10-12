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
    ) : FeedUiState
    data object Error : FeedUiState
}
