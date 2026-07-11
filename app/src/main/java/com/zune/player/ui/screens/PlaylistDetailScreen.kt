package com.zune.player.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zune.player.data.AudioItem
import com.zune.player.ui.components.HeaderAction
import com.zune.player.ui.components.metroClickable
import com.zune.player.LocalSharedTransitionScope
import com.zune.player.LocalAnimatedVisibilityScope
import com.zune.player.ui.theme.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class PlaylistTrackUiItem(
    val stableId: String,
    val audioItem: AudioItem
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlistName: String,
    tracks: List<AudioItem>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlayNextPlaylist: () -> Unit,
    onAddToQueuePlaylist: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit, // from, to
    onPlayNextTrack: (AudioItem) -> Unit,
    onAddToQueueTrack: (AudioItem) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    currentPlayingTitle: String? = null
) {
    val items = remember { mutableStateListOf<PlaylistTrackUiItem>() }
    
    LaunchedEffect(tracks) {
        val newItems = mutableListOf<PlaylistTrackUiItem>()
        val existingMap = items.groupBy { it.audioItem.id }.mapValues { it.value.toMutableList() }
        
        tracks.forEach { audioItem ->
            val match = existingMap[audioItem.id]?.removeFirstOrNull()
            if (match != null) {
                newItems.add(match)
            } else {
                newItems.add(
                    PlaylistTrackUiItem(
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
        onMoveTrack(from.index, to.index)
    }

    var longPressedTrack by remember { mutableStateOf<Pair<Int, AudioItem>?>(null) }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
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

                Text(
                    text = "playlists",
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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = playlistName.uppercase(),
                    style = ZuneTypography.h1.copy(
                        fontFamily = SegoeUiFontFamily,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = LocalZuneAccent.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play and Shuffle buttons (Header Actions row)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        onClick = onPlayNextPlaylist
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeaderAction(
                        text = "add to queue",
                        icon = Icons.Default.ArrowDownward,
                        onClick = onAddToQueuePlaylist
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Track List
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("no tracks in this playlist", color = ZuneTextSecondary, style = ZuneTypography.body1)
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    itemsIndexed(items, key = { _, wrapper -> wrapper.stableId }) { index, wrapper ->
                        val track = wrapper.audioItem
                        ReorderableItem(reorderState, key = wrapper.stableId) { isDragging ->
                            PlaylistTrackItem(
                                track = track,
                                isDragging = isDragging,
                                draggableHandleModifier = Modifier.draggableHandle(),
                                onClick = { onTrackClick(index) },
                                onLongClick = { longPressedTrack = Pair(index, track) },
                                isCurrentlyPlaying = track.title.equals(currentPlayingTitle, ignoreCase = true)
                            )
                        }
                    }
                }
            }
        }

        // Long Press Context Menu
        if (longPressedTrack != null) {
            val (idx, track) = longPressedTrack!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { longPressedTrack = null }
            )
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .drawBehind {
                        val strokeWidth = 1.dp.toPx()
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = strokeWidth
                        )
                    }
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp, top = 8.dp)
                    .clickable(enabled = false) {}
            ) {
                Text(
                    text = track.title.lowercase(),
                    style = ZuneTypography.body2.copy(fontSize = 14.sp),
                    color = ZuneTextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                
                DropUpMenuItem(text = "play") {
                    longPressedTrack = null
                    onTrackClick(idx)
                }
                
                DropUpMenuItem(text = "play next") {
                    longPressedTrack = null
                    onPlayNextTrack(track)
                }
                
                DropUpMenuItem(text = "add to queue") {
                    longPressedTrack = null
                    onAddToQueueTrack(track)
                }
                
                DropUpMenuItem(text = "remove from playlist") {
                    longPressedTrack = null
                    onRemoveTrack(idx)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistTrackItem(
    track: AudioItem,
    isDragging: Boolean,
    draggableHandleModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isCurrentlyPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isDragging) Color.White.copy(0.07f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "Drag to reorder",
            tint = Color.White.copy(alpha = 0.28f),
            modifier = Modifier
                .size(24.dp)
                .then(draggableHandleModifier)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.lowercase(),
                style = ZuneTypography.h4.copy(fontSize = 18.sp),
                color = if (isCurrentlyPlaying) LocalZuneAccent.current else ZuneTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist.lowercase(),
                style = ZuneTypography.body2.copy(fontSize = 13.sp),
                color = ZuneTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DropUpMenuItem(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text.lowercase(),
            style = ZuneTypography.h2.copy(
                fontFamily = SegoeUiLightFontFamily,
                fontSize = 20.sp,
                color = Color.White
            )
        )
    }
}
