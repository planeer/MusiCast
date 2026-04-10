package com.musicast.musicast.download

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class DownloadProgress(
    val episodeId: Long,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

expect class EpisodeDownloader {
    /**
     * Downloads an episode audio file to local storage.
     * Returns a Flow emitting progress updates.
     * The final emission has status COMPLETED with the local file path accessible via [getLocalPath].
     */
    fun download(episodeId: Long, url: String): Flow<DownloadProgress>

    /**
     * Returns the local file path for a downloaded episode, or null if not downloaded.
     */
    fun getLocalPath(episodeId: Long): String?

    /**
     * Deletes a downloaded episode file.
     */
    fun deleteDownload(episodeId: Long): Boolean

    /**
     * All currently active downloads.
     */
    val activeDownloads: StateFlow<Map<Long, DownloadProgress>>
}
