package com.zune.player

import com.zune.player.data.OnlineMusicStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

class OnlineMusicStoreTest {

    private fun postUrlText(urlStr: String, jsonBody: String): String {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            
            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }
            
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                return ""
            }
        } catch (e: Exception) {
            return ""
        }
    }

    private fun getShelfTitle(res: String): String {
        if (res.isEmpty()) return "EMPTY"
        try {
            val responseJson = JSONObject(res)
            val contents = responseJson.optJSONObject("contents") ?: return "NO CONTENTS"
            val tabbed = contents.optJSONObject("tabbedSearchResultsRenderer") ?: return "NO TABBED"
            val tabs = tabbed.optJSONArray("tabs") ?: return "NO TABS"
            if (tabs.length() == 0) return "TABS EMPTY"
            val tab0 = tabs.getJSONObject(0)
            val tabRenderer = tab0.optJSONObject("tabRenderer") ?: return "NO TAB RENDERER"
            val content = tabRenderer.optJSONObject("content") ?: return "NO CONTENT"
            val sectionList = content.optJSONObject("sectionListRenderer") ?: return "NO SECTION LIST"
            val sectionContents = sectionList.optJSONArray("contents") ?: return "NO SECTION CONTENTS"
            if (sectionContents.length() == 0) return "SECTION CONTENTS EMPTY"
            
            val titles = mutableListOf<String>()
            for (i in 0 until sectionContents.length()) {
                val section = sectionContents.getJSONObject(i)
                val shelf = section.optJSONObject("musicShelfRenderer")
                if (shelf != null) {
                    val titleObj = shelf.optJSONObject("title")
                    val runs = titleObj?.optJSONArray("runs")
                    if (runs != null && runs.length() > 0) {
                        titles.add(runs.getJSONObject(0).optString("text", "UNTITLED"))
                    }
                }
            }
            return if (titles.isNotEmpty()) titles.joinToString(", ") else "NO SHELF TITLES"
        } catch (e: Exception) {
            return "ERROR: ${e.message}"
        }
    }

    @Test
    fun testParamFinder() = runBlocking {
        val candidates = listOf(
            // Songs
            "EgWKAQIIAWoKEAkQBRAKGAEYAQ==", 
            
            // Album candidates
            "EgWKAQIIAWoKEAkQEBAKGAEYAQ==", // Currently used for albums (Song)
            "EgWKAQIYAWoMEA4QChADEAQQCRAF", // Derived Album filter
            "EgWKAQIIARoKEAkQEBAKGAEYAQ==", // Album candidate 1
            "EgWKAQIIARoKEAkQEBAKGAEgAQ==", // Album candidate 2
            "EgWKAQIIARoKEAkQEBAKGAEwAQ==", // Album candidate 3
            "EgWKAQIIARoKEAkQEBAKGAEYASAB", // Album candidate 4
            "EgWKAQIIARoKEAkQEBAKGAEYAyAB", // Album candidate 5
            
            // Artist candidates
            "EgWKAQIIAWoKEAkQDhAKGAEYAQ==", // Currently used for artists (Song)
            "EgWKAQIgAWoMEA4QChADEAQQCRAF", // Derived Artist filter
            "EgWKAQIIAmoKEAkQDhAKGAEYAQ==", // Artist candidate 1
            "EgWKAQIIAmoKEAkQDhAKGAEgAQ==", // Artist candidate 2
            "EgWKAQIIAmoKEAkQDhAKGAEwAQ==", // Artist candidate 3
            "EgWKAQIIAmoKEAkQDhAKGAEYASAB" // Artist candidate 4
        )

        println("=== SCANNING SEARCH FILTER PARAMETERS ===")
        for (param in candidates) {
            val jsonBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20260213.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", "Hybrid Theory")
                put("params", param)
            }.toString()
            val res = postUrlText("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", jsonBody)
            val result = getShelfTitle(res)
            println("Param '$param' -> $result")
        }
    }

    @Test
    fun testBrowseAlbumAndArtist() = runBlocking {
        println("=== TESTING BROWSE ALBUM AND ARTIST ===")
        
        // 1. Search for Albums
        val albumSearchBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20260213.01.00")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("query", "Hybrid Theory")
            put("params", "EgWKAQIYAWoMEA4QChADEAQQCRAF")
        }.toString()
        
        val albumSearchRes = postUrlText("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", albumSearchBody)
        var albumBrowseId = ""
        var albumTitle = ""
        try {
            val responseJson = JSONObject(albumSearchRes)
            val contents = responseJson.getJSONObject("contents")
            val tabbed = contents.getJSONObject("tabbedSearchResultsRenderer")
            val tabs = tabbed.getJSONArray("tabs")
            val tab0 = tabs.getJSONObject(0)
            val sectionContents = tab0.getJSONObject("tabRenderer")
                .getJSONObject("content")
                .getJSONObject("sectionListRenderer")
                .getJSONArray("contents")
            
            for (i in 0 until sectionContents.length()) {
                val section = sectionContents.getJSONObject(i)
                val shelf = section.optJSONObject("musicShelfRenderer")
                if (shelf != null) {
                    val items = shelf.optJSONArray("contents")
                    if (items != null && items.length() > 0) {
                        val renderer = items.getJSONObject(0).getJSONObject("musicResponsiveListItemRenderer")
                        val flexColumns = renderer.getJSONArray("flexColumns")
                        val col0 = flexColumns.getJSONObject(0)
                        val text0 = col0.getJSONObject("musicResponsiveListItemFlexColumnRenderer").getJSONObject("text")
                        albumTitle = text0.getJSONArray("runs").getJSONObject(0).getString("text")
                        
                        val navEndpoint = renderer.optJSONObject("navigationEndpoint")
                            ?: flexColumns.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                                ?.optJSONObject("navigationEndpoint")
                        val browseEndpoint = navEndpoint?.optJSONObject("browseEndpoint")
                        albumBrowseId = browseEndpoint?.optString("browseId", "") ?: ""
                        break
                    }
                }
            }
        } catch (e: Exception) {
            println("Error parsing search albums: ${e.message}")
        }
        
        println("Found album: '$albumTitle' with browseId: '$albumBrowseId'")
        assert(albumBrowseId.isNotEmpty()) { "Album browse ID should not be empty" }

        // 2. Browse Album Tracks
        val albumBrowseBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20260213.01.00")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("browseId", albumBrowseId)
        }.toString()
        
        val albumTracksRes = postUrlText("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false", albumBrowseBody)
        // Write raw JSON to scratch directory for inspection
        java.io.File("C:\\Users\\Sreenivishal\\.gemini\\antigravity-ide\\scratch\\album_browse.json").writeText(albumTracksRes)

        var albumTracksCount = 0
        try {
            val responseJson = JSONObject(albumTracksRes)
            val contents = responseJson.optJSONObject("contents")
            val target = contents?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?: contents?.optJSONObject("twoColumnBrowseResultsRenderer")
            
            var sectionContents: JSONArray? = null
            val tabs = target?.optJSONArray("tabs")
            if (tabs != null && tabs.length() > 0) {
                val tab0 = tabs.optJSONObject(0)
                val content = tab0?.optJSONObject("tabRenderer")?.optJSONObject("content")
                val sectionListRenderer = content?.optJSONObject("sectionListRenderer")
                sectionContents = sectionListRenderer?.optJSONArray("contents")
            }
            if (sectionContents == null) {
                val secondaryContents = target?.optJSONObject("secondaryContents")
                val sectionListRenderer = secondaryContents?.optJSONObject("sectionListRenderer")
                sectionContents = sectionListRenderer?.optJSONArray("contents")
            }
            
            if (sectionContents != null) {
                for (i in 0 until sectionContents.length()) {
                    val section = sectionContents.getJSONObject(i)
                    val musicPlaylistShelfRenderer = section.optJSONObject("musicPlaylistShelfRenderer")
                    val items = musicPlaylistShelfRenderer?.optJSONArray("contents")
                        ?: section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                    
                    if (items != null) {
                        albumTracksCount = items.length()
                        for (j in 0 until items.length()) {
                            val item = items.getJSONObject(j)
                            val renderer = item.optJSONObject("musicResponsiveListItemRenderer")
                            if (renderer != null) {
                                val flexColumns = renderer.optJSONArray("flexColumns")
                                val col0 = flexColumns?.optJSONObject(0)
                                val text0 = col0?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                                val title = text0?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "Unknown")
                                println("  Track ${j+1}: $title")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error parsing album tracks: ${e.message}")
        }
        println("Parsed $albumTracksCount tracks from the album.")
        assert(albumTracksCount > 0) { "Album tracks count should be greater than 0" }

        // 3. Search for Artists
        val artistSearchBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20260213.01.00")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("query", "Linkin Park")
            put("params", "EgWKAQIgAWoMEA4QChADEAQQCRAF")
        }.toString()
        
        val artistSearchRes = postUrlText("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", artistSearchBody)
        var artistBrowseId = ""
        var artistName = ""
        try {
            val responseJson = JSONObject(artistSearchRes)
            val contents = responseJson.getJSONObject("contents")
            val tabbed = contents.getJSONObject("tabbedSearchResultsRenderer")
            val tabs = tabbed.getJSONArray("tabs")
            val tab0 = tabs.getJSONObject(0)
            val sectionContents = tab0.getJSONObject("tabRenderer")
                .getJSONObject("content")
                .getJSONObject("sectionListRenderer")
                .getJSONArray("contents")
            
            for (i in 0 until sectionContents.length()) {
                val section = sectionContents.getJSONObject(i)
                val shelf = section.optJSONObject("musicShelfRenderer")
                if (shelf != null) {
                    val items = shelf.optJSONArray("contents")
                    if (items != null && items.length() > 0) {
                        val renderer = items.getJSONObject(0).getJSONObject("musicResponsiveListItemRenderer")
                        val flexColumns = renderer.getJSONArray("flexColumns")
                        val col0 = flexColumns.getJSONObject(0)
                        val text0 = col0.getJSONObject("musicResponsiveListItemFlexColumnRenderer").getJSONObject("text")
                        artistName = text0.getJSONArray("runs").getJSONObject(0).getString("text")
                        
                        val navEndpoint = renderer.optJSONObject("navigationEndpoint")
                            ?: flexColumns.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                                ?.optJSONObject("navigationEndpoint")
                        val browseEndpoint = navEndpoint?.optJSONObject("browseEndpoint")
                        artistBrowseId = browseEndpoint?.optString("browseId", "") ?: ""
                        break
                    }
                }
            }
        } catch (e: Exception) {
            println("Error parsing search artists: ${e.message}")
        }
        
        println("Found artist: '$artistName' with browseId: '$artistBrowseId'")
        assert(artistBrowseId.isNotEmpty()) { "Artist browse ID should not be empty" }

        // 4. Browse Artist details (top songs & albums)
        val artistBrowseBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20260213.01.00")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("browseId", artistBrowseId)
        }.toString()
        
        val artistBrowseRes = postUrlText("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false", artistBrowseBody)
        java.io.File("C:\\Users\\Sreenivishal\\.gemini\\antigravity-ide\\scratch\\artist_browse.json").writeText(artistBrowseRes)
        var topSongsCount = 0
        var albumsCount = 0
        try {
            val responseJson = JSONObject(artistBrowseRes)
            val contents = responseJson.optJSONObject("contents")
            val target = contents?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?: contents?.optJSONObject("twoColumnBrowseResultsRenderer")
            val tabs = target?.optJSONArray("tabs")
            val tab0 = tabs?.optJSONObject(0)
            val content = tab0?.optJSONObject("tabRenderer")?.optJSONObject("content")
            val sectionListRenderer = content?.optJSONObject("sectionListRenderer")
            val sectionContents = sectionListRenderer?.optJSONArray("contents")
            
            if (sectionContents != null) {
                for (i in 0 until sectionContents.length()) {
                    val section = sectionContents.getJSONObject(i)
                    
                    // Top songs shelf
                    val musicShelfRenderer = section.optJSONObject("musicShelfRenderer")
                    if (musicShelfRenderer != null) {
                        val items = musicShelfRenderer.optJSONArray("contents")
                        if (items != null) {
                            topSongsCount = items.length()
                            println("Artist Top Songs:")
                            for (j in 0 until items.length()) {
                                val item = items.getJSONObject(j)
                                val renderer = item.optJSONObject("musicResponsiveListItemRenderer")
                                if (renderer != null) {
                                    val flexColumns = renderer.optJSONArray("flexColumns")
                                    val col0 = flexColumns?.optJSONObject(0)
                                    val text0 = col0?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                                    val title = text0?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "Unknown")
                                    println("  Song ${j+1}: $title")
                                }
                            }
                        }
                    }
                    
                    // Albums carousel
                    val musicCarouselShelfRenderer = section.optJSONObject("musicCarouselShelfRenderer")
                    if (musicCarouselShelfRenderer != null) {
                        val items = musicCarouselShelfRenderer.optJSONArray("contents")
                        if (items != null) {
                            albumsCount = items.length()
                            println("Artist Albums:")
                            for (j in 0 until items.length()) {
                                val item = items.getJSONObject(j)
                                val renderer = item.optJSONObject("musicTwoRowItemRenderer")
                                if (renderer != null) {
                                    val titleObj = renderer.optJSONObject("title")
                                    val runs = titleObj?.optJSONArray("runs")
                                    val title = runs?.optJSONObject(0)?.optString("text", "Unknown")
                                    println("  Album ${j+1}: $title")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error parsing artist page: ${e.message}")
        }
        
        println("Parsed $topSongsCount top songs and $albumsCount albums for the artist.")
        assert(topSongsCount > 0) { "Top songs count should be greater than 0" }
        assert(albumsCount > 0) { "Artist albums count should be greater than 0" }
    }

    @Test
    fun testResolveStream() = runBlocking {
        println("=== TESTING RESOLVE DIRECT STREAM ===")
        val videoId = "dQw4w9WgXcQ"
        val url = OnlineMusicStore.resolveDirectStream(videoId)
        println("Resolved stream URL: $url")
    }
}

