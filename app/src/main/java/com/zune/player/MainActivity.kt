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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.zune.player.ui.theme.LocalZuneAccent
import androidx.compose.material.Text
import coil.imageLoader
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    
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
    var selectedBg by remember { mutableStateOf(prefs.getInt("bg_selection", 0)) }

    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "bg_selection") {
                selectedBg = sharedPreferences.getInt("bg_selection", 0)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var extractedColor by remember { mutableStateOf(ZuneAccent) }
    
    LaunchedEffect(currentAudio?.albumArtUri, selectedBg) {
        if (selectedBg == R.drawable.bg_4) {
            extractedColor = AeroBlueOrbAccentColor
        } else {
            val newColor = extractDominantColor(context, currentAudio?.albumArtUri?.toString())
            extractedColor = newColor ?: ZuneAccent
        }
    }

    val animatedAccent by animateColorAsState(
        targetValue = extractedColor,
        animationSpec = tween(durationMillis = 1000)
    )

    val horizontalScrollOffset = remember { mutableFloatStateOf(0f) }

    ZuneTheme(dynamicAccent = animatedAccent) {
        var backStack by remember { mutableStateOf(listOf<AppScreen>(AppScreen.Home())) }
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
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                val playingTitle = currentAudio?.title
                
                val isForward = remember(previousScreen, currentScreen) {
                    isForwardTransition(previousScreen ?: AppScreen.Home(1), currentScreen)
                }
                
                val progress by transition.animateFloat(
                    transitionSpec = {
                        tween(
                            durationMillis = 500,
                            easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
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
                            
                            if (!isNowPlayingTransition && !isCategoryTransition) {
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
                                    initialPage = targetScreen.initialPage,
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
                                    onScrollPositionChanged = onScrollPositionChanged
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
                                    onScrollPositionChanged = onScrollPositionChanged
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
                                    onTrackClick = { index ->
                                        viewModel.player.playList(playlistTracks, index)
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
                            LaunchedEffect(albumArtUri, selectedBg) {
                                if (selectedBg == R.drawable.bg_4) {
                                    albumExtractedColor = AeroBlueOrbAccentColor
                                } else {
                                    val newColor = extractDominantColor(context, albumArtUri?.toString())
                                    albumExtractedColor = newColor ?: ZuneAccent
                                }
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
                                if (selectedBg == R.drawable.bg_4) {
                                    albumExtractedColor = AeroBlueOrbAccentColor
                                } else {
                                    val newColor = extractDominantColor(context, artworkUrl.ifEmpty { null })
                                    albumExtractedColor = newColor ?: ZuneAccent
                                }
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
                                                viewModel.player.playList(albumTracks.shuffled())
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
                            
                            var topSongs by remember { mutableStateOf<List<com.zune.player.data.OnlineSong>>(emptyList()) }
                            var albums by remember { mutableStateOf<List<com.zune.player.data.OnlineAlbum>>(emptyList()) }
                            var isLoading by remember { mutableStateOf(true) }
                            LaunchedEffect(browseId) {
                                 isLoading = true
                                 var artistTopSongs = emptyList<com.zune.player.data.OnlineSong>()
                                 var artistAlbums = emptyList<com.zune.player.data.OnlineAlbum>()
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
                                                 year = album.year,
                                                 artworkUrl = album.thumbnails.lastOrNull()?.url ?: ""
                                             )
                                         } ?: emptyList()
                                         val singleResults = data?.singles?.results?.map { single ->
                                             com.zune.player.data.OnlineAlbum(
                                                 browseId = single.browseId,
                                                 title = single.title,
                                                 artist = artistName,
                                                 year = single.year,
                                                 artworkUrl = single.thumbnails.lastOrNull()?.url ?: ""
                                             )
                                         } ?: emptyList()
                                         artistAlbums = albumResults + singleResults
                                     }
                                 } catch (e: Exception) {
                                     e.printStackTrace()
                                 }
                                 topSongs = artistTopSongs
                                 albums = artistAlbums
                                 isLoading = false
                             }

                            var artistExtractedColor by remember { mutableStateOf(ZuneAccent) }
                            LaunchedEffect(artworkUrl, selectedBg) {
                                if (selectedBg == R.drawable.bg_4) {
                                    artistExtractedColor = AeroBlueOrbAccentColor
                                } else {
                                    val newColor = extractDominantColor(context, artworkUrl.ifEmpty { null })
                                    artistExtractedColor = newColor ?: ZuneAccent
                                }
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
                                        onBack = { navigateBack() },
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
                val currentIndex = fullQueue.indexOfFirst { it.id == currentAudio?.id }.coerceAtLeast(0)
                val displayQueue = fullQueue.drop(currentIndex)

                QueuePanel(
                    queue     = displayQueue,
                    currentId = currentAudio?.id,
                    accent    = animatedAccent,
                    onPlayAt  = { viewModel.player.playFromQueue(it + currentIndex) },
                    onRemove  = { viewModel.player.removeFromQueue(it + currentIndex) },
                    onMove    = { f, t -> viewModel.player.reorderQueue(f + currentIndex, t + currentIndex) },
                    onDismiss = { showGlobalQueue = false }
                )
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
        }
    }
    return rank(target) > rank(initial)
}

@Composable
fun ParallaxBackground(selectedBg: Int, horizontalScrollOffset: androidx.compose.runtime.FloatState) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    val customBgUriStr = remember(selectedBg) { prefs.getString("bg_custom_uri", null) }
    
    if (selectedBg != 0) {
        val smoothedScrollOffsetState = animateFloatAsState(
            targetValue = horizontalScrollOffset.floatValue,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "ParallaxSmoothing"
        )
        val painter = if (selectedBg == -1 && !customBgUriStr.isNullOrEmpty()) {
            coil.compose.rememberAsyncImagePainter(model = android.net.Uri.parse(customBgUriStr))
        } else {
            painterResource(id = if (selectedBg > 0) selectedBg else R.drawable.bg_1)
        }
        val parallaxFactor = 0.3f
        
        // Offload translation to GPU via graphicsLayer instead of Canvas redraws
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        val intrinsicSize = painter.intrinsicSize
                        val imgWidthVal = if (intrinsicSize.width > 0f) intrinsicSize.width else 1080f
                        val imgHeightVal = if (intrinsicSize.height > 0f) intrinsicSize.height else 1920f
                        
                        val heightScale = constraints.maxHeight.toFloat() / imgHeightVal
                        val widthScale = constraints.maxWidth.toFloat() / imgWidthVal
                        val scale = maxOf(heightScale, widthScale)
                        
                        val placeableWidth = (imgWidthVal * scale).toInt()
                        val placeableHeight = (imgHeightVal * scale).toInt()
                        
                        val placeable = measurable.measure(
                            Constraints.fixed(placeableWidth, placeableHeight)
                        )
                        
                        layout(placeableWidth, placeableHeight) {
                            placeable.place(0, 0)
                        }
                    }
                    .graphicsLayer {
                        val scrollOffset = smoothedScrollOffsetState.value * parallaxFactor
                        val imgWidth = size.width
                        val xOffset = (-scrollOffset * 400f) % imgWidth
                        translationX = xOffset
                    }
            )
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        val intrinsicSize = painter.intrinsicSize
                        val imgWidthVal = if (intrinsicSize.width > 0f) intrinsicSize.width else 1080f
                        val imgHeightVal = if (intrinsicSize.height > 0f) intrinsicSize.height else 1920f
                        
                        val heightScale = constraints.maxHeight.toFloat() / imgHeightVal
                        val widthScale = constraints.maxWidth.toFloat() / imgWidthVal
                        val scale = maxOf(heightScale, widthScale)
                        
                        val placeableWidth = (imgWidthVal * scale).toInt()
                        val placeableHeight = (imgHeightVal * scale).toInt()
                        
                        val placeable = measurable.measure(
                            Constraints.fixed(placeableWidth, placeableHeight)
                        )
                        
                        layout(placeableWidth, placeableHeight) {
                            placeable.place(0, 0)
                        }
                    }
                    .graphicsLayer {
                        val scrollOffset = smoothedScrollOffsetState.value * parallaxFactor
                        val imgWidth = size.width
                        val xOffset = (-scrollOffset * 400f) % imgWidth
                        translationX = if (xOffset > 0) xOffset - imgWidth else xOffset + imgWidth
                    }
            )
        }
    }

    // Dark tint over background images (not aero, not pure black)
    if (selectedBg != 0 && selectedBg != R.drawable.bg_4) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
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
