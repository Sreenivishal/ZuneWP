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
import com.zune.player.ui.theme.ZuneIcons
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
import androidx.compose.ui.draw.drawWithContent
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

fun Color.lightenForText(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = hsl[2].coerceAtLeast(0.6f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

@Composable
fun rememberVideoTileThumbnail(context: android.content.Context, videoUri: android.net.Uri?): android.graphics.Bitmap? {
    var bitmap by remember(videoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(videoUri) {
        if (videoUri != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(videoUri, android.util.Size(512, 512), null)
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

@Composable
fun TileEqualizer(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "EqAnim")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqBar1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqBar2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqBar3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EqBar4"
    )

    Row(
        modifier = modifier.height(24.dp).width(36.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(h1, h2, h3, h4)
        heights.forEach { heightVal ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightVal)
                    .background(color)
            )
        }
    }
}

@Composable
fun PinnedTileView(
    tileItem: PinnedTileItem,
    size: Int,
    isPlaying: Boolean,
    isEditMode: Boolean,
    isHovered: Boolean,
    isDragged: Boolean,
    dragOffset: Offset,
    isAeroTheme: Boolean,
    onPlay: (PinnedTileItem) -> Unit,
    onUnpin: (Long) -> Unit,
    onCycleSize: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val videoThumbnail = if (tileItem.type == "video") rememberVideoTileThumbnail(context, tileItem.imageUri as? android.net.Uri) else null

    // Determine photo/gallery app exception
    val isPhotoOrGalleryApp = remember(tileItem) {
        tileItem.type == "app" && (tileItem.title.lowercase().contains("photo") || tileItem.title.lowercase().contains("gallery"))
    }
    val shouldLoadPhotos = isPhotoOrGalleryApp

    // Photo cycling slideshow
    val localPhotos = remember { mutableStateListOf<PhotoItem>() }
    LaunchedEffect(shouldLoadPhotos) {
        if (shouldLoadPhotos) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val list = queryLocalPhotos(context)
                if (list.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        localPhotos.clear()
                        localPhotos.addAll(list)
                    }
                }
            }
        }
    }

    // Notification Reader for app status using ZuneNotificationListenerService
    val activeNotifications by com.zune.player.service.ZuneNotificationListenerService.activeNotifications.collectAsState()
    val matchingNotifs = remember(activeNotifications, tileItem.title) {
        val titleLabel = tileItem.title.lowercase().trim()
        activeNotifications.filter { sbn ->
            val pkg = sbn.packageName.lowercase()
            val appLabel = try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString().lowercase()
            } catch (e: Exception) {
                ""
            }
            
            // Check direct match
            val isDirectMatch = pkg.contains(titleLabel) || appLabel.contains(titleLabel)
            
            // Check generic alias maps for native Metro/Zune apps to standard Android apps
            isDirectMatch || when (titleLabel) {
                "phone", "dialer", "call", "calls" -> {
                    pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("telecom") || pkg.contains("telephony") ||
                    appLabel.contains("phone") || appLabel.contains("dialer") || appLabel.contains("call")
                }
                "people", "contacts" -> {
                    pkg.contains("contacts") || pkg.contains("people") ||
                    appLabel.contains("contacts") || appLabel.contains("people")
                }
                "messaging", "messages", "sms", "text" -> {
                    pkg.contains("messaging") || pkg.contains("message") || pkg.contains("mms") || pkg.contains("sms") ||
                    appLabel.contains("message") || appLabel.contains("messaging") || appLabel.contains("sms") || appLabel.contains("chat")
                }
                "email", "mail", "gmail", "outlook" -> {
                    pkg.contains("mail") || pkg.contains("gm") || pkg.contains("outlook") ||
                    appLabel.contains("mail") || appLabel.contains("gmail") || appLabel.contains("outlook")
                }
                "internet", "browser", "chrome", "explorer" -> {
                    pkg.contains("browser") || pkg.contains("chrome") || pkg.contains("webview") || pkg.contains("firefox") ||
                    appLabel.contains("browser") || appLabel.contains("chrome") || appLabel.contains("internet")
                }
                "music", "zune", "player" -> {
                    pkg.contains("music") || pkg.contains("player") || pkg.contains("zune") ||
                    appLabel.contains("music") || appLabel.contains("player") || appLabel.contains("zune")
                }
                "photos", "gallery", "camera" -> {
                    pkg.contains("photo") || pkg.contains("gallery") || pkg.contains("camera") || pkg.contains("media") ||
                    appLabel.contains("photo") || appLabel.contains("gallery") || appLabel.contains("camera")
                }
                else -> false
            }
        }
    }

    var currentNotifIndex by remember { mutableIntStateOf(0) }
    var activeNotificationsText by remember { mutableStateOf("") }
    
    // Periodic update fallback to ensure listener service list is updated
    LaunchedEffect(Unit) {
        while (true) {
            try {
                com.zune.player.service.ZuneNotificationListenerService.updateNotifications()
            } catch (e: Exception) {
                // ignore
            }
            delay(5000)
        }
    }

    LaunchedEffect(matchingNotifs, currentNotifIndex, tileItem.title) {
        if (matchingNotifs.isNotEmpty()) {
            val matchingNotif = matchingNotifs.getOrNull(currentNotifIndex % matchingNotifs.size) ?: matchingNotifs.first()
            val notif = matchingNotif.notification
            val extras = notif?.extras
            val appLabel = try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(matchingNotif.packageName, 0)).toString()
            } catch (e: Exception) {
                matchingNotif.packageName
            }
            
            // Extract title and text
            val titleStr = extras?.get("android.title")?.toString() ?: extras?.get("android.title.big")?.toString()
            var textStr = extras?.get("android.text")?.toString()
            if (textStr.isNullOrEmpty()) {
                textStr = extras?.get("android.bigText")?.toString()
            }
            if (textStr.isNullOrEmpty()) {
                val lines = extras?.getCharSequenceArray("android.textLines")
                if (lines != null && lines.isNotEmpty()) {
                    textStr = lines.lastOrNull()?.toString()
                }
            }
            if (textStr.isNullOrEmpty()) {
                textStr = notif?.tickerText?.toString()
            }
            
            val detailsText = when {
                !titleStr.isNullOrEmpty() && !textStr.isNullOrEmpty() -> "$titleStr: $textStr"
                !titleStr.isNullOrEmpty() -> titleStr
                !textStr.isNullOrEmpty() -> textStr
                else -> ""
            }
            
            activeNotificationsText = if (detailsText.isNotEmpty()) {
                detailsText
            } else {
                appLabel
            }
        } else {
            activeNotificationsText = "no notifications"
        }
    }

    // Check if live tile updates are allowed
    val isLiveAllowed = remember(tileItem, activeNotificationsText, size, isPlaying) {
        if (size == 1) {
            false
        } else if (tileItem.type == "app") {
            false
        } else if (tileItem.type == "song") {
            !isPlaying // Allow live updates for songs except when currently playing!
        } else {
            val label = tileItem.title.lowercase()
            val isException = label.contains("photo") || label.contains("gallery")
            // Calendar and Clock are statically rendered on front face, so they don't slide/flip
            val isCalOrClock = label.contains("calendar") || label.contains("clock") || label.contains("time")
            !isCalOrClock && isException
        }
    }

    var tileState by remember { mutableIntStateOf(0) }

    var transitionStyle by remember(tileItem.id) {
        mutableIntStateOf((tileItem.id % 7).toInt()) // 0=FlipY, 1=VertSlide, 2=HorizSlide, 3=FlipX, 4=DiagSlide, 5=Scale, 6=Fade
    }

    if (size > 1 && tileItem.type != "app") {
        LaunchedEffect(tileItem.id, isLiveAllowed) {
            if (!isLiveAllowed) {
                tileState = 0
                return@LaunchedEffect
            }
            delay(kotlin.random.Random.nextLong(1000, 5000))
            while (true) {
                delay(kotlin.random.Random.nextLong(5000, 12000))
                tileState = if (tileState == 0) {
                    transitionStyle = kotlin.random.Random.nextInt(7) // Dynamically randomize among all 7 transition styles
                    if (kotlin.random.Random.nextBoolean()) 1 else 2
                } else {
                    0
                }
            }
        }
    }

    var currentPhotoIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(tileState) {
        if (tileState == 0 && localPhotos.isNotEmpty()) {
            currentPhotoIndex = (currentPhotoIndex + 1) % localPhotos.size
        }
    }

    LaunchedEffect(tileState) {
        if (tileState == 0 && matchingNotifs.isNotEmpty()) {
            currentNotifIndex = (currentNotifIndex + 1) % matchingNotifs.size
        }
    }

    val activePhotoUri = if (shouldLoadPhotos && localPhotos.isNotEmpty()) {
        localPhotos.getOrNull(currentPhotoIndex)?.uri
    } else {
        null
    }

    val backPhotoUri = if (shouldLoadPhotos && localPhotos.size > 1) {
        localPhotos.getOrNull((currentPhotoIndex + 1) % localPhotos.size)?.uri
    } else {
        null
    }

    val imageModel = if (tileItem.type == "video") videoThumbnail else (activePhotoUri ?: tileItem.imageUri)



    val flipAngle by animateFloatAsState(
        targetValue = if (tileState != 0 && transitionStyle == 0) 180f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "TileFlipAngle"
    )

    val flipAngleX by animateFloatAsState(
        targetValue = if (tileState != 0 && transitionStyle == 3) 180f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "TileFlipAngleX"
    )

    val slidePercent by animateFloatAsState(
        targetValue = if (tileState == 0) {
            0f
        } else {
            if (tileItem.type == "song" && transitionStyle == 1) 0.5f else 1f
        },
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "TileSlidePercent"
    )

    val scalePercent by animateFloatAsState(
        targetValue = if (tileState != 0 && transitionStyle == 5) 0f else 1f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "TileScalePercent"
    )

    val fadePercent by animateFloatAsState(
        targetValue = if (tileState != 0 && transitionStyle == 6) 0f else 1f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "TileFadePercent"
    )

    val isFlipped = (transitionStyle == 0 && flipAngle > 90f) || (transitionStyle == 3 && flipAngleX > 90f)

    // Dynamic text info for Calendar and Clock exceptions
    val calendarText = remember {
        val sdf = java.text.SimpleDateFormat("EEEE, MMMM dd", java.util.Locale.US)
        sdf.format(java.util.Date())
    }

    var currentTimeText by remember { mutableStateOf("") }
    LaunchedEffect(tileItem.id) {
        val label = tileItem.title.lowercase()
        if (label.contains("clock") || label.contains("time")) {
            while (true) {
                val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
                currentTimeText = sdf.format(java.util.Date())
                delay(10000)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        val isCalendar = remember(tileItem) { tileItem.title.lowercase().contains("calendar") }
        val isClock = remember(tileItem) { tileItem.title.lowercase().contains("clock") || tileItem.title.lowercase().contains("time") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .then(
                    if (isAeroTheme) {
                        val tileShape = RoundedCornerShape(6.dp)
                        val innerShape = RoundedCornerShape(5.dp)
                        if (isPlaying) {
                            Modifier
                                .border(width = 1.dp, color = LocalZuneAccent.current.copy(alpha = 0.5f), shape = tileShape)
                                .padding(1.dp)
                                .border(width = 1.5.dp, brush = Brush.verticalGradient(listOf(Color.White, LocalZuneAccent.current)), shape = innerShape)
                                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), LocalZuneAccent.current.copy(alpha = 0.08f))), shape = innerShape)
                                .clip(innerShape)
                        } else {
                            Modifier
                                .border(width = 1.dp, color = Color.Black.copy(alpha = 0.35f), shape = tileShape)
                                .padding(1.dp)
                                .border(width = 1.dp, brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))), shape = innerShape)
                                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))), shape = innerShape)
                                .clip(innerShape)
                        }
                    } else {
                        val isWhiteAccent = LocalZuneAccent.current == Color.White || LocalZuneAccent.current.toArgb() == -1
                        val backImageModel = if (shouldLoadPhotos) backPhotoUri else null
                        val showWhiteBorder = isWhiteAccent && (if (isFlipped) backImageModel == null else imageModel == null)
                        val tileBackground = if (isWhiteAccent) Color.Black else LocalZuneAccent.current
                        val tileModifier = if (showWhiteBorder) {
                            Modifier.border(width = if (isPlaying) 3.dp else 1.5.dp, color = Color.White)
                        } else {
                            if (isPlaying && !isWhiteAccent) Modifier.border(3.dp, LocalZuneAccent.current) else Modifier
                        }
                        Modifier
                            .then(tileModifier)
                            .background(tileBackground)
                    }
                )
                .graphicsLayer {
                    if (transitionStyle == 0) {
                        rotationY = flipAngle
                        cameraDistance = 12f * density
                    } else if (transitionStyle == 3) {
                        rotationX = flipAngleX
                        cameraDistance = 12f * density
                    }
                }
        ) {
            if (isFlipped) {
                // BACK FACE (Flip details)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (transitionStyle == 0) rotationY = 180f
                            if (transitionStyle == 3) rotationX = 180f
                        }
                ) {
                    val backImageModel = if (shouldLoadPhotos) backPhotoUri else null
                    if (backImageModel != null) {
                        AsyncImage(
                            model = backImageModel,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                         val backText = remember(tileItem, activeNotificationsText, size) {
                             val raw = when (tileItem.type) {
                                 "app" -> activeNotificationsText
                                 "song" -> {
                                     if (size == 4) {
                                         "${tileItem.title} BY ${tileItem.subtitle}"
                                     } else {
                                         tileItem.title
                                     }
                                 }
                                 "photo" -> "view gallery"
                                 "video" -> "play clip"
                                 else -> "pin status"
                             }
                             raw.uppercase()
                         }
                         val adjustedBackFontSize = remember(backText, size) {
                             val length = backText.length
                             val base = if (size == 4) 18 else 13
                             val adjusted = when {
                                 length > 40 -> base - 3
                                 length > 20 -> base - 1
                                 else -> base
                             }
                             adjusted.coerceAtLeast(10).sp
                         }
                         AutoScaleText(
                             text = backText,
                             style = ZuneTypography.h2.copy(
                                 fontSize = adjustedBackFontSize,
                                 fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.zune.player.R.font.segoeuithibd)),
                                 fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                 letterSpacing = (-1).sp
                             ),
                             color = Color.White,
                             modifier = Modifier.fillMaxWidth(),
                             maxLines = 2
                         )
                    }
                }
            } else {
                // FRONT FACE
                if (isCalendar) {
                    val dayOfWeekAbbr = remember {
                        val sdf = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
                        sdf.format(java.util.Date()).lowercase()
                    }
                    val dayOfMonth = remember {
                        val sdf = java.text.SimpleDateFormat("dd", java.util.Locale.US)
                        sdf.format(java.util.Date())
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        // Top-left: Event info
                        Column(modifier = Modifier.align(Alignment.TopStart)) {
                            Text(
                                text = "no upcoming events",
                                style = ZuneTypography.h2.copy(
                                    fontSize = if (size == 4) 14.sp else 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = SegoeUiFontFamily
                                ),
                                color = Color.White
                            )
                        }

                        // Bottom-right: Day and Date
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = dayOfWeekAbbr,
                                style = ZuneTypography.h2.copy(
                                    fontSize = if (size == 4) 18.sp else 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = SegoeUiFontFamily
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = dayOfMonth,
                                style = ZuneTypography.h1.copy(
                                    fontSize = if (size == 4) 54.sp else 42.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = SegoeUiLightFontFamily
                                ),
                                color = Color.White
                            )
                        }
                    }
                } else if (isClock) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Text(
                            text = currentTimeText.lowercase(),
                            style = ZuneTypography.h1.copy(
                                fontSize = if (size == 4) 32.sp else 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SegoeUiLightFontFamily
                            ),
                            color = Color.White
                        )
                    }
                } else {
                    // Under slide details shown under slides
                    if (size > 1 && transitionStyle != 0) {
                        val slideImageModel = if (shouldLoadPhotos) backPhotoUri else null
                        if (slideImageModel != null) {
                            AsyncImage(
                                model = slideImageModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                             val displayText = remember(tileItem, activeNotificationsText, size) {
                                 val raw = if (tileItem.type == "app" && activeNotificationsText.isNotEmpty() && activeNotificationsText != "no notifications") {
                                     activeNotificationsText
                                 } else if (tileItem.type == "song") {
                                     if (size == 4) {
                                         "${tileItem.title} BY ${tileItem.subtitle}"
                                     } else {
                                         tileItem.title
                                     }
                                 } else {
                                     tileItem.title
                                 }
                                 raw.uppercase()
                             }
                             val adjustedSlideFontSize = remember(displayText, size) {
                                 val length = displayText.length
                                 val base = if (size == 4) 22 else 18
                                 val adjusted = when {
                                     length > 40 -> base - 6
                                     length > 20 -> base - 3
                                     else -> base
                                 }
                                 adjusted.coerceAtLeast(11).sp
                             }
                             AutoScaleText(
                                 text = displayText,
                                 style = ZuneTypography.h2.copy(
                                     fontSize = adjustedSlideFontSize,
                                     fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.zune.player.R.font.segoeuithibd)),
                                     fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                     letterSpacing = (-1).sp
                                 ),
                                 color = Color.White,
                                 modifier = Modifier.fillMaxWidth(),
                                 maxLines = 2
                             )
                        }
                    }

                    // Front slide container
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (transitionStyle == 1) {
                                    translationY = this.size.height * slidePercent
                                } else if (transitionStyle == 2) {
                                    translationX = this.size.width * slidePercent
                                } else if (transitionStyle == 4) {
                                    translationX = this.size.width * slidePercent
                                    translationY = this.size.height * slidePercent
                                } else if (transitionStyle == 5) {
                                    scaleX = scalePercent
                                    scaleY = scalePercent
                                } else if (transitionStyle == 6) {
                                    alpha = fadePercent
                                }
                            }
                    ) {
                        if (imageModel != null) {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = tileItem.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                alpha = if (isEditMode && !isHovered) 0.7f else 1f
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isAeroTheme) LocalZuneAccent.current.copy(alpha = 0.6f) else LocalZuneAccent.current)
                            )
                        }

                        // Notification count badge overlay on the front face of standard app tiles (excluding Calendar/Clock)
                        if (tileItem.type == "app" && matchingNotifs.isNotEmpty() && !isCalendar && !isClock) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = matchingNotifs.size.toString(),
                                    style = ZuneTypography.h1.copy(
                                        fontSize = if (size == 4) 28.sp else 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = SegoeUiFontFamily
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Title overlay at the bottom
                if (size > 1 && !isCalendar && !isClock && tileItem.type != "song" && tileItem.type != "photo") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tileItem.title.lowercase(),
                            style = ZuneTypography.h2.copy(
                                fontSize = if (size == 4) 14.sp else 12.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Edit control buttons overlay
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
                    .metroClickable { onUnpin(tileItem.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(ZuneIcons.Close, contentDescription = "Unpin", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
                    .metroClickable { onCycleSize(tileItem.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(ZuneIcons.Refresh, contentDescription = "Resize", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun PinnedPage(
    pinnedItems: List<Pair<PinnedTileItem, Int>>,
    currentPlayingId: Long?,
    onPlay: (PinnedTileItem) -> Unit,
    onUnpin: (Long) -> Unit,
    onCycleSize: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    isAeroTheme: Boolean,
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> },
    isNested: Boolean = false
) {
    var isEditMode by remember { mutableStateOf(false) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var hoveredId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var pointerOffset by remember { mutableStateOf(Offset.Zero) }
    val itemBounds = remember { mutableStateMapOf<Long, Rect>() }

    val initialPos = remember { getScrollPosition("home_pinned") }
    val scrollState = if (isNested) null else rememberScrollState(initial = initialPos.first)

    if (!isNested && scrollState != null) {
        DisposableEffect(scrollState) {
            onDispose {
                onScrollPositionChanged("home_pinned", scrollState.value, 0)
            }
        }
    }

    val columnModifier = if (isNested) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState!!)
    }

    Column(
        modifier = columnModifier
            .pointerInput(isEditMode) {
                if (isEditMode) {
                    detectTapGestures { isEditMode = false }
                }
            }
    ) {
        if (!isNested) {
            val pinsTitleStyle = if (isAeroTheme) {
                ZuneTypography.h2.copy(
                    fontSize = 48.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    letterSpacing = 0.sp,
                    brush = AeroBlueOrbGradient
                )
            } else {
                ZuneTypography.h2.copy(
                    fontSize = 80.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    letterSpacing = 2.sp
                )
            }
            val pinsTitleColor = if (isAeroTheme) Color.Unspecified else Color.White
            val pinsTitlePadding = if (isAeroTheme) {
                Modifier.padding(start = 24.dp, bottom = 16.dp, top = 24.dp)
            } else {
                Modifier.padding(start = 24.dp, bottom = 16.dp, top = 8.dp)
            }

            androidx.compose.material.Text(
                text = if (isAeroTheme) "Pins" else "pins",
                style = pinsTitleStyle,
                color = pinsTitleColor,
                modifier = pinsTitlePadding
            )
        }

        if (pinnedItems.isEmpty()) {
            PlaceholderPage("pinned items")
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val columns = if (isLandscape) 7 else 4
                val horizontalSpacing = 8.dp
                val verticalSpacing = 8.dp
                val colWidth = (maxWidth - horizontalSpacing * (columns - 1)) / columns

                val occupied = remember(pinnedItems, columns) {
                    mutableSetOf<Pair<Int, Int>>()
                }
                val placements = remember(pinnedItems, columns) {
                    val map = mutableMapOf<Long, Rect>()
                    occupied.clear()
                    for ((tileItem, size) in pinnedItems) {
                        val w = if (size == 4) 4 else if (size == 2) 2 else 1
                        val h = if (size == 4) 2 else if (size == 2) 2 else 1
                        var found = false
                        var searchY = 0
                        while (!found) {
                            for (searchX in 0..columns - w) {
                                var collision = false
                                for (dy in 0 until h) {
                                    for (dx in 0 until w) {
                                        if (occupied.contains(Pair(searchX + dx, searchY + dy))) {
                                            collision = true
                                            break
                                        }
                                    }
                                    if (collision) break
                                }
                                if (!collision) {
                                    for (dy in 0 until h) {
                                        for (dx in 0 until w) {
                                            occupied.add(Pair(searchX + dx, searchY + dy))
                                        }
                                    }
                                    map[tileItem.id] = Rect(
                                        left = searchX.toFloat(),
                                        top = searchY.toFloat(),
                                        right = (searchX + w).toFloat(),
                                        bottom = (searchY + h).toFloat()
                                    )
                                    found = true
                                    break
                                }
                            }
                            if (!found) searchY++
                        }
                    }
                    map
                }

                val maxY = if (occupied.isEmpty()) 0 else occupied.maxOf { it.second } + 1
                val totalHeight = if (maxY > 0) (colWidth * maxY) + (verticalSpacing * (maxY - 1)) else 0.dp

                Box(modifier = Modifier.fillMaxWidth().height(totalHeight + 0.dp).padding(top = 0.dp)) {
                    pinnedItems.forEachIndexed { index, (tileItem, size) ->
                        key(tileItem.id) {
                            val rect = placements[tileItem.id]
                            if (rect != null) {
                                val id = tileItem.id
                        val isDragged = draggedId == id
                        val isHovered = hoveredId == id
                        val isPlaying = currentPlayingId == id
                        val xOffset = (colWidth * rect.left) + (horizontalSpacing * rect.left)
                        val yOffset = (colWidth * rect.top) + (verticalSpacing * rect.top)
                        val animatedXOffset by animateDpAsState(
                            targetValue = xOffset,
                            animationSpec = if (isDragged) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                            label = "TileXOffset"
                        )
                        val animatedYOffset by animateDpAsState(
                            targetValue = yOffset,
                            animationSpec = if (isDragged) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                            label = "TileYOffset"
                        )
                        val width = (colWidth * rect.width) + (horizontalSpacing * (rect.width - 1f))
                        val height = (colWidth * rect.height) + (verticalSpacing * (rect.height - 1f))

                        val targetScale = if (isDragged) 1.05f else if (isEditMode) {
                            if (isHovered) 0.85f else 0.92f
                        } else 1f
                        val animatedScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                            label = "TileScale"
                        )

                        val targetAlpha = if (isDragged) 0.9f else if (isEditMode) {
                            if (isHovered) 0.5f else 1f
                        } else 1f
                        val animatedAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(durationMillis = 200),
                            label = "TileAlpha"
                        )

                        Box(
                            modifier = Modifier
                                .offset(x = animatedXOffset, y = animatedYOffset)
                                .size(width = width, height = height)
                                .onGloballyPositioned { coordinates ->
                                    itemBounds[id] = coordinates.boundsInWindow()
                                }
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                    alpha = animatedAlpha
                                    if (isDragged) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                    }
                                }
                                .then(
                                    if (isAeroTheme) {
                                        val tileShape = RoundedCornerShape(6.dp)
                                        val innerShape = RoundedCornerShape(5.dp)
                                        if (isPlaying) {
                                            Modifier
                                                .border(
                                                    width = 1.dp,
                                                    color = LocalZuneAccent.current.copy(alpha = 0.5f),
                                                    shape = tileShape
                                                )
                                                .padding(1.dp)
                                                .border(
                                                    width = 1.5.dp,
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White,
                                                            LocalZuneAccent.current
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.18f),
                                                            LocalZuneAccent.current.copy(alpha = 0.08f)
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .clip(innerShape)
                                        } else {
                                            Modifier
                                                .border(
                                                    width = 1.dp,
                                                    color = Color.Black.copy(alpha = 0.35f),
                                                    shape = tileShape
                                                )
                                                .padding(1.dp)
                                                .border(
                                                    width = 1.dp,
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.55f),
                                                            Color.White.copy(alpha = 0.08f)
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.14f),
                                                            Color.White.copy(alpha = 0.04f)
                                                        )
                                                    ),
                                                    shape = innerShape
                                                )
                                                .clip(innerShape)
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .pointerInput(isEditMode, id) {
                                    if (isEditMode) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                draggedId = id
                                                hoveredId = null
                                                dragOffset = Offset.Zero
                                                pointerOffset = offset
                                            },
                                            onDragEnd = {
                                                if (hoveredId != null && draggedId != null && hoveredId != draggedId) {
                                                    val sourceIndex = pinnedItems.indexOfFirst { it.first.id == draggedId }
                                                    val targetIndex = pinnedItems.indexOfFirst { it.first.id == hoveredId }
                                                    if (sourceIndex != -1 && targetIndex != -1) {
                                                        onMove(sourceIndex, targetIndex)
                                                    }
                                                }
                                                draggedId = null
                                                hoveredId = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggedId = null
                                                hoveredId = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount

                                                val myBounds = itemBounds[id]
                                                if (myBounds != null) {
                                                    val absoluteFingerPos = myBounds.topLeft + pointerOffset + dragOffset

                                                    var newHovered: Long? = null
                                                    for ((targetId, bounds) in itemBounds) {
                                                        if (targetId != id && bounds.contains(absoluteFingerPos)) {
                                                            newHovered = targetId
                                                            break
                                                        }
                                                    }
                                                    hoveredId = newHovered
                                                }
                                            }
                                        )
                                    } else {
                                        detectTapGestures(
                                            onTap = { onPlay(tileItem) },
                                            onLongPress = { isEditMode = true }
                                        )
                                    }
                                }
                        ) {
                            PinnedTileView(
                                tileItem = tileItem,
                                size = size,
                                isPlaying = isPlaying,
                                isEditMode = isEditMode,
                                isHovered = isHovered,
                                isDragged = isDragged,
                                dragOffset = dragOffset,
                                isAeroTheme = isAeroTheme,
                                onPlay = onPlay,
                                onUnpin = onUnpin,
                                onCycleSize = onCycleSize
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

@Composable
fun AutoScaleText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 2
) {
    var fontSize by remember(text, style.fontSize) { mutableStateOf(style.fontSize) }
    var readyToDraw by remember(text, style.fontSize) { mutableStateOf(false) }

    Text(
        text = text,
        style = style.copy(fontSize = fontSize),
        color = color,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow && fontSize.value > 8f) {
                fontSize = (fontSize.value * 0.9f).sp
            } else {
                readyToDraw = true
            }
        }
    )
}