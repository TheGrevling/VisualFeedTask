package com.example.visualfeedtask.data.repository

import com.example.visualfeedtask.data.model.StoryFeed
import com.example.visualfeedtask.data.model.StoryPageKey

interface StoryRepository {
    suspend fun getStories(page: StoryPageKey? = null): StoryFeed
}
