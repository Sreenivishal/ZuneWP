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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.zune.player.ui.theme.*
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
    val videoUrl: String = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
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
                        MediaStore.Video.Media.DATE_ADDED
                    )
                    resolver.query(uri, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                        val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                        
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn) ?: "video_$id"
                            val duration = cursor.getLong(durationColumn)
                            val dateAdded = cursor.getLong(dateAddedColumn)
                            val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            
                            loadedList.add(
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
    
    // Fullscreen player target
    var activePlaybackVideo by remember { mutableStateOf<VideoItem?>(null) }
    
    // Deep-linked navigation handler
    LaunchedEffect(videos, initialVideoId) {
        if (initialVideoId != null && videos.isNotEmpty()) {
            val video = videos.find { it.id == initialVideoId }
            if (video != null) {
                activePlaybackVideo = video
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!hasPermission && !useSamplesFallback) {
            VideosPermissionPrompt(
                onAllow = { permissionLauncher.launch(permissionString) },
                onUseSamples = { useSamplesFallback = true },
                onBack = onBack
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.zune.player.R.drawable.zune_back),
                    contentDescription = "Back",
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .offset(x = (-20).dp, y = (-8).dp)
                        .size(80.dp)
                        .metroClickable { onBack() }
                )
                // Small Category / Section Header
                Text(
                    text = if (isAeroTheme) "Video Library" else "VIDEOS",
                    style = ZuneTypography.h4.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ZuneTextSecondary,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 4.dp)
                )
                
                // Large ALL header
                Text(
                    text = "all",
                    style = ZuneTypography.h1.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 56.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("loading videos...", color = ZuneTextSecondary, style = ZuneTypography.body1)
                    }
                } else if (currentVideosFiltered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("no videos found.", color = ZuneTextSecondary, style = ZuneTypography.body1)
                    }
                } else {
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    if (isLandscape) {
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(currentVideosFiltered) { index, video ->
                                VideoListCard(
                                    video = video,
                                    isAeroTheme = isAeroTheme,
                                    onClick = { activePlaybackVideo = video },
                                    onLongClick = { longPressedVideo = video }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(currentVideosFiltered) { index, video ->
                                VideoListCard(
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
        
        // Immersive Fullscreen Playback View Overlay (using ExoPlayer)
        activePlaybackVideo?.let { video ->
            FullscreenVideoPlayer(
                video = video,
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
    onLongClick: () -> Unit = {}
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val thumbnailModel = video.posterUrl
                if (thumbnailModel != null && !isError) {
                    AsyncImage(
                        model = thumbnailModel,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        onError = { isError = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = video.gradientColors.ifEmpty { listOf(Color(0xFFEE0979), Color(0xFFFF6A00)) },
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
                    imageVector = Icons.Default.PlayArrow,
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
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Check if loading online URL is successful. If not, fallback seamlessly to simulator.
    var useSimulator by remember { mutableStateOf(video.uri == null) }
    
    // Power player states
    var isMuted by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    
    val resizeModes = listOf(
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM, // Crop/Zoom
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL  // Stretch
    )
    val resizeLabels = listOf("fit", "crop", "stretch")
    var resizeIndex by remember { mutableIntStateOf(0) }
    
    val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
    var speedIndex by remember { mutableIntStateOf(1) } // default 1.0f

    val exoPlayer = remember(video) {
        if (!useSimulator) {
            try {
                ExoPlayer.Builder(context).build().apply {
                    val mediaUri = video.uri ?: Uri.parse(video.videoUrl)
                    setMediaItem(MediaItem.fromUri(mediaUri))
                    prepare()
                    playWhenReady = true
                    volume = if (isMuted) 0f else 1f
                    setPlaybackSpeed(speeds[speedIndex])
                    
                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == androidx.media3.common.Player.STATE_READY) {
                                duration = this@apply.duration
                            }
                        }
                    })
                }
            } catch (e: Exception) {
                useSimulator = true
                null
            }
        } else null
    }
    
    // Dynamically apply mute and playback speed states when modified
    LaunchedEffect(isMuted, exoPlayer) {
        exoPlayer?.volume = if (isMuted) 0f else 1f
    }
    LaunchedEffect(speedIndex, exoPlayer) {
        exoPlayer?.setPlaybackSpeed(speeds[speedIndex])
    }
    
    // Report play positioning
    LaunchedEffect(isPlaying, useSimulator) {
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
    
    // Controls Visibility state & auto-hide
    var isHUDVisible by remember { mutableStateOf(true) }
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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                                val x = (size.width / 2f - tapOffset.x) * 1.5f
                                val y = (size.height / 2f - tapOffset.y) * 1.5f
                                offset = Offset(x, y)
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
                                colors = video.gradientColors.ifEmpty { listOf(Color(0xFF333333), Color(0xFF111111)) },
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
                            imageVector = Icons.Default.Movie,
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
        
        // CUSTOM WINDOWS PHONE METRO CONTROLS HUD
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .border(1.2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                
                Text(
                    text = video.title.uppercase(),
                    style = ZuneTypography.h2.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp).weight(1f)
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Circular Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable {
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
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "share", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    
                    // Delete Circular Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable { onDeleteVideo() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "delete", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        
        // Bottom Playback Panel
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Interactive timeline slider
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = currentPos.toFloat(),
                        onValueChange = { seekValue ->
                            currentPos = seekValue.toLong()
                            if (!useSimulator && exoPlayer != null) {
                                exoPlayer.seekTo(seekValue.toLong())
                            }
                        },
                        valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                        colors = SliderDefaults.colors(
                            thumbColor = ZuneAccent,
                            activeTrackColor = ZuneAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().height(16.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatVideoDuration(currentPos),
                            style = ZuneTypography.caption.copy(fontFamily = SegoeUiLightFontFamily),
                            color = ZuneTextSecondary
                        )
                        Text(
                            text = formatVideoDuration(duration),
                            style = ZuneTypography.caption.copy(fontFamily = SegoeUiLightFontFamily),
                            color = ZuneTextSecondary
                        )
                    }
                }
                
                // Advanced power controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute Circular Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable { isMuted = !isMuted },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = "mute",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // Resize Mode Label Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable { resizeIndex = (resizeIndex + 1) % resizeModes.size },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = resizeLabels[resizeIndex],
                            style = ZuneTypography.caption.copy(fontFamily = SegoeUiLightFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = Color.White
                        )
                    }
                    
                    // Speed Selector Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable { speedIndex = (speedIndex + 1) % speeds.size },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${speeds[speedIndex]}x",
                            style = ZuneTypography.caption.copy(fontFamily = SegoeUiLightFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = Color.White
                        )
                    }

                    // Details Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable { showDetails = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "details",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                // Transport controls row (Circular WP Design)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip Back 10s Circular Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                            .clickable {
                                val seekTo = (currentPos - 10000).coerceAtLeast(0)
                                currentPos = seekTo
                                if (!useSimulator && exoPlayer != null) {
                                    exoPlayer.seekTo(seekTo)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "skip back 10s",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(32.dp))
                    
                    // Main Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .border(1.8.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                            .clickable {
                                isPlaying = !isPlaying
                                if (!useSimulator && exoPlayer != null) {
                                    if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "play/pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(32.dp))
                    
                    // Skip Forward 30s Circular Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                            .clickable {
                                val seekTo = (currentPos + 30000).coerceAtMost(duration)
                                currentPos = seekTo
                                if (!useSimulator && exoPlayer != null) {
                                    exoPlayer.seekTo(seekTo)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward,
                            contentDescription = "skip forward 30s",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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

// Lighten helper for blacks
private fun Color.lightenForText(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = hsl[2].coerceAtLeast(0.6f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
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
