package com.zune.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.zune.player.ui.theme.LocalZuneAccent

/**
 * Simulates the true Windows Phone / Zune UI 3D "tilt" effect.
 * Elements scale down slightly and physically rotate on X/Y axes based on touch position.
 */
fun Modifier.metroClickable(
    hapticFeedbackEnabled: Boolean = true,
    onClick: () -> Unit
) = composed {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember(context) { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }

    val view = LocalView.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    var tapOffset by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            val hapticEnabled = prefs.getBoolean("haptic_feedback_enabled", true)
            if (hapticFeedbackEnabled && hapticEnabled) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "MetroTiltScale"
    )
    
    val rotateX by animateFloatAsState(
        targetValue = if (isPressed && itemSize.height > 0) {
            val normalizedY = (tapOffset.y / itemSize.height) - 0.5f
            -normalizedY * 12f // Toned down tilt angle based on Y
        } else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "MetroTiltRotateX"
    )

    val rotateY by animateFloatAsState(
        targetValue = if (isPressed && itemSize.width > 0) {
            val normalizedX = (tapOffset.x / itemSize.width) - 0.5f
            normalizedX * 12f // Toned down tilt angle based on X
        } else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
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
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * Simulates the true Windows Phone / Zune UI 3D "tilt" effect based on a manual MutableInteractionSource.
 * Useful for widgets that handle custom gesture detectors (like detectTapGestures).
 */
fun Modifier.metroTilt(
    interactionSource: MutableInteractionSource
) = composed {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE) }
    val view = LocalView.current

    val isPressed by interactionSource.collectIsPressedAsState()

    var tapOffset by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            val hapticEnabled = prefs.getBoolean("haptic_feedback_enabled", true)
            if (hapticEnabled) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "MetroTiltScale"
    )
    
    val rotateX by animateFloatAsState(
        targetValue = if (isPressed && itemSize.height > 0) {
            val normalizedY = (tapOffset.y / itemSize.height) - 0.5f
            -normalizedY * 15f
        } else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "MetroTiltRotateX"
    )

    val rotateY by animateFloatAsState(
        targetValue = if (isPressed && itemSize.width > 0) {
            val normalizedX = (tapOffset.x / itemSize.width) - 0.5f
            normalizedX * 15f
        } else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
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
            }
        }
}




