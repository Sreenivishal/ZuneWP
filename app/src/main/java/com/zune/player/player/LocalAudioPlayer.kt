package com.zune.player.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zune.player.data.AudioItem
import com.zune.player.data.LyricLine
import com.zune.player.data.LyricsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalAudioPlayer(private val context: Context) {
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var localController: MediaController? = null
    var localPlaylist = emptyList<AudioItem>()
    private var localPositionPollJob: Job? = null
    private var localLyricsJob: Job? = null

    private var isUserScrubbing = false
    private var isActive = false

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

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                localController = controllerFuture.get()
                setupControllerListener()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun setActive(active: Boolean) {
        isActive = active
        if (!active) {
            pause()
        } else {
            if (localController?.isPlaying == true) {
                _isPlaying.value = true
                startPollingPosition()
            }
        }
    }

    private fun setupControllerListener() {
        val controller = localController ?: return
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startPollingPosition()
                } else {
                    localPositionPollJob?.cancel()
                    _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
                    _duration.value = controller.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackState.value = playbackState
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                _duration.value = controller.duration.coerceAtLeast(0L)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val mediaId = mediaItem?.mediaId
                var matchedItem = localPlaylist.find { it.uri.toString() == mediaId }
                if (matchedItem == null) {
                    matchedItem = localPlaylist.find { it.id.toString() == mediaId }
                }
                if (matchedItem != null) {
                    _currentAudio.value = matchedItem
                }
                _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
                _duration.value = controller.duration.coerceAtLeast(0L)

                refreshQueue()
                updateUpcomingQueue()
                fetchLyrics()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleEnabled.value = shuffleModeEnabled
                refreshQueue()
                updateUpcomingQueue()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
        })
    }

    private fun startPollingPosition() {
        localPositionPollJob?.cancel()
        localPositionPollJob = playerScope.launch {
            while (this@launch.isActive) {
                val controller = localController
                if (controller != null && controller.isPlaying) {
                    if (!isUserScrubbing) {
                        _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
                    }
                    _duration.value = controller.duration.coerceAtLeast(0L)

                    val lines = _lyrics.value
                    if (lines.isNotEmpty()) {
                        val targetPosition = if (isUserScrubbing) _currentPosition.value else controller.currentPosition
                        _currentLyricIndex.value = lines.indexOfLast { it.timeMs <= targetPosition }
                    }
                }
                delay(500L)
            }
        }
    }

    private fun refreshQueue() {
        val controller = localController ?: return
        val count = controller.mediaItemCount
        val rebuilt = mutableListOf<AudioItem>()
        for (i in 0 until count) {
            val mi = controller.getMediaItemAt(i)
            val mediaId = mi.mediaId
            var item = localPlaylist.find { it.uri.toString() == mediaId }
            if (item == null) {
                item = localPlaylist.find { it.id.toString() == mediaId }
            }
            if (item != null) rebuilt.add(item)
        }
        _queue.value = rebuilt
    }

    private fun updateUpcomingQueue() {
        val q = _queue.value
        val curr = _currentAudio.value
        val index = q.indexOfFirst { it.id == curr?.id }
        _upcomingQueue.value = if (index in q.indices) {
            q.drop(index + 1).take(3)
        } else {
            emptyList()
        }
    }

    private fun fetchLyrics() {
        localLyricsJob?.cancel()
        val item = _currentAudio.value
        if (item == null) {
            _lyrics.value = emptyList()
            _currentLyricIndex.value = -1
            return
        }
        _lyrics.value = emptyList()
        _currentLyricIndex.value = -1
        localLyricsJob = playerScope.launch {
            try {
                val fetched = LyricsRepository.getLyrics(item.title, item.artist)
                _lyrics.value = fetched
            } catch (e: Exception) {
                _lyrics.value = emptyList()
            }
        }
    }

    private fun buildMediaItem(item: AudioItem): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)
            .setArtworkUri(item.albumArtUri)
            .build()
        return MediaItem.Builder()
            .setMediaId(item.uri.toString())
            .setUri(item.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun play(item: AudioItem) {
        val controller = localController ?: return
        localPlaylist = listOf(item)
        _currentAudio.value = item
        _queue.value = listOf(item)
        updateUpcomingQueue()

        val mediaItem = buildMediaItem(item)
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
        fetchLyrics()
    }

    fun playList(items: List<AudioItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        val controller = localController ?: return
        localPlaylist = items
        _currentAudio.value = items[startIndex]
        _queue.value = items
        updateUpcomingQueue()

        val mediaItems = items.map { buildMediaItem(it) }
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
        fetchLyrics()
    }
    
    fun restoreLastQueue(items: List<AudioItem>, startIndex: Int) {
        if (items.isEmpty()) return
        val controller = localController ?: return
        localPlaylist = items
        _currentAudio.value = items[startIndex]
        _queue.value = items
        updateUpcomingQueue()
        
        val mediaItems = items.map { buildMediaItem(it) }
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        fetchLyrics()
    }

    fun addToQueue(items: List<AudioItem>) {
        val controller = localController ?: return
        val newItems = items.filter { item -> !localPlaylist.any { it.id == item.id } }
        localPlaylist = localPlaylist + newItems
        controller.addMediaItems(items.map { buildMediaItem(it) })
        _queue.value = _queue.value + items
        updateUpcomingQueue()
    }

    fun playNext(items: List<AudioItem>) {
        val controller = localController ?: return
        val insertAt = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItems(insertAt, items.map { buildMediaItem(it) })
        val updated = _queue.value.toMutableList()
        updated.addAll(insertAt, items)
        _queue.value = updated
        val newItems = items.filter { item -> !localPlaylist.any { it.id == item.id } }
        localPlaylist = localPlaylist + newItems
        updateUpcomingQueue()
    }

    fun toggleShuffle() {
        val controller = localController ?: return
        val next = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = next
        _shuffleEnabled.value = next
    }

    fun toggleRepeat() {
        val controller = localController ?: return
        val next = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = next
        _repeatMode.value = next
    }

    fun playFromQueue(index: Int) {
        localController?.seekTo(index, 0L)
        localController?.play()
    }

    fun reorderQueue(from: Int, to: Int) {
        val controller = localController ?: return
        controller.moveMediaItem(from, to)
        val updated = _queue.value.toMutableList()
        if (from in updated.indices && to in updated.indices) {
            val item = updated.removeAt(from)
            updated.add(to, item)
            _queue.value = updated
            localPlaylist = updated
            updateUpcomingQueue()
        }
    }

    fun removeFromQueue(index: Int) {
        val controller = localController ?: return
        controller.removeMediaItem(index)
        val updated = _queue.value.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            _queue.value = updated
            localPlaylist = updated
            updateUpcomingQueue()
        }
    }

    fun resume() {
        localController?.play()
    }

    fun pause() {
        localController?.pause()
    }

    fun togglePlayPause() {
        if (localController?.isPlaying == true) pause() else resume()
    }

    fun setUserScrubbing(scrubbing: Boolean) {
        isUserScrubbing = scrubbing
    }

    fun seekTo(position: Long) {
        _currentPosition.value = position
        localController?.seekTo(position)
    }

    fun skipToNext() {
        if (localController?.hasNextMediaItem() == true) localController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        if (localController?.hasPreviousMediaItem() == true) {
            localController?.seekToPreviousMediaItem()
        } else {
            localController?.seekTo(0L)
        }
    }

    fun release() {
        localPositionPollJob?.cancel()
        localLyricsJob?.cancel()
        playerScope.cancel()
        localController?.release()
    }
}
