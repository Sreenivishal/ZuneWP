package com.zune.player.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

data class LyricLine(
    val timeMs: Long,
    val text: String
)

object LyricsRepository {
    private const val TAG = "LyricsRepository"
    private val client = OkHttpClient()

    /**
     * Cleans metadata noise from track names to maximize search hits on LRCLIB.
     */
    fun cleanTrackName(track: String): String {
        return track
            .replace(Regex("\\s*[\\(\\[](?:remaster|live|feat|official|mono|stereo|bonus|acoustic|edit|video|single|version|remix|instrumental|deluxe).*?[\\)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*(?:remaster|live|feat|official|mono|stereo|bonus|acoustic|edit|video|single|version|remix|instrumental|deluxe).*$", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { track }
    }

    /**
     * Searches and parses synchronized lyrics from the public LRCLIB API.
     * Falls back to plain text lyrics parsed as UNSYNCED if synced is unavailable.
     */
    suspend fun getLyrics(track: String, artist: String): List<LyricLine> = withContext(Dispatchers.IO) {
        try {
            val cleanedTrack = cleanTrackName(track)
            val queryTrack = URLEncoder.encode(cleanedTrack, "UTF-8")
            val queryArtist = URLEncoder.encode(artist, "UTF-8")
            val url = "https://lrclib.net/api/search?q_track=$queryTrack&q_artist=$queryArtist"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "ZunePlayer/1.0 (Android; FOSS; https://github.com/MetrolistGroup/Metrolist)")
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "LRCLIB API failed: ${response.code}")
                    return@withContext emptyList()
                }
                
                val bodyStr = response.body?.string() ?: ""
                if (bodyStr.isEmpty() || bodyStr == "[]") return@withContext emptyList()
                
                val jsonArray = JSONArray(bodyStr)
                if (jsonArray.length() == 0) return@withContext emptyList()
                
                // Retrieve the first match
                val bestMatch = jsonArray.getJSONObject(0)
                val syncedLyrics = bestMatch.optString("syncedLyrics", "")
                val plainLyrics = bestMatch.optString("plainLyrics", "")
                
                return@withContext when {
                    syncedLyrics.isNotEmpty() -> parseLrc(syncedLyrics)
                    plainLyrics.isNotEmpty() -> parsePlain(plainLyrics)
                    else -> emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch lyrics for $track by $artist", e)
            emptyList()
        }
    }

    /**
     * Parses standard LRC format ([mm:ss.xx] Text) into timed LyricLine objects.
     */
    private fun parseLrc(lrcText: String): List<LyricLine> {
        val lines = lrcText.lines()
        val lyricLines = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        
        for (line in lines) {
            val match = regex.matchEntire(line.trim()) ?: continue
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toLongOrNull() ?: 0L
            val fractionStr = match.groupValues[3]
            val fraction = fractionStr.toLongOrNull() ?: 0L
            val text = match.groupValues[4].trim()
            
            val ms = min * 60_000L + sec * 1000L + if (fractionStr.length == 2) fraction * 10L else fraction
            lyricLines.add(LyricLine(ms, text))
        }
        return lyricLines.sortedBy { it.timeMs }
    }

    /**
     * Parses unsynced plain text lyrics by spacing them evenly across a standard duration.
     */
    private fun parsePlain(plainText: String): List<LyricLine> {
        val lines = plainText.lines().filter { it.trim().isNotEmpty() }
        val lyricLines = mutableListOf<LyricLine>()
        lines.forEachIndexed { index, line ->
            // Space lines roughly every 4 seconds as a fallback
            lyricLines.add(LyricLine(index * 4000L, line.trim()))
        }
        return lyricLines
    }
}
