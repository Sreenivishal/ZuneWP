package com.zune.player.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import com.zune.player.ui.theme.ZuneIcons
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
import com.zune.player.LocalSharedTransitionScope
import com.zune.player.LocalAnimatedVisibilityScope
import com.zune.player.ui.theme.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@Composable
fun AlbumDetailScreen(
    albumName: String,
    artistName: String,
    tracks: List<AudioItem>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlayNextAlbum: () -> Unit = {},
    onAddToQueueAlbum: () -> Unit = {},
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
        val sharedTransitionScope = LocalSharedTransitionScope.current
        val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .metroClickable { onBack() }
            ) {
                val firstTrackArt = tracks.firstOrNull()?.albumArtUri
                if (firstTrackArt != null) {
                    AsyncImage(
                        model = firstTrackArt,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.BottomCenter
                    )
                }
            }
            var albumFontSize by remember(albumName) { mutableStateOf(56.sp) }
            var albumReadyToDraw by remember(albumName) { mutableStateOf(false) }

            var artistFontSize by remember(artistName) { mutableStateOf(32.sp) }
            var artistReadyToDraw by remember(artistName) { mutableStateOf(false) }

            // Header - Album Name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = albumName.uppercase(),
                    style = ZuneTypography.h1.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.zune.player.R.font.segoeuithibd)),
                        fontSize = albumFontSize,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        letterSpacing = (-1).sp
                    ),
                    color = LocalZuneAccent.current,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    onTextLayout = { textLayoutResult ->
                        if (textLayoutResult.hasVisualOverflow) {
                            val nextFontSize = (albumFontSize.value - 2f).sp
                            if (nextFontSize.value >= 11f) {
                                albumFontSize = nextFontSize
                            } else {
                                albumReadyToDraw = true
                            }
                        } else {
                            albumReadyToDraw = true
                        }
                    },
                    modifier = Modifier.drawWithContent {
                        if (albumReadyToDraw) {
                            drawContent()
                        }
                    }
                )
            }

            // Header - Artist Name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = artistName.uppercase(),
                    style = ZuneTypography.h2.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.zune.player.R.font.segoeuithibd)),
                        fontSize = artistFontSize,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    onTextLayout = { textLayoutResult ->
                        if (textLayoutResult.hasVisualOverflow) {
                            val nextFontSize = (artistFontSize.value - 1.5f).sp
                            if (nextFontSize.value >= 11f) {
                                artistFontSize = nextFontSize
                            } else {
                                artistReadyToDraw = true
                            }
                        } else {
                            artistReadyToDraw = true
                        }
                    },
                    modifier = Modifier.drawWithContent {
                        if (artistReadyToDraw) {
                            drawContent()
                        }
                    }
                )
            }

            // Play, Shuffle, and Play Next buttons (Header Actions row)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeaderAction(
                        text = "play",
                        icon = ZuneIcons.Play,
                        onClick = onPlayAll
                    )
                    HeaderAction(
                        text = "shuffle",
                        icon = ZuneIcons.Shuffle,
                        onClick = onShuffleAll
                    )
                    HeaderAction(
                        text = "play next",
                        icon = ZuneIcons.ArrowForward,
                        onClick = onPlayNextAlbum
                    )
                    HeaderAction(
                        text = "add to queue",
                        icon = ZuneIcons.QueueMusic,
                        onClick = onAddToQueueAlbum
                    )
                    if (onDownloadAlbum != null && tracks.isNotEmpty()) {
                        HeaderAction(
                            text = "download",
                            icon = ZuneIcons.ArrowDownward,
                            onClick = onDownloadAlbum
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Track List - Vertical
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                verticalArrangement = Arrangement.spacedBy(4.dp),
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember(context) { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(track) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = {
                            if (prefs.getBoolean("haptic_feedback_enabled", true)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            showMenu = true
                        }
                    )
                }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = ZuneTypography.h1.copy(fontSize = 24.sp),
                    color = if (isCurrentlyPlaying) LocalZuneAccent.current else ZuneTextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Text(
                    text = formatDuration(track.durationMs),
                    style = ZuneTypography.h4.copy(fontSize = 14.sp),
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
