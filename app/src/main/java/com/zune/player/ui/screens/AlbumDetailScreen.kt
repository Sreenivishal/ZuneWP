package com.zune.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zune.player.data.AudioItem
import com.zune.player.ui.components.HeaderAction
import com.zune.player.ui.components.metroClickable
import com.zune.player.ui.theme.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

@Composable
fun AlbumDetailScreen(
    albumName: String,
    artistName: String,
    tracks: List<AudioItem>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlayNextAlbum: () -> Unit = {},
    onTrackClick: (Int) -> Unit,
    currentPlayingTitle: String? = null,
    onPlayNextTrack: (String) -> Unit = {},
    onAddToQueueTrack: (String) -> Unit = {},
    onAddToPlaylistTrack: (com.zune.player.data.AudioItem, String) -> Unit = { _, _ -> },
    playlists: List<String> = emptyList(),
    isPinnedTrack: (String) -> Boolean = { false },
    onPinTrack: (String) -> Unit = {},
    onDownloadAlbum: (() -> Unit)? = null,
    onDownloadTrack: ((AudioItem) -> Unit)? = null
) {
    var songToAddToPlaylist by remember { mutableStateOf<com.zune.player.data.AudioItem?>(null) } // songToAddToPlaylist

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Background Image (Album Art)
        val firstTrackArt = tracks.firstOrNull()?.albumArtUri
        AsyncImage(
            model = firstTrackArt,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(20.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.4f
        )
        
        // Dark Gradient/Tint Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.zune.player.R.drawable.zune_back),
                contentDescription = "Back",
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .offset(x = (-20).dp, y = (-8).dp)
                    .size(80.dp)
                    .metroClickable { onBack() }
            )
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 24.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = albumName.lowercase(),
                    style = ZuneTypography.h1.copy(fontSize = 56.sp),
                    color = LocalZuneAccent.current,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Play, Shuffle, and Play Next buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeaderAction(
                    text = "play",
                    icon = Icons.Default.PlayArrow,
                    onClick = onPlayAll
                )
                HeaderAction(
                    text = "shuffle",
                    icon = Icons.Default.Shuffle,
                    onClick = onShuffleAll
                )
                HeaderAction(
                    text = "play next",
                    icon = Icons.Default.ArrowForward,
                    onClick = onPlayNextAlbum
                )
                if (onDownloadAlbum != null && tracks.isNotEmpty()) {
                    HeaderAction(
                        text = "download",
                        icon = Icons.Default.ArrowDownward,
                        onClick = onDownloadAlbum
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Track List - Vertical
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                    AlbumTrackCard(
                        track = track,
                        index = index + 1,
                        onClick = { onTrackClick(index) },
                        isCurrentlyPlaying = track.title.equals(currentPlayingTitle, ignoreCase = true),
                        onPin = { onPinTrack(track.title) },
                        isPinned = isPinnedTrack(track.title),
                        onPlayNext = { onPlayNextTrack(track.title) },
                        onAddToQueue = { onAddToQueueTrack(track.title) },
                        onAddToPlaylistClick = { songToAddToPlaylist = track },
                        onDownload = onDownloadTrack?.let { { it(track) } }
                    )
                }
            }
        }

        // Add to Playlist Dialog
        if (songToAddToPlaylist != null) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { songToAddToPlaylist = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, LocalZuneAccent.current)
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "add to playlist",
                            style = ZuneTypography.h2.copy(fontSize = 24.sp),
                            color = Color.White
                        )
                        if (playlists.isEmpty()) {
                            Text(
                                text = "no playlists available",
                                style = ZuneTypography.body1,
                                color = ZuneTextSecondary
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 240.dp)
                            ) {
                                items(playlists) { playlist ->
                                    Text(
                                        text = playlist.lowercase(),
                                        style = ZuneTypography.body1.copy(fontSize = 20.sp),
                                        color = Color.White,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .metroClickable {
                                                onAddToPlaylistTrack(songToAddToPlaylist!!, playlist)
                                                songToAddToPlaylist = null
                                            }
                                            .padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "cancel",
                                style = ZuneTypography.body1,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .metroClickable { songToAddToPlaylist = null }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumTrackCard(
    track: AudioItem,
    index: Int,
    onClick: () -> Unit,
    isCurrentlyPlaying: Boolean = false,
    onPin: () -> Unit = {},
    isPinned: Boolean = false,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onAddToPlaylistClick: () -> Unit = {},
    onDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(track) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { showMenu = true }
                    )
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = index.toString().padStart(2, '0'),
                style = ZuneTypography.h1.copy(fontSize = 48.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Thin),
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.width(72.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title.lowercase(),
                    style = ZuneTypography.h1.copy(fontSize = 36.sp),
                    color = if (isCurrentlyPlaying) LocalZuneAccent.current else ZuneTextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Text(
                    text = formatDuration(track.durationMs),
                    style = ZuneTypography.h4.copy(fontSize = 20.sp),
                    color = LocalZuneAccent.current
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Color(0xFF1A1A1A))
        ) {
            DropdownMenuItem(onClick = {
                showMenu = false
                onClick()
            }) {
                Text("play", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            if (onDownload != null) {
                DropdownMenuItem(onClick = {
                    showMenu = false
                    onDownload()
                }) {
                    Text("download", style = ZuneTypography.body1, color = ZuneTextPrimary)
                }
            }
            DropdownMenuItem(onClick = {
                showMenu = false
                onPin()
            }) {
                Text(if (isPinned) "unpin from home" else "pin to home", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            DropdownMenuItem(onClick = {
                showMenu = false
                onPlayNext()
            }) {
                Text("play next", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            DropdownMenuItem(onClick = {
                showMenu = false
                onAddToQueue()
            }) {
                Text("add to queue", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            DropdownMenuItem(onClick = {
                showMenu = false
                onAddToPlaylistClick()
            }) {
                Text("add to playlist", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / 1000) / 60
    return "%d:%02d".format(min, sec)
}
