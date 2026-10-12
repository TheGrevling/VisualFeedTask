package com.example.visualfeedtask.data.repository

import com.example.visualfeedtask.core.di.IoDispatcher
import com.example.visualfeedtask.data.local.ThumbnailDao
import com.example.visualfeedtask.data.model.Story
import com.example.visualfeedtask.data.thumbnail.ThumbnailGenerator
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

fun interface StoryThumbnailRepository {
    suspend fun getThumbnail(story: Story): String
}

class LocalStoryThumbnailRepository @Inject constructor(
    private val generator: ThumbnailGenerator,
    private val thumbnailDao: ThumbnailDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StoryThumbnailRepository {
    override suspend fun getThumbnail(story: Story): String {
        val path = generator.generateThumbnail(story.id, story.title)
        withContext(ioDispatcher) {
            if (thumbnailDao.setThumbnailPath(story.id, path) != 1) {
                throw IOException("Cannot save thumbnail for missing story ${story.id}")
            }
        }
        return path
    }
}
