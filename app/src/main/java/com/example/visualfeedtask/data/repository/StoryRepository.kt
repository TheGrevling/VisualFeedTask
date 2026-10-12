package com.example.visualfeedtask.data.repository

import com.example.visualfeedtask.data.model.Story
import kotlinx.coroutines.flow.Flow

interface StoryRepository {
    fun observeStories(): Flow<List<Story>>
}
