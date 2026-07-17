package com.zune.player.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.graphics.Bitmap
import android.content.ContentValues
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import com.zune.player.ui.theme.ZuneIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.zune.player.ui.components.PivotLayout
import com.zune.player.ui.components.metroClickable
import com.zune.player.ui.components.metroTilt
import com.zune.player.ui.components.paperTexture
import com.zune.player.LocalSharedTransitionScope
import com.zune.player.LocalAnimatedVisibilityScope
import com.zune.player.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// Dynamic high-fidelity photo data class representing local or mock photos
data class PhotoItem(
    val id: Long,
    val uri: Uri?,
    val dateTaken: Long,
    val albumName: String,
    val title: String,
    val gradientColors: List<Color> = emptyList(),
    var isFavorite: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(
    isAeroTheme: Boolean = false,
    pinnedIds: List<Long> = emptyList(),
    initialPhotoId: Long? = null,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    onPin: (Long) -> Unit = {},
    onUnpin: (Long) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var longPressedPhoto by remember { mutableStateOf<PhotoItem?>(null) }
    var photoToShowDetails by remember { mutableStateOf<PhotoItem?>(null) }

    val permissionString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
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

    // Grid state and photos state
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load local and mock photos
    LaunchedEffect(hasPermission, useSamplesFallback, reloadTrigger) {
        isLoading = true
        val loadedList = mutableListOf<PhotoItem>()

        if (hasPermission && !useSamplesFallback) {
            withContext(Dispatchers.IO) {
                try {
                    val resolver = context.contentResolver
                    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val projection = arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_TAKEN,
                        MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                    )
                    resolver.query(uri, projection, null, null, null)?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                        val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                        val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn) ?: "photo_$id"
                            var date = cursor.getLong(dateColumn)
                            val dateAdded = cursor.getLong(dateAddedColumn)
                            if (date <= 0L) {
                                date = dateAdded * 1000L
                            }
                            val album = cursor.getString(bucketColumn) ?: "camera roll"
                            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                            loadedList.add(
                                PhotoItem(
                                    id = id,
                                    uri = contentUri,
                                    dateTaken = date,
                                    albumName = album.lowercase(),
                                    title = name
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                loadedList.sortByDescending { it.dateTaken }
            }
        }

        if (loadedList.isEmpty()) {
            loadedList.addAll(generateMockPhotos())
        }

        photos = loadedList
        isLoading = false
    }

    // Detail light-box state
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }

    // Jump selector modal state
    var showJumpSelector by remember { mutableStateOf(false) }

    BackHandler(enabled = showJumpSelector || selectedPhotoIndex != null) {
        if (showJumpSelector) {
            showJumpSelector = false
        } else if (selectedPhotoIndex != null) {
            selectedPhotoIndex = null
        }
    }

    // Search query state
    var searchQuery by remember { mutableStateOf("") }

    // Global filtered photos state (incorporating visual mock deletes)
    val currentPhotosFiltered = remember(photos, deletedMockIds) {
        photos.filterNot { it.id in deletedMockIds }
    }

    // Grouped photos mapping for Month Jump List
    val groupedPhotos = remember(currentPhotosFiltered) {
        val groups = mutableMapOf<String, MutableList<PhotoItem>>()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        currentPhotosFiltered.forEach { p ->
            val monthYear = sdf.format(Date(p.dateTaken))
            groups.getOrPut(monthYear) { mutableListOf() }.add(p)
        }
        // Keep order (since photos are already sorted newest first)
        groups.entries.map { it.key to it.value.toList() }
    }

    // A flat list of items (headers and photo sub-lists) for our lazy grid
    val flatGridItems = remember(groupedPhotos) {
        val list = mutableListOf<Any>()
        groupedPhotos.forEach { (monthStr, photoList) ->
            list.add(monthStr) // Header item
            list.addAll(photoList) // Grid items
        }
        list
    }

    val lazyGridState = rememberLazyGridState()

    // Deep-linked navigation handler
    LaunchedEffect(currentPhotosFiltered, initialPhotoId) {
        if (initialPhotoId != null && currentPhotosFiltered.isNotEmpty()) {
            val idx = currentPhotosFiltered.indexOfFirst { it.id == initialPhotoId }
            if (idx != -1) {
                selectedPhotoIndex = idx
            }
        }
    }

    val currentViewPhotos = currentPhotosFiltered

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        if (!hasPermission && !useSamplesFallback) {
            PhotosPermissionPrompt(
                onAllow = { permissionLauncher.launch(permissionString) },
                onUseSamples = { useSamplesFallback = true },
                onBack = onBack
            )
        } else {
            // Main Photos Hub Pivot
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
                                    rememberSharedContentState(key = "header_pictures"),
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
                            text = "pictures",
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
                    val pages = listOf("all", "albums", "search")
                    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
                    LaunchedEffect(pagerState.currentPage) {
                        onPageChanged(pagerState.currentPage)
                    }
                    val tabWidths = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

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
                            pages.forEachIndexed { index, title ->
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

                    // HorizontalPager
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { pageIndex ->
                        when (pageIndex) {
                            0 -> {
                                // "all" page - dense 4-column date grid
                                if (isLoading) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("loading photos...", color = ZuneTextSecondary, style = ZuneTypography.body1)
                                    }
                                } else if (photos.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("no photos found.", color = ZuneTextSecondary, style = ZuneTypography.body1)
                                    }
                                } else {
                                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                    val defaultColumns = if (isLandscape) 7 else 4
                                    var columns by remember(isLandscape) { mutableIntStateOf(defaultColumns) }
                                    var zoomScale by remember { mutableStateOf(1f) }

                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(columns),
                                        state = lazyGridState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 0.dp)
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
                                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        flatGridItems.forEachIndexed { globalIndex, item ->
                                            if (item is String) {
                                                // Header month item (span entire columns)
                                                item(span = { GridItemSpan(columns) }, key = "header_$item") {
                                                    val accent = LocalZuneAccent.current
                                                    val headerColor = if (accent == Color.White) Color(0xFFDCDCDC) else accent
                                                    Text(
                                                         text = item.uppercase(),
                                                         style = ZuneTypography.h2.copy(
                                                             fontFamily = SegoeUiLightFontFamily,
                                                             fontSize = 28.sp,
                                                             color = headerColor
                                                         ),
                                                         modifier = Modifier
                                                             .fillMaxWidth()
                                                             .clickable { showJumpSelector = true }
                                                             .padding(vertical = 16.dp)
                                                     )
                                                }
                                            } else if (item is PhotoItem) {
                                                // Photo item in grid
                                                val photoIndex = currentViewPhotos.indexOf(item)
                                                item(key = "photo_${item.id}_$globalIndex") {
                                                    PhotoGridCard(
                                                        modifier = Modifier.animateItem(),
                                                        photo = item,
                                                        isAeroTheme = isAeroTheme,
                                                        onClick = {
                                                            if (photoIndex != -1) {
                                                                selectedPhotoIndex = photoIndex
                                                            }
                                                        },
                                                        onLongClick = {
                                                            longPressedPhoto = item
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // "albums" page - folders (Camera Roll, Saved Pictures, Screenshots)
                                val albums = remember(photos) {
                                    val map = photos.groupBy { it.albumName }
                                    map.entries.map { it.key to it.value }
                                }

                                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                val columns = if (isLandscape) 4 else 2

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(columns),
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 0.dp),
                                    contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    // Folders grid
                                    itemsIndexed(albums) { index, (albumName, albumPhotos) ->
                                        AlbumFolderCard(
                                            albumName = albumName,
                                            photos = albumPhotos,
                                            isAeroTheme = isAeroTheme,
                                            onClick = {
                                                 onAlbumClick(albumName)
                                            }
                                        )
                                    }

                                    // Online albums divider and section
                                    item(span = { GridItemSpan(columns) }) {
                                        Column(modifier = Modifier.padding(top = 24.dp)) {
                                            Text(
                                                text = "online",
                                                style = ZuneTypography.h2.copy(
                                                    fontFamily = SegoeUiLightFontFamily,
                                                    fontSize = 24.sp,
                                                    color = ZuneTextSecondary
                                                ),
                                                modifier = Modifier.padding(bottom = 12.dp)
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // Mock SkyDrive / OneDrive
                                                OnlineAlbumTile(
                                                    name = "skydrive",
                                                    icon = ZuneIcons.Cloud,
                                                    tint = Color(0xFF0078D4)
                                                )
                                                // Mock Facebook
                                                OnlineAlbumTile(
                                                    name = "facebook",
                                                    icon = ZuneIcons.ThumbUp,
                                                    tint = Color(0xFF1877F2)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(2.dp, Color.White.copy(alpha = 0.6f))
                                            .background(Color.Black)
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "type to search pictures...",
                                                style = ZuneTypography.body1,
                                                color = ZuneTextSecondary
                                            )
                                        }
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            textStyle = ZuneTypography.body1.copy(color = Color.White),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    val searchResults = remember(searchQuery, currentPhotosFiltered) {
                                        if (searchQuery.isBlank()) emptyList()
                                        else currentPhotosFiltered.filter {
                                            it.title.contains(searchQuery, ignoreCase = true) ||
                                                it.albumName.contains(searchQuery, ignoreCase = true)
                                        }
                                    }

                                    if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("no results found", color = ZuneTextSecondary, style = ZuneTypography.body2)
                                        }
                                    } else {
                                        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                                        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                        val columns = if (isLandscape) 6 else 4
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(columns),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            contentPadding = PaddingValues(bottom = 96.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            itemsIndexed(searchResults) { _, photo ->
                                                val globalIdx = currentPhotosFiltered.indexOf(photo)
                                                PhotoGridCard(
                                                    photo = photo,
                                                    isAeroTheme = isAeroTheme,
                                                    onClick = {
                                                        if (globalIdx != -1) {
                                                            selectedPhotoIndex = globalIdx
                                                        }
                                                    },
                                                    onLongClick = {
                                                        longPressedPhoto = photo
                                                    },
                                                    modifier = Modifier.animateItem()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Circular slide-bar app buttons at the bottom (Reference Image 0 style)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Camera Circular Action
                        CircularActionButton(
                            icon = ZuneIcons.CameraAlt,
                            onClick = {
                                try {
                                    val cameraIntent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                                    context.startActivity(cameraIntent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "No camera application found.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentDescription = "camera"
                        )
                        // Slideshow Circular Action
                        CircularActionButton(
                            icon = ZuneIcons.Play,
                            onClick = {
                                // Start custom slideshow from photo 0
                                if (photos.isNotEmpty()) {
                                    selectedPhotoIndex = 0
                                }
                            },
                            contentDescription = "slideshow"
                        )
                    }
                }

        // 4. Full-screen Lightbox image viewer overlay
        val showLightbox = selectedPhotoIndex != null
        var lastNonNullIndex by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(selectedPhotoIndex) {
            if (selectedPhotoIndex != null) {
                lastNonNullIndex = selectedPhotoIndex
            }
        }

        AnimatedVisibility(
            visible = showLightbox,
            enter = fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.85f, animationSpec = tween(350, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                   scaleOut(targetScale = 0.85f, animationSpec = tween(300, easing = FastOutSlowInEasing))
        ) {
            val index = lastNonNullIndex
            if (index != null && index in currentViewPhotos.indices) {
                FullscreenLightbox(
                    photos = currentViewPhotos,
                    initialIndex = index,
                    pinnedIds = pinnedIds,
                    onPin = onPin,
                    onUnpin = onUnpin,
                    onDismiss = { selectedPhotoIndex = null },
                    onDeletePhoto = { idx ->
                        val targetPhoto = currentViewPhotos[idx]
                        if (targetPhoto.uri != null) {
                            pendingDeleteUri = targetPhoto.uri
                            deleteMediaStoreUri(
                                context = context,
                                uri = targetPhoto.uri,
                                onDeleteCompleted = {
                                    reloadTrigger++
                                    selectedPhotoIndex = null
                                },
                                onLauncherNeeded = { intentSender: android.content.IntentSender ->
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
                            deletedMockIds = deletedMockIds + targetPhoto.id
                            selectedPhotoIndex = null
                        }
                    },
                    onSaveEditedCopy = { editedBmp, title ->
                        coroutineScope.launch {
                            val newUri = saveBitmapToMediaStore(context, editedBmp, title)
                            if (newUri != null) {
                                android.widget.Toast.makeText(context, "Saved edited copy!", android.widget.Toast.LENGTH_SHORT).show()
                                reloadTrigger++
                            } else {
                                val mockId = System.currentTimeMillis()
                                val mockPhoto = PhotoItem(
                                    id = mockId,
                                    uri = null,
                                    dateTaken = System.currentTimeMillis(),
                                    albumName = "edited",
                                    title = "copy_$title",
                                    gradientColors = listOf(Color(0xFFE51400), Color(0xFFF09609))
                                )
                                val newList = photos.toMutableList()
                                newList.add(0, mockPhoto)
                                photos = newList
                                android.widget.Toast.makeText(context, "Saved edited copy (simulated)", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            selectedPhotoIndex = null
                        }
                    }
                )
            }
        }

        // 5. Fullscreen Month Jump Selector Dialog (Reference Image 1)
        AnimatedVisibility(
            visible = showJumpSelector,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.9f, animationSpec = tween(300))
        ) {
            MonthJumpListSelector(
                months = groupedPhotos.map { it.first },
                onMonthSelected = { selectedMonth ->
                    showJumpSelector = false
                    // Find flat index of month header
                    val targetIndex = flatGridItems.indexOf(selectedMonth)
                    if (targetIndex != -1) {
                        coroutineScope.launch {
                            lazyGridState.animateScrollToItem(targetIndex)
                        }
                    }
                },
                onDismiss = { showJumpSelector = false }
            )
        }

        // 6. Long Press Drop-Up Context Menu
        val hasLongPressedPhoto = longPressedPhoto != null
        var lastNonNullPhoto by remember { mutableStateOf<PhotoItem?>(null) }
        LaunchedEffect(longPressedPhoto) {
            if (longPressedPhoto != null) {
                lastNonNullPhoto = longPressedPhoto
            }
        }

        AnimatedVisibility(
            visible = hasLongPressedPhoto,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { longPressedPhoto = null }
            )
        }

        AnimatedVisibility(
            visible = hasLongPressedPhoto,
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
            val photo = lastNonNullPhoto
            if (photo != null) {
                val targetId = remember(photo.id) { photo.id or 0x1000000000000000L }
                val isPinned = remember(targetId, pinnedIds) {
                    pinnedIds.contains(targetId)
                }

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
                        text = photo.title.lowercase(),
                        style = ZuneTypography.body2.copy(fontSize = 14.sp),
                        color = ZuneTextSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    DropUpMenuItem(text = if (isPinned) "unpin from start" else "pin to start") {
                        longPressedPhoto = null
                        if (isPinned) {
                            onUnpin(targetId)
                        } else {
                            onPin(targetId)
                        }
                        android.widget.Toast.makeText(context, if (isPinned) "unpinned" else "pinned", android.widget.Toast.LENGTH_SHORT).show()
                    }

                    DropUpMenuItem(text = "use as background") {
                        longPressedPhoto = null
                        val prefs = context.getSharedPreferences("zune_prefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putInt("bg_selection", -1)
                            .putString("bg_custom_uri", photo.uri?.toString() ?: "")
                            .apply()
                        android.widget.Toast.makeText(context, "background updated", android.widget.Toast.LENGTH_SHORT).show()
                    }

                    DropUpMenuItem(text = "view details") {
                        longPressedPhoto = null
                        photoToShowDetails = photo
                    }

                    DropUpMenuItem(text = "delete") {
                        longPressedPhoto = null
                        if (photo.uri != null) {
                            pendingDeleteUri = photo.uri
                            deleteMediaStoreUri(
                                context = context,
                                uri = photo.uri,
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
                            deletedMockIds = deletedMockIds + photo.id
                        }
                    }
                }
            }
        }

        // 7. Context Details Dialog
        if (photoToShowDetails != null) {
            val detailPhoto = photoToShowDetails!!
            val metadata = remember(detailPhoto) { queryFileMetadata(context, detailPhoto.uri) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { photoToShowDetails = null },
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
                        text = "picture details",
                        style = ZuneTypography.h2.copy(
                            fontFamily = SegoeUiLightFontFamily,
                            fontSize = 24.sp,
                            color = ZuneAccent.lightenForText()
                        )
                    )

                    val sdf = java.text.SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", java.util.Locale.US)
                    val dateStr = sdf.format(java.util.Date(detailPhoto.dateTaken))

                    DetailRow(label = "name", value = detailPhoto.title)
                    DetailRow(label = "date taken", value = dateStr)
                    DetailRow(label = "album", value = detailPhoto.albumName)
                    DetailRow(label = "resolution", value = if (metadata.width > 0) "${metadata.width} x ${metadata.height}" else "1080 x 1080 (simulated)")
                    DetailRow(label = "file size", value = metadata.size)
                    DetailRow(label = "location", value = if (detailPhoto.uri != null) detailPhoto.uri.path ?: "internal storage" else "Zune catalog")

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .background(Color(0xFF222222))
                            .clickable { photoToShowDetails = null }
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

// Circular app bar action button styled after Windows Phone
@Composable
fun CircularActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun rememberPhotoThumbnail(context: android.content.Context, photoUri: android.net.Uri?): android.graphics.Bitmap? {
    var bitmap by remember(photoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(photoUri) {
        if (photoUri != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(photoUri, android.util.Size(256, 256), null)
                    } else {
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        context.contentResolver.openInputStream(photoUri)?.use { input ->
                            android.graphics.BitmapFactory.decodeStream(input, null, options)
                        }
                        var scale = 1
                        val REQUIRED_SIZE = 256
                        var width_tmp = options.outWidth
                        var height_tmp = options.outHeight
                        while (true) {
                            if (width_tmp / 2 < REQUIRED_SIZE || height_tmp / 2 < REQUIRED_SIZE) {
                                break
                            }
                            width_tmp /= 2
                            height_tmp /= 2
                            scale *= 2
                        }
                        val o2 = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = scale
                        }
                        context.contentResolver.openInputStream(photoUri)?.use { input ->
                            android.graphics.BitmapFactory.decodeStream(input, null, o2)
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

// Photo display card inside lazy grids
@Composable
fun PhotoGridCard(
    photo: PhotoItem,
    isAeroTheme: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnail = rememberPhotoThumbnail(context, photo.uri)

    val cardGlassModifier = if (isAeroTheme) {
        Modifier
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.40f),
                shape = RoundedCornerShape(3.dp)
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
                shape = RoundedCornerShape(2.dp)
            )
            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(2.dp))
            .clip(RoundedCornerShape(2.dp))
    } else {
        Modifier.background(Color(0xFF1E1E1E))
    }

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val prefs = remember(context) { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(cardGlassModifier)
            .metroTilt(interactionSource)
            .pointerInput(photo) {
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
    ) {
        if (photo.uri != null) {
            if (thumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = photo.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().paperTexture()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF222222))
                )
            }
        } else {
            // Mock photos represent stunning color patterns
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = photo.gradientColors,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
            }
        }
    }
}

// Album Folders UI representing Camera Roll, Saved Pictures, and Screenshots (Reference Image 2)
@Composable
fun AlbumFolderCard(
    albumName: String,
    photos: List<PhotoItem>,
    isAeroTheme: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .metroTilt(interactionSource)
            .pointerInput(albumName) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        tryAwaitRelease()
                        interactionSource.emit(androidx.compose.foundation.interaction.PressInteraction.Release(press))
                    },
                    onTap = { onClick() }
                )
            }
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val folderGlassModifier = if (isAeroTheme) {
            Modifier
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                .background(Color(0xFF181818), RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
        } else {
            Modifier
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                .background(Color(0xFF151515), RoundedCornerShape(2.dp))
                .clip(RoundedCornerShape(2.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(folderGlassModifier),
            contentAlignment = Alignment.BottomStart
        ) {
            if (photos.size >= 4) {
                // 2x2 grid cluster for rich album feel
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { AlbumImageOrFallback(photos[0]) }
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { AlbumImageOrFallback(photos[1]) }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { AlbumImageOrFallback(photos[2]) }
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { AlbumImageOrFallback(photos[3]) }
                    }
                }
            } else if (photos.size >= 2) {
                // 1x2 split
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { AlbumImageOrFallback(photos[0]) }
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { AlbumImageOrFallback(photos[1]) }
                }
            } else if (photos.isNotEmpty()) {
                // Single cover photo
                AlbumImageOrFallback(photos[0])
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF222222))
                )
            }

            // Dark gradient backing for text readability
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

            // Labels inside the preview box
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                val sharedNameModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "photo_album_title_$albumName"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    Modifier
                }

                Text(
                    text = albumName.uppercase(),
                    style = ZuneTypography.h2.copy(
                        fontSize = 18.sp,
                        fontFamily = SegoeUiFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = sharedNameModifier
                )

                val subtitleText = remember(photos) {
                    if (albumName.lowercase() == "screenshots") "6/12 - 6/14" else "${photos.size} ITEMS"
                }

                Text(
                    text = subtitleText.uppercase(),
                    style = ZuneTypography.body1.copy(fontSize = 11.sp, fontFamily = SegoeUiLightFontFamily),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AlbumImageOrFallback(photo: PhotoItem) {
    if (photo.uri != null) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().paperTexture()
        )
    } else {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = if (photo.gradientColors.size >= 2) photo.gradientColors else listOf(Color(0xFFEE0979), Color(0xFFFF6A00))
                )
            )
        }
    }
}

// Online links represented at bottom of Albums tab
@Composable
fun OnlineAlbumTile(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = Modifier
            .width(140.dp)
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color.White.copy(alpha = 0.1f))
            .metroTilt(interactionSource)
            .pointerInput(name) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        tryAwaitRelease()
                        interactionSource.emit(androidx.compose.foundation.interaction.PressInteraction.Release(press))
                    },
                    onTap = { /* Online action */ }
                )
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = name,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = name,
            style = ZuneTypography.body1.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
            color = Color.White
        )
    }
}

// Fullscreen Month Jump Selector Dialog (Reference Image 1)
@Composable
fun MonthJumpListSelector(
    months: List<String>,
    onMonthSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "jump to date",
                style = ZuneTypography.h2.copy(
                    fontFamily = SegoeUiLightFontFamily,
                    fontSize = 24.sp,
                    color = ZuneTextSecondary
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // List of months in blue rectangles (Reference Image 1)
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val columns = if (isLandscape) 5 else 3

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
            ) {
                itemsIndexed(months) { index, month ->
                    val displayMonth = remember(month) {
                        val parts = month.split(" ")
                        if (parts.isNotEmpty()) {
                            val monthName = parts[0]
                            val shortMonth = if (monthName.length >= 3) monthName.substring(0, 3) else monthName
                            if (parts.size > 1) "$shortMonth ${parts[1]}" else shortMonth
                        } else {
                            month
                        }
                    }
                    val accent = LocalZuneAccent.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF151515))
                            .border(width = 1.5.dp, color = accent)
                            .metroClickable { onMonthSelected(month) }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayMonth.lowercase(),
                            style = ZuneTypography.h2.copy(
                                fontFamily = SegoeUiFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// 4. Immersive Full-screen Lightbox Image Viewer Overlay
@Composable
fun FullscreenLightbox(
    photos: List<PhotoItem>,
    initialIndex: Int,
    pinnedIds: List<Long> = emptyList(),
    onPin: (Long) -> Unit = {},
    onUnpin: (Long) -> Unit = {},
    onDismiss: () -> Unit,
    onDeletePhoto: (Int) -> Unit,
    onSaveEditedCopy: (Bitmap, String) -> Unit
) {
    val context = LocalContext.current
    val pagerState = key(initialIndex) {
        rememberPagerState(initialPage = initialIndex.coerceIn(0, photos.size - 1)) { photos.size }
    }
    val currentIndex = pagerState.currentPage
    val currentPhoto = photos.getOrNull(currentIndex) ?: return

    var isHUDVisible by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }

    BackHandler(enabled = showEditor || showDetails || showMenu) {
        if (showEditor) {
            showEditor = false
        } else if (showDetails) {
            showDetails = false
        } else if (showMenu) {
            showMenu = false
        }
    }

    // visual rotation states
    val photoRotations = remember { mutableStateMapOf<Long, Float>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isZoomed // Disable pager swipe when photo is zoomed in
        ) { page ->
            val photo = photos[page]
            ZoomablePhotoItem(
                photo = photo,
                rotationDegrees = photoRotations[photo.id] ?: 0f,
                onDismiss = onDismiss,
                onZoomChanged = { zoomed -> isZoomed = zoomed },
                onSwipeUp = { showMenu = true },
                onTap = {}
            )
        }

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
                DropUpMenuItem(text = "use as background") {
                    showMenu = false
                    val prefs = context.getSharedPreferences("zune_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putInt("bg_selection", -1)
                        .putString("bg_custom_uri", currentPhoto.uri?.toString() ?: "")
                        .apply()
                }
                DropUpMenuItem(text = "view details") {
                    showMenu = false
                    showDetails = true
                }
                DropUpMenuItem(text = "rotate") {
                    showMenu = false
                    val currentRot = photoRotations[currentPhoto.id] ?: 0f
                    photoRotations[currentPhoto.id] = (currentRot + 90f) % 360f
                }
                DropUpMenuItem(text = "share") {
                    showMenu = false
                    currentPhoto.uri?.let { uri ->
                        try {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "share image"))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "failed to share.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        android.widget.Toast.makeText(context, "cannot share catalog asset.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                DropUpMenuItem(text = "set as wallpaper") {
                    showMenu = false
                    currentPhoto.uri?.let { uri ->
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_ATTACH_DATA).apply {
                                setDataAndType(uri, "image/*")
                                putExtra("mimeType", "image/*")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "set as"))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "failed to attach image.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        android.widget.Toast.makeText(context, "cannot set catalog asset.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                val targetId = remember(currentPhoto.id) { currentPhoto.id or 0x1000000000000000L }
                val isPinned = remember(targetId, pinnedIds) {
                    pinnedIds.contains(targetId)
                }
                DropUpMenuItem(text = if (isPinned) "unpin from start" else "pin to start") {
                    showMenu = false
                    if (isPinned) {
                        onUnpin(targetId)
                    } else {
                        onPin(targetId)
                    }
                    android.widget.Toast.makeText(context, if (isPinned) "unpinned" else "pinned", android.widget.Toast.LENGTH_SHORT).show()
                }

                DropUpMenuItem(text = "delete") {
                    showMenu = false
                    onDeletePhoto(currentIndex)
                }

                DropUpMenuItem(text = "close photo") {
                    showMenu = false
                    onDismiss()
                }
            }
        }

        // Details Dialog
        if (showDetails) {
            val metadata = remember(currentPhoto) { queryFileMetadata(context, currentPhoto.uri) }
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
                        text = "picture details",
                        style = ZuneTypography.h2.copy(
                            fontFamily = SegoeUiLightFontFamily,
                            fontSize = 24.sp,
                            color = ZuneAccent.lightenForText()
                        )
                    )

                    val sdf = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.US)
                    val dateStr = sdf.format(Date(currentPhoto.dateTaken))

                    DetailRow(label = "name", value = currentPhoto.title)
                    DetailRow(label = "date taken", value = dateStr)
                    DetailRow(label = "album", value = currentPhoto.albumName)
                    DetailRow(label = "resolution", value = if (metadata.width > 0) "${metadata.width} x ${metadata.height}" else "1080 x 1080 (simulated)")
                    DetailRow(label = "file size", value = metadata.size)
                    DetailRow(label = "location", value = if (currentPhoto.uri != null) currentPhoto.uri.path ?: "internal storage" else "Zune catalog")

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .background(Color(0xFF222222))
                            .clickable { showDetails = false }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Text("close", color = Color.White, style = ZuneTypography.body2)
                    }
                }
            }
        }

        if (showEditor) {
            ZunePhotoEditor(
                photo = currentPhoto,
                onDismiss = { showEditor = false },
                onSave = { bmp ->
                    onSaveEditedCopy(bmp, currentPhoto.title)
                    showEditor = false
                }
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

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(text = label, style = ZuneTypography.caption, color = ZuneTextSecondary)
        Text(text = value, style = ZuneTypography.body1, color = Color.White)
    }
}

@Composable
fun ZoomablePhotoItem(
    photo: PhotoItem,
    rotationDegrees: Float,
    onDismiss: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onSwipeUp: () -> Unit,
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dismissOffset by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    // When scale is updated, let the parent pager know if we are zoomed in or not
    LaunchedEffect(scale) {
        onZoomChanged(scale > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dismissOffset
                alpha = (1f - dismissOffset / 1000f).coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    var isPinch = false
                    var dragStarted = false
                    var isVerticalDrag = false
                    var accumulatedDragY = 0f

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
                            if (change != null) {
                                val dragAmount = change.position - change.previousPosition
                                if (scale > 1f) {
                                    val panXLimit = ((scale - 1f) * size.width) / 2f
                                    val panYLimit = ((scale - 1f) * size.height) / 2f
                                    offset = Offset(
                                        x = (offset.x + dragAmount.x).coerceIn(-panXLimit, panXLimit),
                                        y = (offset.y + dragAmount.y).coerceIn(-panYLimit, panYLimit)
                                    )
                                    change.consume()
                                } else {
                                    if (!dragStarted) {
                                        val dragDistSq = dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y
                                        if (dragDistSq > 25f) { // 5px threshold
                                            dragStarted = true
                                            isVerticalDrag = kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x)
                                        }
                                    }

                                    if (dragStarted) {
                                        if (isVerticalDrag) {
                                            accumulatedDragY += dragAmount.y
                                            if (accumulatedDragY > 0) {
                                                dismissOffset = accumulatedDragY
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (scale > 1f) {
                        // no vertical dismiss if zoomed in
                    } else if (isVerticalDrag) {
                        if (dismissOffset > 250f) {
                            coroutineScope.launch {
                                androidx.compose.animation.core.animate(
                                    initialValue = dismissOffset,
                                    targetValue = 2000f,
                                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                                ) { value, _ ->
                                    dismissOffset = value
                                }
                                onDismiss()
                            }
                        } else {
                            if (accumulatedDragY < -120f) {
                                onSwipeUp()
                            }
                            coroutineScope.launch {
                                androidx.compose.animation.core.animate(
                                    initialValue = dismissOffset,
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 200)
                                ) { value, _ ->
                                    dismissOffset = value
                                }
                            }
                        }
                    }
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
                    onTap = { onTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val graphicsModifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotationDegrees
                translationX = offset.x
                translationY = offset.y
            }

        if (photo.uri != null) {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.title,
                contentScale = ContentScale.Fit,
                modifier = graphicsModifier
            )
        } else {
            Canvas(
                modifier = graphicsModifier
                    .aspectRatio(1.2f)
            ) {
                drawRect(
                    brush = Brush.linearGradient(photo.gradientColors)
                )
            }
        }
    }
}

// 5. Photos Hub Storage Permission Prompt (Refined Windows Phone style)
@Composable
fun PhotosPermissionPrompt(
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

            // Header PHOTOS
            Text(
                text = "PHOTOS",
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
                text = "zune needs access to your phone storage to view, display, and organize your pictures.",
                style = ZuneTypography.body1.copy(
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            )

            Text(
                text = "you can grant permission now, or bypass to explore Zune with high-quality sample pictures.",
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

            // "use sample photos" Tile Button
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
                    text = "view sample photos",
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

// 6. Generate stunning mock photos (curated HSL gradient study designs)
fun generateMockPhotos(): List<PhotoItem> {
    val now = System.currentTimeMillis()
    val oneMonth = 30L * 24 * 60 * 60 * 1000

    val colors = listOf(
        listOf(Color(0xFFEE0979), Color(0xFFFF6A00)), // Sunset Orange
        listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)), // Royal Purple
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)), // Emerald Mint
        listOf(Color(0xFF00c6ff), Color(0xFF0072ff)), // Ocean Blue
        listOf(Color(0xFFf21b3f), Color(0xFFab0e28)), // Crimson Red
        listOf(Color(0xFFff9966), Color(0xFFff5e62)), // Coral Sunset
        listOf(Color(0xFF159957), Color(0xFF155799)), // Deep Green Blue
        listOf(Color(0xFFe1eec3), Color(0xFFf05053)), // Pastel Pink Peach
        listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B)), // Sunset Trio
        listOf(Color(0xFF4CA1AF), Color(0xFF2C3E50)), // Slate Teal
        listOf(Color(0xFF780206), Color(0xFF061161)), // Fire & Ice
        listOf(Color(0xFF56CCF2), Color(0xFF2F80ED))  // Sky Blue
    )

    val albumNames = listOf("camera roll", "saved pictures", "screenshots")
    val titles = listOf(
        "metro style banner.png", "segoe typography font.png", "zune user interface.png",
        "aesthetic gradient art.png", "windows phone tiles.png", "retro design draft.png",
        "minimalist poster.png", "color study 01.png", "abstract layout.png",
        "geometric pattern.jpg", "neon wave landscape.png", "flat design vectors.png"
    )

    return List(12) { i ->
        val dateOffset = (i / 4) * oneMonth
        PhotoItem(
            id = i.toLong() + 1000000000L,
            uri = null,
            dateTaken = now - dateOffset - (i % 4) * 2 * 24 * 60 * 60 * 1000L,
            albumName = albumNames[i % albumNames.size],
            title = titles[i % titles.size],
            gradientColors = colors[i % colors.size],
            isFavorite = i % 3 == 0
        )
    }
}

data class FileMetadata(val size: String, val width: Int, val height: Int)

private fun queryFileMetadata(context: Context, uri: Uri?): FileMetadata {
    if (uri == null) return FileMetadata(size = "450 KB", width = 1080, height = 1080)
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
                return FileMetadata(size = sizeFormatted, width = width, height = height)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return FileMetadata(size = "unknown", width = 0, height = 0)
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
fun ZunePhotoEditor(
    photo: PhotoItem,
    onSave: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var rotation by remember { mutableStateOf(0f) }
    var selectedAspect by remember { mutableStateOf("free") }
    var activeFilter by remember { mutableStateOf("original") }
    var isDrawingMode by remember { mutableStateOf(false) }

    var cropLeft by remember { mutableFloatStateOf(0.0f) }
    var cropTop by remember { mutableFloatStateOf(0.0f) }
    var cropRight by remember { mutableFloatStateOf(1.0f) }
    var cropBottom by remember { mutableFloatStateOf(1.0f) }

    val drawingLines = remember { mutableStateListOf<List<Offset>>() }
    val currentLine = remember { mutableStateListOf<Offset>() }

    val accentColor = ZuneAccent

    var originalBitmap by remember(photo.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.uri) {
        withContext(Dispatchers.IO) {
            try {
                if (photo.uri != null) {
                    val inputStream = context.contentResolver.openInputStream(photo.uri)
                    originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                } else {
                    val bmp = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    val paint = android.graphics.Paint()
                    paint.shader = android.graphics.LinearGradient(
                        0f, 0f, 800f, 800f,
                        photo.gradientColors.firstOrNull()?.toArgb() ?: Color.Red.toArgb(),
                        photo.gradientColors.lastOrNull()?.toArgb() ?: Color.Blue.toArgb(),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, 800f, 800f, paint)
                    originalBitmap = bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "cancel",
                    style = ZuneTypography.body1,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onDismiss() }
                )
                Text(
                    text = "edit picture",
                    style = ZuneTypography.h2.copy(fontSize = 20.sp, fontFamily = SegoeUiLightFontFamily),
                    color = Color.White
                )
                Text(
                    text = "save copy",
                    style = ZuneTypography.body1.copy(fontWeight = FontWeight.Bold),
                    color = accentColor.lightenForText(),
                    modifier = Modifier.clickable {
                        originalBitmap?.let { bmp ->
                            val edited = processEditedBitmap(
                                bmp, rotation, selectedAspect, activeFilter, drawingLines, accentColor,
                                cropLeft, cropTop, cropRight, cropBottom
                            )
                            onSave(edited)
                        }
                    }
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (originalBitmap != null) {
                    val bmp = originalBitmap!!
                    val containerWidth = constraints.maxWidth.toFloat()
                    val containerHeight = constraints.maxHeight.toFloat()

                    // Calculate scaled image size (ContentScale.Fit behavior)
                    val imgAspect = bmp.width.toFloat() / bmp.height.toFloat()
                    val viewAspect = containerWidth / containerHeight
                    val fitWidth: Float
                    val fitHeight: Float
                    if (imgAspect > viewAspect) {
                        fitWidth = containerWidth
                        fitHeight = containerWidth / imgAspect
                    } else {
                        fitHeight = containerHeight
                        fitWidth = containerHeight * imgAspect
                    }

                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val fitWidthDp = with(density) { fitWidth.toDp() }
                    val fitHeightDp = with(density) { fitHeight.toDp() }

                    val targetAspect = when (selectedAspect) {
                        "1:1" -> 1f
                        "4:3" -> 4f / 3f
                        "16:9" -> 16f / 9f
                        else -> 1f
                    }
                    if (selectedAspect != "free") {
                        val currentAspect = fitWidth / fitHeight
                        val w: Float
                        val h: Float
                        if (currentAspect > targetAspect) {
                            h = fitHeight * 0.8f
                            w = h * targetAspect
                        } else {
                            w = fitWidth * 0.8f
                            h = w / targetAspect
                        }
                        val left = (fitWidth - w) / 2
                        val top = (fitHeight - h) / 2
                        cropLeft = left / fitWidth
                        cropTop = top / fitHeight
                        cropRight = (left + w) / fitWidth
                        cropBottom = (top + h) / fitHeight
                    } else {
                        if (cropLeft == 0.0f && cropTop == 0.0f && cropRight == 1.0f && cropBottom == 1.0f) {
                            cropLeft = 0.1f
                            cropTop = 0.1f
                            cropRight = 0.9f
                            cropBottom = 0.9f
                        }
                    }

                    val cropLeftPx = cropLeft * fitWidth
                    val cropTopPx = cropTop * fitHeight
                    val cropRightPx = cropRight * fitWidth
                    val cropBottomPx = cropBottom * fitHeight
                    val cropWidthPx = cropRightPx - cropLeftPx
                    val cropHeightPx = cropBottomPx - cropTopPx

                    val cropLeftDp = with(density) { cropLeftPx.toDp() }
                    val cropTopDp = with(density) { cropTopPx.toDp() }
                    val cropWidthDp = with(density) { cropWidthPx.toDp() }
                    val cropHeightDp = with(density) { cropHeightPx.toDp() }

                    val colorFilter = when (activeFilter) {
                        "grayscale" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                        )
                        "sepia" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                0.393f, 0.769f, 0.189f, 0f, 0f,
                                0.349f, 0.686f, 0.168f, 0f, 0f,
                                0.272f, 0.534f, 0.131f, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            ))
                        )
                        "magenta" -> androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFE51400), androidx.compose.ui.graphics.BlendMode.Color)
                        "orange" -> androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFF09609), androidx.compose.ui.graphics.BlendMode.Color)
                        "cyan" -> androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF1BA1E2), androidx.compose.ui.graphics.BlendMode.Color)
                        else -> null
                    }

                    Box(
                        modifier = Modifier
                            .size(fitWidthDp, fitHeightDp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 1. Scaled & rotated container for the image and drawings
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = rotation
                                    val scale = when (selectedAspect) {
                                        "1:1" -> 0.8f
                                        "4:3" -> 0.85f
                                        "16:9" -> 0.75f
                                        else -> 0.95f
                                    }
                                    scaleX = scale
                                    scaleY = scale
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                colorFilter = colorFilter,
                                modifier = Modifier.fillMaxSize()
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawingLines.forEach { line ->
                                    for (i in 0 until line.size - 1) {
                                        drawLine(
                                            color = accentColor,
                                            start = Offset(line[i].x * size.width, line[i].y * size.height),
                                            end = Offset(line[i + 1].x * size.width, line[i + 1].y * size.height),
                                            strokeWidth = 4.dp.toPx(),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    }
                                }
                                if (currentLine.size > 1) {
                                    for (i in 0 until currentLine.size - 1) {
                                        drawLine(
                                            color = accentColor,
                                            start = Offset(currentLine[i].x * size.width, currentLine[i].y * size.height),
                                            end = Offset(currentLine[i + 1].x * size.width, currentLine[i + 1].y * size.height),
                                            strokeWidth = 4.dp.toPx(),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Unrotated Crop Overlay (stands upright, aligned to fitWidth/fitHeight)
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(isDrawingMode, fitWidth, fitHeight) {
                                    if (!isDrawingMode) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val xNorm = offset.x / fitWidth
                                            val yNorm = offset.y / fitHeight
                                            currentLine.clear()
                                            currentLine.add(Offset(xNorm, yNorm))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val xNorm = change.position.x / fitWidth
                                            val yNorm = change.position.y / fitHeight
                                            currentLine.add(Offset(xNorm, yNorm))
                                        },
                                        onDragEnd = {
                                            drawingLines.add(currentLine.toList())
                                            currentLine.clear()
                                        }
                                    )
                                }
                        ) {
                            val w = size.width
                            val h = size.height
                            val left = cropLeft * w
                            val top = cropTop * h
                            val right = cropRight * w
                            val bottom = cropBottom * h

                            // Draw semi-transparent background outside crop box
                            drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, top))
                            drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, bottom), size = androidx.compose.ui.geometry.Size(w, h - bottom))
                            drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, bottom - top))
                            drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(right, top), size = androidx.compose.ui.geometry.Size(w - right, bottom - top))

                            // Draw crop boundary line
                            drawRect(
                                color = Color.White.copy(alpha = 0.8f),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }

                        // 3. Upright Crop Resizing corner handles
                        if (selectedAspect == "free" && !isDrawingMode) {
                            val handleSize = 28.dp
                            val handleOffset = handleSize / 2

                            // Top-Left Handle
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(cropLeftDp - handleOffset, cropTopDp - handleOffset)
                                    .size(handleSize)
                                    .background(accentColor, CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                                    .pointerInput(fitWidth, fitHeight) {
                                        detectDragGestures(
                                            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val newLeftPx = (cropLeft * fitWidth + dragAmount.x).coerceIn(0f, cropRight * fitWidth - 50.dp.toPx())
                                                val newTopPx = (cropTop * fitHeight + dragAmount.y).coerceIn(0f, cropBottom * fitHeight - 50.dp.toPx())
                                                cropLeft = newLeftPx / fitWidth
                                                cropTop = newTopPx / fitHeight
                                            }
                                        )
                                    }
                            )

                            // Top-Right Handle
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(cropWidthDp + cropLeftDp - handleOffset, cropTopDp - handleOffset)
                                    .size(handleSize)
                                    .background(accentColor, CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                                    .pointerInput(fitWidth, fitHeight) {
                                        detectDragGestures(
                                            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val newRightPx = (cropRight * fitWidth + dragAmount.x).coerceIn(cropLeft * fitWidth + 50.dp.toPx(), fitWidth)
                                                val newTopPx = (cropTop * fitHeight + dragAmount.y).coerceIn(0f, cropBottom * fitHeight - 50.dp.toPx())
                                                cropRight = newRightPx / fitWidth
                                                cropTop = newTopPx / fitHeight
                                            }
                                        )
                                    }
                            )

                            // Bottom-Left Handle
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(cropLeftDp - handleOffset, cropHeightDp + cropTopDp - handleOffset)
                                    .size(handleSize)
                                    .background(accentColor, CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                                    .pointerInput(fitWidth, fitHeight) {
                                        detectDragGestures(
                                            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val newLeftPx = (cropLeft * fitWidth + dragAmount.x).coerceIn(0f, cropRight * fitWidth - 50.dp.toPx())
                                                val newBottomPx = (cropBottom * fitHeight + dragAmount.y).coerceIn(cropTop * fitHeight + 50.dp.toPx(), fitHeight)
                                                cropLeft = newLeftPx / fitWidth
                                                cropBottom = newBottomPx / fitHeight
                                            }
                                        )
                                    }
                            )

                            // Bottom-Right Handle
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(cropWidthDp + cropLeftDp - handleOffset, cropHeightDp + cropTopDp - handleOffset)
                                    .size(handleSize)
                                    .background(accentColor, CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                                    .pointerInput(fitWidth, fitHeight) {
                                        detectDragGestures(
                                            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val newRightPx = (cropRight * fitWidth + dragAmount.x).coerceIn(cropLeft * fitWidth + 50.dp.toPx(), fitWidth)
                                                val newBottomPx = (cropBottom * fitHeight + dragAmount.y).coerceIn(cropTop * fitHeight + 50.dp.toPx(), fitHeight)
                                                cropRight = newRightPx / fitWidth
                                                cropBottom = newBottomPx / fitHeight
                                            }
                                        )
                                    }
                            )
                        }
                    }
                } else {
                    androidx.compose.material.CircularProgressIndicator(color = accentColor)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            selectedAspect = when (selectedAspect) {
                                "free" -> "1:1"
                                "1:1" -> "4:3"
                                "4:3" -> "16:9"
                                else -> "free"
                            }
                        }
                    ) {
                        Icon(imageVector = ZuneIcons.Crop, contentDescription = null, tint = Color.White)
                        Text(selectedAspect.lowercase(), style = ZuneTypography.caption, color = ZuneTextSecondary)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { rotation = (rotation + 90f) % 360f }
                    ) {
                        Icon(imageVector = ZuneIcons.RotateRight, contentDescription = null, tint = Color.White)
                        Text("rotate", style = ZuneTypography.caption, color = ZuneTextSecondary)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { isDrawingMode = !isDrawingMode }
                    ) {
                        Icon(
                            imageVector = ZuneIcons.Brush,
                            contentDescription = null,
                            tint = if (isDrawingMode) accentColor else Color.White
                        )
                        Text(if (isDrawingMode) "drawing" else "draw", style = ZuneTypography.caption, color = ZuneTextSecondary)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            rotation = 0f
                            selectedAspect = "free"
                            activeFilter = "original"
                            cropLeft = 0f
                            cropTop = 0f
                            cropRight = 1f
                            cropBottom = 1f
                            drawingLines.clear()
                            isDrawingMode = false
                        }
                    ) {
                        Icon(imageVector = ZuneIcons.Refresh, contentDescription = null, tint = Color.White)
                        Text("reset", style = ZuneTypography.caption, color = ZuneTextSecondary)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val filters = listOf("original", "grayscale", "sepia", "magenta", "orange", "cyan")
                    filters.forEach { filter ->
                        val isSelected = activeFilter == filter
                        Box(
                            modifier = Modifier
                                .border(1.dp, if (isSelected) accentColor else Color.White.copy(alpha = 0.2f))
                                .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { activeFilter = filter }
                                .padding(vertical = 6.dp, horizontal = 12.dp)
                        ) {
                            Text(filter, color = Color.White, style = ZuneTypography.body2)
                        }
                    }
                }
            }
        }
    }
}

fun processEditedBitmap(
    src: Bitmap,
    rotation: Float,
    aspect: String,
    filter: String,
    drawings: List<List<Offset>>,
    accentColor: Color,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float
): Bitmap {
    // 1. Draw drawings first on a copy of original bitmap (so drawings rotate and crop with the bitmap)
    val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = android.graphics.Paint()

    canvas.drawBitmap(src, 0f, 0f, paint)

    paint.color = accentColor.toArgb()
    paint.strokeWidth = (src.width.toFloat() / 150f).coerceAtLeast(8f)
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeCap = android.graphics.Paint.Cap.ROUND

    drawings.forEach { line ->
        val path = android.graphics.Path()
        if (line.isNotEmpty()) {
            val first = line[0]
            path.moveTo(first.x * src.width, first.y * src.height)
            for (i in 1 until line.size) {
                val pt = line[i]
                path.lineTo(pt.x * src.width, pt.y * src.height)
            }
            canvas.drawPath(path, paint)
        }
    }

    // 2. Rotate the drawn bitmap
    val matrix = android.graphics.Matrix()
    if (rotation != 0f) {
        matrix.postRotate(rotation)
    }
    var result = Bitmap.createBitmap(output, 0, 0, output.width, output.height, matrix, true)

    // 3. Crop rotated/drawn bitmap using the precise crop bounds!
    val x = (cropLeft * result.width).toInt().coerceIn(0, result.width - 1)
    val y = (cropTop * result.height).toInt().coerceIn(0, result.height - 1)
    val w = ((cropRight - cropLeft) * result.width).toInt().coerceIn(1, result.width - x)
    val h = ((cropBottom - cropTop) * result.height).toInt().coerceIn(1, result.height - y)
    
    result = Bitmap.createBitmap(result, x, y, w, h)

    // 4. Apply color filter to final result
    if (filter != "original") {
        val finalOutput = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
        val finalCanvas = android.graphics.Canvas(finalOutput)
        val filterPaint = android.graphics.Paint()
        val cfMatrix = android.graphics.ColorMatrix()
        when (filter) {
            "grayscale" -> cfMatrix.setSaturation(0f)
            "sepia" -> cfMatrix.set(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            "magenta" -> filterPaint.colorFilter = android.graphics.PorterDuffColorFilter(Color(0xFFE51400).toArgb(), android.graphics.PorterDuff.Mode.SRC_ATOP)
            "orange" -> filterPaint.colorFilter = android.graphics.PorterDuffColorFilter(Color(0xFFF09609).toArgb(), android.graphics.PorterDuff.Mode.SRC_ATOP)
            "cyan" -> filterPaint.colorFilter = android.graphics.PorterDuffColorFilter(Color(0xFF1BA1E2).toArgb(), android.graphics.PorterDuff.Mode.SRC_ATOP)
        }
        if (filter == "grayscale" || filter == "sepia") {
            filterPaint.colorFilter = android.graphics.ColorMatrixColorFilter(cfMatrix)
        }
        finalCanvas.drawBitmap(result, 0f, 0f, filterPaint)
        return finalOutput
    }

    return result
}

fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap, title: String): Uri? {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "copy_$title.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    return uri
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

@Composable
fun PhotoHeaderAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .metroClickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = ZuneTypography.body1.copy(fontSize = 14.sp),
            color = Color.White
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoAlbumDetailScreen(
    albumName: String,
    isAeroTheme: Boolean = false,
    onBack: () -> Unit
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val context = LocalContext.current
    val photos = remember<List<PhotoItem>> {
        val loadedList = mutableListOf<PhotoItem>()
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                val resolver = context.contentResolver
                val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    android.provider.MediaStore.Images.Media.DATE_TAKEN,
                    android.provider.MediaStore.Images.Media.DATE_ADDED,
                    android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                )
                resolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                    val dateColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_TAKEN)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_ADDED)
                    val bucketColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "photo_$id"
                        var date = cursor.getLong(dateColumn)
                        val dateAdded = cursor.getLong(dateAddedColumn)
                        if (date <= 0L) {
                            date = dateAdded * 1000L
                        }
                        val album = cursor.getString(bucketColumn) ?: "camera roll"
                        val contentUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        loadedList.add(
                            PhotoItem(
                                id = id,
                                uri = contentUri,
                                dateTaken = date,
                                albumName = album.lowercase(),
                                title = name
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            loadedList.sortByDescending { it.dateTaken }
        }
        if (loadedList.isEmpty()) {
            loadedList.addAll(generateMockPhotos())
        }
        loadedList
    }

    val currentViewPhotos = remember<List<PhotoItem>>(photos, albumName) {
        photos.filter { it.albumName.lowercase() == albumName.lowercase() }
    }

    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var longPressedPhoto by remember { mutableStateOf<PhotoItem?>(null) }
    var photoToShowDetails by remember { mutableStateOf<PhotoItem?>(null) }

    BackHandler(enabled = selectedPhotoIndex != null) {
        if (selectedPhotoIndex != null) {
            selectedPhotoIndex = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "header_pictures"),
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
                    text = "pictures",
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

            // Album details header
            Column(
                modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
            ) {
                val sharedNameModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "photo_album_title_$albumName"),
                            animatedVisibilityScope = animatedVisibilityScope
                        ).skipToLookaheadSize()
                    }
                } else {
                    Modifier
                }

                Text(
                    text = albumName.uppercase(),
                    style = ZuneTypography.h1.copy(
                        fontFamily = SegoeUiFontFamily,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = LocalZuneAccent.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = sharedNameModifier
                )
                Text(
                    text = "${currentViewPhotos.size} PHOTOS",
                    style = ZuneTypography.body2.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 16.sp
                    ),
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Header actions row (slideshow)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PhotoHeaderAction(
                    text = "slideshow",
                    icon = ZuneIcons.Play,
                    onClick = {
                        if (currentViewPhotos.isNotEmpty()) {
                            selectedPhotoIndex = 0
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // COMPACT photos grid: NO padding, NO gaps!
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val columns = if (isLandscape) 7 else 4

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 0.dp),
                contentPadding = PaddingValues(top = 0.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(currentViewPhotos) { idx, photo ->
                    PhotoGridCard(
                        photo = photo,
                        isAeroTheme = isAeroTheme,
                        onClick = {
                            selectedPhotoIndex = idx
                        },
                        onLongClick = {
                            longPressedPhoto = photo
                        }
                    )
                }
            }
        }

        // Lightbox overlay inside the album detail screen
        if (selectedPhotoIndex != null) {
            FullscreenLightbox(
                photos = currentViewPhotos,
                initialIndex = selectedPhotoIndex!!,
                pinnedIds = emptyList(),
                onPin = {},
                onUnpin = {},
                onDismiss = { selectedPhotoIndex = null },
                onDeletePhoto = { idx ->
                    // no-op
                },
                onSaveEditedCopy = { _, _ -> }
            )
        }
    }
}