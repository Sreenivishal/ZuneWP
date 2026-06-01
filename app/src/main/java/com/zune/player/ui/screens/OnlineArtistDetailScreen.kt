package com.zune.player.ui.screens

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zune.player.data.AudioItem
import com.zune.player.data.OnlineAlbum
import com.zune.player.data.OnlineSong
import com.zune.player.ui.components.metroClickable
import com.zune.player.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnlineArtistDetailScreen(
    artistName: String,
    artworkUrl: String,
    topSongs: List<OnlineSong>,
    albums: List<OnlineAlbum>,
    onBack: () -> Unit,
    onSongClick: (OnlineSong) -> Unit,
    onSongAddToQueue: (OnlineSong) -> Unit,
    onSongAddToPlaylist: (AudioItem, String) -> Unit,
    onAlbumClick: (OnlineAlbum) -> Unit,
    playlists: List<String>,
    currentPlayingTitle: String? = null,
    onSongDownload: ((OnlineSong) -> Unit)? = null,
    onAlbumDownload: ((OnlineAlbum) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val pages = listOf("songs", "albums")
    val pagerState = rememberPagerState(initialPage = 0) { pages.size }
    val tabWidths = remember { mutableStateMapOf<Int, Float>() }
    var songToAddToPlaylist by remember { mutableStateOf<AudioItem?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Blur background
        if (artworkUrl.isNotEmpty()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.35f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
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
            // Header Row: Artist Name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 24.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = artistName.lowercase(),
                    style = ZuneTypography.h1.copy(fontSize = 56.sp),
                    color = LocalZuneAccent.current,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Tabs/Pivots: songs, albums
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clipToBounds()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(maxWidth = Constraints.Infinity))
                            layout(constraints.maxWidth, placeable.height) {
                                var offsetPx = 0f
                                val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                val activePageIndex = pageOffset.toInt()
                                val fraction = pageOffset - activePageIndex
 
                                for (i in 0 until activePageIndex) {
                                    offsetPx += (tabWidths[i] ?: 0f)
                                }
                                if (fraction > 0f) {
                                    offsetPx += (tabWidths[activePageIndex] ?: 0f) * fraction
                                } else if (fraction < 0f && activePageIndex > 0) {
                                    offsetPx += (tabWidths[activePageIndex - 1] ?: 0f) * fraction
                                }
                                placeable.place(x = -offsetPx.toInt(), y = 0)
                            }
                        }
                        .padding(start = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    pages.forEachIndexed { index, title ->
                        val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        val distance = kotlin.math.abs(pageOffset - index)
                        val alpha = (1f - distance * 0.6f).coerceIn(0.4f, 1f)

                        Text(
                            text = title,
                            style = ZuneTypography.h2.copy(
                                fontFamily = SegoeUiLightFontFamily,
                                fontSize = 32.sp
                            ),
                            color = Color.White.copy(alpha = alpha),
                            modifier = Modifier
                                .metroClickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                }
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    val spacingPx = 24.dp.toPx()
                                    tabWidths[index] = placeable.width + spacingPx
                                    layout(placeable.width, placeable.height) {
                                        placeable.place(0, 0)
                                    }
                                }
                        )
                    }
                }
            }

            // Horizontal Pager for sections
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        // Songs List
                        if (topSongs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("no songs found", style = ZuneTypography.body1, color = ZuneTextSecondary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(topSongs, key = { index, song -> "${song.trackId}_$index" }) { index, song ->
                                    ArtistSongCard(
                                        song = song,
                                        index = index + 1,
                                        onClick = { onSongClick(song) },
                                        onAddToQueue = { onSongAddToQueue(song) },
                                        onAddToPlaylist = {
                                            val playItem = AudioItem(
                                                id = -song.trackId,
                                                title = song.title,
                                                artist = song.artist,
                                                album = song.album,
                                                uri = Uri.parse("zune://online/${song.previewUrl}"),
                                                albumArtUri = if (song.artworkUrl.isNotEmpty()) Uri.parse(song.artworkUrl) else null,
                                                durationMs = song.durationMs
                                            )
                                            songToAddToPlaylist = playItem
                                        },
                                        isCurrentlyPlaying = song.title.equals(currentPlayingTitle, ignoreCase = true),
                                        onDownload = onSongDownload?.let { { it(song) } }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Albums List
                        if (albums.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("no albums found", style = ZuneTypography.body1, color = ZuneTextSecondary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(albums, key = { index, album -> "${album.browseId}_$index" }) { index, album ->
                                    ArtistAlbumCard(
                                        album = album,
                                        onClick = { onAlbumClick(album) },
                                        onDownloadAlbum = onAlbumDownload?.let { { it(album) } }
                                    )
                                }
                            }
                        }
                    }
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
                                                onSongAddToPlaylist(songToAddToPlaylist!!, playlist)
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
fun ArtistSongCard(
    song: OnlineSong,
    index: Int,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    isCurrentlyPlaying: Boolean,
    onDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(song) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showMenu = true }
                )
            }
            .padding(vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = index.toString().padStart(2, '0'),
                style = ZuneTypography.h1.copy(fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Thin),
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.width(48.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title.lowercase(),
                    style = ZuneTypography.h4.copy(fontSize = 24.sp),
                    color = if (isCurrentlyPlaying) LocalZuneAccent.current else ZuneTextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = song.album.lowercase(),
                    style = ZuneTypography.body2,
                    color = ZuneTextSecondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                onAddToQueue()
            }) {
                Text("add to queue", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            DropdownMenuItem(onClick = {
                showMenu = false
                onAddToPlaylist()
            }) {
                Text("add to playlist", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
        }
    }
}

@Composable
fun ArtistAlbumCard(
    album: OnlineAlbum,
    onClick: () -> Unit,
    onDownloadAlbum: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(album) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { showMenu = true }
                    )
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (album.artworkUrl.isNotEmpty()) {
                AsyncImage(
                    model = album.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFF1E1E1E))
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title.lowercase(),
                    style = ZuneTypography.h4.copy(fontSize = 24.sp),
                    color = ZuneTextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = album.year,
                    style = ZuneTypography.body2,
                    color = ZuneTextSecondary,
                    maxLines = 1
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
                Text("view album", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            if (onDownloadAlbum != null) {
                DropdownMenuItem(onClick = {
                    showMenu = false
                    onDownloadAlbum()
                }) {
                    Text("download album", style = ZuneTypography.body1, color = ZuneTextPrimary)
                }
            }
        }
    }
}
