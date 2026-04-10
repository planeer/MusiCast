@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.musicast.musicast.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile

actual class EpisodeDownloader {

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
            val nsUrl = NSURL.URLWithString(url) ?: throw Exception("Invalid URL: $url")
            val data = NSData.dataWithContentsOfURL(nsUrl)
                ?: throw Exception("Failed to download: $url")

            val downloading = DownloadProgress(
                episodeId = episodeId,
                bytesDownloaded = data.length.toLong(),
                totalBytes = data.length.toLong(),
                status = DownloadStatus.DOWNLOADING,
            )
            _activeDownloads.update { it + (episodeId to downloading) }
            emit(downloading)

            val written = data.writeToFile(targetPath, atomically = true)
            if (!written) throw Exception("Failed to write file")

            val completed = DownloadProgress(
                episodeId = episodeId,
                bytesDownloaded = data.length.toLong(),
                totalBytes = data.length.toLong(),
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
    }.flowOn(Dispatchers.IO)

    actual fun getLocalPath(episodeId: Long): String? {
        val path = "$downloadDir/episode_$episodeId.mp3"
        return if (NSFileManager.defaultManager.fileExistsAtPath(path)) path else null
    }

    actual fun deleteDownload(episodeId: Long): Boolean {
        val path = "$downloadDir/episode_$episodeId.mp3"
        return NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}
