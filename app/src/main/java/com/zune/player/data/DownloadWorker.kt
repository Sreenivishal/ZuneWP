package com.zune.player.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.context.GlobalContext
import org.mp4parser.IsoFile
import org.mp4parser.Box
import org.mp4parser.Container
import org.mp4parser.boxes.iso14496.part12.ChunkOffsetBox
import org.mp4parser.boxes.iso14496.part12.HandlerBox
import org.mp4parser.boxes.iso14496.part12.MetaBox
import org.mp4parser.boxes.iso14496.part12.MovieBox
import org.mp4parser.boxes.iso14496.part12.UserDataBox
import org.mp4parser.boxes.apple.AppleAlbumBox
import org.mp4parser.boxes.apple.AppleArtistBox
import org.mp4parser.boxes.apple.AppleCoverBox
import org.mp4parser.boxes.apple.AppleItemListBox
import org.mp4parser.boxes.apple.AppleNameBox
import java.io.IOException

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val title = inputData.getString("title") ?: "Unknown Title"
        val artist = inputData.getString("artist") ?: "Unknown Artist"
        val album = inputData.getString("album") ?: "Unknown Album"
        val previewUrl = inputData.getString("previewUrl")
        val artworkUrl = inputData.getString("artworkUrl")

        if (previewUrl.isNullOrEmpty()) {
            return@withContext Result.failure()
        }

        var tempAudioFile: File? = null
        var tempTaggedFile: File? = null

        try {
            val streamRepository = GlobalContext.get().get<com.maxrave.domain.repository.StreamRepository>()
            val dataStoreManager = GlobalContext.get().get<com.maxrave.domain.manager.DataStoreManager>()
            val resolvedUrl = streamRepository.getStream(
                dataStoreManager = dataStoreManager,
                videoId = previewUrl,
                isDownloading = true,
                isVideo = false
            ).firstOrNull()

            if (resolvedUrl.isNullOrEmpty()) {
                Log.e("DownloadWorker", "Failed to resolve stream URL for videoId: $previewUrl")
                return@withContext Result.failure()
            }

            val client = OkHttpClient()

            // 1. Download raw audio to temp file
            tempAudioFile = File(applicationContext.cacheDir, "temp_download_${System.currentTimeMillis()}.m4a")
            val request = Request.Builder().url(resolvedUrl).build()
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                return@withContext Result.retry()
            }

            if (!response.isSuccessful) {
                return@withContext if (response.code in 500..599 || response.code == 429) Result.retry() else Result.failure()
            }

            response.body?.byteStream()?.use { input ->
                FileOutputStream(tempAudioFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 2. Fetch Album Art
            var artworkBytes: ByteArray? = null
            if (!artworkUrl.isNullOrEmpty()) {
                val artRequest = Request.Builder().url(artworkUrl).build()
                try {
                    val artResponse = client.newCall(artRequest).execute()
                    if (artResponse.isSuccessful) {
                        artworkBytes = artResponse.body?.bytes()
                    }
                } catch (e: IOException) {
                    // Ignore art fetch failure — artwork is optional
                }
            }

            // 3. Embed Metadata using mp4parser
            tempTaggedFile = File(applicationContext.cacheDir, "temp_tagged_${System.currentTimeMillis()}.m4a")

            val isoFile = IsoFile(tempAudioFile.absolutePath)

            val moov = isoFile.getBoxes(MovieBox::class.java).firstOrNull()
            if (moov != null) {
                val oldMoovSize = moov.size

                var udta = moov.getBoxes(UserDataBox::class.java).firstOrNull()
                if (udta == null) {
                    udta = UserDataBox()
                    moov.addBox(udta)
                }

                var meta = udta.getBoxes(MetaBox::class.java).firstOrNull()
                if (meta == null) {
                    meta = MetaBox()
                    val hdlr = HandlerBox()
                    hdlr.handlerType = "mdir"
                    meta.addBox(hdlr)
                    udta.addBox(meta)
                }

                var ilst = meta.getBoxes(AppleItemListBox::class.java).firstOrNull()
                if (ilst == null) {
                    ilst = AppleItemListBox()
                    meta.addBox(ilst)
                }

                // Remove existing tags to avoid duplicates
                ilst.boxes = ilst.boxes.filter {
                    it !is AppleNameBox && it !is AppleArtistBox && it !is AppleAlbumBox && it !is AppleCoverBox
                }

                val nam = AppleNameBox()
                nam.value = title
                ilst.addBox(nam)

                val art = AppleArtistBox()
                art.value = artist
                ilst.addBox(art)

                val alb = AppleAlbumBox()
                alb.value = album
                ilst.addBox(alb)

                if (artworkBytes != null) {
                    val covr = AppleCoverBox()
                    // Detect PNG by magic bytes [0x89, 'P', 'N', 'G'] at offset 0–3
                    // Detect JPEG by magic bytes [0xFF, 0xD8] at offset 0–1
                    // Default to JPEG for anything else (most iTunes artwork is JPEG)
                    val isPng = artworkBytes.size > 4 &&
                        artworkBytes[0] == 0x89.toByte() &&
                        artworkBytes[1] == 'P'.code.toByte() &&
                        artworkBytes[2] == 'N'.code.toByte() &&
                        artworkBytes[3] == 'G'.code.toByte()
                    if (isPng) {
                        covr.setPng(artworkBytes)   // ✅ correct method name
                    } else {
                        covr.setJpg(artworkBytes)  // ✅ correct method name
                    }
                    ilst.addBox(covr)
                }

                // Recalculate size and apply stco/co64 chunk offset corrections
                val newMoovSize = moov.size
                val correction = newMoovSize - oldMoovSize
                val boxes = isoFile.boxes
                val moovIndex = boxes.indexOf(moov)
                val mdatIndex = boxes.indexOfFirst { it.type == "mdat" }
                val isMoovBeforeMdat = moovIndex != -1 && mdatIndex != -1 && moovIndex < mdatIndex

                if (isMoovBeforeMdat && correction != 0L) {
                    val chunkOffsetBoxes = moov.findBoxesRecursive(ChunkOffsetBox::class.java)
                    for (box in chunkOffsetBoxes) {
                        val offsets = box.chunkOffsets
                        for (i in offsets.indices) {
                            offsets[i] += correction
                        }
                        box.chunkOffsets = offsets
                    }
                }

                FileOutputStream(tempTaggedFile).use { fos ->
                    isoFile.getBox(fos.channel)
                }
            } else {
                // moov box not found — fall back to untagged file
                tempAudioFile.copyTo(tempTaggedFile, overwrite = true)
            }
            isoFile.close()

            // 4. Sanitize filename and copy to Music folder
            val sanitizedTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val sanitizedArtist = artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "$sanitizedTitle - $sanitizedArtist.m4a"

            val resolver = applicationContext.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val audioUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.failure()

            resolver.openOutputStream(audioUri)?.use { output ->
                FileInputStream(tempTaggedFile).use { input ->
                    input.copyTo(output)
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val pendingValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                resolver.update(audioUri, pendingValues, null, null)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Error downloading song: ${e.message}", e)
            if (e is IOException) Result.retry() else Result.failure()
        } finally {
            tempAudioFile?.delete()
            tempTaggedFile?.delete()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Box> Container.findBoxesRecursive(clazz: Class<T>): List<T> {
        val results = mutableListOf<T>()
        for (box in this.boxes) {
            if (clazz.isInstance(box)) {
                results.add(box as T)
            }
            if (box is Container) {
                results.addAll(box.findBoxesRecursive(clazz))
            }
        }
        return results
    }
}