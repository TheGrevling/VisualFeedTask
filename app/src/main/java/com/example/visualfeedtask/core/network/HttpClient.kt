package com.example.visualfeedtask.core.network

interface HttpClient {
    /**
     * Performs an HTTP GET off the main thread and returns the response body.
     * HTTP and transport failures propagate to the caller; cancellation cancels the request.
     */
    suspend fun get(url: String): String
}
