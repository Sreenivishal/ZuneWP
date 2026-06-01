package com.zune.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.zune.player.ui.theme.SegoeUiLightFontFamily
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
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val categories = listOf("playlists", "songs", "artists", "albums")
    val initialIndex = categories.indexOf(initialCategory.lowercase()).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { categories.size }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(pagerState.currentPage) {
        onCategoryChanged(categories[pagerState.currentPage])
    }
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var songToAddToPlaylist by remember { mutableStateOf<com.zune.player.data.AudioItem?>(null) } // songToAddToPlaylist

    val tabWidths = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.zune.player.R.drawable.zune_back),
                contentDescription = "Back",
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .offset(x = (-20).dp, y = (-8).dp)
                    .size(80.dp)
                    .metroClickable { onBack() }
            )
            
            // Pivot Header
            Text(
                text = if (isAeroTheme) "Music Library" else "COLLECTION",
                style = if (isAeroTheme) {
                    ZuneTypography.h4.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        brush = AeroBlueOrbGradient
                    )
                } else {
                    ZuneTypography.h4.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                color = if (isAeroTheme) Color.Unspecified else ZuneTextSecondary,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
            )
            
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
                                
                                // The exact page we are currently on (e.g. 0.5 means halfway between 0 and 1)
                                val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                
                                val activePageIndex = pageOffset.toInt()
                                val fraction = pageOffset - activePageIndex
                                
                                // Add up the widths of all tabs BEFORE the active one
                                for (i in 0 until activePageIndex) {
                                    offsetPx += (tabWidths[i] ?: 0f)
                                }
                                
                                // Add the proportional width of the active tab being scrolled past
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
                    categories.forEachIndexed { index, title ->
                        val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        val distance = kotlin.math.abs(pageOffset - index)
                        val alpha = (1f - distance * 0.6f).coerceIn(0.4f, 1f)
                        
                        val isCurrentTab = pagerState.currentPage == index
                        val displayTitle = if (isAeroTheme) title.replaceFirstChar { it.uppercase() } else title
                        val displayText = if (isAeroTheme && isCurrentTab) "< $displayTitle >" else displayTitle
                        val textColor = if (isAeroTheme) {
                            // Win7 Aero: active = bright white, inactive = soft translucent white (not cyan)
                            if (isCurrentTab) Color.White else Color.White.copy(alpha = 0.42f)
                        } else {
                            Color.White.copy(alpha = alpha)
                        }
                        val textStyle = if (isAeroTheme) {
                            ZuneTypography.h2.copy(
                                fontFamily = SegoeUiLightFontFamily,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Normal
                            )
                        } else {
                            ZuneTypography.h1.copy(
                                fontFamily = SegoeUiLightFontFamily,
                                fontSize = 56.sp
                            )
                        }
                        
                        Text(
                            text = displayText,
                            style = textStyle,
                            color = textColor,
                            modifier = Modifier
                                .metroClickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val currentCategory = categories[page]
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
            
            // Persistent Now Playing Bar
            if (currentPlayingTitle != null) {
                val glassModifier = if (isAeroTheme) {
                    Modifier
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f), // stronger Win7 frosted glass
                                    Color.White.copy(alpha = 0.08f)
                                )
                            )
                        )
                } else {
                    Modifier.background(LocalZuneAccent.current)
                }

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

                    if (isAeroTheme) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val w = this.size.width
                            val h = this.size.height

                            // Win7 Aero Double Border Top Highlight
                            drawLine(
                                color = Color.Black.copy(alpha = 0.40f), // stronger outer shadow line
                                start = Offset(0f, 0.5.dp.toPx()),
                                end = Offset(w, 0.5.dp.toPx()),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.65f), // brighter Win7 inner glow line
                                start = Offset(0f, 1.5.dp.toPx()),
                                end = Offset(w, 1.5.dp.toPx()),
                                strokeWidth = 1.dp.toPx()
                            )

                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(w * 0.4f, 0f)
                                lineTo(0f, h)
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
                                    end = Offset(w * 0.3f, h)
                                )
                            )
                        }
                    }
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
        val map = mutableMapOf<Char, MutableList<Any>>()
        items.forEach { item ->
            val title = when (item) {
                is String -> item
                is com.zune.player.data.AudioItem -> {
                    if (categoryTitle.lowercase() == "albums") item.album else item.title
                }
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
                        else -> "${item.hashCode()}_$index"
                    }
                }
            ) { index, item ->
                if (item is Char) {
                    Box(
                        modifier = Modifier
                            .padding(top = 24.dp, bottom = 8.dp)
                            .size(64.dp)
                            .background(LocalZuneAccent.current)
                            .metroClickable { showJumpGrid = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.toString(),
                            style = ZuneTypography.h1.copy(fontSize = 42.sp),
                            color = Color.White
                        )
                    }
                } else {
                    val isSong = item is com.zune.player.data.AudioItem
                    val title = if (isSong) {
                        if (categoryTitle.lowercase() == "albums") (item as com.zune.player.data.AudioItem).album else (item as com.zune.player.data.AudioItem).title
                    } else item as String
                    var showMenu by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(item) {
                                    detectTapGestures(
                                        onTap = { onItemClick(title) },
                                        onLongPress = { showMenu = true }
                                    )
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSong) {
                                val audioItem = item as com.zune.player.data.AudioItem
                                if (audioItem.albumArtUri != null) {
                                    AsyncImage(
                                        model = audioItem.albumArtUri,
                                        contentDescription = "Album Art",
                                        modifier = Modifier.size(64.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(modifier = Modifier.size(64.dp).background(Color(0xFF222222)))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                val isCurrentPlaying = title.equals(currentPlayingTitle, ignoreCase = true)
                                val titleColor = if (isAeroTheme) {
                                    if (isCurrentPlaying) Color.White else LocalZuneAccent.current.copy(alpha = 0.7f)
                                } else {
                                    if (isCurrentPlaying) LocalZuneAccent.current else ZuneTextPrimary
                                }
                                val subtitleColor = if (isAeroTheme) {
                                    LocalZuneAccent.current.copy(alpha = 0.5f)
                                } else {
                                    ZuneTextSecondary
                                }

                                Text(
                                    text = title.lowercase(),
                                    style = ZuneTypography.h4.copy(fontSize = 32.sp),
                                    color = titleColor,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (isSong && categoryTitle.lowercase() == "songs") {
                                    val audioItem = item as com.zune.player.data.AudioItem
                                    Text(
                                        text = "${audioItem.artist} • ${audioItem.album}".lowercase(),
                                        style = ZuneTypography.body1,
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



        if (showJumpGrid) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) { detectTapGestures { showJumpGrid = false } }
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
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
                                style = ZuneTypography.h1.copy(fontSize = 32.sp),
                                color = if (hasItems) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
