package com.zune.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zune.player.data.LyricLine
import com.zune.player.ui.theme.LocalZuneAccent
import com.zune.player.ui.theme.SegoeUiLightFontFamily

/**
 * A highly immersive, scroll-synchronized lyrics view designed with the Metro UI aesthetic.
 * Automatically scroll-aligns to the active line and supports tap-to-seek playback.
 */
@Composable
fun SynchronizedLyricsView(
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    onLyricClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val accent = LocalZuneAccent.current

    // Automatically scroll to and center the active lyric line
    LaunchedEffect(currentLyricIndex) {
        if (currentLyricIndex in lyrics.indices) {
            val targetIndex = (currentLyricIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(index = targetIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (lyrics.isEmpty()) {
            Text(
                text = "no lyrics available",
                fontFamily = SegoeUiLightFontFamily,
                fontSize = 24.sp,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 180.dp, bottom = 220.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isActive = index == currentLyricIndex
                    
                    // Smooth visual transition for active state
                    val fontSize by animateFloatAsState(
                        targetValue = if (isActive) 28f else 22f,
                        animationSpec = tween(durationMillis = 350),
                        label = "LyricSize"
                    )
                    
                    val textColor by animateColorAsState(
                        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                        animationSpec = tween(durationMillis = 350),
                        label = "LyricColor"
                    )

                    val textWeight = if (isActive) FontWeight.SemiBold else FontWeight.Light

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLyricClick(line.timeMs) }
                            .padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = line.text.lowercase(),
                            fontSize = fontSize.sp,
                            fontFamily = SegoeUiLightFontFamily,
                            fontWeight = textWeight,
                            color = textColor,
                            textAlign = TextAlign.Start,
                            lineHeight = (fontSize * 1.3f).sp
                        )
                    }
                }
            }
        }
    }
}
