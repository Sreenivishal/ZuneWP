package com.zune.player.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.component.inject
import kotlinx.coroutines.flow.firstOrNull

class MusicRepository(private val context: Context) : org.koin.core.component.KoinComponent {

    suspend fun getAudioItems(): List<AudioItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<AudioItem>()
        
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        // Only explicitly music files
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val query: Cursor? = context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            val artworkUri = Uri.parse("content://media/external/audio/albumart")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Title"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdColumn)
                val duration = cursor.getLong(durationColumn)

                val contentUri = ContentUris.withAppendedId(collection, id)
                val albumArtUri = ContentUris.withAppendedId(artworkUri, albumId)

                audioList.add(
                    AudioItem(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        uri = contentUri,
                        albumArtUri = albumArtUri,
                        durationMs = duration
                    )
                )
            }
        }
        
        // Merge offline downloaded files from SongRepository
        try {
            val songRepo: com.maxrave.domain.repository.SongRepository by inject()
            val songs = songRepo.getDownloadedSongs().firstOrNull() ?: emptyList()
            val items = songs.map { song ->
                AudioItem(
                    id = song.videoId.hashCode().toLong(),
                    title = song.title,
                    artist = song.artistName?.firstOrNull() ?: "Unknown Artist",
                    album = song.albumName ?: "Unknown Album",
                    uri = Uri.parse("zune://online/${song.videoId}"),
                    albumArtUri = song.thumbnails?.let { Uri.parse(it) },
                    durationMs = song.durationSeconds * 1000L
                )
            }
            audioList.addAll(items)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        audioList.sortBy { it.title.lowercase() }
        audioList
    }

    private val playlistPrefs by lazy {
        context.getSharedPreferences("zune_playlists_v2", Context.MODE_PRIVATE)
    }

    suspend fun getPlaylists(): List<String> = withContext(Dispatchers.IO) {
        val jsonStr = playlistPrefs.getString("playlists_list", "") ?: ""
        val list = mutableListOf<String>()
        if (jsonStr.isNotEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        list.distinct().sorted()
    }

    suspend fun getPlaylistTracks(playlistName: String): List<AudioItem> = withContext(Dispatchers.IO) {
        val jsonStr = playlistPrefs.getString("playlist_tracks_$playlistName", "") ?: ""
        val tracks = mutableListOf<AudioItem>()
        if (jsonStr.isNotEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    tracks.add(
                        AudioItem(
                            id = obj.getLong("id"),
                            title = obj.getString("title"),
                            artist = obj.getString("artist"),
                            album = obj.getString("album"),
                            uri = Uri.parse(obj.getString("uri")),
                            albumArtUri = obj.optString("albumArtUri", "").let { if (it.isNotEmpty()) Uri.parse(it) else null },
                            durationMs = obj.getLong("durationMs")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        tracks
    }

    suspend fun createPlaylist(name: String) = withContext(Dispatchers.IO) {
        val current = getPlaylists().toMutableList()
        if (!current.contains(name)) {
            current.add(name)
            val array = JSONArray()
            current.forEach { array.put(it) }
            playlistPrefs.edit().putString("playlists_list", array.toString()).apply()
            playlistPrefs.edit().putString("playlist_tracks_$name", JSONArray().toString()).apply()
        }
    }

    suspend fun deletePlaylist(playlistName: String) = withContext(Dispatchers.IO) {
        val current = getPlaylists().toMutableList()
        if (current.remove(playlistName)) {
            val array = JSONArray()
            current.forEach { array.put(it) }
            playlistPrefs.edit().putString("playlists_list", array.toString()).apply()
            playlistPrefs.edit().remove("playlist_tracks_$playlistName").apply()
        }
    }

    suspend fun addToPlaylist(playlistName: String, item: AudioItem) = withContext(Dispatchers.IO) {
        val tracks = getPlaylistTracks(playlistName).toMutableList()
        tracks.add(item)
        
        val array = JSONArray()
        tracks.forEach { track ->
            val obj = JSONObject().apply {
                put("id", track.id)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("uri", track.uri.toString())
                put("albumArtUri", track.albumArtUri?.toString() ?: "")
                put("durationMs", track.durationMs)
            }
            array.put(obj)
        }
        playlistPrefs.edit().putString("playlist_tracks_$playlistName", array.toString()).apply()
    }

    suspend fun removeFromPlaylist(playlistName: String, audioId: Long) = withContext(Dispatchers.IO) {
        val tracks = getPlaylistTracks(playlistName).toMutableList()
        val index = tracks.indexOfFirst { it.id == audioId }
        if (index != -1) {
            tracks.removeAt(index)
            val array = JSONArray()
            tracks.forEach { track ->
                val obj = JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    put("uri", track.uri.toString())
                    put("albumArtUri", track.albumArtUri?.toString() ?: "")
                    put("durationMs", track.durationMs)
                }
                array.put(obj)
            }
            playlistPrefs.edit().putString("playlist_tracks_$playlistName", array.toString()).apply()
        }
    }
}
