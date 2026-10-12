package com.example.visualfeedtask.data.remote

import com.example.visualfeedtask.core.di.IoDispatcher
import com.example.visualfeedtask.core.network.HttpClient
import com.google.gson.Gson
import com.google.gson.JsonParseException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class HackerNewsApiImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val gson: Gson,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HackerNewsApi {
    override suspend fun getTopStoryIds(): List<Long> = getStoryIds("topstories.json")

    override suspend fun getNewStoryIds(): List<Long> = getStoryIds("newstories.json")

    override suspend fun getItem(id: Long): HackerNewsItemDto? = withContext(ioDispatcher) {
        val json = httpClient.get("${BASE_URL}item/$id.json")
        if (json.isBlank()) {
            throw JsonParseException("Hacker News returned an empty item response")
        }
        gson.fromJson(json, HackerNewsItemDto::class.java)
    }

    private suspend fun getStoryIds(endpoint: String): List<Long> = withContext(ioDispatcher) {
        val json = httpClient.get("$BASE_URL$endpoint")
        val ids = gson.fromJson(json, Array<Long?>::class.java)
            ?: throw JsonParseException("Hacker News returned null instead of a story ID array")
        ids.map { it ?: throw JsonParseException("Hacker News returned a null story ID") }
    }

    private companion object {
        const val BASE_URL = "https://hacker-news.firebaseio.com/v0/"
    }
}