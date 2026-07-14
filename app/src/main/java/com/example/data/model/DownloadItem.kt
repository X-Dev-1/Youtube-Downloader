package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_tracks")
data class DownloadItem(
    @PrimaryKey val id: String, // YouTube Video ID
    val title: String,
    val author: String,
    val duration: String,
    val thumbnailUrl: String,
    val filePath: String, // Path or Uri string where saved
    val fileSize: String,
    val downloadedAt: Long = System.currentTimeMillis()
)
