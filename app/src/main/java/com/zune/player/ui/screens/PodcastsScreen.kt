package com.zune.player.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zune.player.data.AudioItem
import com.zune.player.player.AudioPlayer
import com.zune.player.ui.components.metroClickable
import com.zune.player.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PodcastInfo(
    val feedUrl: String,
    val title: String,
    val author: String,
    val artworkUrl: String,
    val description: String = ""
)

data class PodcastEpisode(
    val title: String,
    val description: String,
    val streamUrl: String,
    val pubDate: String,
    val duration: String
)

@Composable
fun PodcastsScreen(
    isAeroTheme: Boolean = false,
    player: AudioPlayer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pages = listOf("subscribed", "episodes", "search")
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0) { pages.size }
    val tabWidths = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }

    // Subscriptions list state
    var subscribedPodcasts by remember { mutableStateOf<List<PodcastInfo>>(emptyList()) }
    
    // Search states
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PodcastInfo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Active viewed podcast and its parsed episodes
    var activePodcast by remember { mutableStateOf<PodcastInfo?>(null) }
    var episodesList by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }

    // Load initial subscriptions
    LaunchedEffect(Unit) {
        subscribedPodcasts = getSubscriptions(context)
    }

    // Helper: fetch episodes of active podcast
    fun loadEpisodes(podcast: PodcastInfo) {
        activePodcast = podcast
        episodesList = emptyList()
        isLoadingEpisodes = true
        coroutineScope.launch {
            val xmlText = fetchUrlText(podcast.feedUrl)
            val parsed = parsePodcastRss(xmlText)
            withContext(Dispatchers.Main) {
                episodesList = parsed
                isLoadingEpisodes = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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
            
            // Small category header
            Text(
                text = if (isAeroTheme) "Podcast Hub" else "PODCASTS",
                style = ZuneTypography.h4.copy(
                    fontFamily = SegoeUiLightFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ZuneTextSecondary,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 4.dp)
            )

            // Dynamic sliding tabs
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

            // Pager views
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                when (pageIndex) {
                    0 -> {
                        // "subscribed" page
                        if (subscribedPodcasts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "no subscribed podcasts.\nswipe right to search online.",
                                    style = ZuneTypography.body1.copy(
                                        fontFamily = SegoeUiLightFontFamily,
                                        fontSize = 20.sp
                                    ),
                                    color = ZuneTextSecondary,
                                    modifier = Modifier.padding(24.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(subscribedPodcasts) { _, podcast ->
                                    PodcastCard(
                                        podcast = podcast,
                                        isAeroTheme = isAeroTheme,
                                        onClick = {
                                            loadEpisodes(podcast)
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(1)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // "episodes" detail page
                        val active = activePodcast
                        if (active == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "select a podcast to view episodes",
                                    style = ZuneTypography.body1.copy(fontFamily = SegoeUiLightFontFamily),
                                    color = ZuneTextSecondary
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp)
                            ) {
                                // Header Info
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Cover Art
                                    AsyncImage(
                                        model = active.artworkUrl,
                                        contentDescription = active.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .background(Color(0xFF1E1E1E))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f))
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = active.title,
                                            style = ZuneTypography.h2.copy(
                                                fontFamily = SegoeUiLightFontFamily,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = active.author,
                                            style = ZuneTypography.body2.copy(fontSize = 13.sp),
                                            color = ZuneTextSecondary,
                                            maxLines = 1
                                        )
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // Subscribe Button
                                        val isSubscribed = subscribedPodcasts.any { it.feedUrl == active.feedUrl }
                                        Row(
                                            modifier = Modifier
                                                .border(1.dp, if (isSubscribed) Color.White.copy(alpha = 0.3f) else LocalZuneAccent.current)
                                                .clickable {
                                                    val currentList = subscribedPodcasts.toMutableList()
                                                    if (isSubscribed) {
                                                        currentList.removeAll { it.feedUrl == active.feedUrl }
                                                    } else {
                                                        currentList.add(active)
                                                    }
                                                    subscribedPodcasts = currentList
                                                    saveSubscriptions(context, currentList)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isSubscribed) Icons.Default.Check else Icons.Default.Add,
                                                contentDescription = if (isSubscribed) "subscribed" else "subscribe",
                                                tint = if (isSubscribed) Color.White.copy(alpha = 0.5f) else Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (isSubscribed) "subscribed" else "subscribe",
                                                style = ZuneTypography.caption.copy(fontSize = 11.sp),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                                // Episode List or loading
                                if (isLoadingEpisodes) {
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = LocalZuneAccent.current)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        itemsIndexed(episodesList) { _, episode ->
                                            EpisodeRow(
                                                episode = episode,
                                                onClick = {
                                                    val audioItem = AudioItem(
                                                        id = java.lang.Math.abs(episode.streamUrl.hashCode().toLong()),
                                                        title = episode.title,
                                                        artist = active.title,
                                                        album = active.author,
                                                        uri = Uri.parse(episode.streamUrl),
                                                        albumArtUri = Uri.parse(active.artworkUrl),
                                                        durationMs = parseDuration(episode.duration)
                                                    )
                                                    player.play(audioItem)
                                                    android.widget.Toast.makeText(context, "streaming episode...", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // "search" page
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                        ) {
                            // Search bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("search podcasts online...", color = ZuneTextSecondary, style = ZuneTypography.body1.copy(fontSize = 14.sp)) },
                                    colors = TextFieldDefaults.textFieldColors(
                                        backgroundColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        textColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        if (searchQuery.isNotEmpty()) {
                                            isSearching = true
                                            coroutineScope.launch {
                                                val term = URLEncoder.encode(searchQuery, "UTF-8")
                                                val text = fetchUrlText("https://itunes.apple.com/search?media=podcast&term=$term")
                                                val parsed = parseItunesSearch(text)
                                                withContext(Dispatchers.Main) {
                                                    searchResults = parsed
                                                    isSearching = false
                                                }
                                            }
                                        }
                                    })
                                )
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable {
                                            if (searchQuery.isNotEmpty()) {
                                                isSearching = true
                                                coroutineScope.launch {
                                                    val term = URLEncoder.encode(searchQuery, "UTF-8")
                                                    val text = fetchUrlText("https://itunes.apple.com/search?media=podcast&term=$term")
                                                    val parsed = parseItunesSearch(text)
                                                    withContext(Dispatchers.Main) {
                                                        searchResults = parsed
                                                        isSearching = false
                                                    }
                                                }
                                            }
                                        }
                                )
                            }

                            if (isSearching) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = LocalZuneAccent.current)
                                }
                            } else if (searchResults.isEmpty()) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "enter keywords to find podcasts",
                                        style = ZuneTypography.body1.copy(fontFamily = SegoeUiLightFontFamily),
                                        color = ZuneTextSecondary
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    itemsIndexed(searchResults) { _, podcast ->
                                        PodcastCard(
                                            podcast = podcast,
                                            isAeroTheme = isAeroTheme,
                                            onClick = {
                                                loadEpisodes(podcast)
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(1)
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
        }

        // Circular back arrow action button at the bottom center
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .metroClickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PodcastCard(
    podcast: PodcastInfo,
    isAeroTheme: Boolean,
    onClick: () -> Unit
) {
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(cardGlassModifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = podcast.artworkUrl,
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .background(Color(0xFF1C1C1C))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.title,
                style = ZuneTypography.h2.copy(
                    fontFamily = SegoeUiLightFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = podcast.author,
                style = ZuneTypography.body1.copy(fontSize = 12.sp),
                color = ZuneTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EpisodeRow(
    episode: PodcastEpisode,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = episode.title,
            style = ZuneTypography.h2.copy(
                fontFamily = SegoeUiLightFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = cleanHtml(episode.description),
            style = ZuneTypography.body2.copy(fontSize = 11.sp),
            color = ZuneTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = episode.pubDate.take(16),
                style = ZuneTypography.caption.copy(fontSize = 10.sp),
                color = ZuneTextSecondary
            )
            if (episode.duration.isNotEmpty()) {
                Text(
                    text = episode.duration,
                    style = ZuneTypography.caption.copy(fontSize = 10.sp),
                    color = ZuneTextSecondary
                )
            }
        }
        Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(top = 8.dp))
    }
}

// Networking helper: fetch text from url
suspend fun fetchUrlText(urlStr: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                val newLocation = conn.getHeaderField("Location")
                if (!newLocation.isNullOrEmpty()) {
                    return@withContext fetchUrlText(newLocation)
                }
            }
            
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}

// iTunes Search Parser
fun parseItunesSearch(jsonText: String): List<PodcastInfo> {
    val list = mutableListOf<PodcastInfo>()
    if (jsonText.isEmpty()) return list
    try {
        val json = JSONObject(jsonText)
        val results = json.optJSONArray("results")
        if (results != null) {
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val feedUrl = item.optString("feedUrl", "")
                if (feedUrl.isNotEmpty() && feedUrl != "null") {
                    list.add(
                        PodcastInfo(
                            feedUrl = feedUrl,
                            title = item.optString("collectionName", "unknown podcast"),
                            author = item.optString("artistName", "unknown author"),
                            artworkUrl = item.optString("artworkUrl100", ""),
                            description = ""
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

// Native XML parser for RSS feed
fun parsePodcastRss(xmlText: String): List<PodcastEpisode> {
    val episodes = mutableListOf<PodcastEpisode>()
    if (xmlText.isEmpty()) return episodes
    try {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlText))
        var eventType = parser.eventType
        var currentEpisode: PodcastEpisode? = null
        var inItem = false
        var text = ""
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name.lowercase() == "item") {
                        inItem = true
                        currentEpisode = PodcastEpisode(
                            title = "",
                            description = "",
                            streamUrl = "",
                            pubDate = "",
                            duration = ""
                        )
                    } else if (inItem && name.lowercase() == "enclosure") {
                        val url = parser.getAttributeValue(null, "url")
                        currentEpisode = currentEpisode?.copy(streamUrl = url ?: "")
                    }
                }
                XmlPullParser.TEXT -> {
                    text = parser.text ?: ""
                }
                XmlPullParser.END_TAG -> {
                    if (inItem) {
                        when (name.lowercase()) {
                            "item" -> {
                                currentEpisode?.let { episodes.add(it) }
                                inItem = false
                                currentEpisode = null
                            }
                            "title" -> {
                                currentEpisode = currentEpisode?.copy(title = text.trim())
                            }
                            "description" -> {
                                currentEpisode = currentEpisode?.copy(description = text.trim())
                            }
                            "pubdate" -> {
                                currentEpisode = currentEpisode?.copy(pubDate = text.trim())
                            }
                            "duration" -> {
                                currentEpisode = currentEpisode?.copy(duration = text.trim())
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return episodes
}

// Subscriptions storage in SharedPreferences
fun saveSubscriptions(context: Context, list: List<PodcastInfo>) {
    val prefs = context.getSharedPreferences("zune_podcasts", Context.MODE_PRIVATE)
    val array = JSONArray()
    list.forEach { p ->
        val obj = JSONObject()
        obj.put("feedUrl", p.feedUrl)
        obj.put("title", p.title)
        obj.put("author", p.author)
        obj.put("artworkUrl", p.artworkUrl)
        obj.put("description", p.description)
        array.put(obj)
    }
    prefs.edit().putString("subscribed_list", array.toString()).apply()
}

fun getSubscriptions(context: Context): List<PodcastInfo> {
    val prefs = context.getSharedPreferences("zune_podcasts", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("subscribed_list", "") ?: ""
    val list = mutableListOf<PodcastInfo>()
    if (jsonStr.isNotEmpty()) {
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PodcastInfo(
                        feedUrl = obj.optString("feedUrl"),
                        title = obj.optString("title"),
                        author = obj.optString("author"),
                        artworkUrl = obj.optString("artworkUrl"),
                        description = obj.optString("description")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return list
}

private fun cleanHtml(html: String): String {
    return html.replace(Regex("<[^>]*>"), "").trim()
}

private fun parseDuration(duration: String): Long {
    if (duration.isEmpty()) return 1800000L // 30 mins fallback
    val parts = duration.split(":")
    var ms = 0L
    try {
        if (parts.size == 3) {
            ms += parts[0].toLong() * 3600000L
            ms += parts[1].toLong() * 60000L
            ms += parts[2].toLong() * 1000L
        } else if (parts.size == 2) {
            ms += parts[0].toLong() * 60000L
            ms += parts[1].toLong() * 1000L
        } else {
            ms += parts[0].toLong() * 1000L
        }
    } catch (e: Exception) {
        return duration.toLongOrNull()?.times(1000L) ?: 1800000L
    }
    return ms
}
