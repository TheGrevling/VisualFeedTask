package com.example.visualfeedtask.data.remote

interface HackerNewsApi {
    suspend fun getTopStoryIds(): List<Long>

    suspend fun getNewStoryIds(): List<Long>

    suspend fun getItem(id: Long): HackerNewsItemDto?
}
