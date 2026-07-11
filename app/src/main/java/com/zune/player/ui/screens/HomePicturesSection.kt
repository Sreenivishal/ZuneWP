package com.zune.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.RectangleShape
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
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.zune.player.R
import com.zune.player.data.AudioItem
import com.zune.player.player.AudioPlayer
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

@Composable
fun WmcStartOrbAndClock() {
    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sdf = java.text.SimpleDateFormat("h:mm", java.util.Locale.getDefault())
        while (true) {
            timeText = sdf.format(java.util.Date()).uppercase()
            kotlinx.coroutines.delay(1000)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = timeText,
            style = ZuneTypography.h2.copy(
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ),
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}


data class PinnedTileItem(
    val id: Long,
    val type: String,
    val title: String,
    val subtitle: String,
    val imageUri: Any?,
    val gradientColors: List<Color>,
    val size: Int
)

fun queryLocalPhotos(context: android.content.Context): List<PhotoItem> {
    val list = mutableListOf<PhotoItem>()
    val permissionString = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, permissionString) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (hasPermission) {
        try {
            val resolver = context.contentResolver
            val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Images.Media._ID,
                android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                android.provider.MediaStore.Images.Media.DATE_TAKEN,
                android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            resolver.query(uri, projection, null, null, "${android.provider.MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_TAKEN)
                val bucketColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "photo_$id"
                    val date = cursor.getLong(dateColumn)
                    val album = cursor.getString(bucketColumn) ?: "camera roll"
                    val contentUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    list.add(
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
    if (list.isEmpty()) {
        list.addAll(generateMockPhotos())
    }
    return list
}

fun queryLocalVideos(context: android.content.Context): List<VideoItem> {
    val list = mutableListOf<VideoItem>()
    val permissionString = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, permissionString) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (hasPermission) {
        try {
            val resolver = context.contentResolver
            val uri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Video.Media._ID,
                android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                android.provider.MediaStore.Video.Media.DURATION,
                android.provider.MediaStore.Video.Media.DATE_ADDED
            )
            resolver.query(uri, projection, null, null, "${android.provider.MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "video_$id"
                    val duration = cursor.getLong(durationColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val contentUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    list.add(
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
    if (list.isEmpty()) {
        list.addAll(generateMockVideos())
    }
    return list.sortedByDescending { it.dateAdded }
}

private sealed class LiveTileItem {
    data class ImageUri(val uri: android.net.Uri) : LiveTileItem()
    data class Gradient(val colors: List<Color>) : LiveTileItem()
}

@Composable
fun PicturesAndVideosPagePreview(
    isAeroTheme: Boolean,
    photosList: List<PhotoItem>,
    videosList: List<VideoItem> = emptyList(),
    onNavigateToPhotos: () -> Unit,
    onNavigateToVideos: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val liveTileItems = remember(photosList) {
        val localPhotos = photosList.filter { it.uri != null }
        if (localPhotos.isNotEmpty()) {
            localPhotos.take(10).map { LiveTileItem.ImageUri(it.uri!!) }
        } else {
            photosList.take(10).map {
                if (it.gradientColors.isNotEmpty()) {
                    LiveTileItem.Gradient(it.gradientColors)
                } else {
                    LiveTileItem.Gradient(listOf(Color(0xFFEE0979), Color(0xFFFF6A00)))
                }
            }
        }
    }

    var currentIndex by remember { mutableStateOf(0) }
    LaunchedEffect(liveTileItems) {
        if (liveTileItems.isNotEmpty()) {
            while (true) {
                delay(6000)
                currentIndex = (currentIndex + 1) % liveTileItems.size
            }
        }
    }

    val latestVideo = remember(videosList) { videosList.firstOrNull() }
    val videoThumbnail = rememberVideoTileThumbnail(context, latestVideo?.uri)

    val infiniteTransition = rememberInfiniteTransition(label = "ken_burns")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val previewHeight = if (isLandscape) 130.dp else 180.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(previewHeight),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val photosBorderModifier = Modifier
                .border(1.5.dp, Color.White.copy(alpha = 0.15f))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(photosBorderModifier)
                    .background(Color(0xFF1E1E1E))
                    .metroClickable { onNavigateToPhotos() }
                    .clipToBounds(),
                contentAlignment = Alignment.BottomStart
            ) {
                if (liveTileItems.isNotEmpty()) {
                    val currentItem = liveTileItems.getOrNull(currentIndex)
                    AnimatedContent(
                        targetState = currentItem,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(1000)) + slideInVertically(
                                animationSpec = tween(1000),
                                initialOffsetY = { it }
                            )).togetherWith(
                                fadeOut(animationSpec = tween(1000)) + slideOutVertically(
                                    animationSpec = tween(1000),
                                    targetOffsetY = { -it }
                                )
                            )
                        },
                        label = "live_tile_transition",
                        modifier = Modifier.fillMaxSize()
                    ) { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        ) {
                            when (item) {
                                is LiveTileItem.ImageUri -> {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                is LiveTileItem.Gradient -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(LocalZuneAccent.current)
                                    )
                                }
                                null -> {}
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalZuneAccent.current)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                startY = 80f
                            )
                        )
                )

                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "photos",
                        style = ZuneTypography.h4.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                        color = Color.White
                    )
                    Text(
                        text = "${photosList.size} items",
                        style = ZuneTypography.body2.copy(fontSize = 11.sp, fontFamily = SegoeUiFontFamily),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            val videosBorderModifier = Modifier
                .border(1.5.dp, Color.White.copy(alpha = 0.15f))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(videosBorderModifier)
                    .background(Color(0xFF1E1E1E))
                    .metroClickable { onNavigateToVideos() }
                    .clipToBounds(),
                contentAlignment = Alignment.BottomStart
            ) {
                if (videoThumbnail != null) {
                    Image(
                        bitmap = videoThumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalZuneAccent.current)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                startY = 80f
                            )
                        )
                )

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(4.dp)
                        .align(Alignment.TopEnd)
                )

                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "videos",
                        style = ZuneTypography.h4.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = SegoeUiFontFamily),
                        color = Color.White
                    )
                    Text(
                        text = "${videosList.size} clips",
                        style = ZuneTypography.body2.copy(fontSize = 11.sp, fontFamily = SegoeUiFontFamily),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Text(
            text = "open pictures + videos",
            style = ZuneTypography.h2.copy(
                fontFamily = SegoeUiLightFontFamily,
                fontSize = 24.sp,
                color = LocalZuneAccent.current
            ),
            modifier = Modifier.metroClickable { onNavigateToPhotos() }
        )
        Text(
            text = "view and organize photos and local videos from your device.",
            style = ZuneTypography.body1,
            color = ZuneTextSecondary
        )
    }
}

// Private helper function for formatting millisecond track timelines
private fun formatPanelTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "$minutes:${String.format("%02d", seconds)}"
}

@Composable
fun FeaturedSectionView(
    audioItems: List<AudioItem>,
    onPlayAlbum: (String) -> Unit,
    isAeroTheme: Boolean = false
) {
    val albums = remember(audioItems) {
        audioItems.distinctBy { it.album }.shuffled().take(4)
    }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        albums.chunked(2).forEach { rowAlbums ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowAlbums.forEach { item ->
                    val cardGlassModifier = Modifier.background(Color(0xFF1E1E1E))

                    Box(
                        modifier = Modifier
                            .weight(1f)
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
                    }
                }
                if (rowAlbums.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
