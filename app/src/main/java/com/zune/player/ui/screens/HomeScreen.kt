package com.zune.player.ui.screens

import androidx.compose.ui.graphics.RectangleShape
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.blur
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import com.zune.player.LocalSharedTransitionScope
import com.zune.player.LocalAnimatedVisibilityScope
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
import com.zune.player.ui.theme.SegoeUiFontFamily
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> },
    onPageSelected: (Int) -> Unit = {}
) {
    val pages = listOf(0, 1)

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedBg by remember { mutableStateOf(prefs.getInt("bg_selection", 0)) }
    var showFeaturedSection by remember { mutableStateOf(prefs.getBoolean("show_featured_section", true)) }
    var activePinnedSongForOptions by remember { mutableStateOf<AudioItem?>(null) }

    var historyEnabled by remember { mutableStateOf(prefs.getBoolean("history_enabled", true)) }
    var historyList by remember { mutableStateOf(emptyList<AudioItem>()) }

    fun refreshHistory() {
        historyEnabled = prefs.getBoolean("history_enabled", true)
        historyList = if (historyEnabled) com.zune.player.getPlaybackHistory(prefs) else emptyList()
    }

    val currentAudioFlowItem by player.currentAudio.collectAsState()
    LaunchedEffect(currentAudioFlowItem) {
        refreshHistory()
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "show_featured_section") {
                showFeaturedSection = prefs.getBoolean("show_featured_section", true)
            } else if (key == "history_enabled") {
                refreshHistory()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (!AppsCache.isInitialized) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    }
                    val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                    val apps = resolveInfos.mapNotNull { ri ->
                        val pkgName = ri.activityInfo.packageName
                        if (pkgName == context.packageName) return@mapNotNull null
                        val appLabel = ri.loadLabel(pm).toString()
                        val appId = (pkgName.hashCode().toLong() and 0x0FFFFFFFFFFFFFFFL) or 0x3000000000000000L
                        AppItem(pkgName, appLabel, appId)
                    }.sortedBy { it.appName.lowercase() }
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        AppsCache.cachedApps = apps
                        AppsCache.isInitialized = true
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasNotifPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasNotifPermission) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        val isListenerEnabled = try {
            val pkgName = context.packageName
            val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            flat?.split(":")?.any {
                val cn = android.content.ComponentName.unflattenFromString(it)
                cn != null && cn.packageName == pkgName
            } == true
        } catch (e: Exception) {
            false
        }
        
        if (!isListenerEnabled) {
            try {
                val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                android.widget.Toast.makeText(context, "please enable zune live tiles listener in settings", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
            val prefix = rawId ushr 60
            when (prefix) {
                1L -> {
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
                2L -> {
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
                3L -> {
                    val packageManager = context.packageManager
                    val pmIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    }
                    val resolveInfos = try {
                        packageManager.queryIntentActivities(pmIntent, 0)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val appInfo = resolveInfos.find { ri ->
                        val pkgName = ri.activityInfo.packageName
                        val appHash = pkgName.hashCode().toLong() and 0x0FFFFFFFFFFFFFFFL
                        val appId = appHash or 0x3000000000000000L
                        appId == rawId
                    }
                    if (appInfo != null) {
                        val appLabel = appInfo.loadLabel(packageManager).toString()
                        val rawIcon = appInfo.loadIcon(packageManager)
                        val appIcon = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && rawIcon is android.graphics.drawable.AdaptiveIconDrawable) {
                            try {
                                val size = 512
                                val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                rawIcon.background.bounds = android.graphics.Rect(0, 0, size, size)
                                rawIcon.background.draw(canvas)
                                rawIcon.foreground.bounds = android.graphics.Rect(0, 0, size, size)
                                rawIcon.foreground.draw(canvas)
                                bitmap
                            } catch (e: Exception) {
                                rawIcon
                            }
                        } else {
                            rawIcon
                        }
                        val pkgName = appInfo.activityInfo.packageName
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "app",
                                title = appLabel,
                                subtitle = pkgName,
                                imageUri = appIcon,
                                gradientColors = emptyList(),
                                size = size
                            ),
                            size
                        )
                    } else {
                        Pair(
                            PinnedTileItem(
                                id = rawId,
                                type = "app",
                                title = "app",
                                subtitle = "unknown",
                                imageUri = null,
                                gradientColors = listOf(Color(0xFF00B4DB), Color(0xFF0083B0)),
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
            title = null,
            pages = pages,
            initialPage = initialPage,
            isBlackBackground = selectedBg == 0,
            isAeroTheme = isAeroTheme,
            onOffsetChanged = onScroll,
            onPageSelected = onPageSelected
        ) { page ->
            when (page) {
                0 -> {
                    val quickplayScrollState = rememberScrollState(
                        initial = getScrollPosition("home_quickplay").first
                    )
                    DisposableEffect(quickplayScrollState) {
                        onDispose {
                            onScrollPositionChanged("home_quickplay", quickplayScrollState.value, 0)
                        }
                    }
                    val currentPlaying by player.currentAudio.collectAsState()

                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.72f)
                            .verticalScroll(quickplayScrollState)
                            .padding(bottom = 48.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Now Playing sub-section
                        Text(
                            text = "Now Playing",
                            style = ZuneTypography.h4.copy(
                                fontSize = 30.sp,
                                fontFamily = SegoeUiLightFontFamily,
                                fontWeight = FontWeight.Light
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 2.dp)
                        )
                        NowPlayingPanel(
                            player = player,
                            onNavigateToNowPlaying = onNavigateToNowPlaying,
                            onOpenQueue = onOpenQueue,
                            isAeroTheme = isAeroTheme
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Pins sub-section
                        Text(
                            text = "Pins",
                            style = ZuneTypography.h4.copy(
                                fontSize = 50.sp,
                                fontFamily = SegoeUiLightFontFamily,
                                fontWeight = FontWeight.Light
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(start = 24.dp, end = 0.dp, top = 8.dp, bottom = 0.dp)
                        )
                        PinnedPage(
                            pinnedItems = resolvedPinnedTiles,
                            currentPlayingId = currentPlaying?.id,
                            onPlay = { tile ->
                                when (tile.type) {
                                    "song" -> {
                                        val song = audioItems.find { it.id == tile.id }
                                        if (song != null) {
                                            activePinnedSongForOptions = song
                                        }
                                    }
                                    "photo" -> {
                                        onNavigateToPhotos(tile.id xor 0x1000000000000000L)
                                    }
                                    "video" -> {
                                        onNavigateToVideos(tile.id xor 0x2000000000000000L)
                                    }
                                    "app" -> {
                                        try {
                                            val launchIntent = context.packageManager.getLaunchIntentForPackage(tile.subtitle)
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            } else {
                                                android.widget.Toast.makeText(context, "Could not open app", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "App not found", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onUnpin = onUnpin,
                            onCycleSize = onCycleSize,
                            onMove = onMove,
                            isAeroTheme = isAeroTheme,
                            getScrollPosition = getScrollPosition,
                            onScrollPositionChanged = onScrollPositionChanged,
                            isNested = true
                        )

                        Spacer(modifier = Modifier.height(4.dp))


                        // Featured sub-section
                        if (showFeaturedSection) {
                            val featuredTitleStyle = if (isAeroTheme) {
                                ZuneTypography.h2.copy(
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 0.sp,
                                    brush = AeroBlueOrbGradient
                                )
                            } else {
                                ZuneTypography.h4.copy(
                                    fontSize = 50.sp,
                                    fontFamily = SegoeUiLightFontFamily,
                                    fontWeight = FontWeight.Light
                                )
                            }
                            val featuredTitleColor = if (isAeroTheme) Color.Unspecified else Color.White
                            val featuredTitlePadding = if (isAeroTheme) {
                                Modifier.padding(start = 24.dp, bottom = 16.dp, top = 24.dp)
                            } else {
                                Modifier.padding(start = 24.dp, end = 0.dp, top = 8.dp, bottom = 0.dp)
                            }
                            Text(
                                text = if (isAeroTheme) "Featured" else "Featured",
                                style = featuredTitleStyle,
                                color = featuredTitleColor,
                                modifier = featuredTitlePadding
                            )
                            FeaturedSectionView(
                                audioItems = audioItems,
                                onPlayAlbum = onPlayAlbum,
                                isAeroTheme = isAeroTheme
                            )
                        }

                        if (historyEnabled) {
                            // History sub-section
                            val historyTitleStyle = if (isAeroTheme) {
                                ZuneTypography.h2.copy(
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 0.sp,
                                    brush = AeroBlueOrbGradient
                                )
                            } else {
                                ZuneTypography.h4.copy(
                                    fontSize = 50.sp,
                                    fontFamily = SegoeUiLightFontFamily,
                                    fontWeight = FontWeight.Light
                                )
                            }
                            val historyTitleColor = if (isAeroTheme) Color.Unspecified else Color.White
                            val historyTitlePadding = if (isAeroTheme) {
                                Modifier.padding(start = 24.dp, bottom = 12.dp, top = 24.dp)
                            } else {
                                Modifier.padding(start = 24.dp, end = 0.dp, top = 24.dp, bottom = 0.dp)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(historyTitlePadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "History",
                                    style = historyTitleStyle,
                                    color = historyTitleColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (historyList.isEmpty()) {
                                Text(
                                    text = "no recently played songs",
                                    style = ZuneTypography.body2.copy(fontFamily = SegoeUiFontFamily),
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 24.dp)
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    historyList.take(12).forEach { item ->
                                        Box(
                                            modifier = Modifier
                                                .size(100.dp)
                                                .background(Color(0xFF1E1E1E))
                                                .metroClickable { onPlaySong(item) }
                                        ) {
                                            if (item.albumArtUri != null) {
                                                AsyncImage(
                                                    model = item.albumArtUri,
                                                    contentDescription = "Album Art",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(32.dp))
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
            }
        }

        if (activePinnedSongForOptions != null) {
            val song = activePinnedSongForOptions!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { activePinnedSongForOptions = null },
                contentAlignment = Alignment.Center
            ) {
                // Blurred background using a copy of album art
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(40.dp)
                        .graphicsLayer { alpha = 0.35f }
                )
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clickable(enabled = false) {}
                ) {
                    // Enlarged Album Art
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .background(Color(0xFF1C1C1C))
                            .border(2.dp, LocalZuneAccent.current)
                    ) {
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = song.title.uppercase(),
                        style = ZuneTypography.h1.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist.uppercase(),
                        style = ZuneTypography.body1,
                        color = ZuneTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Options
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.width(280.dp)
                    ) {
                        Text(
                            text = "play",
                            style = ZuneTypography.h1.copy(fontSize = 32.sp, fontFamily = SegoeUiLightFontFamily),
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .metroClickable {
                                    player.playList(listOf(song))
                                    activePinnedSongForOptions = null
                                }
                        )
                        Text(
                            text = "play next",
                            style = ZuneTypography.h1.copy(fontSize = 32.sp, fontFamily = SegoeUiLightFontFamily),
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .metroClickable {
                                    player.playNext(listOf(song))
                                    activePinnedSongForOptions = null
                                }
                        )
                        Text(
                            text = "add to queue",
                            style = ZuneTypography.h1.copy(fontSize = 32.sp, fontFamily = SegoeUiLightFontFamily),
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .metroClickable {
                                    player.addToQueue(listOf(song))
                                    activePinnedSongForOptions = null
                                }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "cancel",
                            style = ZuneTypography.body1.copy(fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .metroClickable {
                                    activePinnedSongForOptions = null
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmallClock(modifier: Modifier = Modifier) {
    var timeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        while (true) {
            timeText = sdf.format(java.util.Date()).lowercase()
            kotlinx.coroutines.delay(1000)
        }
    }
    Text(
        text = timeText,
        style = ZuneTypography.body2.copy(
            fontFamily = SegoeUiFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        ),
        color = Color.White.copy(alpha = 0.6f),
        modifier = modifier
    )
}

@Composable
fun NowPlayingPanel(
    player: AudioPlayer,
    onNavigateToNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    isAeroTheme: Boolean = false
) {
    val currentItem by player.currentAudio.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    val accent = LocalZuneAccent.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
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
                .fillMaxWidth()
                .aspectRatio(1.0f)
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
                        .fillMaxSize()
                        .background(accent.copy(alpha = 0.15f))
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
        modifier = Modifier.padding(start = 24.dp, bottom = 16.dp, top = 32.dp)
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

    val context = androidx.compose.ui.platform.LocalContext.current
    var isLauncher by remember { mutableStateOf(isDefaultLauncher(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isLauncher = isDefaultLauncher(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val categories = remember(isLauncher) {
        if (isLauncher) {
            listOf("music", "videos", "pictures", "podcasts", "apps", "settings")
        } else {
            listOf("music", "videos", "pictures", "podcasts", "settings")
        }
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.65f)
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            itemsIndexed(categories, key = { _, category -> category }) { index, category ->
                val accentColor = LocalZuneAccent.current
                val textColor = if (isAeroTheme) {
                    accentColor.copy(alpha = 0.7f)
                } else {
                    if (isBlackBackground) accentColor.lightenForText() else Color.White.copy(alpha = 0.6f)
                }
                
                @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    val isForward = com.zune.player.LocalIsForwardTransition.current
                    with(sharedTransitionScope) {
                        val isTransitionRunning = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState
                        if (isTransitionRunning && isForward) {
                            Modifier.sharedElement(
                                rememberSharedContentState(key = "header_${category.lowercase()}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ ->
                                    androidx.compose.animation.core.spring<androidx.compose.ui.geometry.Rect>(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                        stiffness = 150f
                                    )
                                },
                                renderInOverlayDuringTransition = false
                            ).skipToLookaheadSize()
                        } else {
                            Modifier
                        }
                    }
                } else {
                    Modifier
                }

                Text(
                    text = category,
                    style = ZuneTypography.h2.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 56.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start
                    ),
                    color = textColor,
                     modifier = Modifier
                        .fillMaxWidth()
                        .then(sharedModifier)
                        .metroClickable {
                            onNavigateToCategory(category)
                        }
                )
            }
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
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> },
    onShowFeaturedChanged: (Boolean) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedBg by remember { mutableStateOf(prefs.getInt("bg_selection", 0)) }
    var customBgUriStr by remember { mutableStateOf(prefs.getString("bg_custom_uri", null)) }
    var showFeaturedSection by remember { mutableStateOf(prefs.getBoolean("show_featured_section", true)) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "show_featured_section") {
                showFeaturedSection = prefs.getBoolean("show_featured_section", true)
            } else if (key == "bg_selection") {
                selectedBg = prefs.getInt("bg_selection", 0)
            } else if (key == "bg_custom_uri") {
                customBgUriStr = prefs.getString("bg_custom_uri", null)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (!AppsCache.isInitialized) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    }
                    val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                    val apps = resolveInfos.mapNotNull { ri ->
                        val pkgName = ri.activityInfo.packageName
                        if (pkgName == context.packageName) return@mapNotNull null
                        val appLabel = ri.loadLabel(pm).toString()
                        val appId = (pkgName.hashCode().toLong() and 0x0FFFFFFFFFFFFFFFL) or 0x3000000000000000L
                        AppItem(pkgName, appLabel, appId)
                    }.sortedBy { it.appName.lowercase() }
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        AppsCache.cachedApps = apps
                        AppsCache.isInitialized = true
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    val options = listOf(
        0 to "pure black",
        com.zune.player.R.drawable.bg_1 to "background 1",
        com.zune.player.R.drawable.bg_2 to "background 2",
        com.zune.player.R.drawable.bg_3 to "background 3",
        -1 to "custom image"
    )

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            prefs.edit()
                .putInt("bg_selection", -1)
                .putString("bg_custom_uri", uri.toString())
                .apply()
            selectedBg = -1
        }
    }

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

    var accentSource by remember { mutableStateOf(prefs.getString("accent_source", "music") ?: "music") }
    var customAccentColorVal by remember { mutableIntStateOf(prefs.getInt("accent_custom_color", 0xFF0083D7.toInt())) }

    val metroColors = remember {
        listOf(
            Color(0xFFFFFFFF), // White Accent
            Color(0xFFE5A600), // Gold / Amber
            Color(0xFF8CBF26), // Lime Green
            Color(0xFF0083D7), // Sky Blue / Aero Blue
            Color(0xFFE51400), // Red
            Color(0xFF339933), // Green
            Color(0xFF9900FF), // Violet / Purple
            Color(0xFFA55112), // Brown
            Color(0xFFDF0024), // Crimson
            Color(0xFFF0A30A), // Yellow
            Color(0xFF1BA1E2), // Cyan
            Color(0xFFD80073), // Magenta / Hot Pink
            Color(0xFFA2C139), // Grass Green
            Color(0xFF0050EF), // Cobalt Blue
            Color(0xFF6A00FF), // Indigo
            Color(0xFFE3C800), // Yellow-Gold
            Color(0xFFF472D0), // Pink
            Color(0xFFE05206), // Orange
            Color(0xFF00ABA9), // Teal
            Color(0xFF2D89EF), // Steel Blue
            Color(0xFF647687), // Slate
            Color(0xFF76608A), // Mauve
            Color(0xFF87794E), // Olive
            Color(0xFF6D8764), // Sage
            Color(0xFFBD761E)  // Peach / Ochre
        )
    }

    LazyVerticalGrid(
        state = scrollState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Accent Color Source Selection
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(
                    text = "ACCENT COLOR SOURCE",
                    style = ZuneTypography.h4.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                    color = ZuneTextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        text = "music dynamic",
                        color = if (accentSource == "music") Color.White else Color.Gray,
                        style = ZuneTypography.body2.copy(fontWeight = if (accentSource == "music") FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.metroClickable {
                            accentSource = "music"
                            prefs.edit().putString("accent_source", "music").apply()
                        }
                    )
                    Text(
                        text = "custom color",
                        color = if (accentSource == "custom") Color.White else Color.Gray,
                        style = ZuneTypography.body2.copy(fontWeight = if (accentSource == "custom") FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.metroClickable {
                            accentSource = "custom"
                            prefs.edit().putString("accent_source", "custom").apply()
                        }
                    )
                }
            }
        }

        // Section: Accent Custom Color Picker Bar
        if (accentSource == "custom") {
            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(
                        text = "CHOOSE ACCENT COLOR",
                        style = ZuneTypography.h4.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                        color = ZuneTextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    val lazyRowState = androidx.compose.foundation.lazy.rememberLazyListState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(width = 1.dp, color = Color.White.copy(alpha = 0.2f))
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        androidx.compose.foundation.lazy.LazyRow(
                            state = lazyRowState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(metroColors) { color ->
                                val isColorSelected = customAccentColorVal == color.toArgb()
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(36.dp)
                                        .metroClickable {
                                            customAccentColorVal = color.toArgb()
                                            prefs.edit().putInt("accent_custom_color", color.toArgb()).apply()
                                        }
                                ) {
                                    Text(
                                        text = "▼",
                                        fontSize = 8.sp,
                                        color = if (isColorSelected) Color.White else Color.Transparent,
                                        modifier = Modifier.height(10.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(color)
                                            .border(
                                                width = if (isColorSelected) 2.dp else 0.dp,
                                                color = if (isColorSelected) Color.White else Color.Transparent
                                            )
                                    )
                                    Text(
                                        text = "▲",
                                        fontSize = 8.sp,
                                        color = if (isColorSelected) Color.White else Color.Transparent,
                                        modifier = Modifier.height(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Header for Background settings
        item(span = { GridItemSpan(2) }) {
            Text(
                text = "WALLPAPER BACKGROUND",
                style = ZuneTypography.h4.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                color = ZuneTextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        itemsIndexed(options, key = { _, option -> option.first }) { index, (drawableRes, label) ->
            val isSelected = selectedBg == drawableRes
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .metroClickable {
                        if (drawableRes == -1) {
                            pickerLauncher.launch("image/*")
                        } else {
                            selectedBg = drawableRes
                            prefs.edit().putInt("bg_selection", drawableRes).apply()
                        }
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
                    if (drawableRes == -1) {
                        if (!customBgUriStr.isNullOrEmpty()) {
                            AsyncImage(
                                model = android.net.Uri.parse(customBgUriStr),
                                contentDescription = label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF222222)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "tap to choose",
                                    style = ZuneTypography.body2.copy(color = Color.White.copy(alpha = 0.6f))
                                )
                            }
                        }
                    } else if (drawableRes != 0) {
                        AsyncImage(
                            model = drawableRes,
                            contentDescription = label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = ZuneTypography.body2,
                    color = if (isSelected) LocalZuneAccent.current else Color.White
                )
            }
        }

        // Section: Toggles
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
                Text(
                    text = "FEATURED SECTION",
                    style = ZuneTypography.h4.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                    color = ZuneTextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        text = "show",
                        color = if (showFeaturedSection) Color.White else Color.Gray,
                        style = ZuneTypography.body2.copy(fontWeight = if (showFeaturedSection) FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.metroClickable {
                            prefs.edit().putBoolean("show_featured_section", true).apply()
                            onShowFeaturedChanged(true)
                        }
                    )
                    Text(
                        text = "hide",
                        color = if (!showFeaturedSection) Color.White else Color.Gray,
                        style = ZuneTypography.body2.copy(fontWeight = if (!showFeaturedSection) FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.metroClickable {
                            prefs.edit().putBoolean("show_featured_section", false).apply()
                            onShowFeaturedChanged(false)
                        }
                    )
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            var npBgType by remember { mutableIntStateOf(prefs.getInt("np_bg_type", 0)) }
            var localAlbumsStyle by remember { mutableStateOf(prefs.getString("local_albums_layout_style", "grid") ?: "grid") }
            
            DisposableEffect(prefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "np_bg_type") {
                        npBgType = prefs.getInt("np_bg_type", 0)
                    } else if (key == "local_albums_layout_style") {
                        localAlbumsStyle = prefs.getString("local_albums_layout_style", "grid") ?: "grid"
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Now Playing Backdrop setting
                Column {
                    Text(
                        text = "NOW PLAYING BACKDROP",
                        style = ZuneTypography.h4.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                        color = ZuneTextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(
                            text = "ambient",
                            color = if (npBgType == 0) Color.White else Color.Gray,
                            style = ZuneTypography.body2.copy(fontWeight = if (npBgType == 0) FontWeight.Bold else FontWeight.Normal),
                            modifier = Modifier.metroClickable {
                                prefs.edit().putInt("np_bg_type", 0).apply()
                            }
                        )
                        Text(
                            text = "pure black",
                            color = if (npBgType == 1) Color.White else Color.Gray,
                            style = ZuneTypography.body2.copy(fontWeight = if (npBgType == 1) FontWeight.Bold else FontWeight.Normal),
                            modifier = Modifier.metroClickable {
                                prefs.edit().putInt("np_bg_type", 1).apply()
                            }
                        )
                        Text(
                            text = "artist photo",
                            color = if (npBgType == 2) Color.White else Color.Gray,
                            style = ZuneTypography.body2.copy(fontWeight = if (npBgType == 2) FontWeight.Bold else FontWeight.Normal),
                            modifier = Modifier.metroClickable {
                                prefs.edit().putInt("np_bg_type", 2).apply()
                            }
                        )
                    }
                }
                
                // Local Albums layout setting
                Column {
                    Text(
                        text = "LOCAL ALBUMS LAYOUT",
                        style = ZuneTypography.h4.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                        color = ZuneTextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(
                            text = "grid",
                            color = if (localAlbumsStyle == "grid") Color.White else Color.Gray,
                            style = ZuneTypography.body2.copy(fontWeight = if (localAlbumsStyle == "grid") FontWeight.Bold else FontWeight.Normal),
                            modifier = Modifier.metroClickable {
                                prefs.edit().putString("local_albums_layout_style", "grid").apply()
                            }
                        )
                        Text(
                            text = "song preview",
                            color = if (localAlbumsStyle == "song_preview") Color.White else Color.Gray,
                            style = ZuneTypography.body2.copy(fontWeight = if (localAlbumsStyle == "song_preview") FontWeight.Bold else FontWeight.Normal),
                            modifier = Modifier.metroClickable {
                                prefs.edit().putString("local_albums_layout_style", "song_preview").apply()
                            }
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_feedback_enabled", true)) }
            
            DisposableEffect(prefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "haptic_feedback_enabled") {
                        hapticEnabled = prefs.getBoolean("haptic_feedback_enabled", true)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
                Text(
                    text = "HAPTIC FEEDBACK (VIBRATION)",
                    style = ZuneTypography.h4.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                    color = ZuneTextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        text = "on",
                        color = if (hapticEnabled) Color.White else Color.Gray,
                        style = ZuneTypography.body2.copy(fontWeight = if (hapticEnabled) FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.metroClickable {
                            prefs.edit().putBoolean("haptic_feedback_enabled", true).apply()
                        }
                    )
                    Text(
                        text = "off",
                        color = if (!hapticEnabled) Color.White else Color.Gray,
                        style = ZuneTypography.body2.copy(fontWeight = if (!hapticEnabled) FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.metroClickable {
                            prefs.edit().putBoolean("haptic_feedback_enabled", false).apply()
                        }
                    )
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            var historyEnabledSetting by remember { mutableStateOf(prefs.getBoolean("history_enabled", true)) }
            
            DisposableEffect(prefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "history_enabled") {
                        historyEnabledSetting = prefs.getBoolean("history_enabled", true)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
                Text(
                    text = "PLAYBACK HISTORY TRACKING",
                    style = ZuneTypography.h4.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                    color = ZuneTextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        text = "on",
                        color = if (historyEnabledSetting) Color.White else Color.Gray,
                        style = ZuneTypography.body2.copy(fontWeight = if (historyEnabledSetting) FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.metroClickable {
                            prefs.edit().putBoolean("history_enabled", true).apply()
                        }
                    )
                    Text(
                        text = "off",
                        color = if (!historyEnabledSetting) Color.White else Color.Gray,
                        style = ZuneTypography.body2.copy(fontWeight = if (!historyEnabledSetting) FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.metroClickable {
                            prefs.edit().putBoolean("history_enabled", false).remove("playback_history_json").apply()
                        }
                    )
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