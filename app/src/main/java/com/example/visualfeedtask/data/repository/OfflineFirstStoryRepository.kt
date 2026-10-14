package com.example.visualfeedtask.data.repository

import com.example.visualfeedtask.core.di.IoDispatcher
import com.example.visualfeedtask.core.network.HttpException
import com.example.visualfeedtask.core.network.NetworkMonitor
import com.example.visualfeedtask.core.network.NoNetworkException
import com.example.visualfeedtask.data.local.StoryDao
import com.example.visualfeedtask.data.local.StoryEntity
import com.example.visualfeedtask.data.local.toStory
import com.example.visualfeedtask.data.model.StoryFeed
import com.example.visualfeedtask.data.model.StoryFeedType
import com.example.visualfeedtask.data.model.StoryPageKey
import com.example.visualfeedtask.data.remote.HackerNewsApi
import com.example.visualfeedtask.data.remote.HackerNewsItemDto
import com.google.gson.JsonParseException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class OfflineFirstStoryRepository @Inject constructor(
    private val storyDao: StoryDao,
    private val api: HackerNewsApi,
    private val networkMonitor: NetworkMonitor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StoryRepository {
    override suspend fun getStories(
        page: StoryPageKey?,
        feedType: StoryFeedType,
    ): StoryFeed = withContext(ioDispatcher) {
        val cached = storyDao.observeStories().first()
        val cachedById = cached.associateBy { it.id }
        if (page?.isOffline == true) {
            return@withContext cachedPage(page, cachedById)
        }
        val (currentPage, entities) = try {
            if (!networkMonitor.isOnline()) throw NoNetworkException()
            val key = page ?: StoryPageKey(
                when (feedType) {
                    StoryFeedType.TOP -> api.getTopStoryIds()
                    StoryFeedType.NEW -> api.getNewStoryIds()
                }.distinct(),
                offset = 0,
            )
            val fetched = coroutineScope {
                val requests = Semaphore(8)
                key.storyIds.drop(key.offset).take(PAGE_SIZE).map { id ->
                    async {
                        requests.withPermit {
                            val item = api.getItem(id)
                            if (item != null && item.id != id) {
                                throw JsonParseException("Hacker News returned a mismatched item ID")
                            }
                            item?.toEntity(cachedById[id])
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            key to fetched
        } catch (failure: IOException) {
            if (failure is HttpException) throw failure
            if (cached.isEmpty()) {
                // A known-offline device with nothing saved shows an empty offline feed, not an error.
                if (page == null && failure is NoNetworkException) {
                    return@withContext StoryFeed(emptyList(), isOffline = true)
                }
                throw failure
            }
            if (page == null) {
                return@withContext cachedPage(
                    StoryPageKey(cached.map { it.id }, offset = 0, isOffline = true),
                    cachedById,
                )
            }
            val pageIds = page.storyIds.drop(page.offset).take(PAGE_SIZE)
            if (pageIds.any { it !in cachedById }) throw failure
            return@withContext cachedPage(page, cachedById)
        }
        storyDao.upsertStories(entities)
        StoryFeed(
            entities.map { it.toStory() },
            nextPage = currentPage.next(),
        )
    }

    private fun cachedPage(key: StoryPageKey, cached: Map<Long, StoryEntity>): StoryFeed =
        StoryFeed(
            stories = key.storyIds.drop(key.offset).take(PAGE_SIZE)
                .mapNotNull { cached[it]?.toStory() },
            isOffline = true,
            nextPage = key.next(),
        )

    private fun StoryPageKey.next(): StoryPageKey? {
        val nextOffset = offset + PAGE_SIZE
        return if (nextOffset < storyIds.size) copy(offset = nextOffset) else null
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

private fun HackerNewsItemDto.toEntity(cached: StoryEntity?): StoryEntity? {
    if (deleted == true || dead == true || type != "story") return null
    return StoryEntity(
        id = id ?: throw JsonParseException("Story is missing its ID"),
        title = title ?: throw JsonParseException("Story is missing its title"),
        author = author ?: throw JsonParseException("Story is missing its author"),
        score = score ?: throw JsonParseException("Story is missing its score"),
        submittedAtEpochSeconds = submittedAtEpochSeconds
            ?: throw JsonParseException("Story is missing its submission time"),
        url = url,
        thumbnailPath = cached?.thumbnailPath,
    )
}
