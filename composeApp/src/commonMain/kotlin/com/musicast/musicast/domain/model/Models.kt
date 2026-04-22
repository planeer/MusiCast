package com.musicast.musicast.domain.model

data class Podcast(
    val id: Long,
    val title: String,
    val description: String,
    val feedUrl: String,
    val artworkUrl: String?,
    val lastUpdated: Long,
)

data class Episode(
    val id: Long,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val durationMs: Long?,
    val publishDate: Long?,
    val isPlayed: Boolean,
    val downloadPath: String?,
    val analysisStatus: AnalysisStatus,
    val playbackPositionMs: Long,
)

enum class AnalysisStatus {
    NONE,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
}

enum class ContentType {
    SPEECH,
    MUSIC,
}

data class AudioSegment(
    val startMs: Long,
    val endMs: Long,
    val type: ContentType,
)

data class EpisodeWithPodcast(val episode: Episode, val podcast: Podcast)

data class PlaybackState(
    val episode: Episode? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val currentSpeed: Float = 1.0f,
    val userSpeed: Float = 1.0f,
    val isMusicDetected: Boolean = false,
    val musicDetectionEnabled: Boolean = true,
    val segments: List<AudioSegment> = emptyList(),
    val podcastTitle: String = "",
    val artworkUrl: String? = null,
)
