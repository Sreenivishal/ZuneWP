package com.zune.player.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import com.zune.player.ui.theme.ZuneTypography
import com.zune.player.ui.theme.AeroBlueOrbGradient
import com.zune.player.ui.theme.SegoeUiLightFontFamily

import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember

@Composable
fun PivotLayout(
    title: String? = null,
    pages: List<Any>,
    initialPage: Int = 1,
    modifier: Modifier = Modifier,
    isBlackBackground: Boolean = false,
    isAeroTheme: Boolean = false,
    onOffsetChanged: (Float) -> Unit = {},
    onPageSelected: (Int) -> Unit = {},
    content: @Composable (Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    val coroutineScope = rememberCoroutineScope()

    // Report scroll for parallax based on HorizontalPager state
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }
            .collect { offset ->
                onOffsetChanged(offset)
            }
    }

    LaunchedEffect(pagerState.currentPage) {
        onPageSelected(pagerState.currentPage)
    }

    val animatedVisibilityScope = com.zune.player.LocalAnimatedVisibilityScope.current
    val isTransitionRunning = if (animatedVisibilityScope != null) {
        animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState
    } else {
        false
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(0.dp),
            beyondViewportPageCount = 1,
            userScrollEnabled = !isTransitionRunning,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val rawOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val absOffset = kotlin.math.abs(rawOffset)
                        
                        // Scale inactive pages down to 0.40f
                        val scale = 1f - (absOffset * 0.6f).coerceIn(0f, 0.6f)
                        scaleX = scale
                        scaleY = scale
                        
                        // Do not fade away
                        alpha = 1f
                        
                        // Translate inactive page to hold it on screen as a preview
                        val w = size.width
                        translationX = rawOffset * w * 0.7f
                    }
            ) {
                content(page)
                if (pagerState.currentPage != page) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(page)
                                }
                            }
                    )
                }
            }
        }
        if (title != null) {
            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(maxWidth = Int.MAX_VALUE)
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                    .graphicsLayer {
                        val realOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        if (realOffset < 1f) {
                            // Scrolling between Pins (0) and Now Playing (1)
                            // Scroll the header completely off-screen to the right when on Pins
                            val distance = 1f - realOffset
                            translationX = distance * size.width
                        } else {
                            // Standard parallax for Now Playing and beyond
                            val offsetFromNowPlaying = realOffset - 1f
                            translationX = -offsetFromNowPlaying * 150f
                        }
                        clip = false
                    }
                    .padding(start = 24.dp, top = if (isAeroTheme) 24.dp else 0.dp)
            ) {
                Text(
                    text = title,
                    style = if (isAeroTheme) {
                        ZuneTypography.h1.copy(
                            fontFamily = SegoeUiLightFontFamily,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.sp,
                            brush = AeroBlueOrbGradient
                        )
                    } else {
                        ZuneTypography.h1.copy(
                            fontFamily = SegoeUiLightFontFamily,
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Thin,
                            letterSpacing = (-3).sp
                        )
                    },
                    color = if (isAeroTheme) Color.Unspecified else Color.White,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible
                )
            }
        }

    }
}
