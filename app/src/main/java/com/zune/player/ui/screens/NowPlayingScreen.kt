package com.zune.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import com.zune.player.ui.theme.ZuneIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.zune.player.ui.theme.ZuneTextPrimary
import com.zune.player.ui.theme.ZuneTextSecondary
import com.zune.player.ui.theme.ZuneTypography
import com.zune.player.ui.components.metroClickable
import com.zune.player.player.AudioPlayer
import com.zune.player.LocalSharedTransitionScope
import com.zune.player.LocalAnimatedVisibilityScope
import com.zune.player.ui.theme.LocalZuneAccent
import com.zune.player.ui.theme.SegoeUiFontFamily
import com.zune.player.ui.theme.SegoeUiLightFontFamily
import com.zune.player.ui.theme.SegoeUiBoldFontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun NowPlayingScreen(
    player: AudioPlayer,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val isPlaying     by player.isPlaying.collectAsState()
    val currentItem   by player.currentAudio.collectAsState()

    val localView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        localView.keepScreenOn = true
        onDispose {
            localView.keepScreenOn = false
        }
    }
    val queue         by player.queue.collectAsState()
    val upcomingItems by player.upcomingQueue.collectAsState()
    val shuffleEnabled by player.shuffleEnabled.collectAsState()
    val repeatMode    by player.repeatMode.collectAsState()
    val accent = LocalZuneAccent.current
    // Collect state reactively from AudioPlayer
    val isBuffering by player.isBuffering.collectAsState()
    val lyrics by player.lyrics.collectAsState()

    val playPauseScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "PlayPauseScale"
    )

    var livePosition by remember { mutableLongStateOf(0L) }
    var liveDuration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying, currentItem) {
        if (isPlaying) {
            while (true) {
                livePosition = player.currentPositionValue
                liveDuration = player.durationValue
                delay(250)
            }
        } else {
            livePosition = player.currentPositionValue
            liveDuration = player.durationValue
        }
    }

    var localCurrentPos by remember { mutableLongStateOf(0L) }
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val currentPos = if (isDragging) localCurrentPos else livePosition
    // Prefer the live ExoPlayer timeline duration over the value stamped on AudioItem
    // (which is captured from getPlayerDuration() before the stream is ready).
    val duration = if (liveDuration > 1000L) liveDuration
                   else currentItem?.durationMs?.takeIf { it > 1000L } ?: liveDuration.coerceAtLeast(1L)
    val sliderValue = seekPreview ?: (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val currentPositionState = rememberUpdatedState(currentPos)
    val durationState = rememberUpdatedState(duration)

    val activeLyricIndex = remember(lyrics, currentPos) {
        lyrics.indexOfLast { it.timeMs <= currentPos }
    }

    val currentIndex = queue.indexOfFirst { it.id == currentItem?.id }
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val artSize = screenWidth * 0.64f
    var swipeOffset by remember { mutableStateOf(0f) }
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = if (swipeOffset == 0f) {
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
        } else {
            snap()
        },
        label = "SwipeOffset"
    )

    val isPaused = !isPlaying
    val flipRotation by animateFloatAsState(
        targetValue = if (isPaused) 180f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "LiveTileFlipAnimation"
    )

    var showLyrics by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    var npBgType by remember { mutableIntStateOf(prefs.getInt("np_bg_type", 0)) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "np_bg_type") {
                npBgType = prefs.getInt("np_bg_type", 0)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var artistPhotoUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentItem?.artist) {
        val artistName = currentItem?.artist
        if (artistName != null && artistName.isNotBlank() && artistName != "Unknown Artist") {
            try {
                val searchRepository = org.koin.core.context.GlobalContext.get().get<com.maxrave.domain.repository.SearchRepository>()
                val resource = searchRepository.getSearchDataArtist(artistName).firstOrNull { r ->
                    r is com.maxrave.domain.utils.Resource.Success<*> || r is com.maxrave.domain.utils.Resource.Error<*>
                }
                if (resource is com.maxrave.domain.utils.Resource.Success<*>) {
                    val artistsList = (resource.data as? ArrayList<com.maxrave.domain.data.model.searchResult.artists.ArtistsResult>)
                    val matchedArtist = artistsList?.firstOrNull { it.artist.equals(artistName, ignoreCase = true) } 
                        ?: artistsList?.firstOrNull()
                    val url = matchedArtist?.thumbnails?.lastOrNull()?.url
                    if (url != null) {
                        artistPhotoUrl = url
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                artistPhotoUrl = null
            }
        } else {
            artistPhotoUrl = null
        }
    }

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): Offset {
                if (available.y < -20f) {
                    onOpenQueue()
                } else if (available.y > 20f) {
                    onBack()
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        com.zune.player.MainActivity.volumeTrigger.value = System.currentTimeMillis()
                        showLyrics = false
                    },
                    onDoubleTap = { offset ->
                        val halfWidth = size.width / 2
                        val currentPositionVal = currentPositionState.value
                        val durationVal = durationState.value
                        if (offset.x < halfWidth) {
                            val newPos = (currentPositionVal - 10000L).coerceAtLeast(0L)
                            player.seekTo(newPos)
                        } else {
                            val newPos = (currentPositionVal + 10000L).coerceAtMost(durationVal)
                            player.seekTo(newPos)
                        }
                    }
                )
            }
    ) {
        if (npBgType == 0) {
            // Ambient Blurred Album Art Background
            currentItem?.albumArtUri?.let { artUri ->
                AsyncImage(
                    model = artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(50.dp)
                        .graphicsLayer { alpha = 0.25f }
                )
            }
            
            // Dynamic Ambient Glow based on Accent Color
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(0f, 0f),
                            radius = 2000f
                        )
                    )
            )
        } else if (npBgType == 2) {
            // Artist Photo Background (with Album Art fallback)
            val bgModel = artistPhotoUrl ?: currentItem?.albumArtUri
            bgModel?.let { model ->
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.35f }
                )
                // Dark overlay to ensure readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // 1. Back Button
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.zune.player.R.drawable.zune_back),
                contentDescription = "Back",
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .offset(x = (-40).dp, y = (-24).dp)
                    .size(80.dp)
                    .metroClickable { onBack() }
            )

            // 2. Artist Name
            Text(
                text = (currentItem?.artist ?: "UNKNOWN ARTIST").uppercase(),
                style = ZuneTypography.h1.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.zune.player.R.font.segoeuithibd)),
                    fontSize = 36.sp, 
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black, 
                    letterSpacing = (-1).sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 3. Album Name
            Text(
                text = (currentItem?.album ?: "UNKNOWN ALBUM").uppercase(),
                style = ZuneTypography.h1.copy(fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = (-1).sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            // 4. Album Art & Scrolling Lyrics Frame
            Box(
                modifier = Modifier
                    .size(artSize)
                    .background(Color(0xFF1C1C1C))
                    .graphicsLayer {
                        translationX = animatedSwipeOffset
                        rotationY = flipRotation + (animatedSwipeOffset / size.width) * -15f
                        alpha = (1f - kotlin.math.abs(animatedSwipeOffset) / size.width).coerceIn(0.5f, 1f)
                        scaleX = playPauseScale
                        scaleY = playPauseScale
                        cameraDistance = 12f * density
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffset > 100f) {
                                    player.skipToPrevious()
                                } else if (swipeOffset < -100f) {
                                    player.skipToNext()
                                }
                                swipeOffset = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                swipeOffset += dragAmount
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                player.togglePlayPause()
                            },
                            onDoubleTap = { offset ->
                                val halfWidth = size.width / 2
                                val currentPositionStateVal = currentPositionState.value
                                val durationStateVal = durationState.value
                                if (offset.x < halfWidth) {
                                    val newPos = (currentPositionStateVal - 10000L).coerceAtLeast(0L)
                                    player.seekTo(newPos)
                                } else {
                                    val newPos = (currentPositionStateVal + 10000L).coerceAtMost(durationStateVal)
                                    player.seekTo(newPos)
                                }
                            }
                        )
                    }
            ) {
                if (flipRotation <= 90f) {
                    if (showLyrics) {
                        com.zune.player.ui.components.SynchronizedLyricsView(
                            lyrics = lyrics,
                            currentLyricIndex = activeLyricIndex,
                            onLyricClick = { timestamp -> player.seekTo(timestamp) },
                            onDismiss = { showLyrics = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val sharedTransitionScope = LocalSharedTransitionScope.current
                        val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

                        val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    rememberSharedContentState(key = "now_playing_art"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        } else {
                            Modifier
                        }

                        AsyncImage(
                            model = currentItem?.albumArtUri,
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().then(sharedModifier)
                        )
                        
                        if (isBuffering) {
                            androidx.compose.material.CircularProgressIndicator(
                                color = accent,
                                modifier = Modifier.align(Alignment.Center).size(48.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(accent)
                            .graphicsLayer {
                                rotationY = 180f
                            }
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            text = "PAUSED",
                            color = Color.Black,
                            fontFamily = SegoeUiBoldFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            letterSpacing = (-1.5).sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // 5. Seek Bar & Timestamps
            Column(
                modifier = Modifier
                    .width(artSize)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .pointerInput(duration) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val width = size.width
                                val startX = down.position.x
                                var currentX = startX
                                
                                seekPreview = (currentX / width.toFloat()).coerceIn(0f, 1f)
                                isDragging = true

                                var moveEvent: PointerInputChange?
                                do {
                                    moveEvent = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                        change.consume()
                                    }
                                } while (moveEvent != null && !moveEvent.isConsumed)

                                if (moveEvent != null) {
                                    horizontalDrag(down.id) { change ->
                                        currentX = change.position.x
                                        seekPreview = (currentX / width.toFloat()).coerceIn(0f, 1f)
                                        change.consume()
                                    }
                                }
                                
                                val finalProgress = (currentX / width.toFloat()).coerceIn(0f, 1f)
                                val seekMs = (finalProgress * duration).toLong()
                                player.seekTo(seekMs)
                                localCurrentPos = seekMs
                                seekPreview = null
                                coroutineScope.launch {
                                    delay(1200)
                                    isDragging = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha=0.3f)))
                    Box(modifier = Modifier.fillMaxWidth(sliderValue).height(2.dp).background(Color.White).align(Alignment.CenterStart))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(fmtMs(currentPos), style = ZuneTypography.caption.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color.White)
                    val remaining = duration - currentPos
                    Text("-${fmtMs(remaining)}", style = ZuneTypography.caption.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color.White)
                }
            }

            Spacer(Modifier.height(4.dp))

            // 6. Song Title
            Text(
                text = currentItem?.title ?: "No Track",
                style = ZuneTypography.h2.copy(fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.metroClickable { onOpenQueue() }
            )

            Spacer(Modifier.height(2.dp))

            // 7. Up Next Context
            Column(modifier = Modifier.padding(start = 24.dp)) {
                upcomingItems.take(1).forEach { track ->
                    Text(
                        text = track.title,
                        style = ZuneTypography.body1.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun NpBtn(size: Int, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .border(4.dp, Color.White, CircleShape)
            .metroClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

data class QueueUiItem(
    val stableId: String,
    val audioItem: com.zune.player.data.AudioItem
)

@Composable
fun QueuePanel(
    queue: List<com.zune.player.data.AudioItem>,
    currentId: Long?,
    accent: Color,
    shuffleEnabled: Boolean = false,
    onToggleShuffle: () -> Unit = {},
    onPlayAt: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit = {}
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val items = remember {
        androidx.compose.runtime.mutableStateListOf<QueueUiItem>().apply {
            addAll(queue.map { QueueUiItem(stableId = java.util.UUID.randomUUID().toString(), audioItem = it) })
        }
    }
    
    LaunchedEffect(queue) {
        val newItems = mutableListOf<QueueUiItem>()
        val existingMap = items.groupBy { it.audioItem.id }.mapValues { it.value.toMutableList() }
        
        queue.forEach { audioItem ->
            val match = existingMap[audioItem.id]?.removeFirstOrNull()
            if (match != null) {
                newItems.add(match)
            } else {
                newItems.add(
                    QueueUiItem(
                        stableId = java.util.UUID.randomUUID().toString(),
                        audioItem = audioItem
                    )
                )
            }
        }
        
        if (items.map { it.stableId } != newItems.map { it.stableId }) {
            items.clear()
            items.addAll(newItems)
        }
    }

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    // Auto-scroll to active playing track when the panel opens or track changes
    LaunchedEffect(currentId) {
        val index = queue.indexOfFirst { it.id == currentId }
        if (index >= 0) {
            lazyListState.animateScrollToItem(index)
        }
    }

    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        items.add(to.index, items.removeAt(from.index))
        onMove(from.index, to.index)
    }

    val dragY = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        dragY.snapTo(0f)
    }
    val coroutineScope = rememberCoroutineScope()

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (available.y < 0 && dragY.value > 0f) {
                    coroutineScope.launch {
                        dragY.snapTo((dragY.value + available.y).coerceAtLeast(0f))
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (available.y > 0) {
                    coroutineScope.launch {
                        dragY.snapTo((dragY.value + available.y).coerceAtLeast(0f))
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (dragY.value > 150f) {
                    onDismiss()
                } else {
                    dragY.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f))
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    Column(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(0, dragY.value.toInt()) }
            .fillMaxSize()
            .background(Color(0xFF0C0C0C))
            .nestedScroll(nestedScrollConnection)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY.value > 150f) {
                                onDismiss()
                            } else {
                                coroutineScope.launch {
                                    dragY.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f))
                                }
                            }
                        }
                    ) { change, dragAmount ->
                        coroutineScope.launch {
                            dragY.snapTo((dragY.value + dragAmount).coerceAtLeast(0f))
                        }
                    }
                }
        ) {
            // 1. Large "playing" watermark text cut off at the top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = "playing",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = SegoeUiFontFamily,
                        fontSize = 120.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Light
                    ),
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.offset(y = (-28).dp, x = (-8).dp)
                )
            }

            // 2. Track index counter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentIndex = queue.indexOfFirst { it.id == currentId }
                val currentTrackNum = if (currentIndex >= 0) currentIndex + 1 else 1
                val totalTracks = queue.size

                Text(
                    text = if (shuffleEnabled) "${currentTrackNum} OF ${totalTracks} \u2022 SHUFFLE" else "${currentTrackNum} OF ${totalTracks}",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = SegoeUiFontFamily,
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = Color.White
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material.Icon(
                        imageVector = ZuneIcons.Save,
                        contentDescription = "Save queue as playlist",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(24.dp)
                            .metroClickable { showSaveDialog = true }
                    )

                    androidx.compose.material.Icon(
                        imageVector = ZuneIcons.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) accent else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(24.dp)
                            .metroClickable { onToggleShuffle() }
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))

        if (showSaveDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showSaveDialog = false }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111111))
                        .border(1.dp, Color.White.copy(alpha = 0.2f))
                        .padding(24.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "save queue as playlist",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = SegoeUiLightFontFamily,
                                fontSize = 28.sp,
                                color = Color.White
                            )
                        )
                        
                        androidx.compose.material.TextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 18.sp,
                                fontFamily = SegoeUiFontFamily
                            ),
                            colors = androidx.compose.material.TextFieldDefaults.textFieldColors(
                                backgroundColor = Color(0xFF222222),
                                focusedIndicatorColor = accent,
                                unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f),
                                textColor = Color.White
                            ),
                            singleLine = true
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "cancel",
                                style = androidx.compose.ui.text.TextStyle(fontFamily = SegoeUiFontFamily, fontSize = 16.sp, color = Color.White.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .clickable { showSaveDialog = false }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "save",
                                style = androidx.compose.ui.text.TextStyle(fontFamily = SegoeUiFontFamily, fontSize = 16.sp, color = accent, fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .clickable {
                                        if (newPlaylistName.isNotBlank()) {
                                            onSaveQueueAsPlaylist(newPlaylistName.trim())
                                            showSaveDialog = false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(items, key = { _, wrapper -> wrapper.stableId }) { index, wrapper ->
                val item = wrapper.audioItem
                ReorderableItem(reorderState, key = wrapper.stableId) { isDragging ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDragging) Color.White.copy(0.07f) else if (item.id == currentId) Color.White.copy(0.04f) else Color.Transparent)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = ZuneIcons.DragHandle,
                            contentDescription = "Drag",
                            tint = Color.White.copy(0.28f),
                            modifier = Modifier
                                .size(18.dp)
                                .draggableHandle()
                        )
                        
                        Spacer(Modifier.width(10.dp))

                        // Duration on the left side (only for active track, spacer for others)
                        Box(
                            modifier = Modifier.width(48.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (item.id == currentId) {
                                Text(
                                    text = fmtMs(item.durationMs),
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontFamily = SegoeUiFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                                    ),
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }

                        // Track Title and Artist / Album details
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .metroClickable { onPlayAt(index) }
                        ) {
                            Text(
                                text = item.title,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = SegoeUiFontFamily,
                                    fontSize = 18.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                color = if (item.id == currentId) accent else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Text(
                                text = "${item.artist.uppercase()}   ${item.album.uppercase()}",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = SegoeUiFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                                ),
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = ZuneIcons.Close,
                            contentDescription = "Remove",
                            tint = Color.White.copy(0.35f),
                            modifier = Modifier
                                .size(16.dp)
                                .metroClickable { onRemove(index) }
                        )
                    }
                }
            }
        }
    }
}

private fun fmtMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "${s / 60}:${String.format("%02d", s % 60)}"
}
