package com.example.visualfeedtask.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories ORDER BY submittedAtEpochSeconds DESC, id DESC")
    fun observeStories(): Flow<List<StoryEntity>>

    @Upsert
    suspend fun upsertStories(stories: List<StoryEntity>)
}
