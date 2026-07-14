package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.auth.GoogleAuthManager
import com.example.data.auth.UserSession
import com.example.data.local.AppDatabase
import com.example.data.model.DownloadItem
import com.example.data.repository.DownloadRepository
import com.example.data.repository.PlaylistItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    DOWNLOAD,
    LIBRARY,
    ACCOUNT
}

data class DownloadTask(
    val videoId: String,
    val title: String,
    val author: String,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val errorMessage: String? = null
)

enum class DownloadStatus {
    PENDING,
    CONVERTING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "tracks_database"
    ).build()

    private val repository = DownloadRepository(application, db.downloadItemDao())
    val authManager = GoogleAuthManager(application)

    // UI Tab State
    var currentTab by mutableStateOf(AppTab.DOWNLOAD)

    // User session
    val userSession: StateFlow<UserSession?> = authManager.userSession

    // Library downloaded list
    val downloadedTracks: StateFlow<List<DownloadItem>> = repository.allDownloadedTracks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Playlist Scanning States
    var playlistUrl by mutableStateOf("")
    var isScanning by mutableStateOf(false)
    var scannedVideos by mutableStateOf<List<PlaylistItem>>(emptyList())
    var scanError by mutableStateOf<String?>(null)

    // Active Downloads Queue State
    var activeDownloads = mutableStateOf<List<DownloadTask>>(emptyList())
        private set

    // Media Player States
    private var mediaPlayer: MediaPlayer? = null
    var currentPlayingTrack by mutableStateOf<DownloadItem?>(null)
    var isPlaying by mutableStateOf(false)
    var playbackProgress by mutableStateOf(0f) // 0.0 to 1.0
    var currentPositionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)

    private var progressJob: Job? = null

    init {
        // Observe playing status and progress
        startProgressTracker()
    }

    // --- PLAYLIST ACTIONS ---

    fun scanPlaylist() {
        if (playlistUrl.isBlank()) {
            scanError = "Please enter a valid YouTube link"
            return
        }
        
        viewModelScope.launch {
            isScanning = true
            scanError = null
            scannedVideos = emptyList()
            
            try {
                val videos = repository.fetchPlaylistVideos(playlistUrl)
                if (videos.isEmpty()) {
                    // Try parsing as single video ID
                    val singleVideoId = extractSingleVideoId(playlistUrl)
                    if (singleVideoId != null) {
                        scannedVideos = listOf(
                            PlaylistItem(
                                id = singleVideoId,
                                title = "YouTube Video ($singleVideoId)",
                                author = "YouTube Audio Stream",
                                duration = "03:30",
                                thumbnailUrl = "https://img.youtube.com/vi/$singleVideoId/hqdefault.jpg",
                                url = playlistUrl,
                                isSelected = true
                            )
                        )
                    } else {
                        scanError = "No videos could be retrieved from this link. Make sure the playlist is Public."
                    }
                } else {
                    scannedVideos = videos
                }
            } catch (e: Exception) {
                scanError = "Scan failed: ${e.message}"
            } finally {
                isScanning = false
            }
        }
    }

    private fun extractSingleVideoId(url: String): String? {
        val pattern = "^(?:https?:\\/\\/)?(?:www\\.)?(?:youtube\\.com\\/(?:[^\\/\\n\\s]+\\/\\S+\\/|(?:v|e(?:mbed)?)\\/|\\S*?[?&]v=)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})"
        val regex = Regex(pattern)
        val matchResult = regex.find(url)
        return matchResult?.groupValues?.get(1)
    }

    fun toggleVideoSelection(videoId: String) {
        scannedVideos = scannedVideos.map {
            if (it.id == videoId) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun selectAllVideos(select: Boolean) {
        scannedVideos = scannedVideos.map { it.copy(isSelected = select) }
    }

    // --- DOWNLOAD ACTIONS ---

    fun startDownloadOfSelected() {
        val selected = scannedVideos.filter { it.isSelected }
        if (selected.isEmpty()) return

        val isPremium = userSession.value?.isYouTubePremium == true

        viewModelScope.launch {
            // Append tasks to queue
            val newTasks = selected.map { video ->
                DownloadTask(
                    videoId = video.id,
                    title = video.title,
                    author = video.author,
                    status = DownloadStatus.PENDING
                )
            }
            activeDownloads.value = activeDownloads.value + newTasks

            // Clear scanned selection to show download manager
            scannedVideos = emptyList()

            // Run tasks sequentially
            newTasks.forEach { task ->
                processDownloadTask(task, isPremium)
            }
        }
    }

    private suspend fun processDownloadTask(task: DownloadTask, isPremium: Boolean) {
        updateTaskStatus(task.videoId, DownloadStatus.CONVERTING, 0.1f)

        try {
            // Step 1: Request Cobalt MP3 direct Stream URL
            val streamUrl = repository.getMp3StreamUrl(task.videoId, isPremium)
            
            updateTaskStatus(task.videoId, DownloadStatus.DOWNLOADING, 0.3f)

            // Step 2: Download Stream to MediaStore with real-time stream callbacks
            repository.downloadTrackToMediaStore(
                videoId = task.videoId,
                title = task.title,
                author = task.author,
                streamUrl = streamUrl
            ) { progress ->
                // Map intermediate download progress (0.3 -> 1.0)
                val mappedProgress = 0.3f + (progress * 0.7f)
                updateTaskStatus(task.videoId, DownloadStatus.DOWNLOADING, mappedProgress)
            }

            updateTaskStatus(task.videoId, DownloadStatus.COMPLETED, 1.0f)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error downloading video ${task.videoId}", e)
            updateTaskStatus(task.videoId, DownloadStatus.FAILED, 0f, e.message ?: "Conversion Error")
        }
    }

    private fun updateTaskStatus(videoId: String, status: DownloadStatus, progress: Float, errorMsg: String? = null) {
        activeDownloads.value = activeDownloads.value.map {
            if (it.videoId == videoId) {
                it.copy(status = status, progress = progress, errorMessage = errorMsg)
            } else {
                it
            }
        }
    }

    fun clearDownloadQueue() {
        activeDownloads.value = emptyList()
    }

    fun deleteDownloadedTrack(track: DownloadItem) {
        viewModelScope.launch {
            if (currentPlayingTrack?.id == track.id) {
                stopPlayback()
            }
            repository.deleteTrack(track)
        }
    }

    // --- AUDIO PLAYER ACTIONS ---

    fun playTrack(track: DownloadItem) {
        viewModelScope.launch {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(getApplication(), Uri.parse(track.filePath))
                    prepare()
                    start()
                }
                currentPlayingTrack = track
                isPlaying = true
                durationMs = mediaPlayer?.duration?.toLong() ?: 0L
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to play audio from URI", e)
                // Fallback attempt to load directly
                currentPlayingTrack = null
                isPlaying = false
            }
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            isPlaying = false
        } else {
            player.start()
            isPlaying = true
        }
    }

    fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentPlayingTrack = null
        isPlaying = false
        playbackProgress = 0f
    }

    fun seekTo(progress: Float) {
        val player = mediaPlayer ?: return
        val targetMs = (progress * durationMs).toInt()
        player.seekTo(targetMs)
        playbackProgress = progress
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(500)
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        currentPositionMs = player.currentPosition.toLong()
                        if (durationMs > 0) {
                            playbackProgress = currentPositionMs.toFloat() / durationMs
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        db.close()
    }
}
