package com.example.visualfeedtask.data.model

data class StoryFeed(
    val stories: List<Story>,
    val isOffline: Boolean = false,
    val nextPage: StoryPageKey? = null,
)

enum class StoryFeedType { TOP, NEW }

data class StoryPageKey(
    val storyIds: List<Long>,
    val offset: Int,
    val isOffline: Boolean = false,
)
