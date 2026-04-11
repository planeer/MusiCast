@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.musicast.musicast.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.writeData

actual class EpisodeDownloader {

    private val httpClient = HttpClient(Darwin)

    private val downloadDir: String
        get() {
            val paths = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory, NSUserDomainMask, true
            )
            val documentsDir = paths.firstOrNull() as? String ?: "/tmp"
            val episodesDir = "$documentsDir/episodes"
            NSFileManager.defaultManager.createDirectoryAtPath(episodesDir, true, null, null)
            return episodesDir
        }

    private val _activeDownloads = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    actual val activeDownloads: StateFlow<Map<Long, DownloadProgress>> = _activeDownloads.asStateFlow()

    actual fun download(episodeId: Long, url: String): Flow<DownloadProgress> = flow {
        val targetPath = "$downloadDir/episode_$episodeId.mp3"

        val pending = DownloadProgress(episodeId, 0L, -1L, DownloadStatus.PENDING)
        _activeDownloads.update { it + (episodeId to pending) }
        emit(pending)

        try {
            httpClient.prepareGet(url) {
                onDownload { bytesSentTotal, contentLength ->
                    // Push progress into the StateFlow that the UI observes.
                    // The UI only shows the progress bar when status == DOWNLOADING
                    // && totalBytes > 0, so a non-null contentLength is required
                    // for the bar to render.
                    val progress = DownloadProgress(
                        episodeId = episodeId,
                        bytesDownloaded = bytesSentTotal,
                        totalBytes = contentLength ?: -1L,
                        status = DownloadStatus.DOWNLOADING,
                    )
                    _activeDownloads.update { it + (episodeId to progress) }
                }
            }.execute { response ->
                val channel = response.bodyAsChannel()

                // Ensure the destination file exists and open it for writing.
                NSFileManager.defaultManager.createFileAtPath(targetPath, null, null)
                val handle = NSFileHandle.fileHandleForWritingAtPath(targetPath)
                    ?: throw Exception("Cannot open file for writing: $targetPath")

                try {
                    val buffer = ByteArray(8 * 1024)
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read > 0) {
                            buffer.usePinned { pinned ->
                                val nsData = NSData.create(
                                    bytes = pinned.addressOf(0),
                                    length = read.toULong(),
                                )
                                handle.writeData(nsData)
                            }
                        } else if (read < 0) {
                            break
                        }
                    }
                } finally {
                    handle.closeFile()
                }
            }

            val completed = DownloadProgress(
                episodeId = episodeId,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                status = DownloadStatus.COMPLETED,
            )
            _activeDownloads.update { it - episodeId }
            emit(completed)
        } catch (e: Exception) {
            NSFileManager.defaultManager.removeItemAtPath(targetPath, null)
            val failed = DownloadProgress(episodeId, 0L, -1L, DownloadStatus.FAILED)
            _activeDownloads.update { it - episodeId }
            emit(failed)
        }
    }

    actual fun getLocalPath(episodeId: Long): String? {
        val path = "$downloadDir/episode_$episodeId.mp3"
        return if (NSFileManager.defaultManager.fileExistsAtPath(path)) path else null
    }

    actual fun deleteDownload(episodeId: Long): Boolean {
        val path = "$downloadDir/episode_$episodeId.mp3"
        return NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}
