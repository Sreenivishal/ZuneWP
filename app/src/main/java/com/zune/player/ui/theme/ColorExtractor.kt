package com.zune.player.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun extractDominantColor(context: Context, uriString: String?): Color? = withContext(Dispatchers.IO) {
    if (uriString == null) return@withContext null

    try {
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context)
            .data(uriString)
            .allowHardware(false) // required for palette parsing
            .build()

        val result = (loader.execute(request) as? SuccessResult)?.drawable
        val bitmap = result?.toBitmap()

        if (bitmap != null) {
            val palette = Palette.from(bitmap).generate()
            
            val swatches = palette.swatches.sortedByDescending { it.population }
            
            for (swatch in swatches) {
                val rgb = swatch.rgb
                val hsl = FloatArray(3)
                androidx.core.graphics.ColorUtils.colorToHSL(rgb, hsl)
                
                // If it's a valid, visible color (not too light/dark/desaturated), use it!
                if (hsl[2] <= 0.8f && hsl[2] >= 0.2f && hsl[1] >= 0.15f) {
                    return@withContext Color(rgb)
                }
            }
            null
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
