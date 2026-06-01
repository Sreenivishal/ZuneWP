package com.zune.player.data

data class OnlineSong(
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val previewUrl: String, // Represents videoId
    val artworkUrl: String,
    val durationMs: Long
)

data class OnlineAlbum(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: String,
    val artworkUrl: String
)

data class OnlineArtist(
    val browseId: String,
    val name: String,
    val subscribers: String,
    val artworkUrl: String
)
