package com.example.visualfeedtask.data.model

data class Story(
    val id: Long,
    val title: String,
    val author: String,
    val score: Int,
    val submittedAtEpochSeconds: Long,
    val url: String?,
    val thumbnailPath: String?,
)
