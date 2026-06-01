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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.zune.player.ui.components.PivotLayout
import com.zune.player.ui.components.metroClickable
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

@Composable
fun PhotosScreen(
    isAeroTheme: Boolean = false,
    pinnedIds: List<Long> = emptyList(),
    initialPhotoId: Long? = null,
    onPin: (Long) -> Unit = {},
    onUnpin: (Long) -> Unit = {},
    onBack: () -> Unit
) {
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
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                    )
                    resolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                        val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                        
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn) ?: "photo_$id"
                            val date = cursor.getLong(dateColumn)
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
    
    var activeAlbumName by remember { mutableStateOf<String?>(null) }

    // Deep-linked navigation handler
    LaunchedEffect(photos, initialPhotoId) {
        if (initialPhotoId != null && photos.isNotEmpty()) {
            val idx = photos.indexOfFirst { it.id == initialPhotoId }
            if (idx != -1) {
                activeAlbumName = null
                selectedPhotoIndex = idx
            }
        }
    }
    
    val currentViewPhotos = remember(currentPhotosFiltered, activeAlbumName) {
        if (activeAlbumName != null) {
            currentPhotosFiltered.filter { it.albumName.lowercase() == activeAlbumName!!.lowercase() }
        } else {
            currentPhotosFiltered
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!hasPermission && !useSamplesFallback) {
            PhotosPermissionPrompt(
                onAllow = { permissionLauncher.launch(permissionString) },
                onUseSamples = { useSamplesFallback = true },
                onBack = onBack
            )
        } else {
            if (activeAlbumName != null) {
                // Individual Album photo grid view
                val albumName = activeAlbumName!!
                
                Column(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.zune.player.R.drawable.zune_back),
                        contentDescription = "Back",
                        modifier = Modifier
                            .padding(bottom = 24.dp)
                            .offset(x = (-20).dp, y = (-8).dp)
                            .size(80.dp)
                            .metroClickable { activeAlbumName = null }
                    )
                    Text(
                        text = "ALBUMS",
                        style = ZuneTypography.h4.copy(
                            fontFamily = SegoeUiLightFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ZuneTextSecondary,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 4.dp)
                    )
                    
                    Text(
                        text = albumName.lowercase(),
                        style = ZuneTypography.h1.copy(
                            fontFamily = SegoeUiLightFontFamily,
                            fontSize = 56.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
                    )
                    
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val columns = if (isLandscape) 7 else 4

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.weight(1f).padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularActionButton(
                                icon = Icons.Default.CameraAlt,
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
                        }
                    }
                }
            } else {
                // Main Photos Hub Pivot
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
                    val pages = listOf("all", "albums")
                    val pagerState = rememberPagerState(initialPage = 0) { pages.size }
                    val tabWidths = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

                    // Small Category / Section Header
                    Text(
                        text = if (isAeroTheme) "Photo Library" else "PHOTOS",
                        style = ZuneTypography.h4.copy(
                            fontFamily = SegoeUiLightFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ZuneTextSecondary,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 4.dp)
                    )
                    
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
                                val displayText = if (isAeroTheme && isCurrentTab) "< $title >" else title
                                val textColor = Color.White.copy(alpha = alpha)
                                val textStyle = ZuneTypography.h1.copy(
                                    fontFamily = SegoeUiLightFontFamily,
                                    fontSize = 56.sp
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
                                    val columns = if (isLandscape) 7 else 4

                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(columns),
                                        state = lazyGridState,
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        flatGridItems.forEachIndexed { globalIndex, item ->
                                            if (item is String) {
                                                // Header month item (span entire columns)
                                                item(span = { GridItemSpan(columns) }, key = "header_$item") {
                                                    Text(
                                                        text = item.lowercase(),
                                                        style = ZuneTypography.h2.copy(
                                                            fontFamily = SegoeUiLightFontFamily,
                                                            fontSize = 28.sp,
                                                            color = if (isAeroTheme) AeroBlueOrbAccentColor else ZuneAccent.lightenForText()
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { showJumpSelector = true }
                                                            .padding(vertical = 16.dp)
                                                    )
                                                }
                                            } else if (item is PhotoItem) {
                                                // Photo item in grid
                                                val photoIndex = photos.indexOf(item)
                                                item(key = "photo_${item.id}_$globalIndex") {
                                                    PhotoGridCard(
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
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                    contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    // Folders grid
                                    itemsIndexed(albums) { index, (albumName, albumPhotos) ->
                                        AlbumFolderCard(
                                            albumName = albumName,
                                            photos = albumPhotos,
                                            isAeroTheme = isAeroTheme,
                                            onClick = {
                                                activeAlbumName = albumName
                                            }
                                        )
                                    }
                                    
                                    // Online albums divider and section
                                    item(span = { GridItemSpan(2) }) {
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
                                                    icon = Icons.Default.Cloud,
                                                    tint = Color(0xFF0078D4)
                                                )
                                                // Mock Facebook
                                                OnlineAlbumTile(
                                                    name = "facebook",
                                                    icon = Icons.Default.ThumbUp,
                                                    tint = Color(0xFF1877F2)
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
                            icon = Icons.Default.CameraAlt,
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
                            icon = Icons.Default.PlayArrow,
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
            }
        }
        
        // 4. Full-screen Lightbox image viewer overlay
        selectedPhotoIndex?.let { index ->
            FullscreenLightbox(
                photos = currentViewPhotos,
                initialIndex = index,
                pinnedIds = pinnedIds,
                onPin = onPin,
                onUnpin = onUnpin,
                onDismiss = { selectedPhotoIndex = null },
                onToggleFavorite = { idx ->
                    val newList = photos.toMutableList()
                    val targetPhoto = currentViewPhotos[idx]
                    val mainIdx = newList.indexOfFirst { it.id == targetPhoto.id }
                    if (mainIdx != -1) {
                        newList[mainIdx] = newList[mainIdx].copy(isFavorite = !newList[mainIdx].isFavorite)
                        photos = newList
                    }
                },
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
                }
            )
        }
        
        // 5. Fullscreen Month Jump Selector Dialog (Reference Image 1)
        if (showJumpSelector) {
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
        if (longPressedPhoto != null) {
            val photo = longPressedPhoto!!
            val targetId = remember(photo.id) { photo.id or 0x1000000000000000L }
            val isPinned = remember(targetId, pinnedIds) {
                pinnedIds.contains(targetId)
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { longPressedPhoto = null }
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhotoGridCard(
    photo: PhotoItem,
    isAeroTheme: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(cardGlassModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (photo.uri != null) {
            if (thumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = photo.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
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
        
        // Little heart icon for favorites
        if (photo.isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "favorite",
                tint = Color.Red.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 4.dp, end = 4.dp)
            )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (albumName.lowercase() == "screenshots" && photos.size >= 4) {
            // High-fidelity clustered thumbnail cards (matching Screenshots album in Reference Image 2)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color(0xFF151515))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Miniature 4-square grid cluster
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    userScrollEnabled = false
                ) {
                    itemsIndexed(photos.take(4)) { idx, p ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        ) {
                            if (p.uri != null) {
                                AsyncImage(
                                    model = p.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRect(brush = Brush.linearGradient(p.gradientColors))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Camera Roll & Saved Pictures large square folders with full cover background
            val coverPhoto = photos.firstOrNull()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color(0xFF222222)),
                contentAlignment = Alignment.BottomStart
            ) {
                if (coverPhoto?.uri != null) {
                    AsyncImage(
                        model = coverPhoto.uri,
                        contentDescription = albumName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (coverPhoto != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.linearGradient(coverPhoto.gradientColors)
                        )
                    }
                }
                
                // Dim cover tint
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
        }
        
        // Folder details styled with Segoe typography
        Column {
            Text(
                text = albumName,
                style = ZuneTypography.h2.copy(
                    fontFamily = SegoeUiLightFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White
            )
            
            // Subtitles (e.g. counts or date ranges like "6/12 - 6/14")
            val subtitleText = remember(photos) {
                if (albumName.lowercase() == "screenshots") "6/12 - 6/14" else "${photos.size}"
            }
            
            Text(
                text = subtitleText,
                style = ZuneTypography.body1.copy(fontSize = 13.sp),
                color = ZuneTextSecondary
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
    Row(
        modifier = Modifier
            .width(140.dp)
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color.White.copy(alpha = 0.1f))
            .padding(12.dp)
            .clickable { /* Online action */ },
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
            val columns = if (isLandscape) 3 else 1

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
            ) {
                itemsIndexed(months) { index, month ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2E63FF)) // Vibrant Windows Phone Blue
                            .metroClickable { onMonthSelected(month) }
                            .padding(vertical = 14.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = month.lowercase(),
                            style = ZuneTypography.h2.copy(
                                fontFamily = SegoeUiLightFontFamily,
                                fontSize = 22.sp,
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
    onToggleFavorite: (Int) -> Unit,
    onDeletePhoto: (Int) -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, photos.size - 1)) { photos.size }
    val currentIndex = pagerState.currentPage
    val currentPhoto = photos.getOrNull(currentIndex) ?: return
    
    var isHUDVisible by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    
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
                onTap = { isHUDVisible = !isHUDVisible }
            )
        }
        
        // HUD - Top Bar
        AnimatedVisibility(
            visible = isHUDVisible && !isZoomed,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentIndex + 1} of ${photos.size}",
                    style = ZuneTypography.h2.copy(
                        fontFamily = SegoeUiLightFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White
                )
                
                Text(
                    text = currentPhoto.title.lowercase(),
                    style = ZuneTypography.body2.copy(fontSize = 13.sp),
                    color = ZuneTextSecondary,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 200.dp)
                )
            }
        }
        
        // HUD - Bottom Bar Action Row
        AnimatedVisibility(
            visible = isHUDVisible && !isZoomed,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularActionButton(
                        icon = if (currentPhoto.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        onClick = { onToggleFavorite(currentIndex) },
                        contentDescription = "favorite"
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    CircularActionButton(
                        icon = Icons.Default.MoreHoriz,
                        onClick = { showMenu = !showMenu },
                        contentDescription = "more"
                    )
                }
            }
        }
        
        // WP-style Drop-up Metro Menu Overlay
        if (showMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showMenu = false }
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
                    .clickable(enabled = false) {} // block click propagation
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
    onTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dismissOffset by remember { mutableFloatStateOf(0f) }
    
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
                                            dismissOffset = (dismissOffset + dragAmount.y).coerceAtLeast(0f)
                                            change.consume()
                                        } else {
                                            // Let horizontal drag pass through for pager swipe
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
                            onDismiss()
                        } else {
                            dismissOffset = 0f
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

// Utility extension to lighten the accent colors slightly for black texts/backgrounds
private fun Color.lightenForText(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = hsl[2].coerceAtLeast(0.6f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
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
