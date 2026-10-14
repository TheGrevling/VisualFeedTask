package com.example.visualfeedtask.ui.feed

import com.example.visualfeedtask.data.model.StoryFeedType

sealed interface FeedIntent {
    data object ReachedBottom : FeedIntent
    data object Retry : FeedIntent
    data object Refresh : FeedIntent
    data class SelectFeed(val feedType: StoryFeedType) : FeedIntent
    data class RequestThumbnail(val storyId: Long) : FeedIntent
    data class RetryThumbnail(val storyId: Long) : FeedIntent
    data class ThumbnailLoadFailed(val storyId: Long) : FeedIntent
}
