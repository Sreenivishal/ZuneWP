package com.zune.player

import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.drawscope.translate
import com.zune.player.ui.screens.CategoryListScreen
import com.zune.player.ui.screens.HomeScreen
import com.zune.player.ui.screens.NowPlayingScreen
import com.zune.player.ui.screens.PhotosScreen
import com.zune.player.ui.screens.VideosScreen
import com.zune.player.ui.screens.AppsScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.CircularProgressIndicator
import com.zune.player.ui.theme.ZuneAccent
import com.zune.player.ui.theme.AeroBlueOrbAccentColor
import com.zune.player.ui.theme.extractDominantColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.painterResource
import android.content.SharedPreferences
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import com.zune.player.viewmodel.MusicViewModel
import com.zune.player.ui.theme.ZuneTheme
import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Alignment
import com.zune.player.ui.screens.QueuePanel
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zune.player.ui.theme.LocalZuneAccent
import android.content.Context
import com.zune.player.ui.theme.SegoeUiFontFamily
import com.zune.player.ui.theme.SegoeUiLightFontFamily
import com.zune.player.ui.theme.SegoeUiBoldFontFamily
import com.zune.player.ui.theme.ZuneTextSecondary
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import fluent.ui.system.icons.FluentIcons
import fluent.ui.system.icons.regular.*
import fluent.ui.system.icons.filled.*
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.Text
import coil.imageLoader
import kotlinx.coroutines.delay
import com.zune.player.ui.components.metroClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.QueueMusic

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalIsForwardTransition = staticCompositionLocalOf { true }

class MainActivity : ComponentActivity() {
    
    companion object {
        val volumeLevel = kotlinx.coroutines.flow.MutableStateFlow(-1)
        val volumeTrigger = kotlinx.coroutines.flow.MutableStateFlow(0L)
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())

        setContent {
            MainApp()
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val direction = if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, direction, 0)
            
            val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            
            // Map actual volume level to a 0-30 scale for Zune look and feel
            val scaledVal = ((current.toFloat() / max.toFloat()) * 30f).toInt()
            volumeLevel.value = scaledVal
            volumeTrigger.value = System.currentTimeMillis()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            try {
                this.imageLoader.memoryCache?.clear()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            this.imageLoader.memoryCache?.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

sealed class AppScreen {
    data class Home(val initialPage: Int = 1) : AppScreen()
    object NowPlaying : AppScreen()
    data class CategoryList(val category: String) : AppScreen()
    data class PlaylistDetail(val playlistName: String) : AppScreen()
    data class AlbumDetail(val albumName: String) : AppScreen()
    object Search : AppScreen()
    data class Photos(val initialPhotoId: Long? = null) : AppScreen()
    data class Videos(val initialVideoId: Long? = null) : AppScreen()
    object Podcasts : AppScreen()
    data class OnlineAlbumDetail(val browseId: String, val albumName: String, val artistName: String, val artworkUrl: String) : AppScreen()
    data class OnlineArtistDetail(val browseId: String, val artistName: String, val artworkUrl: String) : AppScreen()
    object Personalize : AppScreen()
    object Apps : AppScreen()
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val window = (context as? android.app.Activity)?.window
    
    DisposableEffect(window) {
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {}
    }

    val viewModel: MusicViewModel = viewModel()
    val scrollStates = remember { mutableMapOf<String, Pair<Int, Int>>() }
    val getScrollPosition: (String) -> Pair<Int, Int> = { key -> scrollStates[key] ?: Pair(0, 0) }
    val onScrollPositionChanged: (String, Int, Int) -> Unit = { key, index, offset -> scrollStates[key] = Pair(index, offset) }
    
    LaunchedEffect(Unit) {
        viewModel.loadMusic()
    }
    
    val currentAudio by viewModel.player.currentAudio.collectAsState()
    val isPlaying by viewModel.player.isPlaying.collectAsState()

    val pinned by viewModel.pinnedItems.collectAsState()
    val prefs = remember { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }

    LaunchedEffect(currentAudio) {
        currentAudio?.let { audio ->
            addToPlaybackHistory(prefs, audio)
        }
    }

    var selectedBg by remember { mutableStateOf(prefs.getInt("bg_selection", 0)) }
    var accentSource by remember { mutableStateOf(prefs.getString("accent_source", "music") ?: "music") }
    var customAccentColorVal by remember { mutableIntStateOf(prefs.getInt("accent_custom_color", 0xFF0083D7.toInt())) }
    var lastHomePageIndex by remember { mutableIntStateOf(1) }

    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "bg_selection") {
                selectedBg = sharedPreferences.getInt("bg_selection", 0)
            } else if (key == "accent_source") {
                accentSource = sharedPreferences.getString("accent_source", "music") ?: "music"
            } else if (key == "accent_custom_color") {
                customAccentColorVal = sharedPreferences.getInt("accent_custom_color", 0xFF0083D7.toInt())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var extractedColor by remember { mutableStateOf(ZuneAccent) }
    
    LaunchedEffect(currentAudio?.albumArtUri, selectedBg, accentSource, customAccentColorVal) {
        if (accentSource == "music") {
            val newColor = extractDominantColor(context, currentAudio?.albumArtUri?.toString())
            extractedColor = newColor ?: ZuneAccent
        } else {
            extractedColor = Color(customAccentColorVal)
        }
    }

    val animatedAccent by animateColorAsState(
        targetValue = extractedColor,
        animationSpec = tween(durationMillis = 1000)
    )

    val horizontalScrollOffset = remember { mutableFloatStateOf(0f) }

    ZuneTheme(dynamicAccent = animatedAccent) {
        var backStack by remember { mutableStateOf(listOf<AppScreen>(AppScreen.Home())) }
        val artistDetailsCache = remember { mutableStateMapOf<String, Triple<List<com.zune.player.data.OnlineSong>, List<com.zune.player.data.OnlineAlbum>, List<com.zune.player.data.OnlineAlbum>>>() }
        val albumTracksCache = remember { mutableStateMapOf<String, List<String>>() }
        LaunchedEffect(backStack) {
            val hasArtistScreen = backStack.any { it is AppScreen.OnlineArtistDetail }
            val hasAlbumScreen = backStack.any { it is AppScreen.OnlineAlbumDetail }
            if (!hasArtistScreen && !hasAlbumScreen) {
                artistDetailsCache.clear()
                albumTracksCache.clear()
            }
        }
        val currentScreen = backStack.last()

        var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
        var isScreensaverActive by remember { mutableStateOf(false) }

        LaunchedEffect(isPlaying, lastInteractionTime, currentScreen) {
            if (isPlaying && currentScreen != AppScreen.NowPlaying) {
                while (true) {
                    val elapsed = System.currentTimeMillis() - lastInteractionTime
                    if (elapsed >= 30000L) {
                        isScreensaverActive = true
                        break
                    }
                    delay(1000L)
                }
            } else {
                isScreensaverActive = false
            }
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        lastInteractionTime = System.currentTimeMillis()
                        if (isScreensaverActive) {
                            isScreensaverActive = false
                        }
                    }
                }
            }
        ) {
            ParallaxBackground(selectedBg = selectedBg, horizontalScrollOffset = horizontalScrollOffset)
            
            var previousScreen by remember { mutableStateOf<AppScreen?>(null) }
            var lastTargetScreen by remember { mutableStateOf<AppScreen?>(null) }
            
            SideEffect {
                if (currentScreen != lastTargetScreen) {
                    previousScreen = lastTargetScreen
                    lastTargetScreen = currentScreen
                }
            }
            
            var showGlobalQueue by remember { mutableStateOf(false) }
            var queueOpenCount by remember { mutableIntStateOf(0) }
            LaunchedEffect(showGlobalQueue) {
                if (showGlobalQueue) {
                    queueOpenCount++
                }
            }
            val coroutineScope = rememberCoroutineScope()

            fun navigateTo(screen: AppScreen) {
                if (currentScreen != screen) {
                    backStack = backStack + screen
                }
            }

            fun navigateBack() {
                if (backStack.size > 1) {
                    backStack = backStack.dropLast(1)
                }
            }
            
            BackHandler(enabled = backStack.size > 1 || showGlobalQueue) {
                if (showGlobalQueue) {
                    showGlobalQueue = false
                } else {
                    navigateBack()
                }
            }

            @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = currentScreen,
                contentKey = { screen ->
                    when (screen) {
                        is AppScreen.Home -> "home"
                        is AppScreen.NowPlaying -> "now_playing"
                        is AppScreen.CategoryList -> "category_list"
                        is AppScreen.PlaylistDetail -> "playlist_detail_${screen.playlistName}"
                        is AppScreen.AlbumDetail -> "album_detail_${screen.albumName}"
                        is AppScreen.Search -> "search"
                        is AppScreen.Photos -> "photos"
                        is AppScreen.Videos -> "videos"
                        is AppScreen.Podcasts -> "podcasts"
                        is AppScreen.OnlineAlbumDetail -> "online_album_detail_${screen.browseId}"
                        is AppScreen.OnlineArtistDetail -> "online_artist_detail_${screen.browseId}"
                        is AppScreen.Personalize -> "personalize"
                        is AppScreen.Apps -> "apps"
                    }
                },
                transitionSpec = {
                    val animationSpec = spring<IntOffset>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                    val fadeSpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                    
                    if (targetState is AppScreen.NowPlaying) {
                        (slideInVertically(
                            animationSpec = animationSpec,
                            initialOffsetY = { fullHeight -> fullHeight }
                        ) + fadeIn(animationSpec = fadeSpec)) togetherWith
                        (fadeOut(animationSpec = fadeSpec))
                    } else if (initialState is AppScreen.NowPlaying) {
                        (fadeIn(animationSpec = fadeSpec)) togetherWith
                        (slideOutVertically(
                            animationSpec = animationSpec,
                            targetOffsetY = { fullHeight -> fullHeight }
                        ) + fadeOut(animationSpec = fadeSpec))
                    } else {
                        val entering = targetState
                        val exiting = initialState
                        val isCategoryTransition = (exiting is AppScreen.CategoryList && entering is AppScreen.CategoryList)
                        val springSpec = spring<Float>(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 150f
                        )
                        val slideSpec = spring<IntOffset>(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 150f
                        )
                        
                        if (isCategoryTransition) {
                            val slideDirection = if (isForwardTransition(exiting, entering)) 1 else -1
                            slideInHorizontally(
                                animationSpec = slideSpec,
                                initialOffsetX = { it * slideDirection }
                            ) + fadeIn(springSpec) togetherWith
                            slideOutHorizontally(
                                animationSpec = slideSpec,
                                targetOffsetX = { -it * slideDirection }
                            ) + fadeOut(springSpec)
                        } else {
                            fadeIn(animationSpec = springSpec) togetherWith
                            fadeOut(animationSpec = springSpec)
                        }
                    }
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                val isForward = remember(previousScreen, currentScreen) {
                    isForwardTransition(previousScreen ?: AppScreen.Home(1), currentScreen)
                }

                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalAnimatedVisibilityScope provides this@AnimatedContent,
                    LocalIsForwardTransition provides isForward
                ) {
                    val playingTitle = currentAudio?.title
                
                val progress by transition.animateFloat(
                    transitionSpec = {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 150f
                        )
                    },
                    label = "TurnstileProgress"
                ) { state ->
                    if (state == EnterExitState.Visible) 1f else 0f
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val entering = (transition.targetState == EnterExitState.Visible)
                            val isNowPlayingTransition = (previousScreen is AppScreen.NowPlaying || currentScreen is AppScreen.NowPlaying)
                            val isCategoryTransition = (previousScreen is AppScreen.CategoryList && currentScreen is AppScreen.CategoryList)
                            val isDetailTransition = (
                                 previousScreen is AppScreen.PlaylistDetail || currentScreen is AppScreen.PlaylistDetail ||
                                 previousScreen is AppScreen.AlbumDetail || currentScreen is AppScreen.AlbumDetail ||
                                 previousScreen is AppScreen.OnlineAlbumDetail || currentScreen is AppScreen.OnlineAlbumDetail ||
                                 previousScreen is AppScreen.OnlineArtistDetail || currentScreen is AppScreen.OnlineArtistDetail
                             )
                             
                             if (!isNowPlayingTransition && !isCategoryTransition && !isDetailTransition) {
                                cameraDistance = 12f * density
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                
                                if (isForward) {
                                    if (entering) {
                                        rotationY = (1f - progress) * 45f
                                        alpha = progress
                                        val scale = 0.97f + progress * 0.03f
                                        scaleX = scale
                                        scaleY = scale
                                    } else {
                                        rotationY = (progress - 1f) * 45f
                                        alpha = progress
                                        val scale = 0.97f + progress * 0.03f
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                } else {
                                    if (entering) {
                                        rotationY = (progress - 1f) * 45f
                                        alpha = progress
                                        val scale = 1f + (1f - progress) * 0.03f
                                        scaleX = scale
                                        scaleY = scale
                                    } else {
                                        rotationY = (1f - progress) * 45f
                                        alpha = progress
                                        val scale = 1f + (1f - progress) * 0.03f
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                }
                            }
                        }
                ) {
                    when (targetScreen) {
                        is AppScreen.Home -> {
                            Box(Modifier.systemBarsPadding()) {
                                val audioItems by viewModel.audioItems.collectAsState()
                                val pinnedItems by viewModel.pinnedItems.collectAsState()
                                HomeScreen(
                                    initialPage = lastHomePageIndex,
                                    player = viewModel.player,
                                    audioItems = audioItems,
                                    pinnedItems = pinnedItems,
                                    onNavigateToNowPlaying = { navigateTo(AppScreen.NowPlaying) },
                                    onOpenQueue = { showGlobalQueue = true },
                                    onNavigateToCategory = { category ->
                                        when (category.lowercase()) {
                                            "search" -> navigateTo(AppScreen.Search)
                                            "pictures" -> navigateTo(AppScreen.Photos())
                                            "videos" -> navigateTo(AppScreen.Videos())
                                            "podcasts" -> navigateTo(AppScreen.Podcasts)
                                            "music" -> navigateTo(AppScreen.CategoryList("songs"))
                                            "settings" -> navigateTo(AppScreen.Personalize)
                                            "apps" -> navigateTo(AppScreen.Apps)
                                            else -> navigateTo(AppScreen.CategoryList(category))
                                        }
                                    },
                                    onNavigateToPhotos = { photoId -> navigateTo(AppScreen.Photos(photoId)) },
                                    onNavigateToVideos = { videoId -> navigateTo(AppScreen.Videos(videoId)) },
                                    onPlayAlbum = { album ->
                                        navigateTo(AppScreen.AlbumDetail(album))
                                    },
                                    onPlaySong = { audioItem ->
                                        viewModel.player.playList(listOf(audioItem))
                                    },
                                    onUnpin = { id -> viewModel.unpinSong(id) },
                                    onCycleSize = { id -> viewModel.cyclePinSize(id) },
                                    onMove = { from, to -> viewModel.reorderPinned(from, to) },
                                    onScroll = { horizontalScrollOffset.floatValue = it },
                                    isAeroTheme = selectedBg == R.drawable.bg_4,
                                    getScrollPosition = getScrollPosition,
                                    onScrollPositionChanged = onScrollPositionChanged,
                                    onPageSelected = { lastHomePageIndex = it }
                                )
                            }
                        }
                        is AppScreen.NowPlaying -> NowPlayingScreen(
                            player = viewModel.player,
                            onBack = { navigateBack() },
                            onOpenQueue = { showGlobalQueue = true }
                        )
                        is AppScreen.CategoryList -> {
                            Box(Modifier.systemBarsPadding()) {
                                val category = targetScreen.category
                                val playlists by viewModel.playlists.collectAsState()
                                val audioItems by viewModel.audioItems.collectAsState()
                                val pinned by viewModel.pinnedItems.collectAsState()
                                CategoryListScreen(
                                     initialCategory = category,
                                     getItemsForCategory = { viewModel.getItemsForCategory(it) },
                                     isAeroTheme = selectedBg == R.drawable.bg_4,
                                     playlists = playlists,
                                     audioItems = audioItems,
                                     onItemClick = { cat, itemTitle ->
                                         when (cat.lowercase()) {
                                             "playlists" -> navigateTo(AppScreen.PlaylistDetail(itemTitle))
                                             "albums" -> navigateTo(AppScreen.AlbumDetail(itemTitle))
                                             else -> {
                                                 viewModel.playCategoryQueue(cat, itemTitle)
                                             }
                                         }
                                     },
                                     onCreatePlaylist = { viewModel.createPlaylist(it) },
                                     onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                     onAddToPlaylist = { songTitle, playlistName ->
                                         viewModel.addItemToPlaylist(playlistName, songTitle)
                                     },
                                     onPlayNext = { cat, itemTitle ->
                                         viewModel.playCategoryNext(cat, itemTitle)
                                     },
                                     onAddToQueue = { cat, itemTitle ->
                                         viewModel.addCategoryToQueue(cat, itemTitle)
                                     },
                                     isPinned = { itemTitle ->
                                         val id = audioItems.find { it.title == itemTitle }?.id
                                         id != null && pinned.any { it.first == id }
                                     },
                                     onPin = { itemTitle ->
                                         val id = audioItems.find { it.title == itemTitle }?.id
                                         if (id != null) {
                                             if (pinned.any { it.first == id }) viewModel.unpinSong(id)
                                             else viewModel.pinSong(id)
                                         }
                                     },
                                     onBack = { navigateBack() },
                                     onScroll = { /* Pager handles parallax now */ },
                                     currentPlayingTitle = playingTitle,
                                     isPlaying = isPlaying,
                                     onTogglePlayPause = { viewModel.player.togglePlayPause() },
                                     onNavigateToNowPlaying = { navigateTo(AppScreen.NowPlaying) },
                                     onCategoryChanged = { newCategory ->
                                         if (backStack.isNotEmpty() && backStack.last() is AppScreen.CategoryList) {
                                             val list = backStack.toMutableList()
                                             list[list.lastIndex] = AppScreen.CategoryList(newCategory)
                                             backStack = list
                                         }
                                     },
                                     getScrollPosition = getScrollPosition,
                                     onScrollPositionChanged = onScrollPositionChanged,
                                     onOnlineTrackClick = { track ->
                                         viewModel.player.playList(listOf(track))
                                     },
                                     onOnlineAddToQueue = { track ->
                                         viewModel.player.addToQueue(listOf(track))
                                     },
                                     onOnlinePlayNext = { track ->
                                         viewModel.player.playNext(listOf(track))
                                     },
                                     onOnlineAlbumClick = { onlineAlbum ->
                                         navigateTo(
                                             AppScreen.OnlineAlbumDetail(
                                                 browseId = onlineAlbum.browseId,
                                                 albumName = onlineAlbum.title,
                                                 artistName = onlineAlbum.artist,
                                                 artworkUrl = onlineAlbum.artworkUrl
                                             )
                                         )
                                     },
                                     onOnlineArtistClick = { onlineArtist ->
                                         navigateTo(
                                             AppScreen.OnlineArtistDetail(
                                                 browseId = onlineArtist.browseId,
                                                 artistName = onlineArtist.name,
                                                 artworkUrl = onlineArtist.artworkUrl
                                             )
                                         )
                                     }
                                 )
                            }
                        }
                        is AppScreen.Search -> {
                             Box(Modifier.systemBarsPadding()) {
                                 val audioItems by viewModel.audioItems.collectAsState()
                                 val playlists by viewModel.playlists.collectAsState()
                                 com.zune.player.ui.screens.SearchScreen(
                                     audioItems = audioItems,
                                     onBack = { navigateBack() },
                                     onTrackClick = { track ->
                                         viewModel.player.playList(listOf(track))
                                     },
                                     onAddToQueue = { track ->
                                         viewModel.player.addToQueue(listOf(track))
                                     },
                                     onPlayNext = { track ->
                                         viewModel.player.playNext(listOf(track))
                                     },
                                     playlists = playlists,
                                     onAddToPlaylist = { track, playlistName ->
                                         viewModel.addItemToPlaylist(playlistName, track)
                                     },
                                     onOnlineAlbumClick = { onlineAlbum ->
                                         navigateTo(
                                             AppScreen.OnlineAlbumDetail(
                                                 browseId = onlineAlbum.browseId,
                                                 albumName = onlineAlbum.title,
                                                 artistName = onlineAlbum.artist,
                                                 artworkUrl = onlineAlbum.artworkUrl
                                             )
                                         )
                                     },
                                     onOnlineArtistClick = { onlineArtist ->
                                         navigateTo(
                                             AppScreen.OnlineArtistDetail(
                                                 browseId = onlineArtist.browseId,
                                                 artistName = onlineArtist.name,
                                                 artworkUrl = onlineArtist.artworkUrl
                                             )
                                         )
                                     },
                                     currentPlayingTitle = playingTitle,
                                     onLibraryChanged = { viewModel.loadMusic() }
                                 )
                             }
                         }
                        is AppScreen.PlaylistDetail -> {
                            Box(Modifier.systemBarsPadding()) {
                                val playlistName = targetScreen.playlistName
                                var playlistTracks by remember { mutableStateOf<List<com.zune.player.data.AudioItem>>(emptyList()) }
                                LaunchedEffect(playlistName) {
                                    playlistTracks = viewModel.getPlaylistTracks(playlistName)
                                }
                                com.zune.player.ui.screens.PlaylistDetailScreen(
                                    playlistName = playlistName,
                                    tracks = playlistTracks,
                                    onBack = { navigateBack() },
                                    onPlayAll = {
                                        viewModel.playCategoryQueue("playlists", playlistName)
                                    },
                                    onShuffleAll = {
                                        viewModel.playCategoryShuffle("playlists", playlistName)
                                    },
                                    onPlayNextPlaylist = {
                                        viewModel.playCategoryNext("playlists", playlistName)
                                    },
                                    onAddToQueuePlaylist = {
                                        viewModel.addCategoryToQueue("playlists", playlistName)
                                    },
                                    onTrackClick = { index ->
                                        viewModel.player.playList(playlistTracks, index)
                                    },
                                    onMoveTrack = { from, to ->
                                        val updated = playlistTracks.toMutableList()
                                        updated.add(to, updated.removeAt(from))
                                        playlistTracks = updated
                                        viewModel.savePlaylistTracks(playlistName, updated)
                                    },
                                    onPlayNextTrack = { track ->
                                        viewModel.playCategoryNext("songs", track.title)
                                    },
                                    onAddToQueueTrack = { track ->
                                        viewModel.addCategoryToQueue("songs", track.title)
                                    },
                                    onRemoveTrack = { index ->
                                        val updated = playlistTracks.toMutableList()
                                        updated.removeAt(index)
                                        playlistTracks = updated
                                        viewModel.savePlaylistTracks(playlistName, updated)
                                    },
                                     onRenamePlaylist = { newName ->
                                         if (newName.isNotBlank()) {
                                             viewModel.renamePlaylist(playlistName, newName)
                                             backStack = backStack.toMutableList().apply {
                                                 val lastIdx = indexOfLast { it is AppScreen.PlaylistDetail && it.playlistName == playlistName }
                                                 if (lastIdx != -1) {
                                                     set(lastIdx, AppScreen.PlaylistDetail(newName))
                                                 }
                                             }
                                         }
                                     },
                                    currentPlayingTitle = playingTitle
                                )
                            }
                        }
                        is AppScreen.AlbumDetail -> {
                            val albumName = targetScreen.albumName
                            var albumTracks by remember { mutableStateOf<List<com.zune.player.data.AudioItem>>(emptyList()) }
                            LaunchedEffect(albumName) {
                                albumTracks = viewModel.getAlbumTracks(albumName)
                            }
                            val artistName = albumTracks.firstOrNull()?.artist ?: "unknown artist"
                            val albumArtUri = albumTracks.firstOrNull()?.albumArtUri
                            
                            var albumExtractedColor by remember { mutableStateOf(ZuneAccent) }
                            LaunchedEffect(albumArtUri) {
                                val newColor = extractDominantColor(context, albumArtUri?.toString())
                                albumExtractedColor = newColor ?: ZuneAccent
                            }
                            
                            val albumAccent by animateColorAsState(
                                targetValue = albumExtractedColor,
                                animationSpec = tween(durationMillis = 1000)
                            )
                            
                            val playlists by viewModel.playlists.collectAsState()
                            val pinned by viewModel.pinnedItems.collectAsState()
                            val audioItems by viewModel.audioItems.collectAsState()

                            CompositionLocalProvider(com.zune.player.ui.theme.LocalZuneAccent provides albumAccent) {
                                com.zune.player.ui.screens.AlbumDetailScreen(
                                    albumName = albumName,
                                    artistName = artistName,
                                    tracks = albumTracks,
                                    onBack = { navigateBack() },
                                    onPlayAll = {
                                        viewModel.playCategoryQueue("albums", albumName)
                                    },
                                    onShuffleAll = {
                                        viewModel.playCategoryShuffle("albums", albumName)
                                    },
                                    onPlayNextAlbum = {
                                        viewModel.playCategoryNext("albums", albumName)
                                    },
                                    onTrackClick = { index ->
                                        viewModel.player.playList(albumTracks, index)
                                    },
                                    currentPlayingTitle = playingTitle,
                                    onPlayNextTrack = { songTitle ->
                                        viewModel.playCategoryNext("songs", songTitle)
                                    },
                                    onAddToQueueTrack = { songTitle ->
                                        viewModel.addCategoryToQueue("songs", songTitle)
                                    },
                                    onAddToPlaylistTrack = { track, playlistName ->
                                        viewModel.addItemToPlaylist(playlistName, track)
                                    },
                                    playlists = playlists,
                                    isPinnedTrack = { songTitle ->
                                        val id = audioItems.find { it.title == songTitle }?.id
                                        id != null && pinned.any { it.first == id }
                                    },
                                    onPinTrack = { songTitle ->
                                        val id = audioItems.find { it.title == songTitle }?.id
                                        if (id != null) {
                                            if (pinned.any { it.first == id }) viewModel.unpinSong(id)
                                            else viewModel.pinSong(id)
                                        }
                                    }
                                )
                            }
                        }
                        is AppScreen.OnlineAlbumDetail -> {
                            val browseId = targetScreen.browseId
                            val albumName = targetScreen.albumName
                            val artistName = targetScreen.artistName
                            val artworkUrl = targetScreen.artworkUrl
                            
                            var albumTracks by remember { mutableStateOf<List<com.zune.player.data.AudioItem>>(emptyList()) }
                            var isLoading by remember { mutableStateOf(true) }
                            
                            LaunchedEffect(browseId) {
                                isLoading = true
                                var songsList = emptyList<com.zune.player.data.AudioItem>()
                                try {
                                    val albumRepo = org.koin.core.context.GlobalContext.get().get<com.maxrave.domain.repository.AlbumRepository>()
                                    val resource = albumRepo.getAlbumData(browseId).firstOrNull { r ->
                                        r is com.maxrave.domain.utils.Resource.Success || r is com.maxrave.domain.utils.Resource.Error
                                    }
                                    if (resource is com.maxrave.domain.utils.Resource.Success) {
                                        songsList = resource.data?.tracks?.map { track ->
                                            com.zune.player.data.AudioItem(
                                                id = -track.videoId.hashCode().toLong(),
                                                title = track.title,
                                                artist = track.artists?.firstOrNull()?.name ?: artistName,
                                                album = track.album?.name ?: albumName,
                                                uri = android.net.Uri.parse("zune://online/${track.videoId}"),
                                                albumArtUri = track.thumbnails?.lastOrNull()?.url?.let { android.net.Uri.parse(it) } ?: if (artworkUrl.isNotEmpty()) android.net.Uri.parse(artworkUrl) else null,
                                                durationMs = (track.durationSeconds ?: 0) * 1000L
                                            )
                                        } ?: emptyList()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                albumTracks = songsList
                                isLoading = false
                            }

                            var albumExtractedColor by remember { mutableStateOf(ZuneAccent) }
                            LaunchedEffect(artworkUrl, selectedBg) {
                                val newColor = extractDominantColor(context, artworkUrl.ifEmpty { null })
                                albumExtractedColor = newColor ?: ZuneAccent
                            }
                            
                            val albumAccent by animateColorAsState(
                                targetValue = albumExtractedColor,
                                animationSpec = tween(durationMillis = 1000)
                            )
                            
                            val playlists by viewModel.playlists.collectAsState()

                            CompositionLocalProvider(com.zune.player.ui.theme.LocalZuneAccent provides albumAccent) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    com.zune.player.ui.screens.AlbumDetailScreen(
                                        albumName = albumName,
                                        artistName = artistName,
                                        tracks = albumTracks,
                                        onBack = { navigateBack() },
                                        onPlayAll = {
                                            if (albumTracks.isNotEmpty()) {
                                                viewModel.player.playList(albumTracks)
                                            }
                                        },
                                        onShuffleAll = {
                                            if (albumTracks.isNotEmpty()) {
                                                if (!viewModel.player.shuffleEnabled.value) {
                                                    viewModel.player.toggleShuffle()
                                                }
                                                viewModel.player.playList(albumTracks, albumTracks.indices.random())
                                            }
                                        },
                                        onPlayNextAlbum = {
                                            if (albumTracks.isNotEmpty()) {
                                                viewModel.player.playNext(albumTracks)
                                            }
                                        },
                                        onTrackClick = { index ->
                                            if (index in albumTracks.indices) {
                                                viewModel.player.playList(albumTracks, index)
                                            }
                                        },
                                        currentPlayingTitle = playingTitle,
                                        onPlayNextTrack = { songTitle ->
                                            val track = albumTracks.find { it.title == songTitle }
                                            if (track != null) {
                                                viewModel.player.playNext(listOf(track))
                                            }
                                        },
                                        onAddToQueueTrack = { songTitle ->
                                            val track = albumTracks.find { it.title == songTitle }
                                            if (track != null) {
                                                viewModel.player.addToQueue(listOf(track))
                                            }
                                        },
                                        onAddToPlaylistTrack = { track, playlistName ->
                                            viewModel.addItemToPlaylist(playlistName, track)
                                        },
                                        playlists = playlists,
                                        isPinnedTrack = { false },
                                        onPinTrack = {},
                                        onDownloadAlbum = {
                                            if (albumTracks.isNotEmpty()) {
                                                albumTracks.forEach { track ->
                                                    downloadAudioItem(context, track)
                                                }
                                                android.widget.Toast.makeText(context, "Started downloading album: $albumName", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onDownloadTrack = { track ->
                                            downloadAudioItem(context, track)
                                            android.widget.Toast.makeText(context, "Started downloading: ${track.title}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    if (isLoading) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = albumAccent)
                                        }
                                    }
                                }
                            }
                        }
                        is AppScreen.OnlineArtistDetail -> {
                            val browseId = targetScreen.browseId
                            val artistName = targetScreen.artistName
                            val artworkUrl = targetScreen.artworkUrl
                            val coroutineScope = rememberCoroutineScope()
                            
                             val cached = artistDetailsCache[browseId]
                             var topSongs by remember(browseId) { mutableStateOf<List<com.zune.player.data.OnlineSong>>(cached?.first ?: emptyList()) }
                             var albums by remember(browseId) { mutableStateOf<List<com.zune.player.data.OnlineAlbum>>(cached?.second ?: emptyList()) }
                             var singles by remember(browseId) { mutableStateOf<List<com.zune.player.data.OnlineAlbum>>(cached?.third ?: emptyList()) }
                             var isLoading by remember(browseId) { mutableStateOf(cached == null) }
                             LaunchedEffect(browseId) {
                                  if (cached != null) return@LaunchedEffect
                                  isLoading = true
                                 var artistTopSongs = emptyList<com.zune.player.data.OnlineSong>()
                                 var artistAlbums = emptyList<com.zune.player.data.OnlineAlbum>()
                                 var artistSingles = emptyList<com.zune.player.data.OnlineAlbum>()
                                 try {
                                     val artistRepo = org.koin.core.context.GlobalContext.get().get<com.maxrave.domain.repository.ArtistRepository>()
                                     val resource = artistRepo.getArtistData(browseId).firstOrNull { r ->
                                         r is com.maxrave.domain.utils.Resource.Success || r is com.maxrave.domain.utils.Resource.Error
                                     }
                                     if (resource is com.maxrave.domain.utils.Resource.Success) {
                                         val data = resource.data
                                         artistTopSongs = data?.songs?.results?.map { song ->
                                             com.zune.player.data.OnlineSong(
                                                 trackId = song.videoId.hashCode().toLong(),
                                                 title = song.title,
                                                 artist = song.artists?.firstOrNull()?.name ?: artistName,
                                                 album = song.album.name,
                                                 previewUrl = song.videoId,
                                                 artworkUrl = song.thumbnails.lastOrNull()?.url ?: artworkUrl,
                                                 durationMs = song.durationSeconds * 1000L
                                             )
                                         } ?: emptyList()
                                         val albumResults = data?.albums?.results?.map { album ->
                                             com.zune.player.data.OnlineAlbum(
                                                 browseId = album.browseId,
                                                 title = album.title,
                                                 artist = artistName,
                                                 year = album.year?.toString() ?: "",
                                                 artworkUrl = album.thumbnails.lastOrNull()?.url ?: ""
                                             )
                                         } ?: emptyList()
                                         val singleResults = data?.singles?.results?.map { single ->
                                             com.zune.player.data.OnlineAlbum(
                                                 browseId = single.browseId,
                                                 title = single.title,
                                                 artist = artistName,
                                                 year = single.year?.toString() ?: "",
                                                 artworkUrl = single.thumbnails.lastOrNull()?.url ?: ""
                                             )
                                         } ?: emptyList()
                                         artistAlbums = albumResults
                                         artistSingles = singleResults
                                     }
                                 } catch (e: Exception) {
                                     e.printStackTrace()
                                 }
                                 topSongs = artistTopSongs
                                 albums = artistAlbums
                                 singles = artistSingles
                                 artistDetailsCache[browseId] = Triple(artistTopSongs, artistAlbums, artistSingles)
                                 isLoading = false
                             }

                             val artistRepo = remember { org.koin.core.context.GlobalContext.get().get<com.maxrave.domain.repository.ArtistRepository>() }
                             var isFollowed by remember { mutableStateOf(false) }
                             LaunchedEffect(browseId) {
                                 artistRepo.getArtistById(browseId).collect { artistEntity ->
                                     isFollowed = (artistEntity?.followed == true)
                                 }
                             }

                             var artistExtractedColor by remember { mutableStateOf(ZuneAccent) }
                            LaunchedEffect(artworkUrl, selectedBg) {
                                val newColor = extractDominantColor(context, artworkUrl.ifEmpty { null })
                                artistExtractedColor = newColor ?: ZuneAccent
                            }
                            
                            val artistAccent by animateColorAsState(
                                targetValue = artistExtractedColor,
                                animationSpec = tween(durationMillis = 1000)
                            )
                            
                            val playlists by viewModel.playlists.collectAsState()

                            CompositionLocalProvider(com.zune.player.ui.theme.LocalZuneAccent provides artistAccent) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    com.zune.player.ui.screens.OnlineArtistDetailScreen(
                                        artistName = artistName,
                                        artworkUrl = artworkUrl,
                                        topSongs = topSongs,
                                        albums = albums,
                                        singles = singles,
                                        albumTracksCache = albumTracksCache,
                                        onBack = { navigateBack() },
                                        isFollowed = isFollowed,
                                        onFollowToggle = {
                                            val nextStatus = !isFollowed
                                            isFollowed = nextStatus
                                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                val artistEntity = com.maxrave.domain.data.entities.ArtistEntity(
                                                    channelId = browseId,
                                                    name = artistName,
                                                    thumbnails = artworkUrl,
                                                    followed = nextStatus
                                                )
                                                artistRepo.insertArtist(artistEntity)
                                                artistRepo.updateFollowedStatus(browseId, if (nextStatus) 1 else 0)
                                            }
                                        },
                                        onSongClick = { song ->
                                            val playItem = com.zune.player.data.AudioItem(
                                                id = -song.trackId,
                                                title = song.title,
                                                artist = song.artist,
                                                album = song.album,
                                                uri = android.net.Uri.parse("zune://online/${song.previewUrl}"),
                                                albumArtUri = if (song.artworkUrl.isNotEmpty()) android.net.Uri.parse(song.artworkUrl) else null,
                                                durationMs = song.durationMs
                                            )
                                            viewModel.player.playList(listOf(playItem))
                                        },
                                        onSongAddToQueue = { song ->
                                            val queueItem = com.zune.player.data.AudioItem(
                                                id = -song.trackId,
                                                title = song.title,
                                                artist = song.artist,
                                                album = song.album,
                                                uri = android.net.Uri.parse("zune://online/${song.previewUrl}"),
                                                albumArtUri = if (song.artworkUrl.isNotEmpty()) android.net.Uri.parse(song.artworkUrl) else null,
                                                durationMs = song.durationMs
                                            )
                                            viewModel.player.addToQueue(listOf(queueItem))
                                            android.widget.Toast.makeText(context, "Added \"${song.title}\" to queue", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                         onSongPlayNext = { song ->
                                             val playItem = com.zune.player.data.AudioItem(
                                                 id = -song.trackId,
                                                 title = song.title,
                                                 artist = song.artist,
                                                 album = song.album,
                                                 uri = android.net.Uri.parse("zune://online/${song.previewUrl}"),
                                                 albumArtUri = if (song.artworkUrl.isNotEmpty()) android.net.Uri.parse(song.artworkUrl) else null,
                                                 durationMs = song.durationMs
                                             )
                                             viewModel.player.playNext(listOf(playItem))
                                             android.widget.Toast.makeText(context, "Added \"" + song.title + "\" to play next", android.widget.Toast.LENGTH_SHORT).show()
                                         },
                                        onSongAddToPlaylist = { track, playlistName ->
                                            viewModel.addItemToPlaylist(playlistName, track)
                                        },
                                        onAlbumClick = { onlineAlbum ->
                                            navigateTo(
                                                AppScreen.OnlineAlbumDetail(
                                                    browseId = onlineAlbum.browseId,
                                                    albumName = onlineAlbum.title,
                                                    artistName = onlineAlbum.artist,
                                                    artworkUrl = onlineAlbum.artworkUrl
                                                )
                                            )
                                        },
                                        playlists = playlists,
                                        currentPlayingTitle = playingTitle,
                                        onSongDownload = { song ->
                                            val playItem = com.zune.player.data.AudioItem(
                                                id = -song.trackId,
                                                title = song.title,
                                                artist = song.artist,
                                                album = song.album,
                                                uri = android.net.Uri.parse("zune://online/${song.previewUrl}"),
                                                albumArtUri = if (song.artworkUrl.isNotEmpty()) android.net.Uri.parse(song.artworkUrl) else null,
                                                durationMs = song.durationMs
                                            )
                                            downloadAudioItem(context, playItem)
                                            android.widget.Toast.makeText(context, "Started downloading: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        onAlbumDownload = { album ->
                                            coroutineScope.launch {
                                                try {
                                                    val albumRepo = org.koin.core.context.GlobalContext.get().get<com.maxrave.domain.repository.AlbumRepository>()
                                                    val resource = albumRepo.getAlbumData(album.browseId).firstOrNull { r ->
                                                        r is com.maxrave.domain.utils.Resource.Success || r is com.maxrave.domain.utils.Resource.Error
                                                    }
                                                    if (resource is com.maxrave.domain.utils.Resource.Success) {
                                                        val tracks = resource.data?.tracks?.map { track ->
                                                            com.zune.player.data.AudioItem(
                                                                id = -track.videoId.hashCode().toLong(),
                                                                title = track.title,
                                                                artist = track.artists?.firstOrNull()?.name ?: album.artist,
                                                                album = track.album?.name ?: album.title,
                                                                uri = android.net.Uri.parse("zune://online/${track.videoId}"),
                                                                albumArtUri = track.thumbnails?.lastOrNull()?.url?.let { android.net.Uri.parse(it) } ?: if (album.artworkUrl.isNotEmpty()) android.net.Uri.parse(album.artworkUrl) else null,
                                                                durationMs = (track.durationSeconds ?: 0) * 1000L
                                                            )
                                                        }
                                                        if (!tracks.isNullOrEmpty()) {
                                                            tracks.forEach { track ->
                                                                downloadAudioItem(context, track)
                                                            }
                                                            android.widget.Toast.makeText(context, "Started downloading album: ${album.title}", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            android.widget.Toast.makeText(context, "No tracks found in album", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        android.widget.Toast.makeText(context, "Failed to load album tracks", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    android.widget.Toast.makeText(context, "Failed to download album: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                    if (isLoading) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = artistAccent)
                                        }
                                    }
                                }
                            }
                        }
                        is AppScreen.Photos -> {
                            Box(Modifier.systemBarsPadding()) {
                                PhotosScreen(
                                    isAeroTheme = selectedBg == R.drawable.bg_4,
                                    pinnedIds = pinned.map { it.first },
                                    initialPhotoId = targetScreen.initialPhotoId,
                                    onPin = { viewModel.pinSong(it) },
                                    onUnpin = { viewModel.unpinSong(it) },
                                    onBack = { navigateBack() }
                                )
                            }
                        }
                        is AppScreen.Videos -> {
                            Box(Modifier.systemBarsPadding()) {
                                VideosScreen(
                                    isAeroTheme = selectedBg == R.drawable.bg_4,
                                    pinnedIds = pinned.map { it.first },
                                    initialVideoId = targetScreen.initialVideoId,
                                    onPin = { viewModel.pinSong(it) },
                                    onUnpin = { viewModel.unpinSong(it) },
                                    onBack = { navigateBack() }
                                )
                            }
                        }
                        is AppScreen.Podcasts -> {
                            Box(Modifier.systemBarsPadding()) {
                                com.zune.player.ui.screens.PodcastsScreen(
                                    isAeroTheme = selectedBg == R.drawable.bg_4,
                                    player = viewModel.player,
                                    onBack = { navigateBack() }
                                )
                            }
                        }
                        is AppScreen.Apps -> {
                            Box(Modifier.systemBarsPadding()) {
                                AppsScreen(
                                    pinnedIds = pinned.map { it.first },
                                    onPin = { viewModel.pinSong(it) },
                                    onUnpin = { viewModel.unpinSong(it) },
                                    onBack = { navigateBack() }
                                )
                            }
                        }
                        is AppScreen.Personalize -> {
                            Box(Modifier.systemBarsPadding()) {
                                com.zune.player.ui.screens.PersonalizeScreen(
                                    onBack = { navigateBack() },
                                    isAeroTheme = selectedBg == R.drawable.bg_4,
                                    getScrollPosition = getScrollPosition,
                                    onScrollPositionChanged = onScrollPositionChanged
                                )
                            }
                        }
                    }
                }
            }
        }
    }

            AnimatedVisibility(
                visible = showGlobalQueue,
                enter = slideInVertically { it },
                exit  = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding()
            ) {
                val fullQueue by viewModel.player.queue.collectAsState()
                val shuffleEnabled by viewModel.player.shuffleEnabled.collectAsState()

                key(queueOpenCount) {
                    QueuePanel(
                        queue     = fullQueue,
                        currentId = currentAudio?.id,
                        accent    = animatedAccent,
                        shuffleEnabled = shuffleEnabled,
                        onToggleShuffle = { viewModel.player.toggleShuffle() },
                        onPlayAt  = { viewModel.player.playFromQueue(it) },
                        onRemove  = { viewModel.player.removeFromQueue(it) },
                        onMove    = { f, t -> viewModel.player.reorderQueue(f, t) },
                        onDismiss = { showGlobalQueue = false },
                        onSaveQueueAsPlaylist = { name -> viewModel.saveQueueAsPlaylist(name, fullQueue) }
                    )
                }
            }

            if (isScreensaverActive && currentScreen != AppScreen.NowPlaying) {
                ZuneHDScreensaver(
                    currentAudio = currentAudio,
                    onDismiss = {
                        isScreensaverActive = false
                        lastInteractionTime = System.currentTimeMillis()
                    }
                )
            }

            // Custom Zune Volume Overlay Panel (styled like classic Windows Phone / Windows 8 overlay)
            val volLevel by MainActivity.volumeLevel.collectAsState()
            val volTrigger by MainActivity.volumeTrigger.collectAsState()
            var showVolumePanel by remember { mutableStateOf(false) }
            var volumePanelInteractionTrigger by remember { mutableStateOf(0L) }

            LaunchedEffect(volTrigger, volumePanelInteractionTrigger) {
                if (volTrigger > 0L || volumePanelInteractionTrigger > 0L) {
                    showVolumePanel = true
                    delay(5000) // Increase time slightly since it has interactive controls
                    showVolumePanel = false
                }
            }

            AnimatedVisibility(
                visible = showVolumePanel,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
                modifier = Modifier.fillMaxSize()
            ) {
                val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
                val maxVol = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }
                val currentPos by viewModel.player.currentPosition.collectAsState()
                val duration by viewModel.player.duration.collectAsState()
                val prefs = remember(context) { context.getSharedPreferences("zune_prefs", Context.MODE_PRIVATE) }

                var batteryLevel by remember { mutableIntStateOf(-1) }
                DisposableEffect(context) {
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                            if (level != -1 && scale != -1) {
                                batteryLevel = (level.toFloat() / scale.toFloat() * 100f).toInt()
                            }
                        }
                    }
                    val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                    context.registerReceiver(receiver, filter)
                    onDispose {
                        context.unregisterReceiver(receiver)
                    }
                }

                var dragAccumulator by remember { mutableStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.92f))
                        .pointerInput(maxVol, volLevel) {
                            detectVerticalDragGestures(
                                onDragStart = { dragAccumulator = 0f },
                                onDragEnd = {},
                                onDragCancel = {}
                            ) { change, dragAmount ->
                                change.consume()
                                dragAccumulator += dragAmount
                                val threshold = 30f // Sensitivity threshold in pixels
                                if (Math.abs(dragAccumulator) >= threshold) {
                                    val steps = (dragAccumulator / threshold).toInt()
                                    val nextVal = (volLevel - steps).coerceIn(0, 30)
                                    if (nextVal != volLevel) {
                                        val currentStreamVol = ((nextVal.toFloat() / 30f) * maxVol.toFloat()).toInt().coerceIn(0, maxVol)
                                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, currentStreamVol, 0)
                                        MainActivity.volumeLevel.value = nextVal
                                        volumePanelInteractionTrigger = System.currentTimeMillis()
                                    }
                                    dragAccumulator %= threshold
                                }
                            }
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showVolumePanel = false
                        }
                ) {
                    // 1. Top status bar row (EXIT only)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(vertical = 16.dp, horizontal = 24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXIT",
                            style = TextStyle(
                                fontFamily = SegoeUiFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .metroClickable {
                                    showVolumePanel = false
                                }
                                .padding(8.dp)
                        )
                    }

                    // 2. Large Volume Display (Top Left)
                    Text(
                        text = volLevel.coerceIn(0, 30).toString(),
                        style = TextStyle(
                            fontFamily = SegoeUiLightFontFamily,
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Light
                        ),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = 70.dp, start = 24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                    )

                    // 3. Center of screen: Cross controller layout with bold custom canvas-drawn buttons
                    Box(
                        modifier = Modifier
                            .size(340.dp)
                            .align(Alignment.Center)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        contentAlignment = Alignment.Center
                    ) {
                        // Play/Pause circular button (CENTER)
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .border(4.dp, Color.White, shape = CircleShape)
                                .metroClickable {
                                    volumePanelInteractionTrigger = System.currentTimeMillis()
                                    if (prefs.getBoolean("haptic_feedback_enabled", true)) {
                                        window?.decorView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    viewModel.player.togglePlayPause()
                                }
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPlaying) {
                                // Bold thick Pause lines
                                Canvas(modifier = Modifier.size(52.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    val barWidth = w * 0.22f
                                    val barHeight = h * 0.64f
                                    val top = h * 0.18f
                                    
                                    // Left bar
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(w * 0.22f, top),
                                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                                    )
                                    // Right bar
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(w * 0.56f, top),
                                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                                    )
                                }
                            } else {
                                // Bold thick Play triangle
                                Canvas(modifier = Modifier.size(52.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    val path = Path().apply {
                                        moveTo(w * 0.28f, h * 0.18f)
                                        lineTo(w * 0.82f, h * 0.5f)
                                        lineTo(w * 0.28f, h * 0.82f)
                                        close()
                                    }
                                    drawPath(path, color = Color.White)
                                }
                            }
                        }

                        // Plus button (+) (TOP CENTER) - Redesigned to be thick canvas & same bounding size
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 2.dp)
                                .size(72.dp)
                                .metroClickable {
                                    volumePanelInteractionTrigger = System.currentTimeMillis()
                                    if (prefs.getBoolean("haptic_feedback_enabled", true)) {
                                        window?.decorView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    val nextVal = (volLevel + 1).coerceIn(0, 30)
                                    val currentStreamVol = ((nextVal.toFloat() / 30f) * maxVol.toFloat()).toInt().coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, currentStreamVol, 0)
                                    MainActivity.volumeLevel.value = nextVal
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(44.dp)) {
                                val thickness = 8.dp.toPx() // Thicker lines
                                val w = size.width
                                val h = size.height
                                // Horizontal bar
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(0f, (h - thickness) / 2f),
                                    size = androidx.compose.ui.geometry.Size(w, thickness)
                                )
                                // Vertical bar
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset((w - thickness) / 2f, 0f),
                                    size = androidx.compose.ui.geometry.Size(thickness, h)
                                )
                            }
                        }

                        // Minus button (—) (BOTTOM CENTER) - Redesigned to be thick canvas & same bounding size
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 2.dp) // Pushed closer to the bottom edge
                                .size(72.dp)
                                .metroClickable {
                                    volumePanelInteractionTrigger = System.currentTimeMillis()
                                    if (prefs.getBoolean("haptic_feedback_enabled", true)) {
                                        window?.decorView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    val nextVal = (volLevel - 1).coerceIn(0, 30)
                                    val currentStreamVol = ((nextVal.toFloat() / 30f) * maxVol.toFloat()).toInt().coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, currentStreamVol, 0)
                                    MainActivity.volumeLevel.value = nextVal
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(44.dp)) {
                                val thickness = 8.dp.toPx() // Thicker lines (same as Plus!)
                                val w = size.width
                                val h = size.height
                                // Horizontal bar
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(0f, (h - thickness) / 2f),
                                    size = androidx.compose.ui.geometry.Size(w, thickness)
                                )
                            }
                        }

                        // Previous track button (|<<) (CENTER START)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(80.dp)
                                .metroClickable {
                                    volumePanelInteractionTrigger = System.currentTimeMillis()
                                    if (prefs.getBoolean("haptic_feedback_enabled", true)) {
                                        window?.decorView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    viewModel.player.skipToPrevious()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(52.dp)) {
                                val w = size.width
                                val h = size.height
                                
                                // Left vertical bar
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(w * 0.05f, h * 0.15f),
                                    size = androidx.compose.ui.geometry.Size(w * 0.14f, h * 0.7f)
                                )
                                
                                // First triangle
                                val path1 = Path().apply {
                                    moveTo(w * 0.19f, h * 0.5f)
                                    lineTo(w * 0.55f, h * 0.15f)
                                    lineTo(w * 0.55f, h * 0.85f)
                                    close()
                                }
                                drawPath(path1, color = Color.White)
                                
                                // Second triangle
                                val path2 = Path().apply {
                                    moveTo(w * 0.52f, h * 0.5f)
                                    lineTo(w * 0.88f, h * 0.15f)
                                    lineTo(w * 0.88f, h * 0.85f)
                                    close()
                                }
                                drawPath(path2, color = Color.White)
                            }
                        }

                        // Next track button (>>|) (CENTER END)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(80.dp)
                                .metroClickable {
                                    volumePanelInteractionTrigger = System.currentTimeMillis()
                                    if (prefs.getBoolean("haptic_feedback_enabled", true)) {
                                        window?.decorView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    viewModel.player.skipToNext()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(52.dp)) {
                                val w = size.width
                                val h = size.height
                                
                                // First triangle
                                val path1 = Path().apply {
                                    moveTo(w * 0.48f, h * 0.5f)
                                    lineTo(w * 0.12f, h * 0.15f)
                                    lineTo(w * 0.12f, h * 0.85f)
                                    close()
                                }
                                drawPath(path1, color = Color.White)
                                
                                // Second triangle
                                val path2 = Path().apply {
                                    moveTo(w * 0.81f, h * 0.5f)
                                    lineTo(w * 0.45f, h * 0.15f)
                                    lineTo(w * 0.45f, h * 0.85f)
                                    close()
                                }
                                drawPath(path2, color = Color.White)
                                
                                // Right vertical bar
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(w * 0.81f, h * 0.15f),
                                    size = androidx.compose.ui.geometry.Size(w * 0.14f, h * 0.7f)
                                )
                            }
                        }
                    }

                    // 4. Bottom Left: Song Title and Artist Name Column (All Caps)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(bottom = 32.dp, start = 24.dp)
                            .fillMaxWidth(0.75f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Column(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showVolumePanel = false
                                showGlobalQueue = true
                            }
                        ) {
                            Text(
                                text = (currentAudio?.artist ?: "UNKNOWN ARTIST").uppercase(),
                                style = TextStyle(
                                    fontFamily = SegoeUiBoldFontFamily,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = (currentAudio?.title ?: "No Track").uppercase(),
                                style = TextStyle(
                                    fontFamily = SegoeUiFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        


                        val formatDurationSeek: (Long) -> String = { ms ->
                            val sec = (ms / 1000) % 60
                            val min = (ms / 1000) / 60
                            "%d:%02d".format(min, sec)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {}
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .pointerInput(duration) {
                                        detectTapGestures { offset ->
                                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                            viewModel.player.seekTo((fraction * duration).toLong())
                                            volumePanelInteractionTrigger = System.currentTimeMillis()
                                        }
                                    }
                                    .pointerInput(duration) {
                                        var dragAccumulator = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = { dragAccumulator = (currentPos.toFloat() / duration.toFloat()) * size.width },
                                            onDragEnd = {},
                                            onDragCancel = {}
                                        ) { change, dragAmount ->
                                            change.consume()
                                            dragAccumulator = (dragAccumulator + dragAmount).coerceIn(0f, size.width.toFloat())
                                            val fraction = dragAccumulator / size.width
                                            viewModel.player.seekTo((fraction * duration).toLong())
                                            volumePanelInteractionTrigger = System.currentTimeMillis()
                                        }
                                    }
                            ) {
                                val progressFraction = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .align(Alignment.Center)
                                        .background(Color.White.copy(alpha = 0.3f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progressFraction)
                                            .fillMaxHeight()
                                            .background(Color.White)
                                    )
                                }
                            }

                            Text(
                                text = "${formatDurationSeek(currentPos)} / ${formatDurationSeek(duration)}",
                                style = TextStyle(
                                    fontFamily = SegoeUiFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Global clock and battery status row (pushed to extreme top right, visible on all screens)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioDeviceIndicator()
                BatteryIndicator()
                SmallClock()
            }
        }
    }
}

@Composable




fun ZuneHDScreensaver(
    currentAudio: com.zune.player.data.AudioItem?,
    onDismiss: () -> Unit
) {
    val artist = (currentAudio?.artist ?: "UNKNOWN ARTIST").uppercase()
    val title = currentAudio?.title ?: "No Track"
    val album = (currentAudio?.album ?: "UNKNOWN ALBUM").uppercase()
    val accent = LocalZuneAccent.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            }
    ) {
        // Soft accent glow vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.08f), Color.Black),
                        center = Offset.Unspecified
                    )
                )
        )

        val infiniteTransition = rememberInfiniteTransition(label = "ScalePulse")
        val bgScale by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "BgScale"
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val W = maxWidth.value
            val H = maxHeight.value

            val bgX = remember { Animatable(W * 0.1f) }
            val bgY = remember { Animatable(H * 0.15f) }
            val fgX = remember { Animatable(W * 0.2f) }
            val fgY = remember { Animatable(H * 0.5f) }

            LaunchedEffect(W, H) {
                if (W > 0 && H > 0) {
                    val bgMinX = -200f
                    val bgMaxX = W - 150f
                    val bgMinY = 20f
                    val bgMaxY = H - 120f

                    val fgMinX = 10f
                    val fgMaxX = W - 260f
                    val fgMinY = 50f
                    val fgMaxY = H - 220f

                    launch {
                        while (true) {
                            val targetX = bgMinX + kotlin.random.Random.nextFloat() * (bgMaxX - bgMinX)
                            val duration = ((kotlin.math.abs(targetX - bgX.value) / 15f) * 1000).toInt().coerceAtLeast(3000)
                            bgX.animateTo(targetX, tween(duration, easing = LinearEasing))
                        }
                    }
                    launch {
                        while (true) {
                            val targetY = bgMinY + kotlin.random.Random.nextFloat() * (bgMaxY - bgMinY)
                            val duration = ((kotlin.math.abs(targetY - bgY.value) / 10f) * 1000).toInt().coerceAtLeast(3000)
                            bgY.animateTo(targetY, tween(duration, easing = LinearEasing))
                        }
                    }
                    launch {
                        while (true) {
                            val targetX = fgMinX + kotlin.random.Random.nextFloat() * (fgMaxX - fgMinX)
                            val duration = ((kotlin.math.abs(targetX - fgX.value) / 25f) * 1000).toInt().coerceAtLeast(3000)
                            fgX.animateTo(targetX, tween(duration, easing = LinearEasing))
                        }
                    }
                    launch {
                        while (true) {
                            val targetY = fgMinY + kotlin.random.Random.nextFloat() * (fgMaxY - fgMinY)
                            val duration = ((kotlin.math.abs(targetY - fgY.value) / 20f) * 1000).toInt().coerceAtLeast(3000)
                            fgY.animateTo(targetY, tween(duration, easing = LinearEasing))
                        }
                    }
                }
            }

            // Background huge bold Segoe UI text
            Text(
                text = artist,
                style = TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.zune.player.R.font.segoeuithibd)),
                    fontSize = 110.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                    letterSpacing = (-2).sp
                ),
                color = Color.White.copy(alpha = 0.08f),
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = bgX.value.dp.toPx()
                        translationY = bgY.value.dp.toPx()
                        scaleX = bgScale
                        scaleY = bgScale
                    }
            )

            // Foreground info block
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .graphicsLayer {
                        translationX = fgX.value.dp.toPx()
                        translationY = fgY.value.dp.toPx()
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = com.zune.player.ui.theme.SegoeUiBoldFontFamily,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp
                    ),
                    color = Color.White,
                    maxLines = 2
                )
                Text(
                    text = artist,
                    style = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.zune.player.R.font.segoeuithibd)),
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = accent,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = album,
                    style = TextStyle(
                        fontFamily = com.zune.player.ui.theme.SegoeUiLightFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 0.sp
                    ),
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }
        }
    }
}

private fun downloadAudioItem(context: android.content.Context, item: com.zune.player.data.AudioItem) {
    val videoId = if (item.uri.scheme == "zune" && item.uri.host == "online") {
        item.uri.lastPathSegment ?: ""
    } else {
        ""
    }
    if (videoId.isEmpty()) return

    val data = androidx.work.workDataOf(
        "trackId" to item.id,
        "title" to item.title,
        "artist" to item.artist,
        "album" to item.album,
        "previewUrl" to videoId,
        "artworkUrl" to (item.albumArtUri?.toString() ?: ""),
        "durationMs" to item.durationMs
    )
    val request = androidx.work.OneTimeWorkRequestBuilder<com.zune.player.data.DownloadWorker>()
        .setInputData(data)
        .addTag("download_song")
        .build()
    androidx.work.WorkManager.getInstance(context).enqueue(request)
}

private fun isForwardTransition(initial: AppScreen, target: AppScreen): Boolean {
    if (target is AppScreen.Home) return false
    if (initial is AppScreen.Home) return true
    
    if (initial is AppScreen.CategoryList && target is AppScreen.CategoryList) {
        val categories = listOf("playlists", "songs", "artists", "albums")
        val iIdx = categories.indexOf(initial.category.lowercase())
        val tIdx = categories.indexOf(target.category.lowercase())
        if (iIdx != -1 && tIdx != -1) {
            return tIdx > iIdx
        }
    }
    
    fun rank(screen: AppScreen): Int {
        return when (screen) {
            is AppScreen.Home -> 0
            is AppScreen.CategoryList -> 1
            is AppScreen.Search -> 1
            is AppScreen.PlaylistDetail -> 2
            is AppScreen.AlbumDetail -> 2
            is AppScreen.OnlineAlbumDetail -> 2
            is AppScreen.OnlineArtistDetail -> 2
            is AppScreen.Photos -> 2
            is AppScreen.Videos -> 2
            is AppScreen.Podcasts -> 2
            is AppScreen.NowPlaying -> 3
            is AppScreen.Personalize -> 2
            is AppScreen.Apps -> 2
        }
    }
    return rank(target) > rank(initial)
}

@Composable
fun ParallaxBackground(selectedBg: Int, horizontalScrollOffset: androidx.compose.runtime.FloatState) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    var customBgUriStr by remember { mutableStateOf(prefs.getString("bg_custom_uri", null)) }
    
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "bg_custom_uri") {
                customBgUriStr = prefs.getString("bg_custom_uri", null)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val accent = LocalZuneAccent.current
    
    if (selectedBg != 0) {
        val painter = if (selectedBg == -1 && !customBgUriStr.isNullOrEmpty()) {
            coil.compose.rememberAsyncImagePainter(model = android.net.Uri.parse(customBgUriStr))
        } else {
            painterResource(id = if (selectedBg > 0) selectedBg else R.drawable.bg_1)
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Dark tint and gradient over background images (not pure black)
    if (selectedBg != 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )
    }
}

@Composable
fun WindowsMediaCenterBackground(horizontalScrollOffset: androidx.compose.runtime.FloatState) {
    val infiniteTransition = rememberInfiniteTransition(label = "WMCBackground")
    
    val waveOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveOffset1"
    )
    
    val waveOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveOffset2"
    )

    val scrollVal = horizontalScrollOffset.floatValue

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val scrollOffsetPx = scrollVal * width * 0.15f // subtle parallax offset

        // 1. Base gradient: deep indigo/navy blue to cyan/teal
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    Color(0xFF00112A), // Extra deep navy
                    Color(0xFF002250), // Navy
                    Color(0xFF003D7C), // Medium WMC blue
                    Color(0xFF00112D)  // Back to extra deep navy
                ),
                start = Offset(-scrollOffsetPx, 0f),
                end = Offset(width - scrollOffsetPx, height)
            )
        )

        // Wave 1: Soft Cyan/Teal WMC wave
        val path1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(-50f, height * 0.45f)
            cubicTo(
                width * 0.25f + waveOffset1 - scrollOffsetPx, height * 0.3f,
                width * 0.75f - waveOffset1 - scrollOffsetPx, height * 0.8f,
                width + 100f, height * 0.55f + waveOffset1 * 0.5f
            )
            lineTo(width + 100f, height + 100f)
            lineTo(-50f, height + 100f)
            close()
        }
        drawPath(
            path = path1,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0x000077AA),
                    Color(0x220099DD),
                    Color(0x4400D2EE),
                    Color(0x0500112A)
                ),
                startY = height * 0.35f,
                endY = height
            )
        )

        // Wave 2: A second, higher lighter blue wave
        val path2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(-50f, height * 0.6f)
            cubicTo(
                width * 0.3f - waveOffset2 - scrollOffsetPx * 1.5f, height * 0.45f + waveOffset2 * 0.2f,
                width * 0.7f + waveOffset2 - scrollOffsetPx * 1.5f, height * 0.9f - waveOffset2 * 0.4f,
                width + 100f, height * 0.75f
            )
            lineTo(width + 100f, height + 100f)
            lineTo(-50f, height + 100f)
            close()
        }
        drawPath(
            path = path2,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color(0x0000AACC),
                    Color(0x2600C8EE),
                    Color(0x5500E5FF),
                    Color(0x0000112A)
                ),
                startY = height * 0.45f,
                endY = height
            )
        )
        
        // 3. Glowing cyan radial highlight overlay near the bottom-right (sweeping light)
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    Color(0x5500E5FF),
                    Color(0x220099DD),
                    Color.Transparent
                ),
                center = Offset(width * 0.85f - scrollOffsetPx, height * 0.85f),
                radius = width * 0.75f
            ),
            center = Offset(width * 0.85f - scrollOffsetPx, height * 0.85f),
            radius = width * 0.75f
        )
    }
}

private fun getAudioOutputDeviceName(context: android.content.Context): String {
    val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        val infoList = devices.filter { it.isSink }
        val types = infoList.map { it.type }
        if (types.contains(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) || 
            types.contains(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO)) {
            return "Bluetooth"
        }
        if (types.contains(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES) || 
            types.contains(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET) ||
            types.contains(android.media.AudioDeviceInfo.TYPE_USB_HEADSET)) {
            return "Headphones"
        }
        if (types.contains(android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)) {
            return "Speaker"
        }
        return "Internal Speaker"
    } else {
        @Suppress("DEPRECATION")
        if (audioManager.isBluetoothA2dpOn) return "Bluetooth"
        @Suppress("DEPRECATION")
        if (audioManager.isWiredHeadsetOn) return "Headphones"
        return "Speaker"
    }
}

@Composable
fun SmallClock(modifier: Modifier = Modifier) {
    var timeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = java.text.SimpleDateFormat("h:mm", java.util.Locale.getDefault())
        while (true) {
            timeText = sdf.format(java.util.Date()).lowercase()
            kotlinx.coroutines.delay(1000)
        }
    }
    Text(
        text = timeText,
        style = TextStyle(
            fontFamily = SegoeUiFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        ),
        color = Color.White.copy(alpha = 0.85f),
        modifier = modifier
    )
}

@Composable
fun BatteryIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var isCharging by remember { mutableStateOf(false) }
    
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == android.os.BatteryManager.BATTERY_STATUS_FULL
                if (level != -1 && scale != -1) {
                    batteryLevel = (level.toFloat() / scale.toFloat() * 100f).toInt()
                }
            }
        }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    
    if (batteryLevel != -1) {
        val batteryIcon = remember<androidx.compose.ui.graphics.vector.ImageVector>(batteryLevel, isCharging) {
            if (isCharging) {
                FluentIcons.Regular.BatteryCharge
            } else {
                val step = (batteryLevel / 10).coerceIn(0, 10)
                when (step) {
                    0 -> FluentIcons.Regular.Battery0
                    1 -> FluentIcons.Regular.Battery1
                    2 -> FluentIcons.Regular.Battery2
                    3 -> FluentIcons.Regular.Battery3
                    4 -> FluentIcons.Regular.Battery4
                    5 -> FluentIcons.Regular.Battery5
                    6 -> FluentIcons.Regular.Battery6
                    7 -> FluentIcons.Regular.Battery7
                    8 -> FluentIcons.Regular.Battery8
                    9 -> FluentIcons.Regular.Battery9
                    10 -> FluentIcons.Regular.Battery10
                    else -> FluentIcons.Regular.Battery10
                }
            }
        }
        androidx.compose.material.Icon(
            imageVector = batteryIcon,
            contentDescription = "battery $batteryLevel%",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = modifier.size(16.dp)
        )
    }
}

@Composable
fun AudioDeviceIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isBluetoothActive by remember { mutableStateOf(false) }
    var isHeadphoneActive by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        
        val updateDevices = {
            if (audioManager != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                    val types = devices.filter { it.isSink }.map { it.type }
                    isBluetoothActive = types.contains(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) ||
                                       types.contains(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO) ||
                                       types.contains(android.media.AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                                       types.contains(android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER)
                    isHeadphoneActive = types.contains(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES) ||
                                       types.contains(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET) ||
                                       types.contains(android.media.AudioDeviceInfo.TYPE_USB_HEADSET)
                } else {
                    @Suppress("DEPRECATION")
                    isBluetoothActive = audioManager.isBluetoothA2dpOn
                    @Suppress("DEPRECATION")
                    isHeadphoneActive = audioManager.isWiredHeadsetOn
                }
            }
        }

        updateDevices()

        val callback = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    updateDevices()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    updateDevices()
                }
            }
        } else null

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && callback != null) {
            audioManager?.registerAudioDeviceCallback(callback, null)
        }

        onDispose {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && callback != null) {
                audioManager?.unregisterAudioDeviceCallback(callback)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        if (isBluetoothActive) {
            androidx.compose.material.Icon(
                imageVector = FluentIcons.Regular.Bluetooth,
                contentDescription = "Bluetooth",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }
        if (isHeadphoneActive) {
            androidx.compose.material.Icon(
                imageVector = FluentIcons.Regular.Headphones,
                contentDescription = "Headphones",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun getPlaybackHistory(prefs: android.content.SharedPreferences): List<com.zune.player.data.AudioItem> {
    val jsonStr = prefs.getString("playback_history_json", null) ?: return emptyList()
    try {
        val jsonArray = org.json.JSONArray(jsonStr)
        val list = mutableListOf<com.zune.player.data.AudioItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val albumArtUriStr = obj.optString("albumArtUri", "")
            list.add(
                com.zune.player.data.AudioItem(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    album = obj.getString("album"),
                    uri = android.net.Uri.parse(obj.getString("uri")),
                    albumArtUri = if (albumArtUriStr.isNotEmpty()) android.net.Uri.parse(albumArtUriStr) else null,
                    durationMs = obj.optLong("durationMs", 0L)
                )
            )
        }
        return list
    } catch (e: Exception) {
        return emptyList()
    }
}

fun addToPlaybackHistory(prefs: android.content.SharedPreferences, item: com.zune.player.data.AudioItem) {
    if (!prefs.getBoolean("history_enabled", true)) {
        prefs.edit().remove("playback_history_json").apply()
        return
    }
    val currentHistory = getPlaybackHistory(prefs).toMutableList()
    currentHistory.removeAll { it.title.lowercase() == item.title.lowercase() && it.artist.lowercase() == item.artist.lowercase() }
    currentHistory.add(0, item)
    val limitedHistory = currentHistory.take(12)
    try {
        val jsonArray = org.json.JSONArray()
        for (hist in limitedHistory) {
            val obj = org.json.JSONObject()
            obj.put("id", hist.id)
            obj.put("title", hist.title)
            obj.put("artist", hist.artist)
            obj.put("album", hist.album)
            obj.put("uri", hist.uri.toString())
            obj.put("albumArtUri", hist.albumArtUri?.toString() ?: "")
            obj.put("durationMs", hist.durationMs)
            jsonArray.put(obj)
        }
        prefs.edit().putString("playback_history_json", jsonArray.toString()).apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

