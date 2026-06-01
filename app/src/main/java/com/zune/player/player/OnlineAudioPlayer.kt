package com.zune.player.player

import android.net.Uri
import androidx.media3.common.Player
import com.maxrave.common.Config.PLAYLIST_CLICK
import com.maxrave.common.Config.RECOVER_TRACK_QUEUE
import com.maxrave.common.Config.SONG_CLICK
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Album
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.data.model.searchResult.songs.Thumbnail
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.mediaservice.handler.RepeatState
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import com.zune.player.data.AudioItem
import com.zune.player.data.LyricLine
import com.zune.player.data.LyricsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class OnlineAudioPlayer(
    private val sharedViewModel: SharedViewModel,
    private val mediaPlayerHandler: MediaPlayerHandler
) {
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val _isPlaying = MutableStateFlow(false)
    val _currentAudio = MutableStateFlow<AudioItem?>(null)
    val _queue = MutableStateFlow<List<AudioItem>>(emptyList())
    val _upcomingQueue = MutableStateFlow<List<AudioItem>>(emptyList())
    val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val _shuffleEnabled = MutableStateFlow(false)
    val _isBuffering = MutableStateFlow(false)
    val _currentPosition = MutableStateFlow(0L)
    val _duration = MutableStateFlow(0L)
    val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val _currentLyricIndex = MutableStateFlow(-1)
    val _playbackState = MutableStateFlow(Player.STATE_IDLE)

    private var isUserScrubbing = false
    private var isActive = false

    init {
        // Sync SimpMusic isPlaying state
        playerScope.launch {
            sharedViewModel.controllerState.map { it.isPlaying }.distinctUntilChanged().collect {
                if (isActive) _isPlaying.value = it
            }
        }

        // Sync SimpMusic repeat mode
        playerScope.launch {
            sharedViewModel.controllerState.map { it.repeatState }.distinctUntilChanged().collect { state ->
                if (isActive) {
                    _repeatMode.value = when (state) {
                        RepeatState.None -> Player.REPEAT_MODE_OFF
                        RepeatState.All -> Player.REPEAT_MODE_ALL
                        RepeatState.One -> Player.REPEAT_MODE_ONE
                    }
                }
            }
        }

        // Sync SimpMusic shuffle mode
        playerScope.launch {
            sharedViewModel.controllerState.map { it.isShuffle }.distinctUntilChanged().collect {
                if (isActive) _shuffleEnabled.value = it
            }
        }

        // Sync SimpMusic duration, position, and buffering
        playerScope.launch {
            sharedViewModel.timeline.collect { timeline ->
                if (isActive) {
                    if (!isUserScrubbing) {
                        _currentPosition.value = timeline.current.coerceAtLeast(0L)
                    }
                    _duration.value = timeline.total.coerceAtLeast(0L)
                    _isBuffering.value = timeline.loading

                    val lines = _lyrics.value
                    if (lines.isNotEmpty()) {
                        val targetPosition = if (isUserScrubbing) _currentPosition.value else timeline.current
                        _currentLyricIndex.value = lines.indexOfLast { it.timeMs <= targetPosition }
                    }
                }
            }
        }

        playerScope.launch {
            mediaPlayerHandler.simpleMediaState.collect { state ->
                if (isActive) {
                    when (state) {
                        is com.maxrave.domain.mediaservice.handler.SimpleMediaState.Ended -> _playbackState.value = Player.STATE_ENDED
                        is com.maxrave.domain.mediaservice.handler.SimpleMediaState.Initial -> _playbackState.value = Player.STATE_IDLE
                        is com.maxrave.domain.mediaservice.handler.SimpleMediaState.Buffering -> _playbackState.value = Player.STATE_BUFFERING
                        is com.maxrave.domain.mediaservice.handler.SimpleMediaState.Loading -> _playbackState.value = Player.STATE_BUFFERING
                        is com.maxrave.domain.mediaservice.handler.SimpleMediaState.Ready -> _playbackState.value = Player.STATE_READY
                        else -> {} // Progress doesn't map directly to Player state transitions cleanly here
                    }
                }
            }
        }

        // Sync SimpMusic current audio item
        playerScope.launch {
            mediaPlayerHandler.nowPlayingState.collect { state ->
                if (isActive) {
                    val mediaItem = state.mediaItem
                    if (mediaItem.mediaId.isEmpty()) {
                        _currentAudio.value = null
                    } else {
                        val isLocal = mediaItem.mediaId.startsWith("content:") || mediaItem.mediaId.startsWith("file:") || mediaItem.mediaId.startsWith("/")
                        val isOnline = !isLocal
                        val uriParsed = if (isOnline) {
                            Uri.parse("zune://online/${mediaItem.mediaId}")
                        } else {
                            val fallbackUri = if (mediaItem.uri.isNullOrEmpty() || mediaItem.uri == "null") mediaItem.mediaId else mediaItem.uri
                            Uri.parse(fallbackUri)
                        }
                        _currentAudio.value = AudioItem(
                            id = mediaItem.mediaId.hashCode().toLong(),
                            title = mediaItem.metadata.title ?: "Unknown",
                            artist = mediaItem.metadata.artist ?: "Unknown",
                            album = mediaItem.metadata.albumTitle ?: "Unknown",
                            uri = uriParsed,
                            albumArtUri = mediaItem.metadata.artworkUri?.let { Uri.parse(it) },
                            durationMs = mediaPlayerHandler.getPlayerDuration().takeIf { it > 0L }
                                ?: _duration.value.takeIf { it > 0L } ?: 0L
                        )
                    }
                }
            }
        }

        // Sync SimpMusic queue and upcoming queue
        playerScope.launch {
            kotlinx.coroutines.flow.combine(
                mediaPlayerHandler.queueData,
                mediaPlayerHandler.currentSongIndex
            ) { queueData, index ->
                Pair(queueData, index)
            }.collect { (queueData, index) ->
                if (isActive) {
                    val tracks = queueData?.data?.listTracks ?: emptyList()
                    val audioItems = tracks.map { track ->
                        val isLocal = track.videoId.startsWith("content:") || track.videoId.startsWith("file:") || track.videoId.startsWith("/")
                        val isOnline = !isLocal
                        val uriParsed = if (isOnline) {
                            Uri.parse("zune://online/${track.videoId}")
                        } else {
                            Uri.parse(track.videoId)
                        }
                        AudioItem(
                            id = track.videoId.hashCode().toLong(),
                            title = track.title,
                            artist = track.artists?.firstOrNull()?.name ?: "Unknown",
                            album = track.album?.name ?: "Unknown",
                            uri = uriParsed,
                            albumArtUri = track.thumbnails?.lastOrNull()?.url?.let { Uri.parse(it) },
                            durationMs = (track.durationSeconds ?: 0) * 1000L
                        )
                    }
                    _queue.value = audioItems

                    val upcoming = if (index in audioItems.indices) {
                        audioItems.drop(index + 1).take(3)
                    } else {
                        emptyList()
                    }
                    _upcomingQueue.value = upcoming
                }
            }
        }

        // Sync SimpMusic lyrics
        playerScope.launch {
            sharedViewModel.nowPlayingScreenData.collect { data ->
                if (isActive) {
                    val lines = data.lyricsData?.lyrics?.lines ?: emptyList()
                    if (lines.isNotEmpty()) {
                        _lyrics.value = lines.map { line ->
                            LyricLine(
                                timeMs = line.startTimeMs.toLongOrNull() ?: 0L,
                                text = line.words
                            )
                        }
                    } else {
                        val current = _currentAudio.value
                        if (current != null) {
                            try {
                                val fetched = LyricsRepository.getLyrics(current.title, current.artist)
                                if (fetched.isNotEmpty() && isActive) {
                                    _lyrics.value = fetched
                                }
                            } catch (e: Exception) { }
                        }
                    }
                }
            }
        }
    }

    fun setActive(active: Boolean) {
        isActive = active
        if (!active) {
            pause()
        } else {
            _isPlaying.value = sharedViewModel.controllerState.value.isPlaying
            _shuffleEnabled.value = sharedViewModel.controllerState.value.isShuffle
            _repeatMode.value = when (sharedViewModel.controllerState.value.repeatState) {
                RepeatState.None -> Player.REPEAT_MODE_OFF
                RepeatState.All -> Player.REPEAT_MODE_ALL
                RepeatState.One -> Player.REPEAT_MODE_ONE
            }
            // Sync initial state is handled by the flows automatically
        }
    }

    private fun AudioItem.toTrack(): Track {
        val isOnline = this.uri.scheme == "zune" && this.uri.host == "online"
        val videoId = if (isOnline) {
            this.uri.path?.removePrefix("/") ?: this.id.toString()
        } else {
            this.uri.toString()
        }

        val artworkUrl = this.albumArtUri?.toString() ?: ""
        return Track(
            album = Album(id = "", name = this.album),
            artists = listOf(Artist(id = "", name = this.artist)),
            duration = "",
            durationSeconds = (this.durationMs / 1000).toInt(),
            isAvailable = true,
            isExplicit = false,
            likeStatus = "INDIFFERENT",
            thumbnails = if (artworkUrl.isNotEmpty()) listOf(Thumbnail(height = 544, url = artworkUrl, width = 544)) else emptyList(),
            title = this.title,
            videoId = videoId,
            videoType = "MUSIC_VIDEO_TYPE_ATV",
            category = "songs",
            feedbackTokens = null,
            resultType = "song",
            year = ""
        )
    }

    fun play(item: AudioItem) {
        playerScope.launch {
            val track = item.toTrack()
            sharedViewModel.loadMediaItemFromTrack(track, SONG_CLICK)
        }
    }

    fun playList(items: List<AudioItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        playerScope.launch {
            val track = items[startIndex].toTrack()
            mediaPlayerHandler.setQueueData(
                QueueData.Data(
                    listTracks = items.map { it.toTrack() },
                    firstPlayedTrack = track,
                    playlistId = "RDAMVM" + track.videoId,
                    playlistName = "Zune Queue",
                    playlistType = PlaylistType.RADIO,
                    continuation = null,
                )
            )
            sharedViewModel.loadMediaItemFromTrack(track, PLAYLIST_CLICK, startIndex)
        }
    }

    fun restoreLastQueue(items: List<AudioItem>, startIndex: Int) {
        if (items.isEmpty()) return
        playerScope.launch {
            val track = items[startIndex].toTrack()
            mediaPlayerHandler.setQueueData(
                QueueData.Data(
                    listTracks = items.map { it.toTrack() },
                    firstPlayedTrack = track,
                    playlistId = "RDAMVM" + track.videoId,
                    playlistName = "Zune Queue",
                    playlistType = PlaylistType.RADIO,
                    continuation = null,
                )
            )
            sharedViewModel.loadMediaItemFromTrack(track, RECOVER_TRACK_QUEUE, startIndex)
        }
    }

    fun addToQueue(items: List<AudioItem>) {
        playerScope.launch {
            mediaPlayerHandler.loadMoreCatalog(ArrayList(items.map { it.toTrack() }), isAddToQueue = true)
        }
    }

    fun playNext(items: List<AudioItem>) {
        playerScope.launch {
            items.forEach { item ->
                mediaPlayerHandler.playNext(item.toTrack())
            }
        }
    }

    fun toggleShuffle() {
        playerScope.launch {
            sharedViewModel.onUIEvent(UIEvent.Shuffle)
        }
    }

    fun toggleRepeat() {
        playerScope.launch {
            sharedViewModel.onUIEvent(UIEvent.Repeat)
        }
    }

    fun playFromQueue(index: Int) {
        mediaPlayerHandler.playMediaItemInMediaSource(index)
    }

    fun reorderQueue(from: Int, to: Int) {
        playerScope.launch {
            mediaPlayerHandler.swap(from, to)
        }
    }

    fun removeFromQueue(index: Int) {
        mediaPlayerHandler.removeMediaItem(index)
    }

    fun resume() {
        playerScope.launch {
            if (!_isPlaying.value) {
                sharedViewModel.onUIEvent(UIEvent.PlayPause)
            }
        }
    }

    fun pause() {
        playerScope.launch {
            if (_isPlaying.value) {
                sharedViewModel.onUIEvent(UIEvent.PlayPause)
            }
        }
    }

    fun togglePlayPause() {
        playerScope.launch {
            sharedViewModel.onUIEvent(UIEvent.PlayPause)
        }
    }

    fun setUserScrubbing(scrubbing: Boolean) {
        isUserScrubbing = scrubbing
    }

    fun seekTo(position: Long) {
        _currentPosition.value = position
        playerScope.launch {
            mediaPlayerHandler.player.seekTo(position)
        }
    }

    fun skipToNext() {
        playerScope.launch {
            sharedViewModel.onUIEvent(UIEvent.Next)
        }
    }

    fun skipToPrevious() {
        playerScope.launch {
            sharedViewModel.onUIEvent(UIEvent.Previous)
        }
    }

    fun release() {
        playerScope.cancel()
    }
}
