package com.example.visualfeedtask.data.local

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ThumbnailDao {
    @Query("UPDATE stories SET thumbnailPath = :path WHERE id = :storyId")
    suspend fun setThumbnailPath(storyId: Long, path: String): Int
}
