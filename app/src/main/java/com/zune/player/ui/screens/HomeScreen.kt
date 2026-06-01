package com.zune.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import com.zune.player.R
import androidx.media3.common.Player
import com.zune.player.data.AudioItem
import com.zune.player.player.AudioPlayer
import com.zune.player.ui.components.PivotLayout
import com.zune.player.ui.components.metroClickable
import com.zune.player.ui.theme.LocalZuneAccent
import com.zune.player.ui.theme.ZuneAccent
import com.zune.player.ui.theme.AeroBlueOrbGradient
import com.zune.player.ui.theme.ZuneTextPrimary
import com.zune.player.ui.theme.ZuneTextSecondary
import com.zune.player.ui.theme.ZuneTypography
import com.zune.player.ui.theme.SegoeUiLightFontFamily
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*

@Composable
fun HomeScreen(
    initialPage: Int = 1,
    player: AudioPlayer,
    audioItems: List<AudioItem>,
    pinnedItems: List<Pair<Long, Int>>,
    onNavigateToNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    onNavigateToCategory: (String) -> Unit,
    onNavigateToPhotos: (Long?) -> Unit = {},
    onNavigateToVideos: (Long?) -> Unit = {},
    onPlayAlbum: (String) -> Unit,
    onPlaySong: (AudioItem) -> Unit,
    onUnpin: (Long) -> Unit,
    onCycleSize: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    onScroll: (Float) -> Unit = {},
    isAeroTheme: Boolean = false,
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val pages = listOf(0, 1, 2, 3, 4, 5)

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedBg by remember { mutableStateOf(prefs.getInt("bg_selection", 0)) }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "bg_selection") {
                selectedBg = sharedPreferences.getInt("bg_selection", 0)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var photosList by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var videosList by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reloadTrigger++
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            context.contentResolver.registerContentObserver(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(reloadTrigger) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pList = queryLocalPhotos(context)
            val vList = queryLocalVideos(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                photosList = pList
                videosList = vList
            }
        }
    }

    val resolvedPinnedTiles = remember(pinnedItems, audioItems, photosList, videosList) {
        pinnedItems.mapNotNull { p ->
            val rawId = p.first
            val size = p.second
            when {
                (rawId and 0x1000000000000000L) == 0x1000000000000000L -> {
                    val originalId = rawId xor 0x1000000000000000L
                    val photo = photosList.find { it.id == originalId }
                    if (photo != null) {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "photo",
                                title = photo.title,
                                subtitle = "photo",
                                imageUri = photo.uri,
                                gradientColors = photo.gradientColors,
                                size = size
                            ),
                            size
                        )
                    } else {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "photo",
                                title = "photo",
                                subtitle = "photo",
                                imageUri = null,
                                gradientColors = listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
                                size = size
                            ),
                            size
                        )
                    }
                }
                (rawId and 0x2000000000000000L) == 0x2000000000000000L -> {
                    val originalId = rawId xor 0x2000000000000000L
                    val video = videosList.find { it.id == originalId }
                    if (video != null) {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "video",
                                title = video.title,
                                subtitle = "video",
                                imageUri = video.uri,
                                gradientColors = video.gradientColors,
                                size = size
                            ),
                            size
                        )
                    } else {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "video",
                                title = "video",
                                subtitle = "video",
                                imageUri = null,
                                gradientColors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
                                size = size
                            ),
                            size
                        )
                    }
                }
                else -> {
                    val song = audioItems.find { it.id == rawId }
                    if (song != null) {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "song",
                                title = song.title,
                                subtitle = song.artist,
                                imageUri = song.albumArtUri,
                                gradientColors = emptyList(),
                                size = size
                            ),
                            size
                        )
                    } else null
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        PivotLayout(
            title = if (isAeroTheme) "Media Library" else "music+videos",
            pages = pages,
            initialPage = initialPage,
            isBlackBackground = selectedBg == 0,
            isAeroTheme = isAeroTheme,
            onOffsetChanged = onScroll
        ) { page ->
            when (page) {
                0 -> Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        val currentPlaying by player.currentAudio.collectAsState()
                        PinnedPage(
                            pinnedItems = resolvedPinnedTiles,
                            currentPlayingId = currentPlaying?.id,
                            onPlay = { tile ->
                                when (tile.type) {
                                    "song" -> {
                                        val song = audioItems.find { it.id == tile.id }
                                        if (song != null) onPlaySong(song)
                                    }
                                    "photo" -> {
                                        onNavigateToPhotos(tile.id xor 0x1000000000000000L)
                                    }
                                    "video" -> {
                                        onNavigateToVideos(tile.id xor 0x2000000000000000L)
                                    }
                                }
                            },
                            onUnpin = onUnpin,
                            onCycleSize = onCycleSize,
                            onMove = onMove,
                            isAeroTheme = isAeroTheme,
                            getScrollPosition = getScrollPosition,
                            onScrollPositionChanged = onScrollPositionChanged
                        )
                    }
                }
                1 -> Column(modifier = Modifier.fillMaxSize()) {
                    SectionHeader("now playing", isAeroTheme = isAeroTheme)
                    Box(modifier = Modifier.weight(1f)) {
                        NowPlayingPanel(player = player, onNavigateToNowPlaying = onNavigateToNowPlaying, onOpenQueue = onOpenQueue, isAeroTheme = isAeroTheme)
                    }
                }
                2 -> Column(modifier = Modifier.fillMaxSize()) {
                    SectionHeader("collection", isAeroTheme = isAeroTheme)
                    Box(modifier = Modifier.weight(1f)) {
                        MusicPage(
                            player = player,
                            onNavigateToNowPlaying = onNavigateToNowPlaying,
                            onNavigateToCategory = onNavigateToCategory,
                            onScroll = {},
                            isAeroTheme = isAeroTheme,
                            isBlackBackground = selectedBg == 0,
                            getScrollPosition = getScrollPosition,
                            onScrollPositionChanged = onScrollPositionChanged
                        )
                    }
                }
                3 -> Column(modifier = Modifier.fillMaxSize()) {
                    SectionHeader("featured", isAeroTheme = isAeroTheme)
                    Box(modifier = Modifier.weight(1f)) {
                        FeaturedAlbumsPage(
                            audioItems = audioItems,
                            onPlayAlbum = onPlayAlbum,
                            onScroll = {},
                            isAeroTheme = isAeroTheme,
                            getScrollPosition = getScrollPosition,
                            onScrollPositionChanged = onScrollPositionChanged
                        )
                    }
                }
                4 -> Column(modifier = Modifier.fillMaxSize()) {
                    SectionHeader("pictures+videos", isAeroTheme = isAeroTheme)
                    Box(modifier = Modifier.weight(1f)) {
                        PicturesAndVideosPagePreview(
                            isAeroTheme = isAeroTheme,
                            photosList = photosList,
                            onNavigateToPhotos = { onNavigateToCategory("pictures") },
                            onNavigateToVideos = { onNavigateToCategory("videos") }
                        )
                    }
                }
                5 -> Column(modifier = Modifier.fillMaxSize()) {
                    SectionHeader("personalize", isAeroTheme = isAeroTheme)
                    Box(modifier = Modifier.weight(1f)) {
                        PersonalizePage(
                            getScrollPosition = getScrollPosition,
                            onScrollPositionChanged = onScrollPositionChanged
                        )
                    }
                }
            }
        }

        if (isAeroTheme) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 24.dp)
            ) {
                WmcStartOrbAndClock()
            }
        }
    }
}

@Composable
fun NowPlayingPanel(
    player: AudioPlayer,
    onNavigateToNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    isAeroTheme: Boolean = false
) {
    val isPlaying by player.isPlaying.collectAsState()
    val currentItem by player.currentAudio.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()
    val shuffleEnabled by player.shuffleEnabled.collectAsState()

    val currentPosFlow by player.currentPosition.collectAsState()
    val totalDurationFlow by player.duration.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()

    var localCurrentPos by remember { mutableStateOf(0L) }
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    val currentPos = if (isDragging) localCurrentPos else currentPosFlow
    val totalDuration = if (totalDurationFlow > 1000L) totalDurationFlow
                        else currentItem?.durationMs?.takeIf { it > 1000L } ?: totalDurationFlow.coerceAtLeast(1L)
    val progress = if (totalDuration > 0L) {
        (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val accent = LocalZuneAccent.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            val artGlassModifier = if (isAeroTheme) {
                Modifier
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.40f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(1.dp)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.60f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        ),
                        shape = RoundedCornerShape(5.dp)
                    )
                    .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(5.dp))
                    .clip(RoundedCornerShape(5.dp))
            } else {
                Modifier
                    .background(Color(0xFF1A1A1A))
            }

            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .aspectRatio(1f)
                    .then(artGlassModifier)
                    .metroClickable { onNavigateToNowPlaying() },
                contentAlignment = Alignment.BottomStart
            ) {
                if (currentItem?.albumArtUri != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = currentItem?.albumArtUri,
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isBuffering) {
                            androidx.compose.material.CircularProgressIndicator(
                                color = accent,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(accent)
                            .align(Alignment.BottomStart)
                    )
                }

                if (isAeroTheme) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(w * 0.4f, 0f)
                            lineTo(0f, h * 0.4f)
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.02f),
                                    Color.Transparent
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(w * 0.3f, h * 0.3f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleEnabled) accent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp).metroClickable { player.toggleShuffle() }
                )
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp).metroClickable { player.toggleRepeat() }
                )
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Queue",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(26.dp).metroClickable { onOpenQueue() }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val currentTotalDuration by rememberUpdatedState(totalDuration)
        val sliderValue = seekPreview ?: progress
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width
                        var currentX = down.position.x

                        player.setUserScrubbing(true)
                        isDragging = true

                        val initialProgress = (currentX / width.toFloat()).coerceIn(0f, 1f)
                        localCurrentPos = (initialProgress * currentTotalDuration).toLong()
                        seekPreview = initialProgress

                        var moveEvent: PointerInputChange?
                        do {
                            moveEvent = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }
                        } while (moveEvent != null && !moveEvent.isConsumed)

                        if (moveEvent != null) {
                            horizontalDrag(down.id) { change ->
                                currentX = change.position.x
                                val draggedProgress = (currentX / width.toFloat()).coerceIn(0f, 1f)
                                localCurrentPos = (draggedProgress * currentTotalDuration).toLong()
                                seekPreview = draggedProgress
                                change.consume()
                            }
                        }

                        val finalProgress = (currentX / width.toFloat()).coerceIn(0f, 1f)
                        val seekMs = (finalProgress * currentTotalDuration).toLong()

                        player.seekTo(seekMs)
                        player.setUserScrubbing(false)
                        isDragging = false
                        seekPreview = null
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val sliderHeight by animateDpAsState(
                targetValue = if (isDragging) 6.dp else 3.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "ScrubberHeight"
            )
            if (isAeroTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.25f)
                                )
                            ),
                            shape = RoundedCornerShape(2.dp)
                        )
                        .border(
                            width = 0.5.dp,
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                if (sliderValue > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = sliderValue)
                            .height(3.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFB8EEFF),
                                        Color(0xFF4CC8F0),
                                        Color(0xFF1255BF),
                                        Color(0xFF091A52)
                                    )
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .clip(RoundedCornerShape(2.dp))
                            .align(Alignment.CenterStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.45f)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.55f),
                                            Color.White.copy(alpha = 0.0f)
                                        )
                                    )
                                )
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(1.5.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = sliderValue)
                        .height(sliderHeight)
                        .background(accent, RoundedCornerShape(sliderHeight / 2))
                        .align(Alignment.CenterStart)
                )
                
                // Thumb circle
                val thumbSize by animateDpAsState(
                    targetValue = if (isDragging) 10.dp else 0.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                    label = "ScrubberThumb"
                )
                if (thumbSize > 0.dp) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (sliderValue * maxWidth.value).dp - (thumbSize / 2))
                            .size(thumbSize)
                            .background(Color.White, CircleShape)
                            .border(1.dp, accent, CircleShape)
                    )
                }
            }
        }

        // Timestamps
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatPanelTime(currentPos),
                style = ZuneTypography.caption.copy(fontSize = 11.sp),
                color = if (isAeroTheme) LocalZuneAccent.current.copy(alpha = 0.5f) else ZuneTextSecondary
            )
            Text(
                text = "-" + formatPanelTime(totalDuration - currentPos),
                style = ZuneTypography.caption.copy(fontSize = 11.sp),
                color = if (isAeroTheme) LocalZuneAccent.current.copy(alpha = 0.5f) else ZuneTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = currentItem?.title?.lowercase() ?: "nothing playing",
            style = ZuneTypography.h4.copy(fontSize = 20.sp),
            color = if (isAeroTheme) Color.White else ZuneTextPrimary,
            maxLines = 1
        )
        Text(
            text = (currentItem?.artist?.lowercase() ?: "").let { if (it.isNotBlank()) "by $it" else "" },
            style = ZuneTypography.body2,
            color = if (isAeroTheme) LocalZuneAccent.current.copy(alpha = 0.6f) else ZuneTextSecondary,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .metroClickable { player.skipToPrevious() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Previous",
                    tint = ZuneTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            val playButtonModifier = if (isAeroTheme) {
                Modifier
                    .size(54.dp)
                    .metroClickable { player.togglePlayPause() }
            } else {
                Modifier
                    .size(48.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    .metroClickable { player.togglePlayPause() }
            }
            Box(
                modifier = playButtonModifier,
                contentAlignment = Alignment.Center
            ) {
                if (isAeroTheme) {
                    Image(
                        painter = painterResource(id = com.zune.player.R.drawable.wmc_play_button),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = if (isAeroTheme) Color.White else ZuneTextPrimary,
                    modifier = Modifier.size(if (isAeroTheme) 26.dp else 24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .metroClickable { player.skipToNext() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Next",
                    tint = ZuneTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, isAeroTheme: Boolean = false) {
    val displayTitle = if (isAeroTheme) {
        title.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    } else {
        title.uppercase()
    }
    Text(
        text = displayTitle,
        style = if (isAeroTheme) {
            ZuneTypography.h2.copy(
                fontSize = 22.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                letterSpacing = 0.5.sp,
                brush = AeroBlueOrbGradient
            )
        } else {
            ZuneTypography.h2.copy(
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                letterSpacing = 2.sp
            )
        },
        color = if (isAeroTheme) Color.Unspecified else Color.White,
        modifier = Modifier.padding(start = 24.dp, bottom = 16.dp, top = 116.dp)
    )
}

@Composable
fun MusicPage(
    player: AudioPlayer,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToCategory: (String) -> Unit,
    onScroll: (Float) -> Unit = {},
    isAeroTheme: Boolean = false,
    isBlackBackground: Boolean = false,
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val currentItem by player.currentAudio.collectAsState()
    val initialPos = remember { getScrollPosition("home_music") }
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialPos.first,
        initialFirstVisibleItemScrollOffset = initialPos.second
    )

    DisposableEffect(scrollState) {
        onDispose {
            onScrollPositionChanged("home_music", scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset)
        }
    }

    val categories = listOf("artists", "albums", "songs", "playlists", "podcasts", "search")

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(categories, key = { _, category -> category }) { index, category ->
            val accentColor = LocalZuneAccent.current
            val textColor = if (isAeroTheme) {
                accentColor.copy(alpha = 0.7f)
            } else {
                if (isBlackBackground) accentColor.lightenForText() else Color.White.copy(alpha = 0.6f)
            }
            Text(
                text = category,
                style = ZuneTypography.h2.copy(
                    fontFamily = SegoeUiLightFontFamily,
                    fontSize = 56.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Light
                ),
                color = textColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .metroClickable {
                        onNavigateToCategory(category)
                    }
            )
        }
    }
}

@Composable
fun FeaturedAlbumsPage(
    audioItems: List<AudioItem>,
    onPlayAlbum: (String) -> Unit,
    onScroll: (Float) -> Unit = {},
    isAeroTheme: Boolean = false,
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val initialPos = remember { getScrollPosition("home_featured") }
    val scrollState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        initialFirstVisibleItemIndex = initialPos.first,
        initialFirstVisibleItemScrollOffset = initialPos.second
    )

    DisposableEffect(scrollState) {
        onDispose {
            onScrollPositionChanged("home_featured", scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset)
        }
    }

    val albums = remember(audioItems) {
        audioItems.distinctBy { it.album }.shuffled().take(8)
    }

    LazyVerticalGrid(
        state = scrollState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(albums, key = { _, item -> item.id }) { index, item ->
            val cardGlassModifier = if (isAeroTheme) {
                Modifier
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.40f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(1.dp)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        ),
                        shape = RoundedCornerShape(5.dp)
                    )
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(5.dp))
                    .clip(RoundedCornerShape(5.dp))
            } else {
                Modifier
                    .background(Color(0xFF1E1E1E))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .then(cardGlassModifier)
                    .metroClickable { onPlayAlbum(item.album) },
                contentAlignment = Alignment.BottomStart
            ) {
                if (item.albumArtUri != null) {
                    AsyncImage(
                        model = item.albumArtUri,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isAeroTheme) {
                    val aeroBrush = remember {
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.5f),
                            0.5f to Color.White.copy(alpha = 0.1f),
                            0.5f to Color.Transparent,
                            1f to Color.Transparent
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().background(aeroBrush))
                }
            }
        }
    }
}

@Composable
fun PersonalizePage(
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedBg by remember { mutableStateOf(prefs.getInt("bg_selection", 0)) }

    val options = listOf(
        0 to "pure black",
        R.drawable.bg_1 to "background 1",
        R.drawable.bg_2 to "background 2",
        R.drawable.bg_3 to "background 3",
        R.drawable.bg_4 to "background 4"
    )

    val initialPos = remember { getScrollPosition("home_personalize") }
    val scrollState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        initialFirstVisibleItemIndex = initialPos.first,
        initialFirstVisibleItemScrollOffset = initialPos.second
    )
    DisposableEffect(scrollState) {
        onDispose {
            onScrollPositionChanged("home_personalize", scrollState.firstVisibleItemIndex, scrollState.firstVisibleItemScrollOffset)
        }
    }

    LazyVerticalGrid(
        state = scrollState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(options, key = { _, option -> option.first }) { index, (drawableRes, label) ->
            val isSelected = selectedBg == drawableRes
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .metroClickable {
                        selectedBg = drawableRes
                        prefs.edit().putInt("bg_selection", drawableRes).apply()
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(if (drawableRes == 0) Color.Black else Color.Transparent)
                        .padding(if (isSelected) 4.dp else 0.dp)
                        .then(
                            if (isSelected) Modifier.background(LocalZuneAccent.current).padding(4.dp)
                            else Modifier
                        )
                ) {
                    if (drawableRes != 0) {
                        AsyncImage(
                            model = drawableRes,
                            contentDescription = label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceholderPage(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            text = "no $title found.",
            style = ZuneTypography.body1,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp)
        )
    }
}

private fun Color.lightenForText(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = hsl[2].coerceAtLeast(0.6f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

@Composable
fun rememberVideoTileThumbnail(context: android.content.Context, videoUri: android.net.Uri?): android.graphics.Bitmap? {
    var bitmap by remember(videoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(videoUri) {
        if (videoUri != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(videoUri, android.util.Size(512, 512), null)
                    } else {
                        var retriever: android.media.MediaMetadataRetriever? = null
                        try {
                            retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(context, videoUri)
                            retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } finally {
                            retriever?.release()
                        }
                    }
                    if (bmp != null) {
                        bitmap = bmp
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    return bitmap
}

@Composable
fun PinnedTileView(
    tileItem: PinnedTileItem,
    size: Int,
    isPlaying: Boolean,
    isEditMode: Boolean,
    isHovered: Boolean,
    isDragged: Boolean,
    dragOffset: Offset,
    isAeroTheme: Boolean,
    onPlay: (PinnedTileItem) -> Unit,
    onUnpin: (Long) -> Unit,
    onCycleSize: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val videoThumbnail = if (tileItem.type == "video") rememberVideoTileThumbnail(context, tileItem.imageUri) else null
    val imageModel = if (tileItem.type == "video") videoThumbnail else tileItem.imageUri

    var tileState by remember { mutableIntStateOf(0) }

    if (size > 1) {
        LaunchedEffect(tileItem.id, isPlaying) {
            if (isPlaying) {
                tileState = 0
                return@LaunchedEffect
            }
            delay(kotlin.random.Random.nextLong(500, 3000))
            while (true) {
                delay(kotlin.random.Random.nextLong(4000, 10000))
                tileState = if (tileState == 0) {
                    if (kotlin.random.Random.nextBoolean()) 1 else 2
                } else {
                    0
                }
            }
        }
    }

    val slidePercent by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when (tileState) {
            1 -> 0.5f
            2 -> 1f
            else -> 0f
        },
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "TileSlidePercent"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            if (isAeroTheme) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w * 0.6f, 0f)
                        lineTo(0f, h * 0.6f)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w * 0.5f, h * 0.5f)
                        )
                    )
                }
            }
            if (size > 1) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalZuneAccent.current)
                        .padding(12.dp),
                    contentAlignment = if (tileState == 2) Alignment.Center else Alignment.TopStart
                ) {
                    val fontSize = if (size == 4) 22.sp else 18.sp
                    val text = if (tileState == 2) "${tileItem.title}\n${tileItem.subtitle}" else tileItem.title

                    Text(
                        text = text,
                        style = ZuneTypography.h2.copy(
                            fontSize = fontSize,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                        ),
                        color = Color.White,
                        maxLines = if (tileState == 2) 4 else 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            if (imageModel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = this.size.height * slidePercent
                        }
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = tileItem.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        alpha = if (isEditMode && !isHovered) 0.7f else 1f
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isAeroTheme) LocalZuneAccent.current.copy(alpha = 0.6f) else LocalZuneAccent.current)
                )
            }
        }

        if (isEditMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
                    .metroClickable { onUnpin(tileItem.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Unpin", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
                    .metroClickable { onCycleSize(tileItem.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Resize", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun PinnedPage(
    pinnedItems: List<Pair<PinnedTileItem, Int>>,
    currentPlayingId: Long?,
    onPlay: (PinnedTileItem) -> Unit,
    onUnpin: (Long) -> Unit,
    onCycleSize: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    isAeroTheme: Boolean,
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    var isEditMode by remember { mutableStateOf(false) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var hoveredId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var pointerOffset by remember { mutableStateOf(Offset.Zero) }
    val itemBounds = remember { mutableStateMapOf<Long, Rect>() }

    val initialPos = remember { getScrollPosition("home_pinned") }
    val scrollState = rememberScrollState(initial = initialPos.first)

    DisposableEffect(scrollState) {
        onDispose {
            onScrollPositionChanged("home_pinned", scrollState.value, 0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .pointerInput(isEditMode) {
                if (isEditMode) {
                    detectTapGestures { isEditMode = false }
                }
            }
    ) {
        val pinsTitleStyle = if (isAeroTheme) {
            ZuneTypography.h2.copy(
                fontSize = 48.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                letterSpacing = 0.sp,
                brush = AeroBlueOrbGradient
            )
        } else {
            ZuneTypography.h2.copy(
                fontSize = 80.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                letterSpacing = 2.sp
            )
        }
        val pinsTitleColor = if (isAeroTheme) Color.Unspecified else Color.White
        val pinsTitlePadding = if (isAeroTheme) {
            Modifier.padding(start = 24.dp, bottom = 16.dp, top = 24.dp)
        } else {
            Modifier.padding(start = 24.dp, bottom = 16.dp, top = 8.dp)
        }

        androidx.compose.material.Text(
            text = if (isAeroTheme) "Pins" else "pins",
            style = pinsTitleStyle,
            color = pinsTitleColor,
            modifier = pinsTitlePadding
        )

        if (pinnedItems.isEmpty()) {
            PlaceholderPage("pinned items")
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val columns = if (isLandscape) 7 else 4
                val horizontalSpacing = 8.dp
                val verticalSpacing = 8.dp
                val colWidth = (maxWidth - horizontalSpacing * (columns - 1)) / columns

                val occupied = remember(pinnedItems, columns) {
                    mutableSetOf<Pair<Int, Int>>()
                }
                val placements = remember(pinnedItems, columns) {
                    val map = mutableMapOf<Long, Rect>()
                    occupied.clear()
                    for ((tileItem, size) in pinnedItems) {
                        val w = if (size == 4) 4 else if (size == 2) 2 else 1
                        val h = if (size == 4) 2 else if (size == 2) 2 else 1
                        var found = false
                        var searchY = 0
                        while (!found) {
                            for (searchX in 0..columns - w) {
                                var collision = false
                                for (dy in 0 until h) {
                                    for (dx in 0 until w) {
                                        if (occupied.contains(Pair(searchX + dx, searchY + dy))) {
                                            collision = true
                                            break
                                        }
                                    }
                                    if (collision) break
                                }
                                if (!collision) {
                                    for (dy in 0 until h) {
                                        for (dx in 0 until w) {
                                            occupied.add(Pair(searchX + dx, searchY + dy))
                                        }
                                    }
                                    map[tileItem.id] = Rect(
                                        left = searchX.toFloat(),
                                        top = searchY.toFloat(),
                                        right = (searchX + w).toFloat(),
                                        bottom = (searchY + h).toFloat()
                                    )
                                    found = true
                                    break
                                }
                            }
                            if (!found) searchY++
                        }
                    }
                    map
                }

                val maxY = if (occupied.isEmpty()) 0 else occupied.maxOf { it.second } + 1
                val totalHeight = if (maxY > 0) (colWidth * maxY) + (verticalSpacing * (maxY - 1)) else 0.dp

                Box(modifier = Modifier.fillMaxWidth().height(totalHeight + 148.dp).padding(top = 16.dp)) {
                    pinnedItems.forEachIndexed { index, (tileItem, size) ->
                        val rect = placements[tileItem.id] ?: return@forEachIndexed
                        val xOffset = (colWidth * rect.left) + (horizontalSpacing * rect.left)
                        val yOffset = (colWidth * rect.top) + (verticalSpacing * rect.top)
                        val width = (colWidth * rect.width) + (horizontalSpacing * (rect.width - 1f))
                        val height = (colWidth * rect.height) + (verticalSpacing * (rect.height - 1f))

                        val id = tileItem.id
                        val isDragged = draggedId == id
                        val isHovered = hoveredId == id
                        val isPlaying = currentPlayingId == id

                        Box(
                            modifier = Modifier
                                .offset(x = xOffset, y = yOffset)
                                .size(width = width, height = height)
                                .onGloballyPositioned { coordinates ->
                                    itemBounds[id] = coordinates.boundsInWindow()
                                }
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragged) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                        alpha = 0.9f
                                    } else if (isEditMode) {
                                        scaleX = if (isHovered) 0.85f else 0.92f
                                        scaleY = if (isHovered) 0.85f else 0.92f
                                        alpha = if (isHovered) 0.5f else 1f
                                    }
                                }
                                .then(
                                    if (isAeroTheme) {
                                        val tileShape = RoundedCornerShape(6.dp)
                                        val innerShape = RoundedCornerShape(5.dp)
                                        if (isPlaying) {
                                            Modifier
                                                .border(
                                                    width = 1.dp,
                                                    color = LocalZuneAccent.current.copy(alpha = 0.5f),
                                                    shape = tileShape
                                                )
                                                .padding(1.dp)
                                                .border(
                                                    width = 1.5.dp,
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White,
                                                            LocalZuneAccent.current
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.18f),
                                                            LocalZuneAccent.current.copy(alpha = 0.08f)
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .clip(innerShape)
                                        } else {
                                            Modifier
                                                .border(
                                                    width = 1.dp,
                                                    color = Color.Black.copy(alpha = 0.35f),
                                                    shape = tileShape
                                                )
                                                .padding(1.dp)
                                                .border(
                                                    width = 1.dp,
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.55f),
                                                            Color.White.copy(alpha = 0.08f)
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.14f),
                                                            Color.White.copy(alpha = 0.04f)
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .clip(innerShape)
                                        }
                                    } else {
                                        Modifier
                                            .then(if (isPlaying) Modifier.border(3.dp, LocalZuneAccent.current) else Modifier)
                                            .background(LocalZuneAccent.current)
                                    }
                                )
                                .pointerInput(isEditMode, id) {
                                    if (isEditMode) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                draggedId = id
                                                hoveredId = null
                                                dragOffset = Offset.Zero
                                                pointerOffset = offset
                                            },
                                            onDragEnd = {
                                                if (hoveredId != null && draggedId != null && hoveredId != draggedId) {
                                                    val sourceIndex = pinnedItems.indexOfFirst { it.first.id == draggedId }
                                                    val targetIndex = pinnedItems.indexOfFirst { it.first.id == hoveredId }
                                                    if (sourceIndex != -1 && targetIndex != -1) {
                                                        onMove(sourceIndex, targetIndex)
                                                    }
                                                }
                                                draggedId = null
                                                hoveredId = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggedId = null
                                                hoveredId = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount

                                                val myBounds = itemBounds[id] ?: return@detectDragGestures
                                                val absoluteFingerPos = myBounds.topLeft + pointerOffset + dragOffset

                                                var newHovered: Long? = null
                                                for ((targetId, bounds) in itemBounds) {
                                                    if (targetId != id && bounds.contains(absoluteFingerPos)) {
                                                        newHovered = targetId
                                                        break
                                                    }
                                                }

                                                if (newHovered == null) {
                                                    var closestItem: Long? = null
                                                    var minDistance = Float.MAX_VALUE

                                                    for ((targetId, bounds) in itemBounds) {
                                                        if (targetId == id) continue
                                                        val cx = bounds.left + bounds.width / 2f
                                                        val cy = bounds.top + bounds.height / 2f
                                                        val dx = cx - absoluteFingerPos.x
                                                        val dy = cy - absoluteFingerPos.y
                                                        val dist = dx * dx + dy * dy
                                                        if (dist < minDistance) {
                                                            minDistance = dist
                                                            closestItem = targetId
                                                        }
                                                    }
                                                    newHovered = closestItem
                                                }

                                                hoveredId = newHovered
                                            }
                                        )
                                    } else {
                                        detectTapGestures(
                                            onTap = { onPlay(tileItem) },
                                            onLongPress = { isEditMode = true }
                                        )
                                    }
                                }
                        ) {
                            PinnedTileView(
                                tileItem = tileItem,
                                size = size,
                                isPlaying = isPlaying,
                                isEditMode = isEditMode,
                                isHovered = isHovered,
                                isDragged = isDragged,
                                dragOffset = dragOffset,
                                isAeroTheme = isAeroTheme,
                                onPlay = onPlay,
                                onUnpin = onUnpin,
                                onCycleSize = onCycleSize
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun WmcStartOrbAndClock() {
    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        while (true) {
            timeText = sdf.format(java.util.Date()).uppercase()
            kotlinx.coroutines.delay(1000)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = timeText,
            style = ZuneTypography.h2.copy(
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ),
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}


data class PinnedTileItem(
    val id: Long,
    val type: String,
    val title: String,
    val subtitle: String,
    val imageUri: android.net.Uri?,
    val gradientColors: List<Color>,
    val size: Int
)

private fun queryLocalPhotos(context: android.content.Context): List<PhotoItem> {
    val list = mutableListOf<PhotoItem>()
    val permissionString = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, permissionString) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (hasPermission) {
        try {
            val resolver = context.contentResolver
            val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Images.Media._ID,
                android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                android.provider.MediaStore.Images.Media.DATE_TAKEN,
                android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            resolver.query(uri, projection, null, null, "${android.provider.MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_TAKEN)
                val bucketColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "photo_$id"
                    val date = cursor.getLong(dateColumn)
                    val album = cursor.getString(bucketColumn) ?: "camera roll"
                    val contentUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    list.add(
                        PhotoItem(
                            id = id,
                            uri = contentUri,
                            dateTaken = date,
                            albumName = album.lowercase(),
                            title = name
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (list.isEmpty()) {
        list.addAll(generateMockPhotos())
    }
    return list
}

private fun queryLocalVideos(context: android.content.Context): List<VideoItem> {
    val list = mutableListOf<VideoItem>()
    val permissionString = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, permissionString) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (hasPermission) {
        try {
            val resolver = context.contentResolver
            val uri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Video.Media._ID,
                android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                android.provider.MediaStore.Video.Media.DURATION,
                android.provider.MediaStore.Video.Media.DATE_ADDED
            )
            resolver.query(uri, projection, null, null, "${android.provider.MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "video_$id"
                    val duration = cursor.getLong(durationColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val contentUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    list.add(
                        VideoItem(
                            id = id,
                            uri = contentUri,
                            title = name.removeSuffix(".mp4").lowercase(),
                            subtitle = "local video",
                            durationMs = duration,
                            dateAdded = dateAdded,
                            gradientColors = listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
                            videoUrl = contentUri.toString()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (list.isEmpty()) {
        list.addAll(generateMockVideos())
    }
    return list.sortedByDescending { it.dateAdded }
}

private sealed class LiveTileItem {
    data class ImageUri(val uri: android.net.Uri) : LiveTileItem()
    data class Gradient(val colors: List<Color>) : LiveTileItem()
}

@Composable
fun PicturesAndVideosPagePreview(
    isAeroTheme: Boolean,
    photosList: List<PhotoItem>,
    onNavigateToPhotos: () -> Unit,
    onNavigateToVideos: () -> Unit
) {
    val liveTileItems = remember(photosList) {
        val localPhotos = photosList.filter { it.uri != null }
        if (localPhotos.isNotEmpty()) {
            localPhotos.take(10).map { LiveTileItem.ImageUri(it.uri!!) }
        } else {
            photosList.take(10).map {
                if (it.gradientColors.isNotEmpty()) {
                    LiveTileItem.Gradient(it.gradientColors)
                } else {
                    LiveTileItem.Gradient(listOf(Color(0xFFEE0979), Color(0xFFFF6A00)))
                }
            }
        }
    }

    var currentIndex by remember { mutableStateOf(0) }
    LaunchedEffect(liveTileItems) {
        if (liveTileItems.isNotEmpty()) {
            while (true) {
                delay(6000)
                currentIndex = (currentIndex + 1) % liveTileItems.size
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ken_burns")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val previewHeight = if (isLandscape) 120.dp else 160.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(previewHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .background(Color(0xFF222222))
                    .metroClickable { onNavigateToPhotos() },
                contentAlignment = Alignment.BottomStart
            ) {
                if (liveTileItems.isNotEmpty()) {
                    val currentItem = liveTileItems.getOrNull(currentIndex)
                    AnimatedContent(
                        targetState = currentItem,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(1000)) + slideInVertically(
                                animationSpec = tween(1000),
                                initialOffsetY = { it }
                            )).togetherWith(
                                fadeOut(animationSpec = tween(1000)) + slideOutVertically(
                                    animationSpec = tween(1000),
                                    targetOffsetY = { -it }
                                )
                            )
                        },
                        label = "live_tile_transition",
                        modifier = Modifier.fillMaxSize()
                    ) { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        ) {
                            when (item) {
                                is LiveTileItem.ImageUri -> {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                is LiveTileItem.Gradient -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(if (isAeroTheme) LocalZuneAccent.current.copy(alpha = 0.6f) else LocalZuneAccent.current)
                                    )
                                }
                                null -> {}
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isAeroTheme) LocalZuneAccent.current.copy(alpha = 0.6f) else LocalZuneAccent.current)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                )

                Text(
                    text = "photos",
                    style = ZuneTypography.h2.copy(fontSize = 16.sp, fontFamily = SegoeUiLightFontFamily),
                    color = Color.White,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isAeroTheme) LocalZuneAccent.current.copy(alpha = 0.6f) else LocalZuneAccent.current)
                    .metroClickable { onNavigateToVideos() },
                contentAlignment = Alignment.BottomStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .align(Alignment.TopStart)
                )

                Text(
                    text = "videos",
                    style = ZuneTypography.h2.copy(fontSize = 16.sp, fontFamily = SegoeUiLightFontFamily),
                    color = Color.White,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Text(
            text = "open pictures + videos",
            style = ZuneTypography.h2.copy(
                fontFamily = SegoeUiLightFontFamily,
                fontSize = 24.sp,
                color = LocalZuneAccent.current
            ),
            modifier = Modifier.metroClickable { onNavigateToPhotos() }
        )
        Text(
            text = "view and organize photos and local videos from your device.",
            style = ZuneTypography.body1,
            color = ZuneTextSecondary
        )
    }
}

// Private helper function for formatting millisecond track timelines
private fun formatPanelTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "$minutes:${String.format("%02d", seconds)}"
}
