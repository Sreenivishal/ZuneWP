package com.zune.player.ui.screens

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.Icon
import com.zune.player.ui.theme.ZuneIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zune.player.ui.components.metroClickable
import com.zune.player.LocalSharedTransitionScope
import com.zune.player.LocalAnimatedVisibilityScope
import com.zune.player.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.rememberAsyncImagePainter

object AppsCache {
    var cachedApps: List<AppItem> = emptyList()
    var isInitialized: Boolean = false

    fun invalidate() {
        cachedApps = emptyList()
        isInitialized = false
    }
}

data class AppItem(
    val packageName: String,
    val appName: String,
    val appId: Long
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(
    pinnedIds: List<Long>,
    onPin: (Long) -> Unit,
    onUnpin: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            val q = searchQuery.lowercase().trim()
            installedApps.filter { it.appName.lowercase().contains(q) }
        }
    }

    // Alphabet-grouped items map for quick navigation
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    var showJumpGrid by remember { mutableStateOf(false) }

    val groupedItems = remember(filteredApps) {
        val map = mutableMapOf<Char, MutableList<AppItem>>()
        filteredApps.forEach { app ->
            val title = app.appName.trim()
            if (title.isNotBlank()) {
                val firstChar = title.first().lowercaseChar()
                val key = if (firstChar.isLetter()) firstChar else '#'
                map.getOrPut(key) { mutableListOf() }.add(app)
            }
        }
        val result = mutableListOf<Any>()
        map.keys.sorted().forEach { key ->
            result.add(key)
            result.addAll(map[key]!!)
        }
        result
    }

    // Load installed apps
    LaunchedEffect(Unit) {
        if (AppsCache.isInitialized) {
            installedApps = AppsCache.cachedApps
            isLoading = false
        } else {
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = try {
                    pm.queryIntentActivities(mainIntent, 0)
                } catch (e: Exception) {
                    emptyList()
                }
                
                val apps = resolveInfos.mapNotNull { ri ->
                    val pkgName = ri.activityInfo.packageName
                    if (pkgName == context.packageName) return@mapNotNull null // hide Zune itself from the launcher list
                    val appLabel = ri.loadLabel(pm).toString()
                    val appHash = pkgName.hashCode().toLong() and 0x0FFFFFFFFFFFFFFFL
                    val appId = appHash or 0x3000000000000000L
                    AppItem(pkgName, appLabel, appId)
                }.sortedBy { it.appName.lowercase() }
                
                withContext(Dispatchers.Main) {
                    AppsCache.cachedApps = apps
                    AppsCache.isInitialized = true
                    installedApps = apps
                    isLoading = false
                }
            }
        }
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Back Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "header_apps"),
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
                    text = "apps",
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

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("search apps", color = Color.White.copy(alpha = 0.4f)) },
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

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("loading apps...", color = ZuneTextSecondary, style = ZuneTypography.body1)
                }
            } else {
                if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                        Text(
                            text = if (searchQuery.isEmpty()) "no apps found." else "no matching apps found.",
                            color = ZuneTextSecondary,
                            style = ZuneTypography.body1,
                            modifier = Modifier.padding(start = 24.dp, top = 24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(groupedItems, key = { 
                            when (it) {
                                is Char -> "header_$it"
                                is AppItem -> it.packageName
                                else -> it.hashCode().toString()
                            }
                        }) { item ->
                            when (item) {
                                is Char -> {
                                    val accent = LocalZuneAccent.current
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 16.dp, bottom = 4.dp)
                                            .size(42.dp)
                                            .background(Color(0xFF151515))
                                            .border(width = 1.5.dp, color = accent)
                                            .metroClickable { showJumpGrid = true },
                                        contentAlignment = Alignment.BottomEnd
                                    ) {
                                        Text(
                                            text = item.toString().lowercase(),
                                            style = ZuneTypography.h2.copy(
                                                fontFamily = SegoeUiFontFamily,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White,
                                            modifier = Modifier.padding(bottom = 2.dp, end = 6.dp)
                                        )
                                    }
                                }
                                is AppItem -> {
                                    AppRow(
                                        modifier = Modifier.animateItem(),
                                        app = item,
                                        isPinned = pinnedIds.contains(item.appId),
                                        onPinToggle = {
                                            if (pinnedIds.contains(item.appId)) {
                                                onUnpin(item.appId)
                                                android.widget.Toast.makeText(context, "unpinned ${item.appName}", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                onPin(item.appId)
                                                android.widget.Toast.makeText(context, "pinned ${item.appName}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onClick = {
                                            try {
                                                val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                                                if (launchIntent != null) {
                                                    context.startActivity(launchIntent)
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fullscreen Alphabet Jump Selector Grid (One-Handed, Bottom Positioned)
        AnimatedVisibility(
            visible = showJumpGrid,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.9f, animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .pointerInput(Unit) { detectTapGestures { showJumpGrid = false } },
                contentAlignment = Alignment.BottomCenter
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .pointerInput(Unit) { /* intercept clicks */ }
                ) {
                    val availableLetters = groupedItems.filterIsInstance<Char>().toSet()
                    val alphabet = ('a'..'z').toList() + listOf('#')
                    items(alphabet, key = { it }) { letter ->
                        val hasItems = availableLetters.contains(letter)
                        val accent = LocalZuneAccent.current
                        val modifierWithBorder = if (hasItems) {
                            Modifier
                                .aspectRatio(1f)
                                .background(Color(0xFF151515))
                                .border(width = 1.5.dp, color = accent)
                        } else {
                            Modifier
                                .aspectRatio(1f)
                                .background(Color(0xFF151515))
                        }
                        Box(
                            modifier = modifierWithBorder
                                .metroClickable {
                                    if (hasItems) {
                                        showJumpGrid = false
                                        val index = groupedItems.indexOf(letter)
                                        if (index != -1) {
                                            coroutineScope.launch {
                                                scrollState.scrollToItem(index)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Text(
                                text = letter.toString().lowercase(),
                                style = ZuneTypography.h2.copy(
                                    fontFamily = SegoeUiFontFamily,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (hasItems) Color.White else Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.padding(bottom = 6.dp, end = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppItem,
    isPinned: Boolean,
    onPinToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var appIcon by remember(app.packageName) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(app.packageName) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val icon = pm.getApplicationIcon(app.packageName)
                withContext(Dispatchers.Main) {
                    appIcon = icon
                }
            } catch (e: Exception) {
                // Ignore and keep as null to use colored letter tile fallback
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isWhiteAccent = LocalZuneAccent.current == Color.White
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(if (isWhiteAccent) Color.Black else LocalZuneAccent.current)
                    .then(if (isWhiteAccent) Modifier.border(1.dp, Color.White) else Modifier)
                    .clip(RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                if (appIcon != null) {
                    androidx.compose.foundation.Image(
                        painter = rememberAsyncImagePainter(model = appIcon),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                    )
                } else {
                    Text(
                        text = app.appName.firstOrNull()?.toString()?.uppercase() ?: "",
                        style = ZuneTypography.body1.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            Text(
                text = app.appName,
                style = ZuneTypography.h2.copy(
                    fontFamily = SegoeUiFontFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.background(Color(0xFF1E1E1E))
        ) {
            DropdownMenuItem(
                onClick = {
                    onPinToggle()
                    menuExpanded = false
                }
            ) {
                Text(
                    text = if (isPinned) "unpin from start" else "pin to start",
                    color = Color.White,
                    style = ZuneTypography.body1
                )
            }
            DropdownMenuItem(
                onClick = {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${app.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    menuExpanded = false
                }
            ) {
                Text(
                    text = "app info",
                    color = Color.White,
                    style = ZuneTypography.body1
                )
            }
            DropdownMenuItem(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = android.net.Uri.parse("package:${app.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    menuExpanded = false
                }
            ) {
                Text(
                    text = "uninstall",
                    color = Color.White,
                    style = ZuneTypography.body1
                )
            }
        }
    }
}
