package com.zune.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zune.player.data.AudioItem
import com.zune.player.data.MusicRepository
import com.zune.player.player.AudioPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)
    val player = AudioPlayer(application)
    
    private val _audioItems = MutableStateFlow<List<AudioItem>>(emptyList())
    val audioItems: StateFlow<List<AudioItem>> = _audioItems.asStateFlow()

    private val _playlists = MutableStateFlow<List<String>>(emptyList())
    val playlists: StateFlow<List<String>> = _playlists.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _pinnedItems = MutableStateFlow<List<Pair<Long, Int>>>(emptyList())
    val pinnedItems: StateFlow<List<Pair<Long, Int>>> = _pinnedItems.asStateFlow()

    init {
        viewModelScope.launch {
            player.queue.collect { queue ->
                saveQueueToPrefs(queue)
            }
        }
        viewModelScope.launch {
            player.currentAudio.collect { item ->
                if (item != null) {
                    val prefs = application.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putLong("last_played_id", item.id).apply()
                    saveCurrentIndexToPrefs()
                }
            }
        }
    }

    fun loadMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val newAudio = repository.getAudioItems()
                val newPlaylists = repository.getPlaylists()
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _audioItems.value = newAudio
                    _playlists.value = newPlaylists
                    loadPinned()
                    
                    // Restore last played queue and track
                    val prefs = getApplication<Application>().getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE)
                    val queueStr = prefs.getString("last_played_queue", "") ?: ""
                    val lastPlayedIndex = prefs.getInt("last_played_index", 0)
                    
                    if (queueStr.isNotEmpty()) {
                        val queueIds = queueStr.split(",").mapNotNull { it.toLongOrNull() }
                        val restoredQueue = queueIds.mapNotNull { id -> _audioItems.value.find { it.id == id } }
                        if (restoredQueue.isNotEmpty() && player.currentAudio.value == null) {
                            val safeIndex = lastPlayedIndex.coerceIn(restoredQueue.indices)
                            player.restoreLastQueue(restoredQueue, safeIndex)
                        }
                    } else {
                        val lastPlayedId = prefs.getLong("last_played_id", -1L)
                        if (lastPlayedId != -1L) {
                            val lastItem = _audioItems.value.find { it.id == lastPlayedId }
                            if (lastItem != null && player.currentAudio.value == null) {
                                player.restoreLastPlayed(lastItem)
                            }
                        }
                    }
                    
                    _isLoading.value = false
                }
            }
        }
    }


    fun getItemsForCategory(category: String): List<Any> {
        val items = audioItems.value
        return when (category.lowercase()) {
            "artists" -> items.map { it.artist }.distinct().sorted()
            "albums" -> {
                val grouped = items.groupBy { it.album }
                grouped.keys.sorted().mapNotNull { grouped[it]?.firstOrNull() }
            }
            "songs" -> items.sortedBy { it.title }
            "playlists" -> playlists.value
            else -> emptyList()
        }
    }

    fun playCategoryQueue(category: String, startItemTitle: String) {
        viewModelScope.launch {
            val playlist = resolveItems(category, startItemTitle)
            if (playlist.isEmpty()) return@launch
            
            val startIndex = if (category.lowercase() == "playlists") 0 
                             else playlist.indexOfFirst { it.title == startItemTitle }.coerceAtLeast(0)
            player.playList(playlist, startIndex)
        }
    }

    fun playCategoryShuffle(category: String, itemTitle: String) {
        viewModelScope.launch {
            val items = resolveItems(category, itemTitle)
            if (items.isNotEmpty()) {
                player.playList(items.shuffled())
                if (!player.shuffleEnabled.value) {
                    player.toggleShuffle()
                }
            }
        }
    }

    suspend fun getPlaylistTracks(playlistName: String): List<AudioItem> {
        return repository.getPlaylistTracks(playlistName)
    }

    suspend fun getAlbumTracks(albumName: String): List<AudioItem> {
        return resolveItems("albums", albumName)
    }

    fun addCategoryToQueue(category: String, itemTitle: String) {
        viewModelScope.launch {
            val items = if (category.lowercase() == "songs") {
                audioItems.value.filter { it.title == itemTitle }
            } else {
                resolveItems(category, itemTitle)
            }
            player.addToQueue(items)
        }
    }

    fun playCategoryNext(category: String, itemTitle: String) {
        viewModelScope.launch {
            val items = if (category.lowercase() == "songs") {
                audioItems.value.filter { it.title == itemTitle }
            } else {
                resolveItems(category, itemTitle)
            }
            player.playNext(items)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
            loadMusic()
        }
    }

    fun deletePlaylist(name: String) {
        viewModelScope.launch {
            repository.deletePlaylist(name)
            loadMusic()
        }
    }

    fun addItemToPlaylist(playlistName: String, item: AudioItem) {
        viewModelScope.launch {
            repository.addToPlaylist(playlistName, item)
            // Optionally reload playlists if we are looking at them
            _playlists.value = repository.getPlaylists()
        }
    }

    private suspend fun resolveItems(category: String, itemTitle: String): List<AudioItem> {
        val allItems = audioItems.value
        return when (category.lowercase()) {
            "artists" -> allItems.filter { it.artist == itemTitle }.sortedBy { it.title }
            "albums"  -> allItems.filter { it.album  == itemTitle }.sortedBy { it.title }
            "songs"   -> allItems.sortedBy { it.title }
            "playlists" -> repository.getPlaylistTracks(itemTitle)
            else      -> emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    private fun loadPinned() {
        val prefs = getApplication<Application>().getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE)
        val str = prefs.getString("pinned_songs", "") ?: ""
        if (str.isNotEmpty()) {
            _pinnedItems.value = str.split(",").mapNotNull {
                val p = it.split(":")
                if (p.size == 2) Pair(p[0].toLong(), p[1].toInt()) else null
            }
        }
    }

    private fun savePinned() {
        val prefs = getApplication<Application>().getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("pinned_songs", _pinnedItems.value.joinToString(",") { "${it.first}:${it.second}" }).apply()
    }

    private fun saveQueueToPrefs(queue: List<AudioItem>) {
        if (queue.isEmpty()) return
        val prefs = getApplication<Application>().getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE)
        val queueStr = queue.joinToString(",") { it.id.toString() }
        prefs.edit().putString("last_played_queue", queueStr).apply()
        saveCurrentIndexToPrefs()
    }

    private fun saveCurrentIndexToPrefs() {
        val current = player.currentAudio.value ?: return
        val queue = player.queue.value
        val index = queue.indexOfFirst { it.id == current.id }
        if (index != -1) {
            val prefs = getApplication<Application>().getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putInt("last_played_index", index).apply()
        }
    }

    fun pinSong(id: Long) {
        if (_pinnedItems.value.none { it.first == id }) {
            _pinnedItems.value = _pinnedItems.value + Pair(id, 2)
            savePinned()
        }
    }

    fun unpinSong(id: Long) {
        _pinnedItems.value = _pinnedItems.value.filter { it.first != id }
        savePinned()
    }

    fun cyclePinSize(id: Long) {
        _pinnedItems.value = _pinnedItems.value.map {
            if (it.first == id) {
                val newSize = when (it.second) {
                    1 -> 2
                    2 -> 4
                    else -> 1
                }
                Pair(id, newSize)
            } else it
        }
        savePinned()
    }

    fun reorderPinned(fromIndex: Int, toIndex: Int) {
        val list = _pinnedItems.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _pinnedItems.value = list
            savePinned()
        }
    }
}
