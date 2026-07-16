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
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.zune.player.LocalSharedTransitionScope
import com.zune.player.LocalAnimatedVisibilityScope
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun PersonalizeScreen(
    onBack: () -> Unit,
    isAeroTheme: Boolean = false,
    getScrollPosition: (String) -> Pair<Int, Int> = { Pair(0, 0) },
    onScrollPositionChanged: (String, Int, Int) -> Unit = { _, _, _ -> }
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
            val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "header_settings"),
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
                text = "settings",
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

        Text(
            text = "personalize",
            style = ZuneTypography.h1.copy(
                fontFamily = SegoeUiFontFamily,
                fontSize = 42.sp
            ),
            color = Color.White,
            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
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

            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    PersonalizePage(
                        getScrollPosition = getScrollPosition,
                        onScrollPositionChanged = onScrollPositionChanged
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .background(Color.White.copy(alpha = 0.05f))
                        .metroClickable {
                            openLauncherSelection(context)
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "default launcher",
                            style = ZuneTypography.h4.copy(
                                fontFamily = SegoeUiFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        Text(
                            text = if (isLauncher) "zune is active" else "tap to set as default launcher",
                            style = ZuneTypography.body2,
                            color = if (isLauncher) LocalZuneAccent.current else Color.LightGray
                        )
                    }
                    if (isLauncher) {
                        Icon(
                            imageVector = ZuneIcons.Check,
                            contentDescription = "Active",
                            tint = LocalZuneAccent.current
                        )
                    }
                }
            }
        }
    }
}

fun isDefaultLauncher(context: android.content.Context): Boolean {
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_HOME)
    }
    val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo?.activityInfo?.packageName == context.packageName
}

fun openLauncherSelection(context: android.content.Context) {
    val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    } else {
        android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
    context.startActivity(intent)
}
