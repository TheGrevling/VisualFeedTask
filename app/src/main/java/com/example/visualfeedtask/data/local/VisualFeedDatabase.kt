package com.example.visualfeedtask.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [StoryEntity::class], version = 1, exportSchema = true)
abstract class VisualFeedDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun thumbnailDao(): ThumbnailDao
}
