package com.zune.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zune.player.data.AudioItem
import com.zune.player.ui.components.HeaderAction
import com.zune.player.ui.components.metroClickable
import com.zune.player.ui.theme.*

@Composable
fun PlaylistDetailScreen(
    playlistName: String,
    tracks: List<AudioItem>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onTrackClick: (Int) -> Unit,
    currentPlayingTitle: String? = null
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    text = playlistName.lowercase(),
                    style = ZuneTypography.h1.copy(fontSize = 56.sp),
                    color = LocalZuneAccent.current,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Play and Shuffle buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
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
            }

            // Track List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(tracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                    PlaylistTrackItem(
                        track = track,
                        onClick = { onTrackClick(index) },
                        isCurrentlyPlaying = track.title.equals(currentPlayingTitle, ignoreCase = true)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistTrackItem(
    track: AudioItem,
    onClick: () -> Unit,
    isCurrentlyPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .metroClickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = track.title.lowercase(),
                style = ZuneTypography.h4.copy(fontSize = 24.sp),
                color = if (isCurrentlyPlaying) LocalZuneAccent.current else ZuneTextPrimary,
                maxLines = 1
            )
            Text(
                text = track.artist.lowercase(),
                style = ZuneTypography.body2,
                color = ZuneTextSecondary
            )
        }
    }
}
