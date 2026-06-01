package com.zune.player.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zune.player.data.AudioItem
import com.zune.player.data.OnlineSong
import com.zune.player.ui.components.metroClickable
import com.zune.player.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkInfo
import com.zune.player.data.DownloadWorker

@Composable
fun SearchScreen(
    audioItems: List<AudioItem>,
    onBack: () -> Unit,
    onTrackClick: (AudioItem) -> Unit,
    onAddToQueue: (AudioItem) -> Unit,
    onPlayNext: (AudioItem) -> Unit,
    playlists: List<String>,
    onAddToPlaylist: (AudioItem, String) -> Unit, // audioItem, playlistName
    onOnlineAlbumClick: (com.zune.player.data.OnlineAlbum) -> Unit,
    onOnlineArtistClick: (com.zune.player.data.OnlineArtist) -> Unit,
    currentPlayingTitle: String? = null,
    onLibraryChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pages = listOf("collection", "online")
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0) { pages.size }
    val tabWidths = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

    // Search query states
    var collectionQuery by remember { mutableStateOf("") }
    var onlineQuery by remember { mutableStateOf("") }

    // Online search results state
    var onlineResults by remember { mutableStateOf<List<OnlineSong>>(emptyList()) }
    var onlineAlbums by remember { mutableStateOf<List<com.zune.player.data.OnlineAlbum>>(emptyList()) }
    var onlineArtists by remember { mutableStateOf<List<com.zune.player.data.OnlineArtist>>(emptyList()) }
    var selectedSearchType by remember { mutableStateOf("songs") } // "songs", "albums", "artists"
    var isSearchingOnline by remember { mutableStateOf(false) }

    // Observe WorkManager for downloads
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosByTagFlow("download_song")
        .collectAsState(initial = emptyList())

    val activeDownloads = remember(workInfos) {
        workInfos.filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }

    val completedWorkIds = remember { mutableSetOf<java.util.UUID>() }
    val ourEnqueuedWorkIds = remember { mutableSetOf<java.util.UUID>() }
    val enqueuedTrackTitles = remember { androidx.compose.runtime.mutableStateMapOf<java.util.UUID, String>() }

    val activeDownloadingTitle = remember(activeDownloads, enqueuedTrackTitles) {
        val activeId = activeDownloads.firstOrNull()?.id
        if (activeId != null) enqueuedTrackTitles[activeId] else null
    }

    LaunchedEffect(workInfos) {
        workInfos.forEach { workInfo ->
            if (ourEnqueuedWorkIds.contains(workInfo.id) && workInfo.state.isFinished && !completedWorkIds.contains(workInfo.id)) {
                completedWorkIds.add(workInfo.id)
                val title = enqueuedTrackTitles[workInfo.id] ?: "Song"
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    Toast.makeText(context, "Added \"$title\" to library", Toast.LENGTH_SHORT).show()
                    onLibraryChanged()
                } else if (workInfo.state == WorkInfo.State.FAILED) {
                    Toast.makeText(context, "Failed to download \"$title\"", Toast.LENGTH_SHORT).show()
                }
                enqueuedTrackTitles.remove(workInfo.id)
            }
        }
    }

    // Dialog state for local collection
    var songToAddToPlaylist by remember { mutableStateOf<AudioItem?>(null) } // AudioItem

    // Filter local tracks dynamically
    val filteredTracks = remember(collectionQuery, audioItems) {
        if (collectionQuery.isBlank()) {
            emptyList()
        } else {
            val q = collectionQuery.lowercase().trim()
            audioItems.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }.sortedBy { it.title }
        }
    }

    // Helper to search music from iTunes
    fun performOnlineSearch() {
        if (onlineQuery.isBlank()) return
        isSearchingOnline = true
        coroutineScope.launch {
            try {
                val searchRepository = org.koin.core.context.GlobalContext.get().get<com.maxrave.domain.repository.SearchRepository>()
                when (selectedSearchType) {
                    "songs" -> {
                        var resultsList = emptyList<com.zune.player.data.OnlineSong>()
                        val resource = searchRepository.getSearchDataSong(onlineQuery).firstOrNull { r ->
                            r is com.maxrave.domain.utils.Resource.Success<*> || r is com.maxrave.domain.utils.Resource.Error<*>
                        }
                        if (resource is com.maxrave.domain.utils.Resource.Success<*>) {
                            resultsList = (resource.data as? ArrayList<com.maxrave.domain.data.model.searchResult.songs.SongsResult>)?.map { song ->
                                com.zune.player.data.OnlineSong(
                                    trackId = song.videoId.hashCode().toLong(),
                                    title = song.title ?: "Unknown Title",
                                    artist = song.artists?.firstOrNull()?.name ?: "Unknown Artist",
                                    album = song.album?.name ?: "Unknown Album",
                                    previewUrl = song.videoId,
                                    artworkUrl = song.thumbnails?.lastOrNull()?.url ?: "",
                                    durationMs = (song.durationSeconds ?: 0) * 1000L
                                )
                            } ?: emptyList()
                        }
                        withContext(Dispatchers.Main) {
                            onlineResults = resultsList
                            isSearchingOnline = false
                        }
                    }
                    "albums" -> {
                        var albumsList = emptyList<com.zune.player.data.OnlineAlbum>()
                        val resource = searchRepository.getSearchDataAlbum(onlineQuery).firstOrNull { r ->
                            r is com.maxrave.domain.utils.Resource.Success<*> || r is com.maxrave.domain.utils.Resource.Error<*>
                        }
                        if (resource is com.maxrave.domain.utils.Resource.Success<*>) {
                            albumsList = (resource.data as? ArrayList<com.maxrave.domain.data.model.searchResult.albums.AlbumsResult>)?.map { album ->
                                com.zune.player.data.OnlineAlbum(
                                    browseId = album.browseId,
                                    title = album.title,
                                    artist = album.artists.firstOrNull()?.name ?: "Unknown Artist",
                                    year = album.year,
                                    artworkUrl = album.thumbnails.lastOrNull()?.url ?: ""
                                )
                            } ?: emptyList()
                        }
                        withContext(Dispatchers.Main) {
                            onlineAlbums = albumsList
                            isSearchingOnline = false
                        }
                    }
                    "artists" -> {
                        var artistsList = emptyList<com.zune.player.data.OnlineArtist>()
                        val resource = searchRepository.getSearchDataArtist(onlineQuery).firstOrNull { r ->
                            r is com.maxrave.domain.utils.Resource.Success<*> || r is com.maxrave.domain.utils.Resource.Error<*>
                        }
                        if (resource is com.maxrave.domain.utils.Resource.Success<*>) {
                            artistsList = (resource.data as? ArrayList<com.maxrave.domain.data.model.searchResult.artists.ArtistsResult>)?.map { artist ->
                                com.zune.player.data.OnlineArtist(
                                    browseId = artist.browseId,
                                    name = artist.artist,
                                    subscribers = "",
                                    artworkUrl = artist.thumbnails.lastOrNull()?.url ?: ""
                                )
                            } ?: emptyList()
                        }
                        withContext(Dispatchers.Main) {
                            onlineArtists = artistsList
                            isSearchingOnline = false
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isSearchingOnline = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. Back Button (Zune HD style)
            Image(
                painter = painterResource(id = com.zune.player.R.drawable.zune_back),
                contentDescription = "Back",
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .offset(x = (-20).dp, y = (-8).dp)
                    .size(80.dp)
                    .metroClickable { onBack() }
            )

            // Header Row: Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 0.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SEARCH",
                    style = ZuneTypography.h4.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ZuneTextSecondary
                )
            }

            // Dynamic sliding tabs
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
                        
                        val textColor = Color.White.copy(alpha = alpha)
                        val textStyle = ZuneTypography.h1.copy(
                            fontFamily = SegoeUiLightFontFamily,
                            fontSize = 56.sp
                        )
                        
                        Text(
                            text = title,
                            style = textStyle,
                            color = textColor,
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

            // Downloading active progress indicator
            if (activeDownloadingTitle != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalZuneAccent.current)
                        .padding(vertical = 8.dp, horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "downloading \"$activeDownloadingTitle\" to library...",
                        style = ZuneTypography.body2.copy(color = Color.White, fontSize = 13.sp)
                    )
                }
            }

            // Pager content
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> {
                        // Collection Page
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                        ) {
                            OutlinedTextField(
                                value = collectionQuery,
                                onValueChange = { collectionQuery = it },
                                placeholder = { Text("search songs, albums", color = Color.White.copy(alpha = 0.4f)) },
                                trailingIcon = {
                                    if (collectionQuery.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.metroClickable { collectionQuery = "" }
                                        )
                                    }
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color.White,
                                    focusedBorderColor = LocalZuneAccent.current,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    cursorColor = LocalZuneAccent.current,
                                    backgroundColor = Color(0xFF0F0F0F)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            )

                            if (collectionQuery.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "search your collection",
                                        style = ZuneTypography.body1,
                                        color = ZuneTextSecondary
                                    )
                                }
                            } else if (filteredTracks.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "no results found.",
                                        style = ZuneTypography.body1,
                                        color = ZuneTextSecondary
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(bottom = 32.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(filteredTracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                                        SearchResultCard(
                                            track = track,
                                            onClick = { onTrackClick(track) },
                                            onPlayNext = { onPlayNext(track) },
                                            onAddToQueue = { onAddToQueue(track) },
                                            onAddToPlaylistClick = { songToAddToPlaylist = track },
                                            isCurrentlyPlaying = track.title.equals(currentPlayingTitle, ignoreCase = true)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Online Search Page
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                        ) {
                            OutlinedTextField(
                                value = onlineQuery,
                                onValueChange = { onlineQuery = it },
                                placeholder = { Text("search online music...", color = Color.White.copy(alpha = 0.4f)) },
                                trailingIcon = {
                                    if (onlineQuery.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.metroClickable { onlineQuery = "" }
                                        )
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSearch = { performOnlineSearch() }
                                ),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color.White,
                                    focusedBorderColor = LocalZuneAccent.current,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    cursorColor = LocalZuneAccent.current,
                                    backgroundColor = Color(0xFF0F0F0F)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp)
                            )

                             // Quick search and category filter bar
                             Row(
                                 modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                 horizontalArrangement = Arrangement.SpaceBetween,
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                     listOf("songs", "albums", "artists").forEach { type ->
                                         val isSelected = selectedSearchType == type
                                         Text(
                                             text = type,
                                             style = ZuneTypography.body2.copy(
                                                 fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                 fontSize = 15.sp
                                             ),
                                             color = if (isSelected) LocalZuneAccent.current else Color.White.copy(alpha = 0.5f),
                                             modifier = Modifier
                                                 .metroClickable {
                                                     selectedSearchType = type
                                                     if (onlineQuery.isNotBlank()) {
                                                         performOnlineSearch()
                                                     }
                                                 }
                                                 .padding(vertical = 4.dp, horizontal = 4.dp)
                                         )
                                     }
                                 }
                                 Text(
                                     text = "search online",
                                     style = ZuneTypography.body2.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                     color = LocalZuneAccent.current,
                                     modifier = Modifier
                                         .metroClickable { performOnlineSearch() }
                                         .padding(vertical = 4.dp, horizontal = 8.dp)
                                 )
                             }
 
                             if (isSearchingOnline) {
                                 Box(
                                     modifier = Modifier
                                         .weight(1f)
                                         .fillMaxWidth(),
                                     contentAlignment = Alignment.Center
                                 ) {
                                     CircularProgressIndicator(color = LocalZuneAccent.current)
                                 }
                             } else {
                                 val hasResults = when (selectedSearchType) {
                                     "songs" -> onlineResults.isNotEmpty()
                                     "albums" -> onlineAlbums.isNotEmpty()
                                     "artists" -> onlineArtists.isNotEmpty()
                                     else -> false
                                 }
                                 if (!hasResults) {
                                     Box(
                                         modifier = Modifier
                                             .weight(1f)
                                             .fillMaxWidth(),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Text(
                                             text = if (onlineQuery.isEmpty()) "search music online" else "no online results found.",
                                             style = ZuneTypography.body1,
                                             color = ZuneTextSecondary
                                         )
                                     }
                                 } else {
                                     LazyColumn(
                                         modifier = Modifier
                                             .weight(1f)
                                             .fillMaxWidth(),
                                         contentPadding = PaddingValues(bottom = 32.dp),
                                         verticalArrangement = Arrangement.spacedBy(8.dp)
                                     ) {
                                         when (selectedSearchType) {
                                             "songs" -> {
                                                 itemsIndexed(onlineResults, key = { index, track -> "${track.trackId}_$index" }) { index, track ->
                                                     OnlineSearchResultCard(
                                                         track = track,
                                                         onPlayClick = {
                                                             val playItem = AudioItem(
                                                                 id = -track.trackId,
                                                                 title = track.title,
                                                                 artist = track.artist,
                                                                 album = track.album,
                                                                 uri = Uri.parse("zune://online/${track.previewUrl}"),
                                                                 albumArtUri = if (track.artworkUrl.isNotEmpty()) Uri.parse(track.artworkUrl) else null,
                                                                 durationMs = track.durationMs
                                                             )
                                                             onTrackClick(playItem)
                                                         },
                                                         onAddToQueueClick = {
                                                             val queueItem = AudioItem(
                                                                 id = -track.trackId,
                                                                 title = track.title,
                                                                 artist = track.artist,
                                                                 album = track.album,
                                                                 uri = Uri.parse("zune://online/${track.previewUrl}"),
                                                                 albumArtUri = if (track.artworkUrl.isNotEmpty()) Uri.parse(track.artworkUrl) else null,
                                                                 durationMs = track.durationMs
                                                             )
                                                             onAddToQueue(queueItem)
                                                             Toast.makeText(context, "Added \"${track.title}\" to queue", Toast.LENGTH_SHORT).show()
                                                         },
                                                         onPlayNextClick = {
                                                             val playNextItem = AudioItem(
                                                                 id = -track.trackId,
                                                                 title = track.title,
                                                                 artist = track.artist,
                                                                 album = track.album,
                                                                 uri = Uri.parse("zune://online/${track.previewUrl}"),
                                                                 albumArtUri = if (track.artworkUrl.isNotEmpty()) Uri.parse(track.artworkUrl) else null,
                                                                 durationMs = track.durationMs
                                                             )
                                                             onPlayNext(playNextItem)
                                                             Toast.makeText(context, "Added \"${track.title}\" to play next", Toast.LENGTH_SHORT).show()
                                                         },
                                                         onDownloadClick = {
                                                             val data = workDataOf(
                                                                 "trackId" to track.trackId,
                                                                 "title" to track.title,
                                                                 "artist" to track.artist,
                                                                 "album" to track.album,
                                                                 "previewUrl" to track.previewUrl,
                                                                 "artworkUrl" to track.artworkUrl,
                                                                 "durationMs" to track.durationMs
                                                             )
                                                             val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                                                                 .setInputData(data)
                                                                 .addTag("download_song")
                                                                 .build()
                                                             enqueuedTrackTitles[request.id] = track.title
                                                             ourEnqueuedWorkIds.add(request.id)
                                                             WorkManager.getInstance(context).enqueue(request)
                                                             Toast.makeText(context, "Started download: \"${track.title}\"", Toast.LENGTH_SHORT).show()
                                                         },
                                                         onAddToPlaylistClick = {
                                                             val playItem = AudioItem(
                                                                 id = -track.trackId,
                                                                 title = track.title,
                                                                 artist = track.artist,
                                                                 album = track.album,
                                                                 uri = Uri.parse("zune://online/${track.previewUrl}"),
                                                                 albumArtUri = if (track.artworkUrl.isNotEmpty()) Uri.parse(track.artworkUrl) else null,
                                                                 durationMs = track.durationMs
                                                             )
                                                             songToAddToPlaylist = playItem
                                                         }
                                                     )
                                                 }
                                             }
                                             "albums" -> {
                                                 itemsIndexed(onlineAlbums, key = { index, album -> "${album.browseId}_$index" }) { index, album ->
                                                     OnlineAlbumSearchResultCard(
                                                         album = album,
                                                         onClick = { onOnlineAlbumClick(album) }
                                                     )
                                                 }
                                             }
                                             "artists" -> {
                                                 itemsIndexed(onlineArtists, key = { index, artist -> "${artist.browseId}_$index" }) { index, artist ->
                                                     OnlineArtistSearchResultCard(
                                                         artist = artist,
                                                         onClick = { onOnlineArtistClick(artist) }
                                                     )
                                                 }
                                             }
                                         }
                                     }
                                 }
                             }
                        }
                    }
                }
            }
        }

        // Add to Playlist Dialog for Collection search
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
                                itemsIndexed(playlists, key = { idx, p -> "${p}_$idx" }) { idx, playlist ->
                                    Text(
                                        text = playlist.lowercase(),
                                        style = ZuneTypography.body1.copy(fontSize = 20.sp),
                                        color = Color.White,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .metroClickable {
                                                onAddToPlaylist(songToAddToPlaylist!!, playlist)
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
fun OnlineSearchResultCard(
    track: OnlineSong,
    onPlayClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onPlayNextClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(track) {
                    detectTapGestures(
                        onTap = { showMenu = true },
                        onLongPress = { showMenu = true }
                    )
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art or Placeholder
            if (track.artworkUrl.isNotEmpty()) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF1E1E1E))
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.lowercase(),
                    style = ZuneTypography.h4.copy(fontSize = 24.sp),
                    color = ZuneTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}".lowercase(),
                    style = ZuneTypography.body2,
                    color = ZuneTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                onPlayClick()
            }) {
                Text("play", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            DropdownMenuItem(onClick = {
                showMenu = false
                onAddToQueueClick()
            }) {
                Text("add to queue", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            DropdownMenuItem(onClick = {
                showMenu = false
                onPlayNextClick()
            }) {
                Text("play next", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            DropdownMenuItem(onClick = {
                showMenu = false
                onDownloadClick()
            }) {
                Text("download", style = ZuneTypography.body1, color = ZuneTextPrimary)
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

@Composable
fun OnlineAlbumSearchResultCard(
    album: com.zune.player.data.OnlineAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .metroClickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (album.artworkUrl.isNotEmpty()) {
            AsyncImage(
                model = album.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
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
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.artist} • ${album.year}".lowercase(),
                style = ZuneTypography.body2,
                color = ZuneTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OnlineArtistSearchResultCard(
    artist: com.zune.player.data.OnlineArtist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .metroClickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (artist.artworkUrl.isNotEmpty()) {
            AsyncImage(
                model = artist.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF1E1E1E))
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name.lowercase(),
                style = ZuneTypography.h4.copy(fontSize = 24.sp),
                color = ZuneTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist.subscribers.lowercase(),
                style = ZuneTypography.body2,
                color = ZuneTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SearchResultCard(
    track: AudioItem,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    isCurrentlyPlaying: Boolean,
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
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art or Placeholder
            if (track.albumArtUri != null) {
                AsyncImage(
                    model = track.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF1E1E1E))
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.lowercase(),
                    style = ZuneTypography.h4.copy(fontSize = 24.sp),
                    color = if (isCurrentlyPlaying) LocalZuneAccent.current else ZuneTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}".lowercase(),
                    style = ZuneTypography.body2,
                    color = ZuneTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
