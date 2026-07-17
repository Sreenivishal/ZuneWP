package com.zune.player.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.ui.text.style.TextOverflow
import com.zune.player.ui.theme.ZuneIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import android.content.ContentValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.zune.player.ui.components.PivotLayout
import com.zune.player.ui.components.metroClickable
import com.zune.player.ui.components.metroTilt
import com.zune.player.ui.components.paperTexture
import com.zune.player.LocalSharedTransitionScope
import com.zune.player.LocalAnimatedVisibilityScope
import com.zune.player.ui.theme.*
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

// Dynamic high-fidelity video item representing local or mock videos
data class VideoItem(
    val id: Long,
    val uri: Uri?,
    val title: String,
    val subtitle: String,
    val durationMs: Long,
    val dateAdded: Long = 0L,
    val posterUrl: String? = null,
    val gradientColors: List<Color> = emptyList(),
    val videoUrl: String = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    val folderName: String = "videos"
)

@Composable
fun VideosScreen(
    isAeroTheme: Boolean = false,
    pinnedIds: List<Long> = emptyList(),
    initialVideoId: Long? = null,
    onPin: (Long) -> Unit = {},
    onUnpin: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var longPressedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var videoToShowDetails by remember { mutableStateOf<VideoItem?>(null) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val tabWidths = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }
    
    val permissionString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permissionString) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var useSamplesFallback by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            useSamplesFallback = true
        }
    }
    
    // Scoped Storage and deletion triggers
    var reloadTrigger by remember { mutableIntStateOf(0) }
    
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                reloadTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }
    var deletedMockIds by remember { mutableStateOf(emptySet<Long>()) }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingDeleteUri = null
            reloadTrigger++
        }
    }
    
    // Load local videos & fallback mock catalog
    LaunchedEffect(hasPermission, useSamplesFallback, reloadTrigger) {
        isLoading = true
        val loadedList = mutableListOf<VideoItem>()
        
        if (hasPermission && !useSamplesFallback) {
            withContext(Dispatchers.IO) {
                try {
                    val resolver = context.contentResolver
                    val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    val projection = arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.DURATION,
                        MediaStore.Video.Media.DATE_ADDED,
                        MediaStore.Video.Media.BUCKET_DISPLAY_NAME
                    )
                    resolver.query(uri, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                        val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                        val bucketColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                        
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn) ?: "video_$id"
                            val duration = cursor.getLong(durationColumn)
                            val dateAdded = cursor.getLong(dateAddedColumn)
                            val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            val folder = if (bucketColumn != -1) cursor.getString(bucketColumn) ?: "camera" else "camera"
                            
                            loadedList.add(
                                VideoItem(
                                    id = id,
                                    uri = contentUri,
                                    title = name.removeSuffix(".mp4").lowercase(),
                                    subtitle = "local video",
                                    durationMs = duration,
                                    dateAdded = dateAdded,
                                    gradientColors = listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
                                    videoUrl = contentUri.toString(),
                                    folderName = folder.lowercase()
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        if (loadedList.isEmpty()) {
            loadedList.addAll(generateMockVideos())
        }
        
        videos = loadedList.sortedByDescending { it.dateAdded }
        isLoading = false
    }
    
    // Global filtered videos incorporating mock/visual deletion
    val currentVideosFiltered = remember(videos, deletedMockIds) {
        videos.filterNot { it.id in deletedMockIds }
    }
    
    // Jump selector modal state
    var showJumpSelector by remember { mutableStateOf(false) }
    val videoGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Fullscreen player target
    var activePlaybackVideo by remember { mutableStateOf<VideoItem?>(null) }
    var activePlayerVideo by remember { mutableStateOf<VideoItem?>(null) }
    
    val localView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(activePlaybackVideo) {
        if (activePlaybackVideo != null) {
            localView.keepScreenOn = true
        }
        onDispose {
            localView.keepScreenOn = false
        }
    }
    
    BackHandler(enabled = showJumpSelector || activePlaybackVideo != null || selectedFolder != null) {
        if (showJumpSelector) {
            showJumpSelector = false
        } else if (activePlaybackVideo != null) {
            activePlaybackVideo = null
        } else if (selectedFolder != null) {
            selectedFolder = null
        }
    }

    LaunchedEffect(activePlaybackVideo) {
        if (activePlaybackVideo != null) {
            activePlayerVideo = activePlaybackVideo
        }
    }
    
    // Deep-linked navigation handler
    LaunchedEffect(videos, initialVideoId) {
        if (initialVideoId != null && videos.isNotEmpty()) {
            val video = videos.find { it.id == initialVideoId }
            if (video != null) {
                activePlaybackVideo = video
            }
        }
    }
    
    val groupedVideos = remember(currentVideosFiltered) {
        val groups = mutableMapOf<String, MutableList<VideoItem>>()
        val sdf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US)
        currentVideosFiltered.forEach { v ->
            val monthYear = sdf.format(java.util.Date(v.dateAdded * 1000L))
            groups.getOrPut(monthYear) { mutableListOf() }.add(v)
        }
        groups.entries.map { it.key to it.value.toList() }
    }
    
    val flatVideoGridItems = remember(groupedVideos) {
        val list = mutableListOf<Any>()
        groupedVideos.forEach { (monthStr, videoList) ->
            list.add(monthStr) // Header item
            list.addAll(videoList) // Grid items
        }
        list
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val sharedTransitionScope = LocalSharedTransitionScope.current
        val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

        if (!hasPermission && !useSamplesFallback) {
            VideosPermissionPrompt(
                onAllow = { permissionLauncher.launch(permissionString) },
                onUseSamples = { useSamplesFallback = true },
                onBack = onBack
            )
        } else {
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
                                rememberSharedContentState(key = "header_videos"),
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
                        text = "videos",
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
                

                val tabs = listOf("all", "folders", "search")
                val pagerState = rememberPagerState { tabs.size }

                // Sliding giant tabs
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
                        tabs.forEachIndexed { index, title ->
                            val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            val distance = kotlin.math.abs(pageOffset - index)
                            val alpha = (1f - distance * 0.6f).coerceIn(0.4f, 1f)
                            
                            val isCurrentTab = pagerState.currentPage == index
                            val displayText = if (isAeroTheme && isCurrentTab) "< ${title.uppercase()} >" else title.uppercase()
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

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("loading videos...", color = ZuneTextSecondary, style = ZuneTypography.body1)
                    }
                } else {
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> {
                                if (currentVideosFiltered.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("no videos found.", color = ZuneTextSecondary, style = ZuneTypography.body1)
                                    }
                                } else {
                                    val defaultColumns = if (isLandscape) 5 else 3
                                    var columns by remember(isLandscape) { mutableIntStateOf(defaultColumns) }
                                    var zoomScale by remember { mutableStateOf(1f) }
                                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
                                        state = videoGridState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp)
                                            .pointerInput(isLandscape) {
                                                detectPinchZoomGesture { zoom ->
                                                    zoomScale *= zoom
                                                    if (zoomScale > 1.3f) {
                                                        columns = (columns - 1).coerceAtLeast(2)
                                                        zoomScale = 1f
                                                    } else if (zoomScale < 0.7f) {
                                                        columns = (columns + 1).coerceAtMost(6)
                                                        zoomScale = 1f
                                                    }
                                                }
                                            },
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        flatVideoGridItems.forEachIndexed { globalIndex, item ->
                                            if (item is String) {
                                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(columns) }, key = "header_$item") {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 16.dp, bottom = 8.dp)
                                                            .metroClickable { showJumpSelector = true },
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        val accent = LocalZuneAccent.current
                                                        val headerColor = if (accent == Color.White) Color(0xFFDCDCDC) else accent
                                                        Text(
                                                            text = item.lowercase(),
                                                            style = ZuneTypography.h1.copy(
                                                                fontSize = 20.sp,
                                                                fontFamily = SegoeUiFontFamily,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = headerColor,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            } else if (item is VideoItem) {
                                                item(key = item.id) {
                                                    VideoGridCard(
                                                        video = item,
                                                        isAeroTheme = isAeroTheme,
                                                        onClick = { activePlaybackVideo = item },
                                                        onLongClick = { longPressedVideo = item }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (selectedFolder == null) {
                                    val groupedFolders = remember(currentVideosFiltered) {
                                        currentVideosFiltered.groupBy { it.folderName }
                                    }
                                    if (groupedFolders.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("no folders found.", color = ZuneTextSecondary, style = ZuneTypography.body1)
                                        }
                                    } else {
                                        val columns = if (isLandscape) 4 else 2
                                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 24.dp),
                                            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(20.dp)
                                        ) {
                                            groupedFolders.forEach { (folderName, folderVideos) ->
                                                item(key = folderName) {
                                                    VideoFolderCard(
                                                        folderName = folderName,
                                                        videos = folderVideos,
                                                        isAeroTheme = isAeroTheme,
                                                        onClick = { selectedFolder = folderName }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val videosInFolder = remember(currentVideosFiltered, selectedFolder) {
                                        currentVideosFiltered.filter { it.folderName == selectedFolder }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedFolder = null }
                                                .padding(horizontal = 24.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = ZuneIcons.ArrowBack,
                                                contentDescription = "back to folders",
                                                tint = LocalZuneAccent.current,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "back to folders",
                                                color = LocalZuneAccent.current,
                                                style = ZuneTypography.body2.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        
                                        androidx.compose.foundation.lazy.LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 24.dp),
                                            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            lazyItemsIndexed(videosInFolder, key = { _, video -> video.id }) { idx, video ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .combinedClickable(
                                                            onClick = { activePlaybackVideo = video },
                                                            onLongClick = { longPressedVideo = video }
                                                        )
                                                        .padding(vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Video thumbnail
                                                    val context = LocalContext.current
                                                    val localThumbnail = rememberVideoThumbnail(context, video.uri)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 80.dp, height = 56.dp)
                                                            .background(Color(0xFF1E1E1E))
                                                     ) {
                                                         if (localThumbnail != null) {
                                                             androidx.compose.foundation.Image(
                                                                 bitmap = localThumbnail.asImageBitmap(),
                                                                 contentDescription = video.title,
                                                                 contentScale = ContentScale.Crop,
                                                                 modifier = Modifier.fillMaxSize()
                                                             )
                                                         } else if (video.posterUrl != null) {
                                                             AsyncImage(
                                                                 model = video.posterUrl,
                                                                 contentDescription = video.title,
                                                                 contentScale = ContentScale.Crop,
                                                                 modifier = Modifier.fillMaxSize()
                                                             )
                                                         } else {
                                                             Canvas(modifier = Modifier.fillMaxSize()) {
                                                                 drawRect(
                                                                     brush = Brush.linearGradient(
                                                                         colors = if (video.gradientColors.size >= 2) video.gradientColors else listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
                                                                         start = Offset.Zero,
                                                                         end = Offset(size.width, size.height)
                                                                     )
                                                                 )
                                                             }
                                                         }
                                                     }

                                                     Spacer(modifier = Modifier.width(16.dp))

                                                     // Video Title and Duration details
                                                     Column(
                                                         modifier = Modifier.weight(1f)
                                                     ) {
                                                         Text(
                                                             text = video.title,
                                                             style = ZuneTypography.h1.copy(fontSize = 20.sp),
                                                             color = ZuneTextPrimary,
                                                             maxLines = 1,
                                                             overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                         )
                                                         val durationText = remember(video.durationMs) {
                                                             val secs = video.durationMs / 1000
                                                             val mins = secs / 60
                                                             val remainingSecs = secs % 60
                                                             String.format("%d:%02d", mins, remainingSecs)
                                                         }
                                                         Text(
                                                             text = durationText,
                                                             style = ZuneTypography.h4.copy(fontSize = 13.sp),
                                                             color = LocalZuneAccent.current
                                                         )
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                }
                            }
                            2 -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("search videos...", color = Color.White.copy(alpha = 0.4f)) },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                Icon(
                                                    imageVector = ZuneIcons.Close,
                                                    contentDescription = "Clear",
                                                    tint = Color.White.copy(alpha = 0.6f),
                                                    modifier = Modifier.metroClickable { searchQuery = "" }
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
                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                    )
                                    
                                    val searchFiltered = remember(searchQuery, currentVideosFiltered) {
                                        if (searchQuery.isBlank()) {
                                            emptyList()
                                        } else {
                                            val q = searchQuery.lowercase().trim()
                                            currentVideosFiltered.filter {
                                                it.title.lowercase().contains(q) ||
                                                it.subtitle.lowercase().contains(q) ||
                                                it.folderName.lowercase().contains(q)
                                            }
                                        }
                                    }
                                    
                                    if (searchQuery.isBlank()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("search your videos", color = ZuneTextSecondary, style = ZuneTypography.body1)
                                        }
                                    } else if (searchFiltered.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("no videos found.", color = ZuneTextSecondary, style = ZuneTypography.body1)
                                        }
                                    } else {
                                        val columns = if (isLandscape) 5 else 3
                                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 24.dp),
                                            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            itemsIndexed(searchFiltered, key = { _, video -> video.id }) { index, video ->
                                                VideoGridCard(
                                                    video = video,
                                                    isAeroTheme = isAeroTheme,
                                                    onClick = { activePlaybackVideo = video },
                                                    onLongClick = { longPressedVideo = video }
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
        
        // Immersive Fullscreen Playback View Overlay (using ExoPlayer)
        AnimatedVisibility(
            visible = activePlaybackVideo != null,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(250)) + scaleOut(targetScale = 0.9f, animationSpec = tween(250))
        ) {
            val video = activePlayerVideo
            if (video != null) {
                androidx.compose.runtime.key(video.id) {
                    FullscreenVideoPlayer(
                        video = video,
                        videos = currentVideosFiltered,
                        onVideoChanged = { 
                            activePlaybackVideo = it
                            activePlayerVideo = it
                        },
                        pinnedIds = pinnedIds,
                        onPin = onPin,
                        onUnpin = onUnpin,
                        onDismiss = { activePlaybackVideo = null },
                        onDeleteVideo = {
                            if (video.uri != null) {
                                pendingDeleteUri = video.uri
                                deleteMediaStoreUri(
                                    context = context,
                                    uri = video.uri,
                                    onDeleteCompleted = {
                                        reloadTrigger++
                                        activePlaybackVideo = null
                                    },
                                    onLauncherNeeded = { intentSender ->
                                        try {
                                            deleteLauncher.launch(
                                                androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                )
                            } else {
                                deletedMockIds = deletedMockIds + video.id
                                activePlaybackVideo = null
                            }
                        }
                    )
                }
            }
        }
        // Long Press Drop-Up Context Menu
        if (longPressedVideo != null) {
            val video = longPressedVideo!!
            val targetId = remember(video.id) { video.id or 0x2000000000000000L }
            val isPinned = remember(targetId, pinnedIds) {
                pinnedIds.contains(targetId)
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { longPressedVideo = null }
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
                    text = video.title.lowercase(),
                    style = ZuneTypography.body2.copy(fontSize = 14.sp),
                    color = ZuneTextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                
                DropUpMenuItem(text = if (isPinned) "unpin from start" else "pin to start") {
                    longPressedVideo = null
                    if (isPinned) {
                        onUnpin(targetId)
                    } else {
                        onPin(targetId)
                    }
                    android.widget.Toast.makeText(context, if (isPinned) "unpinned" else "pinned", android.widget.Toast.LENGTH_SHORT).show()
                }
                
                DropUpMenuItem(text = "view details") {
                    longPressedVideo = null
                    videoToShowDetails = video
                }
                
                DropUpMenuItem(text = "delete") {
                    longPressedVideo = null
                    if (video.uri != null) {
                        pendingDeleteUri = video.uri
                        deleteMediaStoreUri(
                            context = context,
                            uri = video.uri,
                            onDeleteCompleted = {
                                reloadTrigger++
                            },
                            onLauncherNeeded = { intentSender ->
                                try {
                                    deleteLauncher.launch(
                                        androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        )
                    } else {
                        deletedMockIds = deletedMockIds + video.id
                    }
                }
            }
        }

        // Month Jump List Selector
        AnimatedVisibility(
            visible = showJumpSelector,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.9f, animationSpec = tween(300))
        ) {
            MonthJumpListSelector(
                months = groupedVideos.map { it.first },
                onMonthSelected = { selectedMonth ->
                    showJumpSelector = false
                    val targetIndex = flatVideoGridItems.indexOf(selectedMonth)
                    if (targetIndex != -1) {
                        coroutineScope.launch {
                            videoGridState.animateScrollToItem(targetIndex)
                        }
                    }
                },
                onDismiss = { showJumpSelector = false }
            )
        }

        // Context Details Dialog
        if (videoToShowDetails != null) {
            val detailVideo = videoToShowDetails!!
            val metadata = remember(detailVideo) { queryVideoMetadata(context, detailVideo.uri) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { videoToShowDetails = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .background(Color(0xFF1A1A1A))
                        .border(1.dp, Color.White.copy(alpha = 0.15f))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "video details",
                        style = ZuneTypography.h2.copy(
                            fontFamily = SegoeUiLightFontFamily,
                            fontSize = 24.sp,
                            color = ZuneAccent.lightenForText()
                        )
                    )
                    
                    DetailRow(label = "name", value = detailVideo.title)
                    DetailRow(label = "duration", value = formatVideoDuration(detailVideo.durationMs))
                    DetailRow(label = "resolution", value = if (metadata.width > 0) "${metadata.width} x ${metadata.height}" else "1920 x 1080 (simulated)")
                    DetailRow(label = "file size", value = metadata.size)
                    DetailRow(label = "location", value = if (detailVideo.uri != null) detailVideo.uri.path ?: "internal storage" else "Zune catalog")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .background(Color(0xFF222222))
                            .clickable { videoToShowDetails = null }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Text("close", color = Color.White, style = ZuneTypography.body2)
                    }
                }
            }
        }
    }
}

// Wide promotional banner card styled after Spotlight slides (Reference Image 3)
@Composable
fun SpotlightPromoBanner(
    title: String,
    gradient: Brush,
    desc: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .background(gradient)
            .clickable { onClick() }
            .padding(20.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Circle grids dots representing Zune SPOTLIGHT layout icon (Reference Image 3)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 40.dp)
            ) {
                // Mini 3x2 dot grid
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(3.dp).background(Color.White, CircleShape))
                        Box(modifier = Modifier.size(3.dp).background(Color.White, CircleShape))
                        Box(modifier = Modifier.size(3.dp).background(Color.White, CircleShape))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(3.dp).background(Color.White, CircleShape))
                        Box(modifier = Modifier.size(3.dp).background(Color.White, CircleShape))
                        Box(modifier = Modifier.size(3.dp).background(Color.White, CircleShape))
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title.lowercase(),
                    style = ZuneTypography.h2.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
            
            Text(
                text = desc,
                style = ZuneTypography.body1.copy(fontSize = 13.sp),
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun rememberVideoThumbnail(context: Context, videoUri: Uri?): Bitmap? {
    var bitmap by remember(videoUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(videoUri) {
        if (videoUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(videoUri, android.util.Size(256, 256), null)
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

// Video item in vertical lazy column lists
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VideoListCard(
    video: VideoItem,
    isAeroTheme: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardGlassModifier = if (isAeroTheme) {
        Modifier
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.40f),
                shape = RoundedCornerShape(5.dp)
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
                shape = RoundedCornerShape(4.dp)
            )
            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
    } else {
        Modifier.background(Color(0xFF1E1E1E))
    }

    var isError by remember { mutableStateOf(false) }
    
    // Load local frame thumbnail asynchronously
    val localThumbnail = rememberVideoThumbnail(context, video.uri)

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val prefs = remember(context) { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .metroTilt(interactionSource)
            .pointerInput(video) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        tryAwaitRelease()
                        interactionSource.emit(androidx.compose.foundation.interaction.PressInteraction.Release(press))
                    },
                    onTap = { onClick() },
                    onLongPress = {
                        if (prefs.getBoolean("haptic_feedback_enabled", true)) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onLongClick()
                    }
                )
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail card
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 70.dp)
                .then(cardGlassModifier),
            contentAlignment = Alignment.Center
        ) {
            if (localThumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = localThumbnail.asImageBitmap(),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().paperTexture()
                )
            } else {
                val thumbnailModel = video.posterUrl
                if (thumbnailModel != null && !isError) {
                    AsyncImage(
                        model = thumbnailModel,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        onError = { isError = true },
                        modifier = Modifier.fillMaxSize().paperTexture()
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = if (video.gradientColors.size >= 2) video.gradientColors else listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height)
                            )
                        )
                    }
                }
            }
            
            // Small Play arrow overlay in center
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ZuneIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = ZuneTypography.h2.copy(
                    fontFamily = SegoeUiLightFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = "${video.subtitle} • ${formatVideoDuration(video.durationMs)}",
                style = ZuneTypography.body1.copy(fontSize = 13.sp),
                color = ZuneTextSecondary
            )
        }
    }
}

// Sleek ExoPlayer Video Playback Overlay inside Jetpack Compose (Refined Windows Phone styled)
@Composable
fun FullscreenVideoPlayer(
    video: VideoItem,
    videos: List<VideoItem>,
    onVideoChanged: (VideoItem) -> Unit,
    pinnedIds: List<Long> = emptyList(),
    onPin: (Long) -> Unit = {},
    onUnpin: (Long) -> Unit = {},
    onDismiss: () -> Unit,
    onDeleteVideo: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(video.durationMs) }
    
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentPos) {
        if (!isDraggingSlider) {
            sliderValue = currentPos.toFloat()
        }
    }

    // Pause music player when entering video playback
    LaunchedEffect(Unit) {
        try {
            com.zune.player.player.AudioPlayer.getInstance(context).pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Check if loading online URL is successful. If not, fallback seamlessly to simulator.
    var useSimulator by remember { mutableStateOf(video.uri == null) }
       // Power player states
    var isMuted by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    // Controls Visibility state & auto-hide
    var isHUDVisible by remember { mutableStateOf(true) }
    var brightness by remember { mutableStateOf(1f) }
    var volume by remember { mutableStateOf(0.8f) }
    var showSeekOverlay by remember { mutableStateOf<String?>(null) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    
    val resizeModes = listOf(
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM, // Crop/Zoom
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL  // Stretch
    )
    val resizeLabels = listOf("fit", "crop", "stretch")
    var resizeIndex by remember { mutableStateOf(0) }
    
    val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
    var speedIndex by remember { mutableStateOf(1) } // default 1.0f

    val exoPlayer = remember {
        try {
            ExoPlayer.Builder(context).build().apply {
                playWhenReady = true
            }
        } catch (e: Exception) {
            null
        }
    }

    // Optimize: Reuse ExoPlayer and update media item when video changes
    LaunchedEffect(video, exoPlayer) {
        useSimulator = video.uri == null
        currentPos = 0L
        duration = video.durationMs
        isPlaying = true
        scale = 1f
        offset = Offset.Zero
        
        exoPlayer?.let { player ->
            try {
                player.stop()
                player.clearMediaItems()
                if (!useSimulator) {
                    val mediaUri = video.uri ?: Uri.parse(video.videoUrl)
                    player.setMediaItem(MediaItem.fromUri(mediaUri))
                    player.prepare()
                    player.playWhenReady = true
                    player.volume = if (isMuted) 0f else 1f
                    player.setPlaybackSpeed(speeds[speedIndex])
                    
                    // Add listener to update duration
                    player.addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == androidx.media3.common.Player.STATE_READY) {
                                duration = player.duration
                            }
                        }
                    })
                }
            } catch (e: Exception) {
                useSimulator = true
            }
        }
    }
    
    // Dynamically apply mute and playback speed states when modified
    LaunchedEffect(isMuted, volume, exoPlayer) {
        exoPlayer?.volume = if (isMuted) 0f else volume
    }
    LaunchedEffect(speedIndex, exoPlayer) {
        exoPlayer?.setPlaybackSpeed(speeds[speedIndex])
    }
    
    // Report play positioning
    LaunchedEffect(isPlaying, useSimulator, video) {
        while (isPlaying) {
            if (useSimulator) {
                currentPos += 1000
                if (currentPos >= duration) {
                    currentPos = 0
                }
            } else {
                exoPlayer?.let {
                    currentPos = it.currentPosition
                }
            }
            delay(1000)
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
        }
    }
    


    LaunchedEffect(showSeekOverlay) {
        if (showSeekOverlay != null) {
            delay(1000)
            showSeekOverlay = null
        }
    }
    LaunchedEffect(volume) {
        showVolumeIndicator = true
        delay(1500)
        showVolumeIndicator = false
    }
    LaunchedEffect(brightness) {
        showBrightnessIndicator = true
        delay(1500)
        showBrightnessIndicator = false
    }
    
    LaunchedEffect(isHUDVisible, isPlaying) {
        if (isHUDVisible && isPlaying) {
            delay(4000)
            isHUDVisible = false
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        var isPinch = false
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val numPointers = event.changes.size
                            if (numPointers >= 2) {
                                isPinch = true
                            }
                            if (isPinch) {
                                val zoomFactor = event.calculateZoom()
                                if (zoomFactor != 1f) {
                                    scale = (scale * zoomFactor).coerceIn(1f, 4f)
                                }
                                val pan = event.calculatePan()
                                if (scale > 1f && pan != Offset.Zero) {
                                    val panXLimit = ((scale - 1f) * size.width) / 2f
                                    val panYLimit = ((scale - 1f) * size.height) / 2f
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-panXLimit, panXLimit),
                                        y = (offset.y + pan.y).coerceIn(-panYLimit, panYLimit)
                                    )
                                }
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = event.changes.firstOrNull()
                                if (change != null && scale > 1f) {
                                    val dragAmount = change.position - change.previousPosition
                                    val panXLimit = ((scale - 1f) * size.width) / 2f
                                    val panYLimit = ((scale - 1f) * size.height) / 2f
                                    offset = Offset(
                                        x = (offset.x + dragAmount.x).coerceIn(-panXLimit, panXLimit),
                                        y = (offset.y + dragAmount.y).coerceIn(-panYLimit, panYLimit)
                                    )
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .then(
                    if (scale == 1f) {
                        Modifier.pointerInput(video) {
                            var totalDragX = 0f
                            detectDragGestures(
                                onDragStart = { totalDragX = 0f },
                                onDragEnd = {
                                    if (totalDragX > 150f) {
                                        // Swipe Right -> Prev
                                        val currentIndex = videos.indexOf(video)
                                        if (currentIndex > 0) {
                                            onVideoChanged(videos[currentIndex - 1])
                                        }
                                    } else if (totalDragX < -150f) {
                                        // Swipe Left -> Next
                                        val currentIndex = videos.indexOf(video)
                                        if (currentIndex < videos.size - 1) {
                                            onVideoChanged(videos[currentIndex + 1])
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragX += dragAmount.x
                                }
                            )
                        }
                    } else Modifier
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val width = size.width
                            if (tapOffset.x < width * 0.35f) {
                                exoPlayer?.let { player ->
                                    val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
                                    player.seekTo(newPos)
                                    currentPos = newPos
                                }
                                showSeekOverlay = "rewind"
                            } else if (tapOffset.x > width * 0.65f) {
                                exoPlayer?.let { player ->
                                    val newPos = (player.currentPosition + 10000).coerceAtMost(player.duration)
                                    player.seekTo(newPos)
                                    currentPos = newPos
                                }
                                showSeekOverlay = "fastforward"
                            } else {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                    val x = (width / 2f - tapOffset.x) * 1.5f
                                    val y = (size.height / 2f - tapOffset.y) * 1.5f
                                    offset = Offset(x, y)
                                }
                            }
                        },
                        onTap = { isHUDVisible = !isHUDVisible }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (!useSimulator && exoPlayer != null) {
                // Real media player View
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false // Custom WP overlay
                            resizeMode = resizeModes[resizeIndex]
                        }
                    },
                    update = { view ->
                        view.resizeMode = resizeModes[resizeIndex]
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Stunning Visual Playback Simulator
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "SimulatorAnim")
                    val shift by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 100f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(8000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "gradientShift"
                    )
                    
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = if (video.gradientColors.size >= 2) video.gradientColors else listOf(Color(0xFF333333), Color(0xFF111111)),
                                center = Offset(size.width / 2f + shift, size.height / 2f),
                                radius = size.width * 0.8f
                            )
                        )
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = ZuneIcons.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "PLAYING: ${video.title.uppercase()}",
                            style = ZuneTypography.h2.copy(
                                fontFamily = SegoeUiLightFontFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "failsafe stream simulator",
                            style = ZuneTypography.caption,
                            color = ZuneTextSecondary
                        )
                    }
                }
            }
        }
        
        // CUSTOM YOUTUBE STYLE HUD (Redesigned to authentic Zune Player layout)
        // Top Bar
        AnimatedVisibility(
            visible = isHUDVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-Left Back Button (Circle with ArrowBack)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .metroClickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ZuneIcons.ArrowBack,
                        contentDescription = "go back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Top-Right Aspect Ratio Resize Button (Circle with DragHandle/ReOrder D-pad icon)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .metroClickable {
                            resizeIndex = (resizeIndex + 1) % resizeModes.size
                            android.widget.Toast.makeText(context, "display: ${resizeLabels[resizeIndex]}", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ZuneIcons.DragHandle,
                        contentDescription = "aspect ratio mode",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Center HUD Controls (Prev, Play/Pause, Next, Volume Up/Down in D-pad Layout)
        AnimatedVisibility(
            visible = isHUDVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                // 1. Play/Pause Button (CENTER)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .metroClickable {
                            isPlaying = !isPlaying
                            if (isPlaying) {
                                try {
                                    com.zune.player.player.AudioPlayer.getInstance(context).pause()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            if (!useSimulator && exoPlayer != null) {
                                if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) ZuneIcons.Pause else ZuneIcons.Play,
                        contentDescription = "play/pause",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // 2. Volume Up (+) (TOP CENTER)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(44.dp)
                        .metroClickable {
                            audioManager.adjustStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                android.media.AudioManager.ADJUST_RAISE,
                                android.media.AudioManager.FLAG_SHOW_UI
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SegoeUiFontFamily
                        )
                    )
                }

                // 3. Volume Down (-) (BOTTOM CENTER)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .size(44.dp)
                        .metroClickable {
                            audioManager.adjustStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                android.media.AudioManager.ADJUST_LOWER,
                                android.media.AudioManager.FLAG_SHOW_UI
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "—",
                        color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SegoeUiFontFamily
                        )
                    )
                }

                // 4. Skip Previous Video (LEFT CENTER)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .size(48.dp)
                        .metroClickable {
                            val currentIndex = videos.indexOf(video)
                            if (currentIndex > 0) {
                                onVideoChanged(videos[currentIndex - 1])
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ZuneIcons.SkipPrevious,
                        contentDescription = "previous video",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // 5. Skip Next Video (RIGHT CENTER)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(48.dp)
                        .metroClickable {
                            val currentIndex = videos.indexOf(video)
                            if (currentIndex < videos.size - 1) {
                                onVideoChanged(videos[currentIndex + 1])
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ZuneIcons.SkipNext,
                        contentDescription = "next video",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Bottom Playback Panel (Redesigned Zune Style)
        AnimatedVisibility(
            visible = isHUDVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title Row on the first line (bold, uppercase, Segoe UI)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.title.uppercase(),
                        style = ZuneTypography.h2.copy(
                            fontFamily = SegoeUiBoldFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = ZuneIcons.MoreHoriz,
                        contentDescription = "more options",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(28.dp)
                            .metroClickable { showMenu = true }
                    )
                }

                // Interactive timeline slider with duration inline on the second line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${formatVideoDuration(currentPos)}/${formatVideoDuration(duration)}",
                        style = ZuneTypography.caption.copy(fontFamily = SegoeUiFontFamily, fontSize = 12.sp),
                        color = Color.White
                    )

                    Slider(
                        value = sliderValue,
                        onValueChange = { seekValue ->
                            isDraggingSlider = true
                            sliderValue = seekValue
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            currentPos = sliderValue.toLong()
                            if (!useSimulator && exoPlayer != null) {
                                exoPlayer.seekTo(sliderValue.toLong())
                            }
                        },
                        valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f).height(4.dp)
                    )
                }
            }
        }
        
        // Slide-up Drop-up Menu for Advanced Options with fade scrim and slide-up animation
        val targetId = remember(video.id) { video.id or 0x2000000000000000L }
        val isPinned = remember(targetId, pinnedIds) { pinnedIds.contains(targetId) }
        
        AnimatedVisibility(
            visible = showMenu,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showMenu = false }
            )
        }
        
        AnimatedVisibility(
            visible = showMenu,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            ) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
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
                    text = video.title.lowercase(),
                    style = ZuneTypography.body2.copy(fontSize = 14.sp),
                    color = ZuneTextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                
                DropUpMenuItem(text = if (isPinned) "unpin from start" else "pin to start") {
                    showMenu = false
                    if (isPinned) {
                        onUnpin(targetId)
                    } else {
                        onPin(targetId)
                    }
                    android.widget.Toast.makeText(context, if (isPinned) "unpinned" else "pinned", android.widget.Toast.LENGTH_SHORT).show()
                }
                
                DropUpMenuItem(text = if (isMuted) "unmute" else "mute") {
                    isMuted = !isMuted
                    showMenu = false
                }
                
                DropUpMenuItem(text = "aspect: ${resizeLabels[resizeIndex]}") {
                    resizeIndex = (resizeIndex + 1) % resizeModes.size
                    showMenu = false
                }
                
                DropUpMenuItem(text = "speed: ${speeds[speedIndex]}x") {
                    speedIndex = (speedIndex + 1) % speeds.size
                    showMenu = false
                }
                
                DropUpMenuItem(text = "share video") {
                    showMenu = false
                    video.uri?.let { uri ->
                        try {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "video/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "share video"))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "failed to share.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        android.widget.Toast.makeText(context, "cannot share catalog video.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                
                DropUpMenuItem(text = "view details") {
                    showMenu = false
                    showDetails = true
                }
                
                DropUpMenuItem(text = "delete video") {
                    showMenu = false
                    onDeleteVideo()
                }
            }
        }
        
        // Video Details Dialog
        if (showDetails) {
            val metadata = remember(video) { queryVideoMetadata(context, video.uri) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { showDetails = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .background(Color(0xFF1A1A1A))
                        .border(1.dp, Color.White.copy(alpha = 0.15f))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "video details",
                        style = ZuneTypography.h2.copy(
                            fontFamily = SegoeUiLightFontFamily,
                            fontSize = 24.sp,
                            color = ZuneAccent.lightenForText()
                        )
                    )
                    
                    DetailRow(label = "name", value = video.title)
                    DetailRow(label = "duration", value = formatVideoDuration(video.durationMs))
                    DetailRow(label = "resolution", value = if (metadata.width > 0) "${metadata.width} x ${metadata.height}" else "1920 x 1080 (simulated)")
                    DetailRow(label = "file size", value = metadata.size)
                    DetailRow(label = "location", value = if (video.uri != null) video.uri.path ?: "internal storage" else "Zune catalog")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val targetId = remember(video.id) { video.id or 0x2000000000000000L }
                        val isPinned = remember(targetId, pinnedIds) {
                            pinnedIds.contains(targetId)
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF222222))
                                .clickable {
                                    if (isPinned) {
                                        onUnpin(targetId)
                                    } else {
                                        onPin(targetId)
                                    }
                                    showDetails = false
                                }
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        ) {
                            Text(text = if (isPinned) "unpin from start" else "pin to start", color = Color.White, style = ZuneTypography.body2)
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF222222))
                                .clickable { showDetails = false }
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        ) {
                            Text("close", color = Color.White, style = ZuneTypography.body2)
                        }
                    }
                }
            }
        }

        // 1. Brightness filter overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 0.9f)))
        )

        // 4. Seek overlay (Center)
        AnimatedVisibility(
            visible = showSeekOverlay != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .padding(24.dp)
            ) {
                Text(
                    text = if (showSeekOverlay == "rewind") "rewind -10s" else "forward +10s",
                    color = Color.White,
                    style = ZuneTypography.body1
                )
            }
        }
    }
}


// 5. Videos Hub Storage Permission Prompt (Refined Windows Phone style)
@Composable
fun VideosPermissionPrompt(
    onAllow: () -> Unit,
    onUseSamples: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header VIDEOS
            Text(
                text = "VIDEOS",
                style = ZuneTypography.h4.copy(
                    fontFamily = SegoeUiLightFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZuneTextSecondary
                )
            )
            
            Text(
                text = "storage access",
                style = ZuneTypography.h1.copy(
                    fontFamily = SegoeUiLightFontFamily,
                    fontSize = 52.sp,
                    color = Color.White
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "zune needs access to your phone storage to find and play videos on your device.",
                style = ZuneTypography.body1.copy(
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            )
            
            Text(
                text = "you can grant permission now, or bypass to explore Zune with high-quality streaming mock videos.",
                style = ZuneTypography.body2.copy(
                    fontSize = 14.sp,
                    color = ZuneTextSecondary
                )
            )
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        ) {
            // "allow access" Tile Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZuneAccent)
                    .clickable { onAllow() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "allow access",
                    style = ZuneTypography.h2.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
            
            // "use sample videos" Tile Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF222222))
                    .border(1.dp, Color.White.copy(alpha = 0.15f))
                    .clickable { onUseSamples() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "view sample videos",
                    style = ZuneTypography.h2.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // "go back" Text Link
            Text(
                text = "go back",
                style = ZuneTypography.body1.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ZuneTextSecondary
                ),
                modifier = Modifier
                    .clickable { onBack() }
                    .align(Alignment.CenterHorizontally)
            )
        }
    }
}

// 6. Mock video catalog (stunning visual feeds with streaming failsafes)
fun generateMockVideos(): List<VideoItem> {
    val now = System.currentTimeMillis() / 1000L
    return listOf(
        VideoItem(
            id = 2000000000L,
            uri = null,
            title = "xbox live achievements spotlight",
            subtitle = "featured cinematic trailer",
            durationMs = 93000L,
            dateAdded = now,
            gradientColors = listOf(Color(0xFF107C10), Color(0xFF003F00)),
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        ),
        VideoItem(
            id = 2000000001L,
            uri = null,
            title = "windows phone launch event",
            subtitle = "keynote highlight reel",
            durationMs = 124000L,
            dateAdded = now - 86400L * 2,
            gradientColors = listOf(Color(0xFF0078D4), Color(0xFF002040)),
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        ),
        VideoItem(
            id = 2000000002L,
            uri = null,
            title = "zune hd metro interface review",
            subtitle = "retro technology review",
            durationMs = 186000L,
            dateAdded = now - 86400L * 10,
            gradientColors = listOf(Color(0xFFE0115F), Color(0xFF6B0029)),
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        )
    )
}

// Helpers
private fun formatVideoDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    
    return if (hours > 0) {
        "$hours:${String.format("%02d", minutes)}:${String.format("%02d", seconds)}"
    } else {
        "$minutes:${String.format("%02d", seconds)}"
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(text = label, style = ZuneTypography.caption, color = ZuneTextSecondary)
        Text(text = value, style = ZuneTypography.body1, color = Color.White)
    }
}



data class VideoFileMetadata(val size: String, val width: Int, val height: Int)

private fun queryVideoMetadata(context: Context, uri: Uri?): VideoFileMetadata {
    if (uri == null) return VideoFileMetadata(size = "12.4 MB", width = 1920, height = 1080)
    try {
        val projection = arrayOf(
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val widthIndex = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightIndex = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                
                val rawBytes = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                val width = if (widthIndex != -1) cursor.getInt(widthIndex) else 0
                val height = if (heightIndex != -1) cursor.getInt(heightIndex) else 0
                
                val sizeFormatted = when {
                    rawBytes <= 0 -> "unknown"
                    rawBytes < 1024 -> "$rawBytes B"
                    rawBytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", rawBytes / 1024f)
                    else -> String.format(Locale.US, "%.1f MB", rawBytes / (1024f * 1024f))
                }
                return VideoFileMetadata(size = sizeFormatted, width = width, height = height)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return VideoFileMetadata(size = "unknown", width = 0, height = 0)
}

private fun deleteMediaStoreUri(
    context: Context,
    uri: Uri,
    onDeleteCompleted: () -> Unit,
    onLauncherNeeded: (android.content.IntentSender) -> Unit
) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            onLauncherNeeded(pendingIntent.intentSender)
        } else {
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            if (rowsDeleted > 0) {
                onDeleteCompleted()
            }
        }
    } catch (securityException: SecurityException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val recoverableSecurityException = securityException as? android.app.RecoverableSecurityException
            if (recoverableSecurityException != null) {
                onLauncherNeeded(recoverableSecurityException.userAction.actionIntent.intentSender)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
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

@Composable
fun VideoGridCard(
    video: VideoItem,
    isAeroTheme: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val localThumbnail = rememberVideoThumbnail(context, video.uri)
    val cardGlassModifier = if (isAeroTheme) {
        Modifier
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.40f),
                shape = RoundedCornerShape(3.dp)
            )
            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp))
    } else {
        Modifier.background(Color(0xFF1E1E1E))
    }

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val prefs = remember(context) { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .then(cardGlassModifier)
            .metroTilt(interactionSource)
            .pointerInput(video) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        tryAwaitRelease()
                        interactionSource.emit(androidx.compose.foundation.interaction.PressInteraction.Release(press))
                    },
                    onTap = { onClick() },
                    onLongPress = {
                        if (prefs.getBoolean("haptic_feedback_enabled", true)) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onLongClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (localThumbnail != null) {
            androidx.compose.foundation.Image(
                bitmap = localThumbnail.asImageBitmap(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().paperTexture()
            )
        } else if (video.posterUrl != null) {
            AsyncImage(
                model = video.posterUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().paperTexture()
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = if (video.gradientColors.size >= 2) video.gradientColors else listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
            }
        }
        // Small Play arrow overlay in center
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ZuneIcons.Play,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun VideoFolderCard(
    folderName: String,
    videos: List<VideoItem>,
    isAeroTheme: Boolean,
    onClick: () -> Unit
) {
    val firstVideo = videos.firstOrNull()
    val context = LocalContext.current
    val localThumbnail = rememberVideoThumbnail(context, firstVideo?.uri)
    
    val cardGlassModifier = if (isAeroTheme) {
        Modifier
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.40f),
                shape = RoundedCornerShape(3.dp)
            )
            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp))
    } else {
        Modifier.background(Color(0xFF1E1E1E))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .metroClickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .then(cardGlassModifier),
            contentAlignment = Alignment.BottomStart
        ) {
            if (localThumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = localThumbnail.asImageBitmap(),
                    contentDescription = folderName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (firstVideo?.posterUrl != null) {
                AsyncImage(
                    model = firstVideo.posterUrl,
                    contentDescription = folderName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = if ((firstVideo?.gradientColors?.size ?: 0) >= 2) firstVideo!!.gradientColors else listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    )
                }
            }

            // Dark gradient backing for readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .fillMaxHeight(0.55f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            // Labels inside the preview
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = folderName.uppercase(),
                    style = ZuneTypography.body1.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "${videos.size} VIDEOS",
                    style = ZuneTypography.caption.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            // Folder icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ZuneIcons.Folder,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

private suspend fun PointerInputScope.detectPinchZoomGesture(
    onGesture: (zoom: Float) -> Unit
) {
    awaitEachGesture {
        var zoom = 1f
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                if (event.changes.size > 1) {
                    val zoomChange = event.calculateZoom()
                    if (!pastTouchSlop) {
                        zoom *= zoomChange
                        val zoomMotion = Math.abs(1 - zoom)
                        if (zoomMotion > touchSlop * 0.01f) {
                            pastTouchSlop = true
                        }
                    }
                    if (pastTouchSlop) {
                        if (zoomChange != 1f) {
                            onGesture(zoomChange)
                        }
                        event.changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}
