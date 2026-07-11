package com.zune.player.ui.screens

import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.filled.Close

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkInfo
import com.zune.player.data.OnlineSong
import com.zune.player.data.OnlineAlbum
import com.zune.player.data.OnlineArtist
import com.zune.player.data.DownloadWorker
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.flow.firstOrNull

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.foundation.border
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zune.player.ui.components.metroClickable
import com.zune.player.ui.theme.ZuneTextPrimary
import com.zune.player.ui.theme.ZuneTextSecondary
import com.zune.player.ui.theme.ZuneTypography
import com.zune.player.ui.theme.SegoeUiFontFamily
import com.zune.player.LocalSharedTransitionScope
import com.zune.player.LocalAnimatedVisibilityScope
import com.zune.player.ui.theme.LocalZuneAccent
import com.zune.player.ui.theme.AeroBlueOrbGradient
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.grid.*
import kotlinx.coroutines.launch

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CategoryListScreen(
    initialCategory: String,
    getItemsForCategory: (String) -> List<Any>,
    isAeroTheme: Boolean = false,
    playlists: List<String> = emptyList(),
    audioItems: List<com.zune.player.data.AudioItem> = emptyList(),
    onItemClick: (String, String) -> Unit, // category, itemTitle
    onBack: () -> Unit,
    onCreatePlaylist: (String) -> Unit = {},
    onDeletePlaylist: (String) -> Unit = {},
    onAddToPlaylist: (com.zune.player.data.AudioItem, String) -> Unit = { _, _ -> }, // audioItem, playlistName
    onPlayNext: (String, String) -> Unit = { _, _ -> }, // category, itemTitle
    onAddToQueue: (String, String) -> Unit = { _, _ -> }, // category, itemTitle
    isPinned: (String) -> Boolean = { false },
    onPin: (String) -> Unit = {},
    onScroll: (Float) -> Unit = {},
    currentPlayingTitle: String? = null,
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    onNavigateToNowPlaying: () -> Unit = {},
    onCategoryChanged: (String) -> Unit = {},
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> },
    onOnlineTrackClick: (com.zune.player.data.AudioItem) -> Unit = {},
    onOnlineAddToQueue: (com.zune.player.data.AudioItem) -> Unit = {},
    onOnlinePlayNext: (com.zune.player.data.AudioItem) -> Unit = {},
    onOnlineAlbumClick: (com.zune.player.data.OnlineAlbum) -> Unit = {},
    onOnlineArtistClick: (com.zune.player.data.OnlineArtist) -> Unit = {}
) {
    val context = LocalContext.current
    val categories = listOf("songs", "playlists", "artists", "albums", "search", "online")
    val initialIndex = categories.indexOf(initialCategory.lowercase()).coerceAtLeast(0)
    val pageCount = 20000
    val middlePage = (10000 / categories.size) * categories.size
    val pagerState = rememberPagerState(initialPage = middlePage + initialIndex) { pageCount }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(pagerState.currentPage) {
        val actualIndex = (pagerState.currentPage % categories.size + categories.size) % categories.size
        onCategoryChanged(categories[actualIndex])
    }
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var songToAddToPlaylist by remember { mutableStateOf<com.zune.player.data.AudioItem?>(null) } // songToAddToPlaylist

    // Search and Online states
    var collectionQuery by remember { mutableStateOf("") }
    var onlineQuery by remember { mutableStateOf("") }
    var onlineResults by remember { mutableStateOf<List<OnlineSong>>(emptyList()) }
    var onlineAlbums by remember { mutableStateOf<List<com.zune.player.data.OnlineAlbum>>(emptyList()) }
    var onlineArtists by remember { mutableStateOf<List<com.zune.player.data.OnlineArtist>>(emptyList()) }
    var selectedSearchType by remember { mutableStateOf("songs") }
    var isSearchingOnline by remember { mutableStateOf(false) }

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
                    android.widget.Toast.makeText(context, "Added \"$title\" to library", android.widget.Toast.LENGTH_SHORT).show()
                } else if (workInfo.state == WorkInfo.State.FAILED) {
                    android.widget.Toast.makeText(context, "Failed to download \"$title\"", android.widget.Toast.LENGTH_SHORT).show()
                }
                enqueuedTrackTitles.remove(workInfo.id)
            }
        }
    }

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

    val tabWidths = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    val isForward = com.zune.player.LocalIsForwardTransition.current
                    if (isForward) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                rememberSharedContentState(key = "header_music"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ ->
                                    androidx.compose.animation.core.spring<androidx.compose.ui.geometry.Rect>(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                        stiffness = 150f
                                    )
                                },
                                renderInOverlayDuringTransition = false
                            ).skipToLookaheadSize()
                        }
                    } else {
                        Modifier
                    }
                } else {
                    Modifier
                }

                Text(
                    text = "music",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = com.zune.player.ui.theme.SegoeUiLightFontFamily,
                        fontSize = 170.sp
                    ),
                    color = Color.White.copy(alpha = 0.12f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                    modifier = Modifier
                        .offset(x = (-12).dp, y = (-48).dp)
                        .wrapContentWidth(align = androidx.compose.ui.Alignment.Start, unbounded = true)
                        .wrapContentHeight(align = androidx.compose.ui.Alignment.Top, unbounded = true)
                        .then(sharedModifier)
                        .metroClickable { onBack() }
                )
            }
            
            // Pivot Titles
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
                                // Calculate the offset to shift the row leftwards based on pager state
                                var offsetPx = 0f
                                val startVirtualIndex = pagerState.currentPage - 2
                                val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                
                                val activePageIndex = pageOffset.toInt()
                                val fraction = pageOffset - activePageIndex
                                
                                for (vIdx in startVirtualIndex until activePageIndex) {
                                    val index = (vIdx % categories.size + categories.size) % categories.size
                                    offsetPx += (tabWidths[index] ?: 0f)
                                }
                                
                                val activeIndex = (activePageIndex % categories.size + categories.size) % categories.size
                                if (fraction > 0f) {
                                    offsetPx += (tabWidths[activeIndex] ?: 0f) * fraction
                                } else if (fraction < 0f) {
                                    val prevIndex = ((activePageIndex - 1) % categories.size + categories.size) % categories.size
                                    offsetPx += (tabWidths[prevIndex] ?: 0f) * fraction
                                }

                                placeable.place(x = -offsetPx.toInt(), y = 0)
                            }
                        }
                        .padding(start = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    val visibleRange = (pagerState.currentPage - 2)..(pagerState.currentPage + 5)
                    visibleRange.forEach { virtualIndex ->
                        val index = (virtualIndex % categories.size + categories.size) % categories.size
                        val title = categories[index]
                        val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        val distance = kotlin.math.abs(pageOffset - virtualIndex)
                        val alpha = (1f - distance * 0.6f).coerceIn(0.4f, 1f)
                        
                        val displayTitle = title.uppercase()
                        val displayText = displayTitle
                        val textColor = Color.White.copy(alpha = alpha)
                        val textStyle = ZuneTypography.h1.copy(
                            fontFamily = SegoeUiFontFamily,
                            fontSize = 42.sp
                        )
                        
                        Text(
                            text = displayText,
                            style = textStyle,
                            color = textColor,
                            modifier = Modifier
                                .metroClickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(virtualIndex) }
                                }
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    // Store exact width including 24dp spacing
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

            // Pager Content
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

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val actualPage = (page % categories.size + categories.size) % categories.size
                val currentCategory = categories[actualPage]
                
                if (currentCategory == "search") {
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
                                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
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
                                        onClick = { onItemClick("songs", track.title) },
                                        onPlayNext = { onPlayNext("songs", track.title) },
                                        onAddToQueue = { onAddToQueue("songs", track.title) },
                                        onAddToPlaylistClick = { songToAddToPlaylist = track },
                                        isCurrentlyPlaying = track.title.equals(currentPlayingTitle, ignoreCase = true)
                                    )
                                }
                            }
                        }
                    }
                } else if (currentCategory == "online") {
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
                                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
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
                                                        val playItem = com.zune.player.data.AudioItem(
                                                            id = -track.trackId,
                                                            title = track.title,
                                                            artist = track.artist,
                                                            album = track.album,
                                                            uri = android.net.Uri.parse("zune://online/${track.previewUrl}"),
                                                            albumArtUri = if (track.artworkUrl.isNotEmpty()) android.net.Uri.parse(track.artworkUrl) else null,
                                                            durationMs = track.durationMs
                                                        )
                                                        onOnlineTrackClick(playItem)
                                                    },
                                                    onAddToQueueClick = {
                                                        val queueItem = com.zune.player.data.AudioItem(
                                                            id = -track.trackId,
                                                            title = track.title,
                                                            artist = track.artist,
                                                            album = track.album,
                                                            uri = android.net.Uri.parse("zune://online/${track.previewUrl}"),
                                                            albumArtUri = if (track.artworkUrl.isNotEmpty()) android.net.Uri.parse(track.artworkUrl) else null,
                                                            durationMs = track.durationMs
                                                        )
                                                        onOnlineAddToQueue(queueItem)
                                                        android.widget.Toast.makeText(context, "Added \"${track.title}\" to queue", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    onPlayNextClick = {
                                                        val playNextItem = com.zune.player.data.AudioItem(
                                                            id = -track.trackId,
                                                            title = track.title,
                                                            artist = track.artist,
                                                            album = track.album,
                                                            uri = android.net.Uri.parse("zune://online/${track.previewUrl}"),
                                                            albumArtUri = if (track.artworkUrl.isNotEmpty()) android.net.Uri.parse(track.artworkUrl) else null,
                                                            durationMs = track.durationMs
                                                        )
                                                        onOnlinePlayNext(playNextItem)
                                                        android.widget.Toast.makeText(context, "Added \"${track.title}\" to play next", android.widget.Toast.LENGTH_SHORT).show()
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
                                                        android.widget.Toast.makeText(context, "Started download: \"${track.title}\"", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    onAddToPlaylistClick = {
                                                        val playItem = com.zune.player.data.AudioItem(
                                                            id = -track.trackId,
                                                            title = track.title,
                                                            artist = track.artist,
                                                            album = track.album,
                                                            uri = android.net.Uri.parse("zune://online/${track.previewUrl}"),
                                                            albumArtUri = if (track.artworkUrl.isNotEmpty()) android.net.Uri.parse(track.artworkUrl) else null,
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
                                                    onClick = {
                                                        onOnlineAlbumClick(album)
                                                    }
                                                )
                                            }
                                        }
                                        "artists" -> {
                                            itemsIndexed(onlineArtists, key = { index, artist -> "${artist.browseId}_$index" }) { index, artist ->
                                                OnlineArtistSearchResultCard(
                                                    artist = artist,
                                                    onClick = {
                                                        onOnlineArtistClick(artist)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val items = remember(currentCategory, playlists, audioItems) { getItemsForCategory(currentCategory) }
                    CategoryPage(
                        categoryTitle = currentCategory,
                        items = items,
                        playlists = playlists,
                        audioItems = audioItems,
                        currentPlayingTitle = currentPlayingTitle,
                        isAeroTheme = isAeroTheme,
                        onItemClick = { title -> onItemClick(currentCategory, title) },
                        onPin = { title -> onPin(title) },
                        onPlayNext = { title -> onPlayNext(currentCategory, title) },
                        onAddToQueue = { title -> onAddToQueue(currentCategory, title) },
                        isPinned = isPinned,
                        onDeletePlaylist = onDeletePlaylist,
                        onCreateClick = { showCreateDialog = true },
                        onAddToPlaylistClick = { songToAddToPlaylist = it },
                        getScrollPosition = getScrollPosition,
                        onScrollPositionChanged = onScrollPositionChanged
                    )
                }
            }
            
            // Persistent Now Playing Bar
            if (currentPlayingTitle != null) {
                val glassModifier = Modifier.background(LocalZuneAccent.current)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(glassModifier)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$currentPlayingTitle".lowercase(),
                            style = ZuneTypography.body1.copy(fontSize = 18.sp),
                            color = Color.White,
                            modifier = Modifier
                                .weight(1f)
                                .metroClickable { onNavigateToNowPlaying() },
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        
                        if (isAeroTheme) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .metroClickable { onTogglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.zune.player.R.drawable.wmc_play_button),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(32.dp)
                                    .metroClickable { onTogglePlayPause() }
                            )
                        }
                    }

                    // No Aero Canvas border highlight
                }
            }
        }

        if (showCreateDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showCreateDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, LocalZuneAccent.current)
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "create playlist",
                            style = ZuneTypography.h2.copy(fontSize = 24.sp),
                            color = Color.White
                        )
                        androidx.compose.material.OutlinedTextField(
                            value = playlistNameInput,
                            onValueChange = { playlistNameInput = it },
                            label = { Text("playlist name", color = Color.White.copy(alpha = 0.6f)) },
                            colors = androidx.compose.material.TextFieldDefaults.outlinedTextFieldColors(
                                textColor = Color.White,
                                focusedBorderColor = LocalZuneAccent.current,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                cursorColor = LocalZuneAccent.current
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "cancel",
                                style = ZuneTypography.body1,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .metroClickable { showCreateDialog = false }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            Text(
                                text = "create",
                                style = ZuneTypography.body1,
                                color = LocalZuneAccent.current,
                                modifier = Modifier
                                    .metroClickable {
                                        if (playlistNameInput.isNotBlank()) {
                                            onCreatePlaylist(playlistNameInput)
                                            playlistNameInput = ""
                                            showCreateDialog = false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

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
fun CategoryPage(
    categoryTitle: String,
    items: List<Any>,
    playlists: List<String>,
    audioItems: List<com.zune.player.data.AudioItem>,
    currentPlayingTitle: String?,
    isAeroTheme: Boolean = false,
    onItemClick: (String) -> Unit,
    onPin: (String) -> Unit,
    onPlayNext: (String) -> Unit,
    onAddToQueue: (String) -> Unit,
    isPinned: (String) -> Boolean,
    onDeletePlaylist: (String) -> Unit,
    onCreateClick: () -> Unit,
    onAddToPlaylistClick: (com.zune.player.data.AudioItem) -> Unit,
    getScrollPosition: (String) -> Pair<Int, Int>,
    onScrollPositionChanged: (String, Int, Int) -> Unit
) {
    val key = "category_${categoryTitle.lowercase()}"
    val initialPos = remember(key) { getScrollPosition(key) }
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialPos.first,
        initialFirstVisibleItemScrollOffset = initialPos.second
    )
    
    DisposableEffect(scrollState) {
        onDispose {
            onScrollPositionChanged(key, scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset)
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var showJumpGrid by remember { mutableStateOf(false) }

    val groupedItems = remember(items) {
        if (categoryTitle.lowercase() == "albums") {
            items.chunked(3)
        } else {
            val map = mutableMapOf<Char, MutableList<Any>>()
            items.forEach { item ->
                val title = when (item) {
                    is String -> item
                    is com.zune.player.data.AudioItem -> item.title
                    else -> ""
                }
                if (title.isNotBlank()) {
                    val firstChar = title.first().lowercaseChar()
                    val key = if (firstChar.isLetter()) firstChar else '#'
                    map.getOrPut(key) { mutableListOf() }.add(item)
                }
            }
            
            val result = mutableListOf<Any>()
            map.keys.sorted().forEach { key ->
                result.add(key)
                result.addAll(map[key]!!)
            }
            result
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    


    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (categoryTitle.lowercase() == "playlists") {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .metroClickable { onCreateClick() }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "create new",
                            style = ZuneTypography.h4.copy(fontSize = 28.sp, color = LocalZuneAccent.current)
                        )
                    }
                }
            }

            itemsIndexed(
                items = groupedItems,
                key = { index, item ->
                    when (item) {
                        is Char -> "char_${item}_$index"
                        is com.zune.player.data.AudioItem -> "song_${item.id}_$index"
                        is String -> "str_${item}_$index"
                        is List<*> -> "row_${item.hashCode()}_$index"
                        else -> "${item.hashCode()}_$index"
                    }
                }
            ) { index, item ->
                if (item is Char) {
                    Box(
                        modifier = Modifier
                            .animateItem()
                            .padding(top = 16.dp, bottom = 4.dp)
                            .size(48.dp)
                            .background(LocalZuneAccent.current)
                            .metroClickable { showJumpGrid = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.toString(),
                            style = ZuneTypography.h1.copy(fontSize = 24.sp, fontFamily = SegoeUiFontFamily, fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                } else if (item is List<*>) {
                    val rowItems = item as List<com.zune.player.data.AudioItem>
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { albumItem ->
                            AlbumGridCell(
                                albumItem = albumItem,
                                isAeroTheme = isAeroTheme,
                                currentPlayingTitle = currentPlayingTitle,
                                modifier = Modifier.weight(1f),
                                onItemClick = { onItemClick(albumItem.album) },
                                onPin = onPin,
                                isPinned = isPinned
                            )
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    val isSong = item is com.zune.player.data.AudioItem
                    val title = if (isSong) {
                        if (categoryTitle.lowercase() == "albums") (item as com.zune.player.data.AudioItem).album else (item as com.zune.player.data.AudioItem).title
                    } else item as String
                    var showMenu by remember { mutableStateOf(false) }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val haptic = LocalHapticFeedback.current
                    val prefs = remember(context) { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }

                    Box(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(item) {
                                    detectTapGestures(
                                        onTap = { onItemClick(title) },
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
                            if (isSong) {
                                val audioItem = item as com.zune.player.data.AudioItem
                                if (audioItem.albumArtUri != null) {
                                     AsyncImage(
                                         model = audioItem.albumArtUri,
                                         contentDescription = "Album Art",
                                         modifier = Modifier.size(48.dp),
                                         contentScale = ContentScale.Crop
                                     )
                                 } else {
                                     Box(modifier = Modifier.size(48.dp).background(Color(0xFF222222)))
                                 }
                                 Spacer(modifier = Modifier.width(12.dp))
                             }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                val isCurrentPlaying = title.equals(currentPlayingTitle, ignoreCase = true)
                                val titleColor = if (isCurrentPlaying) LocalZuneAccent.current else ZuneTextPrimary
                                val subtitleColor = ZuneTextSecondary

                                Text(
                                    text = title.lowercase(),
                                    style = ZuneTypography.h4.copy(fontSize = 24.sp),
                                    color = titleColor,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (isSong && categoryTitle.lowercase() == "songs") {
                                    val audioItem = item as com.zune.player.data.AudioItem
                                    Text(
                                        text = "${audioItem.artist} • ${audioItem.album}".lowercase(),
                                        style = ZuneTypography.body2.copy(fontSize = 13.sp),
                                        color = subtitleColor,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF1A1A1A))
                        ) {
                            DropdownMenuItem(onClick = {
                                showMenu = false
                                onItemClick(title)
                            }) {
                                Text("play", style = ZuneTypography.body1, color = ZuneTextPrimary)
                            }
                            
                            if (categoryTitle.lowercase() == "songs") {
                                DropdownMenuItem(onClick = {
                                    showMenu = false
                                    onPin(title)
                                }) {
                                    Text(if (isPinned(title)) "unpin from home" else "pin to home", style = ZuneTypography.body1, color = ZuneTextPrimary)
                                }
                                DropdownMenuItem(onClick = {
                                    showMenu = false
                                    onPlayNext(title)
                                }) {
                                    Text("play next", style = ZuneTypography.body1, color = ZuneTextPrimary)
                                }
                                DropdownMenuItem(onClick = {
                                    showMenu = false
                                    onAddToQueue(title)
                                }) {
                                    Text("add to queue", style = ZuneTypography.body1, color = ZuneTextPrimary)
                                }
                                DropdownMenuItem(onClick = {
                                    showMenu = false
                                    if (isSong) {
                                        onAddToPlaylistClick(item as com.zune.player.data.AudioItem)
                                    }
                                }) {
                                    Text("add to playlist", style = ZuneTypography.body1, color = ZuneTextPrimary)
                                }
                            }
                            
                            if (categoryTitle.lowercase() == "playlists") {
                                DropdownMenuItem(onClick = {
                                    showMenu = false
                                    onDeletePlaylist(title)
                                }) {
                                    Text("delete", style = ZuneTypography.body1, color = Color.Red.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }

            if (items.isEmpty() && categoryTitle.lowercase() != "playlists") {
                item {
                    Text(
                        text = "it's lonely in here.",
                        style = ZuneTypography.body1,
                        color = ZuneTextSecondary,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                }
            }

        }



        AnimatedVisibility(
            visible = showJumpGrid,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.9f, animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .pointerInput(Unit) { detectTapGestures { showJumpGrid = false } },
                contentAlignment = Alignment.BottomCenter
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .pointerInput(Unit) { /* intercept clicks */ }
                ) {
                    val availableLetters = groupedItems.filterIsInstance<Char>().toSet()
                    val alphabet = ('a'..'z').toList() + listOf('#')
                    items(alphabet, key = { it }) { letter ->
                        val hasItems = availableLetters.contains(letter)
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(if (hasItems) LocalZuneAccent.current else Color(0xFF222222))
                                .metroClickable {
                                    if (hasItems) {
                                        showJumpGrid = false
                                        val index = groupedItems.indexOf(letter)
                                        if (index != -1) {
                                            val offset = if (categoryTitle.lowercase() == "playlists") 1 else 0
                                            coroutineScope.launch {
                                                scrollState.scrollToItem(index + offset)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter.toString(),
                                style = ZuneTypography.h2.copy(
                                    fontFamily = SegoeUiFontFamily,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (hasItems) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumGridCell(
    albumItem: com.zune.player.data.AudioItem,
    isAeroTheme: Boolean,
    currentPlayingTitle: String?,
    modifier: Modifier = Modifier,
    onItemClick: () -> Unit,
    onPin: (String) -> Unit,
    isPinned: (String) -> Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember(context) { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    val isCurrentPlaying = albumItem.album.equals(currentPlayingTitle, ignoreCase = true)
    
    val titleColor = if (isCurrentPlaying) LocalZuneAccent.current else ZuneTextPrimary

    Box(
        modifier = modifier
            .pointerInput(albumItem) {
                detectTapGestures(
                    onTap = { onItemClick() },
                    onLongPress = {
                        if (prefs.getBoolean("haptic_feedback_enabled", true)) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        showMenu = true
                    }
                )
            }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color(0xFF222222))
            ) {
                if (albumItem.albumArtUri != null) {
                    AsyncImage(
                        model = albumItem.albumArtUri,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = albumItem.album.lowercase(),
                style = ZuneTypography.h4.copy(fontSize = 15.sp),
                color = titleColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = albumItem.artist.lowercase(),
                style = ZuneTypography.body2.copy(fontSize = 11.sp),
                color = ZuneTextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Color(0xFF1A1A1A))
        ) {
            DropdownMenuItem(onClick = {
                showMenu = false
                onItemClick()
            }) {
                Text("play", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            DropdownMenuItem(onClick = {
                showMenu = false
                onPin(albumItem.album)
            }) {
                Text(if (isPinned(albumItem.album)) "unpin from home" else "pin to home", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
        }
    }
}


