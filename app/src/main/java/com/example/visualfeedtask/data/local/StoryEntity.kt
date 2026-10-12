package com.example.visualfeedtask.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.visualfeedtask.data.model.Story

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val author: String,
    val score: Int,
    val submittedAtEpochSeconds: Long,
    val url: String?,
    val thumbnailPath: String?,
)

fun StoryEntity.toStory(): Story = Story(
    id = id,
    title = title,
    author = author,
    score = score,
    submittedAtEpochSeconds = submittedAtEpochSeconds,
    url = url,
    thumbnailPath = thumbnailPath,
)
