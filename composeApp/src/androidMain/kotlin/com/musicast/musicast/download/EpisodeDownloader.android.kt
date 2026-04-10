package com.musicast.musicast.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual class EpisodeDownloader(private val context: Context) {

    private val downloadDir: File
        get() = File(context.filesDir, "episodes").also { it.mkdirs() }

    private val _activeDownloads = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    actual val activeDownloads: StateFlow<Map<Long, DownloadProgress>> = _activeDownloads.asStateFlow()

    actual fun download(episodeId: Long, url: String): Flow<DownloadProgress> = flow {
        val targetFile = File(downloadDir, "episode_$episodeId.mp3")

        val pending = DownloadProgress(episodeId, 0L, -1L, DownloadStatus.PENDING)
        _activeDownloads.update { it + (episodeId to pending) }
        emit(pending)

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.connect()

            val totalBytes = connection.contentLengthLong
            var downloaded = 0L

            connection.inputStream.buffered().use { input ->
                targetFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val progress = DownloadProgress(
                            episodeId = episodeId,
                            bytesDownloaded = downloaded,
                            totalBytes = totalBytes,
                            status = DownloadStatus.DOWNLOADING,
                        )
                        _activeDownloads.update { it + (episodeId to progress) }
                        emit(progress)
                    }
                }
            }

            val completed = DownloadProgress(episodeId, downloaded, downloaded, DownloadStatus.COMPLETED)
            _activeDownloads.update { it - episodeId }
            emit(completed)
        } catch (e: Exception) {
            targetFile.delete()
            val failed = DownloadProgress(episodeId, 0L, -1L, DownloadStatus.FAILED)
            _activeDownloads.update { it - episodeId }
            emit(failed)
        }
    }.flowOn(Dispatchers.IO)

    actual fun getLocalPath(episodeId: Long): String? {
        val file = File(downloadDir, "episode_$episodeId.mp3")
        return if (file.exists()) file.absolutePath else null
    }

    actual fun deleteDownload(episodeId: Long): Boolean {
        val file = File(downloadDir, "episode_$episodeId.mp3")
        return file.delete()
    }
}
