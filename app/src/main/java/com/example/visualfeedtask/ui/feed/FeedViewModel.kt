package com.example.visualfeedtask.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.visualfeedtask.data.model.Story
import com.example.visualfeedtask.data.repository.StoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class FeedViewModel @Inject constructor(
    storyRepository: StoryRepository,
) : ViewModel() {
    val uiState: StateFlow<FeedUiState> = storyRepository.observeStories()
        .map<List<Story>, FeedUiState> {
            FeedUiState.Ready(it)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FeedUiState.Loading,
        )
}
