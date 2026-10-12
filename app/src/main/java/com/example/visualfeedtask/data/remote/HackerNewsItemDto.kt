package com.example.visualfeedtask.data.remote

import com.google.gson.annotations.SerializedName

// HN items can be missing, deleted, dead, or non-story items.
data class HackerNewsItemDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("type") val type: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("by") val author: String?,
    @SerializedName("score") val score: Int?,
    @SerializedName("time") val submittedAtEpochSeconds: Long?,
    @SerializedName("url") val url: String?,
    @SerializedName("deleted") val deleted: Boolean?,
    @SerializedName("dead") val dead: Boolean?,
)
