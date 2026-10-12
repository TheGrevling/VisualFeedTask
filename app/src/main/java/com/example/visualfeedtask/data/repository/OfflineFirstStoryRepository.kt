package com.example.visualfeedtask.data.repository

import com.example.visualfeedtask.core.di.IoDispatcher
import com.example.visualfeedtask.data.local.StoryDao
import com.example.visualfeedtask.data.local.toStory
import com.example.visualfeedtask.data.model.Story
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class OfflineFirstStoryRepository @Inject constructor(
    private val storyDao: StoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StoryRepository {
    override fun observeStories(): Flow<List<Story>> =
        storyDao.observeStories()
            .map { stories -> stories.map { it.toStory() } }
            .flowOn(ioDispatcher)
}
