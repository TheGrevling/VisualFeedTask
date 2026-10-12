package com.example.visualfeedtask.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualFeedDatabaseTest {
    @Test
    fun upsertReplacesExistingStoryAndOrdersNewestFirst() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, VisualFeedDatabase::class.java).build()
        try {
            val older = StoryEntity(1, "Older", "alice", 1, 100, null, null)
            val newer = StoryEntity(2, "Newer", "bob", 2, 200, null, "/files/2.png")
            database.storyDao().upsertStories(listOf(older, newer))
            val updated = older.copy(score = 10, thumbnailPath = "/files/1.png")
            database.storyDao().upsertStories(listOf(updated))

            assertEquals(listOf(newer, updated), database.storyDao().observeStories().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun storyMetadataAndThumbnailPathSurviveDatabaseReopen() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test-${UUID.randomUUID()}.db"
        val story = StoryEntity(1, "Persisted", "alice", 5, 100, null, "/files/1.png")
        try {
            val firstDatabase = Room.databaseBuilder(context, VisualFeedDatabase::class.java, name).build()
            try {
                firstDatabase.storyDao().upsertStories(listOf(story))
            } finally {
                firstDatabase.close()
            }

            val reopened = Room.databaseBuilder(context, VisualFeedDatabase::class.java, name).build()
            try {
                assertEquals(listOf(story), reopened.storyDao().observeStories().first())
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
