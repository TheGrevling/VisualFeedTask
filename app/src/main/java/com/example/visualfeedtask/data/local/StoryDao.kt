package com.example.visualfeedtask.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY submittedAtEpochSeconds DESC, id DESC")
    fun observeStories(): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories ORDER BY submittedAtEpochSeconds DESC, id DESC")
    suspend fun getStoriesBySubmissionTime(): List<StoryEntity>

    @Query("SELECT * FROM stories ORDER BY score DESC, submittedAtEpochSeconds DESC, id DESC")
    suspend fun getStoriesByScore(): List<StoryEntity>

    @Upsert
    suspend fun upsertStories(stories: List<StoryEntity>)
}
