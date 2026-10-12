package com.example.visualfeedtask.data.thumbnail

interface ThumbnailGenerator {
    /**
     * Generates an image locally and returns its absolute path in persistent app-private storage.
     * Cached assets are keyed only by story ID, even if the title changes.
     * Implementations must keep all work off the main thread, using injectable Default/IO
     * dispatchers. Native GPU initialization, inference and cleanup must stay on one thread.
     * Missing models, inference and storage failures propagate to the caller.
     */
    suspend fun generateThumbnail(storyId: Long, title: String): String
}
