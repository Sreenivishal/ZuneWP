package com.zune.player.data

import android.net.Uri

data class AudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val albumArtUri: Uri?,
    val durationMs: Long
)
