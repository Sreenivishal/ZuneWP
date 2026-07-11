package com.zune.player.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zune.player.data.AudioItem
import com.zune.player.data.LyricLine
import com.zune.player.data.LyricsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import com.maxrave.common.Config
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.common.C
import android.net.Uri
import androidx.core.net.toUri

class AudioPlayer private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var instance: AudioPlayer? = null

        fun getInstance(context: Context): AudioPlayer {
            return instance ?: synchronized(this) {
                instance ?: AudioPlayer(context.applicationContext).also { instance = it }
            }
        }
    }

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaController: MediaController? = null
    private var positionPollJob: Job? = null
    private var lyricsJob: Job? = null
    private var isUserScrubbing = false
    private var preloadingJob: Job? = null

    // Store original list to match MediaItem to AudioItem
    private var currentPlaylist = emptyList<AudioItem>()
    private var pendingRestoreItem: AudioItem? = null
    private var pendingRestoreQueue: Pair<List<AudioItem>, Int>? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentAudio = MutableStateFlow<AudioItem?>(null)
    val currentAudio = _currentAudio.asStateFlow()

    // Queue: ordered list of upcoming + current tracks
    private val _queue = MutableStateFlow<List<AudioItem>>(emptyList())
    val queue = _queue.asStateFlow()

    private val _upcomingQueue = MutableStateFlow<List<AudioItem>>(emptyList())
    val upcomingQueue = _upcomingQueue.asStateFlow()

    // Repeat
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    // Shuffle
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled = _shuffleEnabled.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex = _currentLyricIndex.asStateFlow()

    val currentPositionValue: Long get() = mediaController?.currentPosition ?: 0L
    val durationValue: Long get() = mediaController?.duration?.coerceAtLeast(0L) ?: 0L

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
                setupControllerListener()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun setupControllerListener() {
        val controller = mediaController ?: return
        // Sync initial state
        _shuffleEnabled.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode
        refreshQueue()

        pendingRestoreItem?.let {
            applyRestore(it)
            pendingRestoreItem = null
        }

        pendingRestoreQueue?.let { (items, index) ->
            applyRestoreQueue(items, index)
            pendingRestoreQueue = null
        }

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startPollingPosition()
                } else {
                    positionPollJob?.cancel()
                    _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
                    _duration.value = controller.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                _duration.value = controller.duration.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                _isBuffering.value = false
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val mediaId = mediaItem?.mediaId
                val matchedItem = currentPlaylist.find {
                    val itemUriStr = it.uri.toString()
                    val cleanItemUri = if (it.uri.scheme == "zune" && it.uri.host == "online") {
                        itemUriStr.substringAfter("zune://online/")
                    } else {
                        itemUriStr
                    }
                    cleanItemUri == mediaId
                }
                if (matchedItem != null) {
                    _currentAudio.value = matchedItem
                }
                _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
                _duration.value = controller.duration.coerceAtLeast(0L)
                refreshQueue()
                fetchLyrics()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleEnabled.value = shuffleModeEnabled
                refreshQueue()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
        })
    }

    private fun startPollingPosition() {
        positionPollJob?.cancel()
        positionPollJob = playerScope.launch {
            while (isActive) {
                val controller = mediaController
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
                delay(250L)
            }
        }
    }

    private fun fetchLyrics() {
        lyricsJob?.cancel()
        val item = _currentAudio.value
        if (item == null) {
            _lyrics.value = emptyList()
            _currentLyricIndex.value = -1
            return
        }
        _lyrics.value = emptyList()
        _currentLyricIndex.value = -1
        lyricsJob = playerScope.launch {
            try {
                val fetched = LyricsRepository.getLyrics(item.title, item.artist)
                _lyrics.value = fetched
            } catch (e: Exception) {
                _lyrics.value = emptyList()
            }
        }
    }

    /** Rebuilds the queue StateFlow from the current controller playlist. */
    private fun refreshQueue() {
        val controller = mediaController ?: return
        val count = controller.mediaItemCount
        val rebuilt = mutableListOf<AudioItem>()
        for (i in 0 until count) {
            val mi = controller.getMediaItemAt(i)
            val mediaId = mi.mediaId
            val item = currentPlaylist.find {
                val itemUriStr = it.uri.toString()
                val cleanItemUri = if (it.uri.scheme == "zune" && it.uri.host == "online") {
                    itemUriStr.substringAfter("zune://online/")
                } else {
                    itemUriStr
                }
                cleanItemUri == mediaId
            }
            if (item != null) rebuilt.add(item)
        }
        _queue.value = rebuilt

        val index = rebuilt.indexOfFirst { it.id == _currentAudio.value?.id }
        _upcomingQueue.value = if (index in rebuilt.indices) {
            rebuilt.drop(index + 1)
        } else {
            emptyList()
        }
        triggerPreloading()
    }

    private fun registerItems(items: List<AudioItem>) {
        val existingUris = currentPlaylist.map { it.uri.toString() }.toSet()
        val newUnique = items.filter { it.uri.toString() !in existingUris }
        currentPlaylist = currentPlaylist + newUnique
    }

    private fun triggerPreloading() {
        preloadingJob?.cancel()
        val current = _currentAudio.value
        val upcoming = _upcomingQueue.value.take(2)
        
        preloadingJob = playerScope.launch(Dispatchers.IO) {
            if (current != null && current.uri.scheme == "zune" && current.uri.host == "online") {
                val videoId = current.uri.toString().substringAfter("zune://online/")
                preloadSong(videoId, preloadEntire = true)
            }
            
            for (item in upcoming) {
                if (isActive && item.uri.scheme == "zune" && item.uri.host == "online") {
                    val videoId = item.uri.toString().substringAfter("zune://online/")
                    preloadSong(videoId, preloadEntire = false)
                }
            }
        }
    }

    private suspend fun preloadSong(videoId: String, preloadEntire: Boolean) {
        try {
            val playerCache = GlobalContext.get().get<SimpleCache>(named(Config.PLAYER_CACHE))
            val streamRepository = GlobalContext.get().get<com.maxrave.domain.repository.StreamRepository>()
            val dataStoreManager = GlobalContext.get().get<com.maxrave.domain.manager.DataStoreManager>()
            
            val streamUrl = streamRepository.getStream(
                dataStoreManager = dataStoreManager,
                videoId = videoId,
                isDownloading = false,
                isVideo = false
            ).firstOrNull() ?: return
            
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(playerCache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setCacheWriteDataSinkFactory(null)
                
            val cacheDataSource = cacheDataSourceFactory.createDataSource()
            val length = if (preloadEntire) C.LENGTH_UNSET.toLong() else 2 * 1024 * 1024L
            
            val dataSpec = DataSpec.Builder()
                .setUri(Uri.parse(streamUrl))
                .setKey(videoId)
                .setPosition(0)
                .setLength(length)
                .build()
                
            val cacheWriter = CacheWriter(
                cacheDataSource,
                dataSpec,
                null,
                null
            )
            
            cacheWriter.cache()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play(item: AudioItem) {
        if (_currentAudio.value?.id == item.id) {
            resume()
            return
        }

        registerItems(listOf(item))
        val mediaItem = buildMediaItem(item)
        mediaController?.setMediaItem(mediaItem)
        mediaController?.prepare()
        mediaController?.play()

        _currentAudio.value = item
        refreshQueue()
        fetchLyrics()
    }

    fun restoreLastPlayed(item: AudioItem) {
        if (mediaController != null) {
            applyRestore(item)
        } else {
            pendingRestoreItem = item
        }
    }

    private fun applyRestore(item: AudioItem) {
        registerItems(listOf(item))
        val mediaItem = buildMediaItem(item)
        mediaController?.setMediaItem(mediaItem)
        mediaController?.prepare()
        _currentAudio.value = item
        refreshQueue()
        fetchLyrics()
    }

    fun restoreLastQueue(items: List<AudioItem>, startIndex: Int) {
        if (items.isEmpty()) return
        if (mediaController != null) {
            applyRestoreQueue(items, startIndex)
        } else {
            pendingRestoreQueue = Pair(items, startIndex)
        }
    }

    private fun applyRestoreQueue(items: List<AudioItem>, startIndex: Int) {
        registerItems(items)
        val mediaItems = items.map { buildMediaItem(it) }
        val safeIndex = startIndex.coerceIn(items.indices)
        mediaController?.setMediaItems(mediaItems, safeIndex, 0)
        mediaController?.prepare()
        _currentAudio.value = items[safeIndex]
        refreshQueue()
        fetchLyrics()
    }

    fun playList(items: List<AudioItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return

        if (currentPlaylist == items && _currentAudio.value?.id == items[startIndex].id) {
            resume()
            return
        }

        registerItems(items)
        val mediaItems = items.map { buildMediaItem(it) }
        val safeIndex = startIndex.coerceIn(items.indices)
        mediaController?.setMediaItems(mediaItems, safeIndex, 0)
        mediaController?.prepare()
        mediaController?.play()

        _currentAudio.value = items[safeIndex]
        refreshQueue()
        fetchLyrics()
    }

    fun addToQueue(items: List<AudioItem>) {
        val controller = mediaController ?: return
        registerItems(items)
        controller.addMediaItems(items.map { buildMediaItem(it) })
        refreshQueue()
    }

    fun playNext(items: List<AudioItem>) {
        val controller = mediaController ?: return
        registerItems(items)
        val insertAt = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItems(insertAt, items.map { buildMediaItem(it) })
        refreshQueue()
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val next = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = next
        _shuffleEnabled.value = next
    }

    fun toggleRepeat() {
        val controller = mediaController ?: return
        val next = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = next
        _repeatMode.value = next
    }

    /** Jump to a specific index in the queue. */
    fun playFromQueue(index: Int) {
        mediaController?.seekTo(index, 0)
        mediaController?.play()
    }

    /** Move queue item from [from] to [to]. */
    fun reorderQueue(from: Int, to: Int) {
        val controller = mediaController ?: return
        controller.moveMediaItem(from, to)
        val updated = _queue.value.toMutableList()
        if (from in updated.indices && to in updated.indices) {
            val item = updated.removeAt(from)
            updated.add(to, item)
            _queue.value = updated
            currentPlaylist = updated
        }
    }

    /** Remove an item from the queue by its index. */
    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        controller.removeMediaItem(index)
        val updated = _queue.value.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            _queue.value = updated
            currentPlaylist = updated
        }
    }

    fun resume() { mediaController?.play() }
    fun pause() { mediaController?.pause() }

    fun togglePlayPause() {
        if (mediaController?.isPlaying == true) pause() else resume()
    }

    fun seekTo(position: Long) { mediaController?.seekTo(position) }

    fun setUserScrubbing(scrubbing: Boolean) {
        isUserScrubbing = scrubbing
    }

    fun skipToNext() {
        if (mediaController?.hasNextMediaItem() == true) mediaController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        if (mediaController?.hasPreviousMediaItem() == true) {
            mediaController?.seekToPreviousMediaItem()
        } else {
            mediaController?.seekTo(0)
        }
    }

    fun release() {
        positionPollJob?.cancel()
        lyricsJob?.cancel()
        playerScope.cancel()
        mediaController?.release()
    }

    private fun buildMediaItem(item: AudioItem): MediaItem {
        val isOnline = item.uri.scheme == "zune" && item.uri.host == "online"
        
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)
            .setArtworkUri(item.albumArtUri)
            .setDescription(if (isOnline) com.maxrave.common.MERGING_DATA_TYPE.SONG else null)
            .build()

        val builder = MediaItem.Builder()
            .setMediaMetadata(metadata)

        if (isOnline) {
            val videoId = item.uri.toString().substringAfter("zune://online/")
            builder.setMediaId(videoId)
            builder.setUri(videoId)
            builder.setCustomCacheKey(videoId)
        } else {
            builder.setMediaId(item.uri.toString())
            builder.setUri(item.uri)
        }

        return builder.build()
    }
}