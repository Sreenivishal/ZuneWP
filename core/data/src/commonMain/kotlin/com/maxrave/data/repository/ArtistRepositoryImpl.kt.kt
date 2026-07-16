package com.maxrave.data.repository

import com.maxrave.data.db.datasource.LocalDataSource
import com.maxrave.data.extension.getFullDataFromDB
import com.maxrave.data.parser.parseArtistData
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.model.browse.artist.ArtistBrowse
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.kotlinytmusicscraper.YouTube
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime

internal class ArtistRepositoryImpl(
    private val localDataSource: LocalDataSource,
    private val youTube: YouTube,
) : ArtistRepository {
    override fun getAllArtists(limit: Int): Flow<List<ArtistEntity>> =
        flow {
            emit(localDataSource.getAllArtists(limit))
        }.flowOn(Dispatchers.IO)

    override fun getArtistById(id: String): Flow<ArtistEntity?> =
        flow {
            emit(localDataSource.getArtist(id))
        }.flowOn(Dispatchers.IO)

    override suspend fun insertArtist(artistEntity: ArtistEntity) =
        withContext(Dispatchers.IO) {
            localDataSource.insertArtist(artistEntity)
        }

    override suspend fun updateArtistImage(
        channelId: String,
        thumbnail: String,
    ) = withContext(
        Dispatchers.Main,
    ) {
        localDataSource.updateArtistImage(
            channelId,
            thumbnail,
        )
    }

    override suspend fun updateFollowedStatus(
        channelId: String,
        followedStatus: Int,
    ) = withContext(
        Dispatchers.Main,
    ) { localDataSource.updateFollowed(followedStatus, channelId) }

    override fun getFollowedArtists(): Flow<List<ArtistEntity>> =
        flow {
            emit(
                getFullDataFromDB { limit, offset ->
                    localDataSource.getFollowedArtists(limit, offset)
                },
            )
        }.flowOn(Dispatchers.IO)

    override suspend fun updateArtistInLibrary(
        inLibrary: LocalDateTime,
        channelId: String,
    ) = withContext(Dispatchers.Main) {
        localDataSource.updateArtistInLibrary(
            inLibrary,
            channelId,
        )
    }

    override fun getArtistData(channelId: String): Flow<Resource<ArtistBrowse>> =
        flow {
            runCatching {
                youTube
                    .artist(channelId)
                    .onSuccess { result ->
                        val initialBrowse = parseArtistData(result)
                        
                        val finalSongs = initialBrowse.songs?.results ?: emptyList()

                        val albumsBrowseId = initialBrowse.albums?.browseId?.toString()
                        val albumsParams = initialBrowse.albums?.params
                        val finalAlbums = if (albumsBrowseId != null) {
                            val albumsResult = youTube.browse(albumsBrowseId, albumsParams)
                            val parsed = albumsResult.getOrNull()?.items?.flatMap { it.items }?.mapNotNull { item ->
                                val album = item as? com.maxrave.kotlinytmusicscraper.models.AlbumItem ?: return@mapNotNull null
                                com.maxrave.domain.data.model.browse.artist.ResultAlbum(
                                    browseId = album.browseId,
                                    isExplicit = false,
                                    thumbnails = listOf(com.maxrave.domain.data.model.searchResult.songs.Thumbnail(544, album.thumbnail, 544)),
                                    title = album.title,
                                    year = album.year?.toString() ?: "",
                                )
                            }
                            if (parsed.isNullOrEmpty()) {
                                initialBrowse.albums?.results ?: emptyList()
                            } else {
                                parsed
                            }
                        } else {
                            initialBrowse.albums?.results ?: emptyList()
                        }

                        val singlesBrowseId = initialBrowse.singles?.browseId
                        val singlesParams = initialBrowse.singles?.params
                        val finalSingles = if (singlesBrowseId != null) {
                            val singlesResult = youTube.browse(singlesBrowseId, singlesParams)
                            val parsed = singlesResult.getOrNull()?.items?.flatMap { it.items }?.mapNotNull { item ->
                                val single = item as? com.maxrave.kotlinytmusicscraper.models.AlbumItem ?: return@mapNotNull null
                                com.maxrave.domain.data.model.browse.artist.ResultSingle(
                                    browseId = single.browseId,
                                    thumbnails = listOf(com.maxrave.domain.data.model.searchResult.songs.Thumbnail(544, single.thumbnail, 544)),
                                    title = single.title,
                                    year = single.year?.toString() ?: "",
                                )
                            }
                            if (parsed.isNullOrEmpty()) {
                                initialBrowse.singles?.results ?: emptyList()
                            } else {
                                parsed
                            }
                        } else {
                            initialBrowse.singles?.results ?: emptyList()
                        }

                        val completeBrowse = initialBrowse.copy(
                            songs = initialBrowse.songs?.copy(results = finalSongs),
                            albums = initialBrowse.albums?.copy(results = finalAlbums),
                            singles = initialBrowse.singles?.copy(results = finalSingles)
                        )

                        emit(Resource.Success<ArtistBrowse>(completeBrowse))
                    }.onFailure { e ->
                        Logger.d("Artist", "Error: ${e.message}")
                        emit(Resource.Error<ArtistBrowse>(e.message.toString()))
                    }
            }
        }.flowOn(Dispatchers.IO)
}