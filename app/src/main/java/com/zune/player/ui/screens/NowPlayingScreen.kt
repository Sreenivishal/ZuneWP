package com.zune.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import com.zune.player.ui.theme.LocalZuneAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val queue         by player.queue.collectAsState()
    val upcomingItems by player.upcomingQueue.collectAsState()
    val shuffleEnabled by player.shuffleEnabled.collectAsState()
    val repeatMode    by player.repeatMode.collectAsState()
    val accent = LocalZuneAccent.current
    // Collect state reactively from AudioPlayer
    val currentPosFlow by player.currentPosition.collectAsState()
    val totalDurationFlow by player.duration.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    val lyrics by player.lyrics.collectAsState()
    val currentLyricIndex by player.currentLyricIndex.collectAsState()

    var localCurrentPos by remember { mutableLongStateOf(0L) }
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    val currentPos = if (isDragging) localCurrentPos else currentPosFlow
    // Prefer the live ExoPlayer timeline duration over the value stamped on AudioItem
    // (which is captured from getPlayerDuration() before the stream is ready).
    val duration = if (totalDurationFlow > 1000L) totalDurationFlow
                   else currentItem?.durationMs?.takeIf { it > 1000L } ?: totalDurationFlow.coerceAtLeast(1L)
    val sliderValue = seekPreview ?: (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    val currentIndex = queue.indexOfFirst { it.id == currentItem?.id }
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val artSize = screenWidth * 0.64f
    var swipeOffset by remember { mutableStateOf(0f) }

    var showLyrics by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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
                    .clickable { showLyrics = !showLyrics }
            ) {
                if (showLyrics) {
                    com.zune.player.ui.components.SynchronizedLyricsView(
                        lyrics = lyrics,
                        currentLyricIndex = currentLyricIndex,
                        onLyricClick = { timestamp -> player.seekTo(timestamp) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = currentItem?.albumArtUri,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    if (isBuffering) {
                        androidx.compose.material.CircularProgressIndicator(
                            color = accent,
                            modifier = Modifier.align(Alignment.Center).size(48.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // 5. Seek Bar & Timestamps
            Column(modifier = Modifier.width(artSize)) {
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
                                
                                isDragging = false
                                val finalProgress = (currentX / width.toFloat()).coerceIn(0f, 1f)
                                val seekMs = (finalProgress * duration).toLong()
                                player.seekTo(seekMs)
                                localCurrentPos = seekMs
                                seekPreview = null
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
                overflow = TextOverflow.Ellipsis
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

            Spacer(Modifier.height(24.dp))

            // 8. Bottom Controls Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NpBtn(48, onClick = { player.toggleShuffle() }) { 
                    androidx.compose.material.Icon(
                        androidx.compose.material.icons.Icons.Default.Shuffle, 
                        "Shuffle", 
                        tint = if (shuffleEnabled) Color.White else Color.White.copy(alpha = 0.4f), 
                        modifier = Modifier.size(20.dp)
                    ) 
                }
                NpBtn(48, onClick = { player.togglePlayPause() }) {
                    androidx.compose.material.Icon(
                        if (isPlaying) androidx.compose.material.icons.Icons.Default.Pause else androidx.compose.material.icons.Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                NpBtn(48, onClick = { onOpenQueue() }) { 
                    androidx.compose.material.Icon(
                        androidx.compose.material.icons.Icons.Default.QueueMusic, 
                        "Queue", 
                        tint = Color.White, 
                        modifier = Modifier.size(20.dp)
                    ) 
                }
            }
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
    onPlayAt: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val items = remember { androidx.compose.runtime.mutableStateListOf<QueueUiItem>() }
    
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
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        items.add(to.index, items.removeAt(from.index))
        onMove(from.index, to.index)
    }

    val dragY = remember { androidx.compose.animation.core.Animatable(0f) }
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
            .fillMaxWidth()
            .fillMaxHeight(0.55f)
            .background(Color(0xFF0C0C0C))
            .nestedScroll(nestedScrollConnection)
    ) {
        // Header
        Row(
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("QUEUE", style = ZuneTypography.body1.copy(fontSize = 11.sp, letterSpacing = 2.sp), color = Color.White)
            Icon(Icons.Default.Close, "Close", tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp).metroClickable { onDismiss() })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))

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
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DragHandle, "Drag", tint = Color.White.copy(0.28f),
                            modifier = Modifier.size(18.dp).draggableHandle())
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f).metroClickable { onPlayAt(index) }) {
                            Text(item.title.lowercase(), style = ZuneTypography.body1.copy(fontSize = 14.sp),
                                color = if (item.id == currentId) accent else Color.White, maxLines = 1)
                            Text(item.artist.lowercase(), style = ZuneTypography.body1.copy(fontSize = 11.sp),
                                color = ZuneTextSecondary, maxLines = 1)
                        }
                        Icon(Icons.Default.Close, "Remove", tint = Color.White.copy(0.35f),
                            modifier = Modifier.size(16.dp).metroClickable { onRemove(index) })
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
