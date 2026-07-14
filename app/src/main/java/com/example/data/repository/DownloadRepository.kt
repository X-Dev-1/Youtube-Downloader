package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.api.CobaltClient
import com.example.data.api.CobaltRequest
import com.example.data.local.DownloadItemDao
import com.example.data.model.DownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.regex.Pattern

data class PlaylistItem(
    val id: String,
    val title: String,
    val author: String,
    val duration: String,
    val thumbnailUrl: String,
    val url: String,
    var isSelected: Boolean = true
)

class DownloadRepository(
    private val context: Context,
    private val dao: DownloadItemDao
) {
    val allDownloadedTracks: Flow<List<DownloadItem>> = dao.getAllTracks()

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Scrapes a YouTube playlist page and extracts up to 50 videos.
     * Uses a robust user-agent and falls back gracefully if youtube blocks it.
     */
    suspend fun fetchPlaylistVideos(playlistUrl: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PlaylistItem>()
        
        // Clean up the URL to make sure it is a direct playlist link
        val playlistId = extractPlaylistId(playlistUrl) ?: return@withContext emptyList<PlaylistItem>()
        val directUrl = "https://www.youtube.com/playlist?list=$playlistId"

        try {
            val request = Request.Builder()
                .url(directUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("DownloadRepository", "Failed to fetch playlist page: ${response.code}")
                return@withContext emptyList()
            }

            val html = response.body?.string() ?: ""
            
            // Look for ytInitialData JSON object inside the script tag
            val ytInitialDataPattern = Pattern.compile("var ytInitialData\\s*=\\s*(\\{.*?\\});")
            val matcher = ytInitialDataPattern.matcher(html)
            
            if (matcher.find()) {
                val jsonString = matcher.group(1) ?: ""
                extractVideosFromJson(jsonString, list)
            } else {
                // Regular watch link extraction fallback
                val watchPattern = Pattern.compile("/watch\\?v=([a-zA-Z0-9_-]{11})")
                val watchMatcher = watchPattern.matcher(html)
                val uniqueIds = mutableSetOf<String>()
                while (watchMatcher.find() && uniqueIds.size < 30) {
                    val id = watchMatcher.group(1) ?: continue
                    if (uniqueIds.add(id)) {
                        list.add(
                            PlaylistItem(
                                id = id,
                                title = "YouTube Video ($id)",
                                author = "YouTube Creator",
                                duration = "03:45",
                                thumbnailUrl = "https://img.youtube.com/vi/$id/hqdefault.jpg",
                                url = "https://www.youtube.com/watch?v=$id"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error scraping playlist", e)
        }

        // Remove duplicates and return
        return@withContext list.distinctBy { it.id }
    }

    private fun extractPlaylistId(url: String): String? {
        if (url.startsWith("PL") && url.length >= 18) return url
        val pattern = Pattern.compile("[&?]list=([^&]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractVideosFromJson(json: String, list: MutableList<PlaylistItem>) {
        // We can parse the JSON of the playlist entries using quick regexes
        val videoPattern = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
        val titlePattern = Pattern.compile("\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"\\}\\]")
        val channelPattern = Pattern.compile("\"shortBylineText\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"\\}\\]")
        val durationPattern = Pattern.compile("\"lengthText\":\\{\"accessibility\":\\{\"accessibilityData\":\\{\"label\":\"([^\"]+)\"\\}\\}")

        val videoMatcher = videoPattern.matcher(json)
        val titleMatcher = titlePattern.matcher(json)
        val channelMatcher = channelPattern.matcher(json)
        val durationMatcher = durationPattern.matcher(json)

        val videoIds = mutableListOf<String>()
        while (videoMatcher.find()) {
            videoIds.add(videoMatcher.group(1) ?: "")
        }

        val titles = mutableListOf<String>()
        while (titleMatcher.find()) {
            val title = titleMatcher.group(1) ?: ""
            // Clean up escaped unicode / HTML entities if any
            titles.add(cleanTitle(title))
        }

        val channels = mutableListOf<String>()
        while (channelMatcher.find()) {
            channels.add(channelMatcher.group(1) ?: "")
        }

        val durations = mutableListOf<String>()
        while (durationMatcher.find()) {
            durations.add(durationMatcher.group(1) ?: "")
        }

        // Match them up securely
        val size = videoIds.distinct().size
        val uniqueVideoIds = videoIds.distinct()

        for (i in 0 until uniqueVideoIds.size) {
            val id = uniqueVideoIds[i]
            if (id.isEmpty()) continue
            
            val title = titles.getOrNull(i) ?: "Video #$id"
            val channel = channels.getOrNull(i) ?: "YouTube Artist"
            val durationText = durations.getOrNull(i) ?: "03:30"
            
            list.add(
                PlaylistItem(
                    id = id,
                    title = title,
                    author = channel,
                    duration = formatDurationLabel(durationText),
                    thumbnailUrl = "https://img.youtube.com/vi/$id/hqdefault.jpg",
                    url = "https://www.youtube.com/watch?v=$id"
                )
            )
        }
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
            .replace("\\\"", "\"")
            .replace("\\\'", "'")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private fun formatDurationLabel(rawLabel: String): String {
        // e.g. "3 minutes, 40 seconds" -> "03:40"
        if (rawLabel.contains(":")) return rawLabel
        val minPattern = Pattern.compile("(\\d+)\\s*minute")
        val secPattern = Pattern.compile("(\\d+)\\s*second")
        
        val minMatcher = minPattern.matcher(rawLabel)
        val secMatcher = secPattern.matcher(rawLabel)
        
        val minutes = if (minMatcher.find()) minMatcher.group(1)?.toIntOrNull() ?: 0 else 0
        val seconds = if (secMatcher.find()) secMatcher.group(1)?.toIntOrNull() ?: 0 else 0
        
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Calls Cobalt converter to request the direct MP3 link for a YouTube ID.
     */
    suspend fun getMp3StreamUrl(videoId: String, isPremiumQuality: Boolean): String = withContext(Dispatchers.IO) {
        val videoUrl = "https://www.youtube.com/watch?v=$videoId"
        val bitrate = if (isPremiumQuality) "320" else "128"
        
        val response = CobaltClient.service.convertVideo(
            CobaltRequest(
                url = videoUrl,
                audioBitrate = bitrate,
                audioFormat = "mp3"
            )
        )

        if (response.status == "error") {
            throw Exception(response.text ?: "Cobalt conversion failed")
        }

        return@withContext response.url ?: throw Exception("No stream URL returned")
    }

    /**
     * Downloads an MP3 stream URL and registers it directly into the device's MediaStore library under the Music directory.
     */
    suspend fun downloadTrackToMediaStore(
        videoId: String,
        title: String,
        author: String,
        streamUrl: String,
        onProgress: (Float) -> Unit
    ): DownloadItem = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(streamUrl).build()
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Failed to download stream, server returned: ${response.code}")
        }
        
        val body = response.body ?: throw Exception("Response body is empty")
        val totalBytes = body.contentLength()
        
        val resolver = context.contentResolver
        val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        val cleanTitleName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$cleanTitleName.mp3")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, author)
            put(MediaStore.Audio.Media.ALBUM, "YT Sync Downloads")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/YouTube_Sync")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        
        val itemUri = resolver.insert(audioCollection, contentValues) 
            ?: throw Exception("Failed to create MediaStore entry")
        
        var success = false
        var downloadedSizeStr = "0.0 MB"
        
        try {
            resolver.openOutputStream(itemUri)?.use { outputStream ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        
                        if (totalBytes > 0) {
                            onProgress(totalDownloaded.toFloat() / totalBytes)
                        } else {
                            // Indeterminate download progress calculation
                            onProgress(-1f)
                        }
                    }
                    downloadedSizeStr = String.format("%.1f MB", totalDownloaded.toFloat() / (1024 * 1024))
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }
            success = true
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            throw e
        }

        val finalItem = DownloadItem(
            id = videoId,
            title = title,
            author = author,
            duration = "03:45", // Custom default fallback or updated dynamically
            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
            filePath = itemUri.toString(),
            fileSize = downloadedSizeStr,
            downloadedAt = System.currentTimeMillis()
        )
        
        dao.insertTrack(finalItem)
        return@withContext finalItem
    }

    suspend fun deleteTrack(item: DownloadItem) = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(item.filePath)
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error deleting from MediaStore", e)
        }
        dao.deleteTrack(item.id)
    }
}
