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
import com.zune.player.ui.theme.ZuneIcons
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
    onRenamePlaylist: (String) -> Unit,
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
    var showRenameDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf(playlistName) }

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
                        onClick = onPlayNextPlaylist
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeaderAction(
                        text = "add to queue",
                        icon = ZuneIcons.ArrowDownward,
                        onClick = onAddToQueuePlaylist
                    )
                    HeaderAction(
                        text = "rename",
                        icon = ZuneIcons.Edit,
                        onClick = {
                            newPlaylistName = playlistName
                            showRenameDialog = true
                        }
                    )
                }
            }

            if (showRenameDialog) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showRenameDialog = false }
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
                                text = "rename playlist",
                                style = ZuneTypography.h2.copy(
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
                                    focusedIndicatorColor = LocalZuneAccent.current,
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
                                    style = ZuneTypography.body1.copy(fontFamily = SegoeUiFontFamily, color = ZuneTextSecondary),
                                    modifier = Modifier
                                        .clickable { showRenameDialog = false }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "rename",
                                    style = ZuneTypography.body1.copy(fontFamily = SegoeUiFontFamily, color = LocalZuneAccent.current, fontWeight = FontWeight.Bold),
                                    modifier = Modifier
                                        .clickable {
                                            if (newPlaylistName.isNotBlank()) {
                                                onRenamePlaylist(newPlaylistName.trim())
                                                showRenameDialog = false
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
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
                                onPlayNext = { onPlayNextTrack(track) },
                                onAddToQueue = { onAddToQueueTrack(track) },
                                onRemove = { onRemoveTrack(index) },
                                isCurrentlyPlaying = track.title.equals(currentPlayingTitle, ignoreCase = true)
                            )
                        }
                    }
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
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemove: () -> Unit,
    isCurrentlyPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDragging) Color.White.copy(0.07f) else Color.Transparent)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (prefs.getBoolean("haptic_feedback_enabled", true)) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        }
                        showMenu = true
                    }
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ZuneIcons.DragHandle,
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
                    color = if (isCurrentlyPlaying) {
                        val accent = LocalZuneAccent.current
                        if (accent == Color.White) Color(0xFFD80073) else accent
                    } else ZuneTextPrimary,
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

        androidx.compose.material.DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Color(0xFF1A1A1A))
        ) {
            androidx.compose.material.DropdownMenuItem(onClick = {
                showMenu = false
                onClick()
            }) {
                Text("play", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            androidx.compose.material.DropdownMenuItem(onClick = {
                showMenu = false
                onPlayNext()
            }) {
                Text("play next", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            androidx.compose.material.DropdownMenuItem(onClick = {
                showMenu = false
                onAddToQueue()
            }) {
                Text("add to queue", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
            androidx.compose.material.DropdownMenuItem(onClick = {
                showMenu = false
                onRemove()
            }) {
                Text("remove from playlist", style = ZuneTypography.body1, color = ZuneTextPrimary)
            }
        }
    }
}
