package com.example.visualfeedtask.ui.feed

sealed interface FeedIntent {
    data object ReachedBottom : FeedIntent
    data object Retry : FeedIntent
}
