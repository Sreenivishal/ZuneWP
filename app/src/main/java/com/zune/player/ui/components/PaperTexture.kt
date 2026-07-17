package com.zune.player.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * Applies a premium paper-like grain and fiber texture overlay to the Composable content
 * (e.g. Album Art) using a native BitmapShader repetition for optimal performance.
 */
fun Modifier.paperTexture(): Modifier = composed {
    val paperTexture = remember {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val random = java.util.Random()
        for (x in 0 until 128) {
            for (y in 0 until 128) {
                // Background paper noise: range from 220 to 255 (near white, textured)
                val noiseVal = 220 + random.nextInt(36)
                // Fiber details (tiny specs of grey/brown/black fibers)
                val isFiber = random.nextFloat() < 0.02f
                val alpha = if (isFiber) 18 else 3 // subtle texture alpha
                bmp.setPixel(x, y, android.graphics.Color.argb(alpha, noiseVal, noiseVal, noiseVal))
            }
        }
        bmp.asImageBitmap()
    }

    this.drawWithContent {
        drawContent() // Draw the underlying album art
        
        // Draw the tiled paper grain/texture on top
        val paint = androidx.compose.ui.graphics.Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            shader = BitmapShader(
                paperTexture.asAndroidBitmap(),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            )
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(
                0f, 0f, size.width, size.height, paint
            )
        }
    }
}
