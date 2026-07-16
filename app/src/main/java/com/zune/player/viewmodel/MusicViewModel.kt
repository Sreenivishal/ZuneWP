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
    val player = AudioPlayer.getInstance(application, forceReconnect = true)
    
    private val _audioItems = MutableStateFlow<List<AudioItem>>(emptyList())
    val audioItems: StateFlow<List<AudioItem>> = _audioItems.asStateFlow()

    private val _playlists = MutableStateFlow<List<String>>(emptyList())
    val playlists: StateFlow<List<String>> = _playlists.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _pinnedItems = MutableStateFlow<List<Pair<Long, Int>>>(emptyList())
    val pinnedItems: StateFlow<List<Pair<Long, Int>>> = _pinnedItems.asStateFlow()

    private val artistRepo = org.koin.core.context.GlobalContext.get().get<com.maxrave.domain.repository.ArtistRepository>()
    private val _followedArtists = MutableStateFlow<List<com.maxrave.domain.data.entities.ArtistEntity>>(emptyList())
    val followedArtists: StateFlow<List<com.maxrave.domain.data.entities.ArtistEntity>> = _followedArtists.asStateFlow()

    init {
        viewModelScope.launch {
            player.currentAudio.collect { item ->
                if (item != null) {
                    val prefs = application.getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putLong("last_played_id", item.id).apply()
                }
            }
        }
        viewModelScope.launch {
            artistRepo.getFollowedArtists().collect { artists ->
                _followedArtists.value = artists
            }
        }
    }

    fun loadMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            _audioItems.value = repository.getAudioItems()
            _playlists.value = repository.getPlaylists()
            loadPinned()
            
            val prefs = getApplication<Application>().getSharedPreferences("zune_prefs", android.content.Context.MODE_PRIVATE)
            val queueJson = prefs.getString("last_queue_json", null)
            val lastQueueIndex = prefs.getInt("last_queue_index", 0)
            
            var queueRestored = false
            if (!queueJson.isNullOrEmpty() && player.currentAudio.value == null) {
                try {
                    val jsonArray = org.json.JSONArray(queueJson)
                    val itemsList = mutableListOf<AudioItem>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.getLong("id")
                        val title = obj.getString("title")
                        val artist = obj.getString("artist")
                        val album = obj.optString("album", "")
                        val uriStr = obj.getString("uri")
                        val albumArtUriStr = obj.optString("albumArtUri", "")
                        val durationMs = obj.optLong("durationMs", 0L)
                        
                        itemsList.add(
                            AudioItem(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                uri = android.net.Uri.parse(uriStr),
                                albumArtUri = if (albumArtUriStr.isEmpty()) null else android.net.Uri.parse(albumArtUriStr),
                                durationMs = durationMs
                            )
                        )
                    }
                    if (itemsList.isNotEmpty()) {
                        player.restoreLastQueue(itemsList, lastQueueIndex)
                        queueRestored = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (!queueRestored) {
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

    fun getItemsForCategory(category: String): List<Any> {
        val items = audioItems.value
        return when (category.lowercase()) {
            // "artists" -> items.map { it.artist }.distinct().sorted()
            "artists" -> followedArtists.value
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
                if (!player.shuffleEnabled.value) {
                    player.toggleShuffle()
                }
                player.playList(items, items.indices.random())
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

    fun saveQueueAsPlaylist(name: String, queue: List<AudioItem>) {
        viewModelScope.launch {
            repository.createPlaylist(name)
            repository.savePlaylistTracks(name, queue)
            loadMusic()
        }
    }

    fun deletePlaylist(name: String) {
        viewModelScope.launch {
            repository.deletePlaylist(name)
            loadMusic()
        }
    }

    fun renamePlaylist(oldName: String, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(oldName, newName)
            loadMusic()
        }
    }

    fun addItemToPlaylist(playlistName: String, item: AudioItem) {
        viewModelScope.launch {
            val added = repository.addToPlaylist(playlistName, item)
            _playlists.value = repository.getPlaylists()
            if (added) {
                android.widget.Toast.makeText(getApplication(), "added to playlist", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(getApplication(), "song is already in this playlist", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun savePlaylistTracks(playlistName: String, tracks: List<AudioItem>) {
        viewModelScope.launch {
            repository.savePlaylistTracks(playlistName, tracks)
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
