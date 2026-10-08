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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
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
    private val downloadJobs = mutableMapOf<Long, Job>()

    // The YAMNet classifier is a shared singleton that MusicDetector closes after
    // each file, so overlapping analyses would free the interpreter mid-inference.
    private val analysisLock = Mutex()

    private val _analysisProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val analysisProgress: StateFlow<Map<Long, Float>> = _analysisProgress.asStateFlow()

    val activeDownloads: StateFlow<Map<Long, DownloadProgress>> get() = downloader.activeDownloads

    fun download(episode: Episode) {
        // Ignore repeat taps: two downloads would write the same file
        if (downloadJobs[episode.id]?.isActive == true) return
        downloadJobs[episode.id] = scope.launch {
            try {
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
            } finally {
                if (downloadJobs[episode.id] === coroutineContext.job) downloadJobs.remove(episode.id)
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
                // A newer job may have replaced this one after a cancel; leave its state alone.
                // Progress is still cleared when no job replaced us: onProgress runs on the
                // decoder thread and can re-add an entry after deleteDownload removed it.
                val current = analysisJobs[episode.id]
                if (current === coroutineContext.job) analysisJobs.remove(episode.id)
                if (current == null || current === coroutineContext.job) {
                    _analysisProgress.update { it - episode.id }
                }
            }
            // Cancelled (download removed) — don't overwrite the reset status
            ensureActive()

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
        _analysisProgress.update { it - episode.id }
        downloader.deleteDownload(episode.id)
        localDataSource.updateDownloadPath(episode.id, null)
        localDataSource.updateAnalysisStatus(episode.id, AnalysisStatus.NONE)
        localDataSource.clearSegmentsData(episode.id)
    }

    /** Unfollows a podcast: stops its work, deletes its downloaded files, episodes, and the podcast. */
    fun deletePodcast(podcastId: Long) {
        scope.launch {
            val episodes = localDataSource.getEpisodesByPodcast(podcastId).first()
            if (playbackManager.state.value.episode?.podcastId == podcastId) {
                playbackManager.stop()
            }
            for (episode in episodes) {
                downloadJobs.remove(episode.id)?.cancel()
                analysisJobs.remove(episode.id)?.cancel()
                _analysisProgress.update { it - episode.id }
                downloader.deleteDownload(episode.id)
            }
            // Foreign keys aren't enabled on the drivers, so ON DELETE CASCADE doesn't fire
            localDataSource.deleteEpisodesByPodcast(podcastId)
            localDataSource.deletePodcast(podcastId)
        }
    }
}
