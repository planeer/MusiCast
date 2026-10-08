package com.musicast.musicast.download

import com.musicast.musicast.audio.MusicDetector
import com.musicast.musicast.data.local.LocalDataSource
import com.musicast.musicast.domain.model.AnalysisStatus
import com.musicast.musicast.domain.model.Episode
import com.musicast.musicast.player.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-wide owner of episode downloads and music analysis.
 *
 * Work runs in this singleton's scope rather than a screen's ViewModel, so it
 * keeps going (and keeps reporting progress) when the user navigates away and
 * comes back to the episode list.
 */
class EpisodeProcessor(
    private val localDataSource: LocalDataSource,
    private val downloader: EpisodeDownloader,
    private val playbackManager: PlaybackManager,
    private val musicDetector: MusicDetector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val analysisJobs = mutableMapOf<Long, Job>()

    // The YAMNet classifier is a shared singleton that MusicDetector closes after
    // each file, so overlapping analyses would free the interpreter mid-inference.
    private val analysisLock = Mutex()

    private val _analysisProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val analysisProgress: StateFlow<Map<Long, Float>> = _analysisProgress.asStateFlow()

    val activeDownloads: StateFlow<Map<Long, DownloadProgress>> get() = downloader.activeDownloads

    fun download(episode: Episode) {
        scope.launch {
            downloader.download(episode.id, episode.audioUrl).collect { progress ->
                if (progress.status == DownloadStatus.COMPLETED) {
                    val localPath = downloader.getLocalPath(episode.id)
                    if (localPath != null) {
                        localDataSource.updateDownloadPath(episode.id, localPath)
                        // Auto-analyze after download
                        analyze(episode.copy(downloadPath = localPath))
                    }
                }
            }
        }
    }

    /** Loads stored segments into the player, or starts analysis if there are none yet. */
    fun prepareForPlayback(episode: Episode) {
        if (episode.downloadPath == null) return
        when (episode.analysisStatus) {
            AnalysisStatus.COMPLETED -> scope.launch {
                val segments = localDataSource.loadSegments(episode.id)
                if (!segments.isNullOrEmpty() && playbackManager.state.value.episode?.id == episode.id) {
                    playbackManager.setSegments(segments)
                }
            }
            AnalysisStatus.NONE -> analyze(episode)
            // FAILED needs an explicit retry; IN_PROGRESS is already running.
            AnalysisStatus.IN_PROGRESS, AnalysisStatus.FAILED -> {}
        }
    }

    fun analyze(episode: Episode) {
        val path = episode.downloadPath ?: return
        if (analysisJobs[episode.id]?.isActive == true) return

        analysisJobs[episode.id] = scope.launch {
            localDataSource.updateAnalysisStatus(episode.id, AnalysisStatus.IN_PROGRESS)
            _analysisProgress.update { it + (episode.id to 0f) }

            val segments = try {
                analysisLock.withLock {
                    musicDetector.analyzeFile(path) { progress ->
                        _analysisProgress.update { it + (episode.id to progress) }
                    }
                }
            } finally {
                _analysisProgress.update { it - episode.id }
                analysisJobs.remove(episode.id)
            }

            if (segments != null) {
                localDataSource.updateAnalysisStatus(episode.id, AnalysisStatus.COMPLETED)
                localDataSource.saveSegments(episode.id, segments)
                if (playbackManager.state.value.episode?.id == episode.id) {
                    playbackManager.setSegments(segments)
                }
            } else {
                localDataSource.updateAnalysisStatus(episode.id, AnalysisStatus.FAILED)
            }
        }
    }

    fun deleteDownload(episode: Episode) {
        analysisJobs.remove(episode.id)?.cancel()
        downloader.deleteDownload(episode.id)
        localDataSource.updateDownloadPath(episode.id, null)
        localDataSource.updateAnalysisStatus(episode.id, AnalysisStatus.NONE)
        localDataSource.clearSegmentsData(episode.id)
    }
}
