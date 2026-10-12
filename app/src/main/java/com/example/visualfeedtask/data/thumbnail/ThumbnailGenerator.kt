package com.example.visualfeedtask.data.thumbnail

interface ThumbnailGenerator {
    /**
     * Generates an image locally and returns its absolute path in persistent app-private storage.
     * Implementations must move inference and file writes off the main thread.
     */
    suspend fun generateThumbnail(storyId: Long, title: String): String
}
