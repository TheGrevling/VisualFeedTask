package com.example.visualfeedtask.data.thumbnail

interface ThumbnailGenerator {
    /**
     * Generates an image locally and returns its absolute path in persistent app-private storage.
     * Implementations must use the injectable Default dispatcher for CPU-bound inference and
     * the IO dispatcher for model loading and file writes, keeping all work off the main thread.
     */
    suspend fun generateThumbnail(storyId: Long, title: String): String
}
