package com.zune.player.player

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.Player
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.zune.player.data.AudioItem
import com.zune.player.data.LyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

enum class PlayerMode {
    NONE, LOCAL, ONLINE
}

class AudioPlayer(private val context: Context) : KoinComponent {
    private val sharedViewModel: SharedViewModel by inject()
    private val mediaPlayerHandler: MediaPlayerHandler by inject()

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val localPlayer = LocalAudioPlayer(context)
    private val onlinePlayer = OnlineAudioPlayer(sharedViewModel, mediaPlayerHandler)

    private val unifiedQueue = mutableListOf<AudioItem>()
    private var currentIndex = -1

    private var activePlayerMode = PlayerMode.NONE

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentAudio = MutableStateFlow<AudioItem?>(null)
    val currentAudio = _currentAudio.asStateFlow()

    private val _queue = MutableStateFlow<List<AudioItem>>(emptyList())
    val queue = _queue.asStateFlow()

    private val _upcomingQueue = MutableStateFlow<List<AudioItem>>(emptyList())
    val upcomingQueue = _upcomingQueue.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

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

    init {
        // Start SimpleMediaService for online playback notifications
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                com.maxrave.media3.di.setServiceActivitySession(
                    context,
                    com.zune.player.MainActivity::class.java,
                    service
                )
            }
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        com.maxrave.media3.di.startService(context, serviceConnection)

        // Sync states from local player when active
        playerScope.launch { localPlayer._isPlaying.collect { if (activePlayerMode == PlayerMode.LOCAL) _isPlaying.value = it } }
        playerScope.launch { localPlayer._currentAudio.collect { if (activePlayerMode == PlayerMode.LOCAL) _currentAudio.value = it } }
        playerScope.launch { localPlayer._repeatMode.collect { if (activePlayerMode == PlayerMode.LOCAL) _repeatMode.value = it } }
        playerScope.launch { localPlayer._shuffleEnabled.collect { if (activePlayerMode == PlayerMode.LOCAL) _shuffleEnabled.value = it } }
        playerScope.launch { localPlayer._isBuffering.collect { if (activePlayerMode == PlayerMode.LOCAL) _isBuffering.value = it } }
        playerScope.launch { localPlayer._currentPosition.collect { if (activePlayerMode == PlayerMode.LOCAL) _currentPosition.value = it } }
        playerScope.launch { localPlayer._duration.collect { if (activePlayerMode == PlayerMode.LOCAL) _duration.value = it } }
        playerScope.launch { localPlayer._lyrics.collect { if (activePlayerMode == PlayerMode.LOCAL) _lyrics.value = it } }
        playerScope.launch { localPlayer._currentLyricIndex.collect { if (activePlayerMode == PlayerMode.LOCAL) _currentLyricIndex.value = it } }

        // Sync states from online player when active
        playerScope.launch { onlinePlayer._isPlaying.collect { if (activePlayerMode == PlayerMode.ONLINE) _isPlaying.value = it } }
        playerScope.launch { onlinePlayer._currentAudio.collect { if (activePlayerMode == PlayerMode.ONLINE) _currentAudio.value = it } }
        playerScope.launch { onlinePlayer._repeatMode.collect { if (activePlayerMode == PlayerMode.ONLINE) _repeatMode.value = it } }
        playerScope.launch { onlinePlayer._shuffleEnabled.collect { if (activePlayerMode == PlayerMode.ONLINE) _shuffleEnabled.value = it } }
        playerScope.launch { onlinePlayer._isBuffering.collect { if (activePlayerMode == PlayerMode.ONLINE) _isBuffering.value = it } }
        playerScope.launch { onlinePlayer._currentPosition.collect { if (activePlayerMode == PlayerMode.ONLINE) _currentPosition.value = it } }
        playerScope.launch { onlinePlayer._duration.collect { if (activePlayerMode == PlayerMode.ONLINE) _duration.value = it } }
        playerScope.launch { onlinePlayer._lyrics.collect { if (activePlayerMode == PlayerMode.ONLINE) _lyrics.value = it } }
        playerScope.launch { onlinePlayer._currentLyricIndex.collect { if (activePlayerMode == PlayerMode.ONLINE) _currentLyricIndex.value = it } }
    }

    private fun switchPlayerMode(newMode: PlayerMode) {
        if (activePlayerMode == newMode) return

        if (activePlayerMode == PlayerMode.LOCAL) {
            localPlayer.setActive(false)
        } else if (activePlayerMode == PlayerMode.ONLINE) {
            onlinePlayer.setActive(false)
        }

        activePlayerMode = newMode

        // Ensure state propagates immediately
        if (newMode == PlayerMode.LOCAL) {
            localPlayer.setActive(true)
            _isPlaying.value = localPlayer._isPlaying.value
            _currentAudio.value = localPlayer._currentAudio.value
            _repeatMode.value = localPlayer._repeatMode.value
            _shuffleEnabled.value = localPlayer._shuffleEnabled.value
            _isBuffering.value = localPlayer._isBuffering.value
            _currentPosition.value = localPlayer._currentPosition.value
            _duration.value = localPlayer._duration.value
            _lyrics.value = localPlayer._lyrics.value
            _currentLyricIndex.value = localPlayer._currentLyricIndex.value
        } else if (newMode == PlayerMode.ONLINE) {
            onlinePlayer.setActive(true)
            _isPlaying.value = onlinePlayer._isPlaying.value
            _currentAudio.value = onlinePlayer._currentAudio.value
            _repeatMode.value = onlinePlayer._repeatMode.value
            _shuffleEnabled.value = onlinePlayer._shuffleEnabled.value
            _isBuffering.value = onlinePlayer._isBuffering.value
            _currentPosition.value = onlinePlayer._currentPosition.value
            _duration.value = onlinePlayer._duration.value
            _lyrics.value = onlinePlayer._lyrics.value
            _currentLyricIndex.value = onlinePlayer._currentLyricIndex.value
        }
    }

    private fun checkPlaybackState() {
        playerScope.launch {
            localPlayer._playbackState.collect { state ->
                if (activePlayerMode == PlayerMode.LOCAL && state == Player.STATE_ENDED) {
                    skipToNext()
                }
            }
        }
        playerScope.launch {
            onlinePlayer._playbackState.collect { state ->
                if (activePlayerMode == PlayerMode.ONLINE && state == Player.STATE_ENDED) {
                    skipToNext()
                }
            }
        }
    }
    
    init {
        checkPlaybackState()
    }

    fun play(item: AudioItem) {
        unifiedQueue.clear()
        unifiedQueue.add(item)
        updateQueues()
        currentIndex = 0
        playCurrentIndex()
    }

    fun playList(items: List<AudioItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        unifiedQueue.clear()
        unifiedQueue.addAll(items)
        updateQueues()
        currentIndex = startIndex
        playCurrentIndex()
    }

    fun restoreLastPlayed(item: AudioItem) {
        restoreLastQueue(listOf(item), 0)
    }

    fun restoreLastQueue(items: List<AudioItem>, startIndex: Int) {
        if (items.isEmpty()) return
        unifiedQueue.clear()
        unifiedQueue.addAll(items)
        updateQueues()
        currentIndex = startIndex
        
        val item = unifiedQueue[currentIndex]
        val isOnline = item.uri.scheme == "zune" && item.uri.host == "online"
        if (isOnline) {
            switchPlayerMode(PlayerMode.ONLINE)
            onlinePlayer.restoreLastQueue(listOf(item), 0)
        } else {
            switchPlayerMode(PlayerMode.LOCAL)
            localPlayer.restoreLastQueue(listOf(item), 0)
        }
    }

    fun addToQueue(items: List<AudioItem>) {
        unifiedQueue.addAll(items)
        updateQueues()
        
        // If nothing is playing, start playing the first added item
        if (activePlayerMode == PlayerMode.NONE || currentIndex == -1) {
            currentIndex = 0
            playCurrentIndex()
        }
    }

    fun playNext(items: List<AudioItem>) {
        if (currentIndex == -1) {
            addToQueue(items)
            return
        }
        unifiedQueue.addAll(currentIndex + 1, items)
        updateQueues()
    }

    private fun playCurrentIndex() {
        if (currentIndex !in unifiedQueue.indices) return
        updateQueues()
        val item = unifiedQueue[currentIndex]
        val isOnline = item.uri.scheme == "zune" && item.uri.host == "online"
        if (isOnline) {
            switchPlayerMode(PlayerMode.ONLINE)
            onlinePlayer.play(item)
        } else {
            switchPlayerMode(PlayerMode.LOCAL)
            localPlayer.play(item)
        }
    }

    fun toggleShuffle() {
        // Implement shuffle logic on unifiedQueue later
        if (activePlayerMode == PlayerMode.LOCAL) {
            localPlayer.toggleShuffle()
        } else if (activePlayerMode == PlayerMode.ONLINE) {
            onlinePlayer.toggleShuffle()
        }
    }

    fun toggleRepeat() {
        if (activePlayerMode == PlayerMode.LOCAL) {
            localPlayer.toggleRepeat()
        } else if (activePlayerMode == PlayerMode.ONLINE) {
            onlinePlayer.toggleRepeat()
        }
    }

    fun playFromQueue(index: Int) {
        if (index in unifiedQueue.indices) {
            currentIndex = index
            playCurrentIndex()
        }
    }

    fun reorderQueue(from: Int, to: Int) {
        if (from !in unifiedQueue.indices || to !in unifiedQueue.indices) return
        val item = unifiedQueue.removeAt(from)
        unifiedQueue.add(to, item)
        
        if (currentIndex == from) {
            currentIndex = to
        } else if (currentIndex in (from + 1)..to) {
            currentIndex--
        } else if (currentIndex in to until from) {
            currentIndex++
        }

        updateQueues()

        if (activePlayerMode == PlayerMode.LOCAL) {
            localPlayer.reorderQueue(from, to)
        } else if (activePlayerMode == PlayerMode.ONLINE) {
            onlinePlayer.reorderQueue(from, to)
        }
    }

    fun removeFromQueue(index: Int) {
        if (index !in unifiedQueue.indices) return
        unifiedQueue.removeAt(index)
        
        if (currentIndex == index) {
            // Keep currentIndex, but play the new item at this index
            if (unifiedQueue.isEmpty()) {
                currentIndex = -1
                pause()
            } else {
                if (currentIndex >= unifiedQueue.size) {
                    currentIndex = 0
                }
                playCurrentIndex()
            }
        } else if (currentIndex > index) {
            currentIndex--
        }

        updateQueues()

        if (activePlayerMode == PlayerMode.LOCAL) {
            localPlayer.removeFromQueue(index)
        } else if (activePlayerMode == PlayerMode.ONLINE) {
            onlinePlayer.removeFromQueue(index)
        }
    }

    fun resume() {
        if (activePlayerMode == PlayerMode.LOCAL) {
            localPlayer.resume()
        } else if (activePlayerMode == PlayerMode.ONLINE) {
            onlinePlayer.resume()
        }
    }

    fun pause() {
        if (activePlayerMode == PlayerMode.LOCAL) {
            localPlayer.pause()
        } else if (activePlayerMode == PlayerMode.ONLINE) {
            onlinePlayer.pause()
        }
    }

    fun togglePlayPause() {
        if (activePlayerMode == PlayerMode.LOCAL) {
            localPlayer.togglePlayPause()
        } else if (activePlayerMode == PlayerMode.ONLINE) {
            onlinePlayer.togglePlayPause()
        }
    }

    fun setUserScrubbing(scrubbing: Boolean) {
        if (activePlayerMode == PlayerMode.LOCAL) {
            localPlayer.setUserScrubbing(scrubbing)
        } else if (activePlayerMode == PlayerMode.ONLINE) {
            onlinePlayer.setUserScrubbing(scrubbing)
        }
    }

    fun seekTo(position: Long) {
        if (activePlayerMode == PlayerMode.LOCAL) {
            localPlayer.seekTo(position)
        } else if (activePlayerMode == PlayerMode.ONLINE) {
            onlinePlayer.seekTo(position)
        }
    }

    fun skipToNext() {
        if (unifiedQueue.isEmpty()) return
        
        // Handle repeat mode
        if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
            playCurrentIndex()
            return
        }

        currentIndex++
        if (currentIndex >= unifiedQueue.size) {
            if (_repeatMode.value == Player.REPEAT_MODE_ALL) {
                currentIndex = 0
            } else {
                currentIndex = unifiedQueue.size - 1 // Clamp to end
                pause() // Stop playback
                return
            }
        }
        playCurrentIndex()
    }

    fun skipToPrevious() {
        if (unifiedQueue.isEmpty()) return
        
        // If we've played for more than 3 seconds, just restart the track
        if (_currentPosition.value > 3000) {
            seekTo(0)
            return
        }
        
        currentIndex--
        if (currentIndex < 0) {
            currentIndex = 0
        }
        playCurrentIndex()
    }

    fun release() {
        playerScope.cancel()
        localPlayer.release()
        onlinePlayer.release()
    }

    private fun updateQueues() {
        _queue.value = unifiedQueue.toList()
        _upcomingQueue.value = if (currentIndex in unifiedQueue.indices) {
            unifiedQueue.drop(currentIndex + 1)
        } else {
            emptyList()
        }
    }
}