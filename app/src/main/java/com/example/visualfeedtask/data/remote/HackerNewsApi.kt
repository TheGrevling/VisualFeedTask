package com.example.visualfeedtask.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface HackerNewsApi {
    @GET("topstories.json")
    suspend fun getTopStoryIds(): List<Long>

    @GET("newstories.json")
    suspend fun getNewStoryIds(): List<Long>

    @GET("item/{id}.json")
    suspend fun getItem(@Path("id") id: Long): HackerNewsItemDto?
}
