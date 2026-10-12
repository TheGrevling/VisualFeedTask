package com.example.visualfeedtask.ui.feed

sealed interface FeedIntent {
    data object ReachedBottom : FeedIntent
    data object Retry : FeedIntent
    data class RequestThumbnail(val storyId: Long) : FeedIntent
    data class RetryThumbnail(val storyId: Long) : FeedIntent
    data class ThumbnailLoadFailed(val storyId: Long) : FeedIntent
}
