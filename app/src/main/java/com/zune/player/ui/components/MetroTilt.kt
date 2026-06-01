package com.zune.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

/**
 * Simulates the true Windows Phone / Zune UI 3D "tilt" effect.
 * Elements scale down slightly and physically rotate on X/Y axes based on touch position.
 */
fun Modifier.metroClickable(
    onClick: () -> Unit
) = composed {
    var isPressed by remember { mutableStateOf(false) }
    var tapOffset by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "MetroTiltScale"
    )
    
    val rotateX by animateFloatAsState(
        targetValue = if (isPressed && itemSize.height > 0) {
            val normalizedY = (tapOffset.y / itemSize.height) - 0.5f
            -normalizedY * 10f // Toned down tilt angle based on Y
        } else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "MetroTiltRotateX"
    )

    val rotateY by animateFloatAsState(
        targetValue = if (isPressed && itemSize.width > 0) {
            val normalizedX = (tapOffset.x / itemSize.width) - 0.5f
            normalizedX * 10f // Toned down tilt angle based on X
        } else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "MetroTiltRotateY"
    )

    this
        .onSizeChanged { itemSize = it }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationX = rotateX
            rotationY = rotateY
            cameraDistance = 12f * density
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                tapOffset = down.position
                isPressed = true
                
                val up = waitForUpOrCancellation()
                isPressed = false
                if (up != null) {
                    onClick()
                }
            }
        }
}



