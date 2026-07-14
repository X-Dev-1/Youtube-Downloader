package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.data.auth.UserSession
import com.example.data.model.DownloadItem
import com.example.data.repository.PlaylistItem
import com.example.ui.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomNavigationBar(
                            selectedTab = viewModel.currentTab,
                            onTabSelected = { viewModel.currentTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ObsidianBg)
                            .padding(innerPadding)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // High Contrast Header
                            HeaderBar(viewModel)

                            // Active Screen Tab Switcher
                            Box(modifier = Modifier.weight(1f)) {
                                when (viewModel.currentTab) {
                                    AppTab.DOWNLOAD -> DownloadScreen(viewModel)
                                    AppTab.LIBRARY -> LibraryScreen(viewModel)
                                    AppTab.ACCOUNT -> AccountScreen(viewModel)
                                }
                            }
                        }

                        // Floating / Dynamic Music Player drawer
                        AnimatedVisibility(
                            visible = viewModel.currentPlayingTrack != null,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeIn(),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            viewModel.currentPlayingTrack?.let { track ->
                                MiniPlayerDrawer(
                                    track = track,
                                    isPlaying = viewModel.isPlaying,
                                    progress = viewModel.playbackProgress,
                                    currentPos = viewModel.currentPositionMs,
                                    duration = viewModel.durationMs,
                                    onPlayPauseToggle = { viewModel.togglePlayPause() },
                                    onStopPlayback = { viewModel.stopPlayback() },
                                    onSeek = { viewModel.seekTo(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderBar(viewModel: MainViewModel) {
    val session by viewModel.userSession.collectAsStateWithLifecycle()
    val isPremium = session?.isYouTubePremium == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "SonicRip",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                color = SophisticatedRose,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 1.em
            )
            Text(
                text = "PLAYLIST MASTER",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = if (isPremium) "Premium" else "Standard",
                color = if (isPremium) SophisticatedViolet else Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )
            
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(BurgundyBg)
                    .border(1.dp, SophisticatedRose.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (session != null) {
                    AsyncImage(
                        model = session?.profilePictureUrl ?: "https://api.dicebear.com/7.x/avataaars/svg?seed=Felix",
                        contentDescription = "User Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "Guest",
                        tint = SophisticatedRose,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    NavigationBar(
        containerColor = ObsidianBg,
        tonalElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f), shape = RectangleShape)
    ) {
        NavigationBarItem(
            selected = selectedTab == AppTab.DOWNLOAD,
            onClick = { onTabSelected(AppTab.DOWNLOAD) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == AppTab.DOWNLOAD) Icons.Filled.Download else Icons.Outlined.Download,
                    contentDescription = "Download Tab"
                )
            },
            label = { Text("Download") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SophisticatedViolet,
                selectedTextColor = SophisticatedViolet,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
                indicatorColor = BurgundyBg
            )
        )

        NavigationBarItem(
            selected = selectedTab == AppTab.LIBRARY,
            onClick = { onTabSelected(AppTab.LIBRARY) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == AppTab.LIBRARY) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic,
                    contentDescription = "Library Tab"
                )
            },
            label = { Text("My Library") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SophisticatedViolet,
                selectedTextColor = SophisticatedViolet,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
                indicatorColor = BurgundyBg
            )
        )

        NavigationBarItem(
            selected = selectedTab == AppTab.ACCOUNT,
            onClick = { onTabSelected(AppTab.ACCOUNT) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == AppTab.ACCOUNT) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                    contentDescription = "Premium Tab"
                )
            },
            label = { Text("Premium") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SophisticatedViolet,
                selectedTextColor = SophisticatedViolet,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
                indicatorColor = BurgundyBg
            )
        )
    }
}

// --- TAB 1: DOWNLOAD SCREEN ---

@Composable
fun DownloadScreen(viewModel: MainViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()
    val isPremium = userSession?.isYouTubePremium == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Link paste section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BorderStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Download Playlist or Video",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Paste a YouTube playlist or single video link to instantly convert and download to your device's music library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = viewModel.playlistUrl,
                        onValueChange = { viewModel.playlistUrl = it },
                        placeholder = { Text("Enter YouTube playlist or video URL", color = TextSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("playlist_url_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SophisticatedViolet,
                            unfocusedBorderColor = BorderStroke,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.4f)
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (viewModel.playlistUrl.isNotEmpty()) {
                                IconButton(onClick = { viewModel.playlistUrl = "" }) {
                                    Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear text", tint = TextSecondary)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.scanPlaylist() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("scan_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SophisticatedRose,
                            contentColor = BurgundyText
                        ),
                        shape = CircleShape,
                        enabled = !viewModel.isScanning
                    ) {
                        if (viewModel.isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BurgundyText, strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = "Scan icon")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Link & Extract Tracks", fontWeight = FontWeight.Black)
                        }
                    }

                    viewModel.scanError?.let { error ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Filled.Error, contentDescription = "Error", tint = SophisticatedRose, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = error, color = SophisticatedRose, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Popular testing tags
        item {
            Column {
                Text(
                    text = "Quick Playlist Demo Hooks",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlaylistDemoTag(
                        title = "Chill Lofi Coding",
                        url = "https://www.youtube.com/playlist?list=PLofmGfWR76EGU4pP6fHOn6pPh4R-62bsh",
                        onClick = { viewModel.playlistUrl = it }
                    )
                    PlaylistDemoTag(
                        title = "Synthwave Beats",
                        url = "https://www.youtube.com/playlist?list=PLofmGfWR76EFd1558yX4819g7_PFr866q",
                        onClick = { viewModel.playlistUrl = it }
                    )
                    PlaylistDemoTag(
                        title = "Ambient Study Space",
                        url = "https://www.youtube.com/playlist?list=PLofmGfWR76EEZzT10G2Y4M3eOaV98hNfX",
                        onClick = { viewModel.playlistUrl = it }
                    )
                }
            }
        }

        // Scanned Playlist Results Checklist
        if (viewModel.scannedVideos.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scanned Tracks (${viewModel.scannedVideos.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { viewModel.selectAllVideos(true) }) {
                            Text("Select All", color = SophisticatedViolet, fontSize = 13.sp)
                        }
                        TextButton(onClick = { viewModel.selectAllVideos(false) }) {
                            Text("Clear", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }

            items(viewModel.scannedVideos) { video ->
                PlaylistItemCard(
                    item = video,
                    onCheckedChange = { viewModel.toggleVideoSelection(video.id) }
                )
            }

            // Quality configuration and trigger button
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, BorderStroke),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Download Preferences",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Convert Audio Quality", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text(
                                    text = if (isPremium) "Hi-Fi 320kbps (Lossless)" else "Standard 128kbps (Unlock 320kbps with Premium)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isPremium) SophisticatedRose else TextSecondary
                                )
                            }

                            Icon(
                                imageVector = if (isPremium) Icons.Filled.WorkspacePremium else Icons.Filled.Lock,
                                contentDescription = "Premium Icon",
                                tint = if (isPremium) SophisticatedRose else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { viewModel.startDownloadOfSelected() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("download_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SophisticatedRose,
                                contentColor = BurgundyText
                            ),
                            shape = CircleShape
                        ) {
                            Icon(imageVector = Icons.Filled.DownloadForOffline, contentDescription = "Download offline")
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Convert & Save Selected Tracks",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Active downloads queue representation
        if (viewModel.activeDownloads.value.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, BorderStroke),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Downloads Manager",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            IconButton(onClick = { viewModel.clearDownloadQueue() }) {
                                Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = "Clear downloads", tint = TextSecondary)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        viewModel.activeDownloads.value.forEach { task ->
                            DownloadTaskRow(task = task)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun PlaylistDemoTag(title: String, url: String, onClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .background(CharcoalCard, RoundedCornerShape(20.dp))
            .border(1.dp, BorderStroke, RoundedCornerShape(20.dp))
            .clickable { onClick(url) }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.PlaylistPlay, contentDescription = "Playlist", tint = SophisticatedRose, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun PlaylistItemCard(
    item: PlaylistItem,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = SophisticatedViolet, uncheckedColor = TextSecondary)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Video Thumbnail
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = "Thumbnail",
                modifier = Modifier
                    .size(72.dp, 50.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1.5f)) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = item.author,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = item.duration,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun DownloadTaskRow(task: DownloadTask) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when (task.status) {
                        DownloadStatus.PENDING -> "Waiting in queue..."
                        DownloadStatus.CONVERTING -> "Converting Video to MP3..."
                        DownloadStatus.DOWNLOADING -> "Downloading audio stream..."
                        DownloadStatus.COMPLETED -> "Saved to Device Library!"
                        DownloadStatus.FAILED -> "Failed: ${task.errorMessage ?: "Unknown error"}"
                    },
                    color = when (task.status) {
                        DownloadStatus.COMPLETED -> Color.Green
                        DownloadStatus.FAILED -> SophisticatedRose
                        else -> TextSecondary
                    },
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))

            if (task.status == DownloadStatus.COMPLETED) {
                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = "Success", tint = Color.Green, modifier = Modifier.size(18.dp))
            } else if (task.status == DownloadStatus.FAILED) {
                Icon(imageVector = Icons.Filled.Cancel, contentDescription = "Failed", tint = SophisticatedRose, modifier = Modifier.size(18.dp))
            } else {
                Text(
                    text = "${(task.progress * 100).toInt()}%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { task.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (task.status == DownloadStatus.FAILED) SophisticatedRose else if (task.status == DownloadStatus.COMPLETED) Color.Green else SophisticatedViolet,
            trackColor = CharcoalCard
        )
    }
}

// --- TAB 2: MY MP3 LIBRARY SCREEN ---

@Composable
fun LibraryScreen(viewModel: MainViewModel) {
    val tracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()

    if (tracks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // High fidelity canvas drawing of a vinyl record
            Canvas(modifier = Modifier.size(140.dp)) {
                // Record disc
                drawCircle(color = Color(0xFF18181A), radius = size.minDimension / 2)
                // Ridges
                drawCircle(color = Color(0xFF262628), radius = size.minDimension / 2.5f, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                drawCircle(color = Color(0xFF262628), radius = size.minDimension / 3.5f, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                drawCircle(color = Color(0xFF262628), radius = size.minDimension / 5.2f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                // Sophisticated Rose & Burgundy label
                drawCircle(color = BurgundyBg, radius = size.minDimension / 8f)
                drawCircle(color = SophisticatedRose, radius = size.minDimension / 16f)
                // Center hole
                drawCircle(color = ObsidianBg, radius = size.minDimension / 30f)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Your MP3 Library is Empty",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tracks downloaded from playlists will show up here as MP3s registered directly in your Android Music directory.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.currentTab = AppTab.DOWNLOAD },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SophisticatedRose,
                    contentColor = BurgundyText
                ),
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add tracks")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan New Playlist", fontWeight = FontWeight.Black)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Saved Audio Files (${tracks.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tracks) { track ->
                    LibraryTrackRow(
                        track = track,
                        isPlayingNow = viewModel.currentPlayingTrack?.id == track.id,
                        onPlayClick = { viewModel.playTrack(track) },
                        onDeleteClick = { viewModel.deleteDownloadedTrack(track) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(140.dp)) // Extra space for sticky media player drawer
                }
            }
        }
    }
}

@Composable
fun LibraryTrackRow(
    track: DownloadItem,
    isPlayingNow: Boolean,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isPlayingNow) CharcoalCard else SlateCard),
        shape = RoundedCornerShape(16.dp),
        border = if (isPlayingNow) BorderStroke(1.dp, SophisticatedViolet) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Audio thumbnail art
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = "Thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Small indicator icon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlayingNow) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow,
                        contentDescription = "Status",
                        tint = if (isPlayingNow) SophisticatedViolet else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontWeight = FontWeight.Bold,
                    color = if (isPlayingNow) SophisticatedViolet else Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.author}  •  ${track.fileSize}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete Track", tint = TextSecondary)
            }
        }
    }
}

// --- DYNAMIC AUDIO PLAYER DRAWER ---

@Composable
fun MiniPlayerDrawer(
    track: DownloadItem,
    isPlaying: Boolean,
    progress: Float,
    currentPos: Long,
    duration: Long,
    onPlayPauseToggle: () -> Unit,
    onStopPlayback: () -> Unit,
    onSeek: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.dp, BorderStroke),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Track description and player buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = "Mini album art",
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Now Playing: ${track.author}",
                        color = SophisticatedViolet,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayPauseToggle) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = onStopPlayback) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop",
                            tint = TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Seek slider
            Slider(
                value = progress,
                onValueChange = onSeek,
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = SophisticatedViolet,
                    activeTrackColor = SophisticatedViolet,
                    inactiveTrackColor = CharcoalCard
                )
            )

            // Duration labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatMs(currentPos), color = TextSecondary, fontSize = 10.sp)
                Text(text = formatMs(duration), color = TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// --- TAB 3: ACCOUNT & PREMIUM CONTROL SCREEN ---

@Composable
fun AccountScreen(viewModel: MainViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val session by viewModel.userSession.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Advantage banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BorderStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.WorkspacePremium, contentDescription = "Premium logo", tint = SophisticatedRose, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "YouTube Account Sync Benefits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    PremiumPrivilegeItem(title = "Hi-Fi 320kbps MP3 Conversion", desc = "Enjoy high-fidelity crystal clear audio files.")
                    PremiumPrivilegeItem(title = "High Speed Multithread Downloader", desc = "Parallel downloads speed up playlist conversion up to 5x.")
                    PremiumPrivilegeItem(title = "Bulk Playlist Extraction Support", desc = "Extract up to 100 tracks from a single YouTube playlist.")
                }
            }
        }

        // Authentication status and Google Button
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, BorderStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Identity & Subscription Scan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sign in using your Google / YouTube Account to scanning premium status and authenticate cloud downloads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (session == null) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.authManager.signInWithGoogle {
                                        Toast.makeText(context, "Google Console integration bypassed for local dev mode.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("google_login"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Login,
                                contentDescription = "Google Logo",
                                tint = ObsidianBg
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Sign In with Google", color = ObsidianBg, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Configuration explanation
                        Box(
                            modifier = Modifier
                                .background(BurgundyBg, RoundedCornerShape(16.dp))
                                .border(1.dp, SophisticatedRose.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Filled.Info, contentDescription = "Developer Notice", tint = SophisticatedRose, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Local Dev Bypass Console", color = SophisticatedRose, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "In an AI Studio virtual container, the active debug SHA-1 of the APK requires GCP Console registration to complete live Google Sign-In. Use the simulation console below to test both Standard and Premium downloader features instantly!",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            viewModel.authManager.loginWithDeveloperAccount(
                                                email = "prince4629@gmail.com",
                                                displayName = "Prince VIP Tester",
                                                isPremium = true
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SophisticatedRose,
                                            contentColor = BurgundyText
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Log in Premium", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.authManager.loginWithDeveloperAccount(
                                                email = "standard_user@gmail.com",
                                                displayName = "Standard User",
                                                isPremium = false
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = BorderStroke),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Log in Standard", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    } else {
                        // User Signed In Display
                        UserProfileDisplay(
                            session = session!!,
                            onLogout = { viewModel.authManager.logout() }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
fun PremiumPrivilegeItem(title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Check",
            tint = Color.Green,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(text = desc, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
fun UserProfileDisplay(
    session: UserSession,
    onLogout: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = session.profilePictureUrl,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (session.isYouTubePremium) SophisticatedRose else Color.Gray, CircleShape)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = session.displayName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Text(text = session.email, color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Subscription Tier Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (session.isYouTubePremium) {
                        Brush.horizontalGradient(listOf(BurgundyBg, SophisticatedViolet))
                    } else {
                        Brush.horizontalGradient(listOf(CharcoalCard, CharcoalCard))
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (session.isYouTubePremium) Icons.Filled.WorkspacePremium else Icons.Filled.Info,
                    contentDescription = "Premium tier flag",
                    tint = if (session.isYouTubePremium) SophisticatedRose else Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (session.isYouTubePremium) "YouTube Subscription: ACTIVE PREMIUM" else "YouTube Subscription: STANDARD FREE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = session.subscriptionType,
                        color = if (session.isYouTubePremium) SophisticatedRose else TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BorderStroke),
            shape = CircleShape
        ) {
            Text("Sign Out Google Account", color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}
