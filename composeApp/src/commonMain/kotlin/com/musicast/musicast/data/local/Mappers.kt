package com.musicast.musicast.data.local

import com.musicast.musicast.db.Episode as DbEpisode
import com.musicast.musicast.db.Podcast as DbPodcast
import com.musicast.musicast.db.SelectDownloadedEpisodesWithPodcast
import com.musicast.musicast.domain.model.AnalysisStatus
import com.musicast.musicast.domain.model.Episode
import com.musicast.musicast.domain.model.EpisodeWithPodcast
import com.musicast.musicast.domain.model.Podcast

fun DbPodcast.toDomain(): Podcast = Podcast(
    id = id,
    title = title,
    description = description,
    feedUrl = feed_url,
    artworkUrl = artwork_url,
    lastUpdated = last_updated,
)

fun SelectDownloadedEpisodesWithPodcast.toEpisodeWithPodcast(): EpisodeWithPodcast {
    val episode = Episode(
        id = id,
        podcastId = podcast_id,
        guid = guid,
        title = episode_title,
        description = description,
        audioUrl = audio_url,
        durationMs = duration_ms,
        publishDate = publish_date,
        isPlayed = is_played != 0L,
        downloadPath = download_path,
        analysisStatus = try {
            AnalysisStatus.valueOf(analysis_status)
        } catch (_: IllegalArgumentException) {
            AnalysisStatus.NONE
        },
        playbackPositionMs = playback_position_ms,
    )
    val podcast = Podcast(
        id = podcast_id,
        title = podcast_title,
        description = "",
        feedUrl = "",
        artworkUrl = podcast_artwork_url,
        lastUpdated = 0L,
    )
    return EpisodeWithPodcast(episode, podcast)
}

fun DbEpisode.toDomain(): Episode = Episode(
    id = id,
    podcastId = podcast_id,
    guid = guid,
    title = title,
    description = description,
    audioUrl = audio_url,
    durationMs = duration_ms,
    publishDate = publish_date,
    isPlayed = is_played != 0L,
    downloadPath = download_path,
    analysisStatus = try {
        AnalysisStatus.valueOf(analysis_status)
    } catch (_: IllegalArgumentException) {
        AnalysisStatus.NONE
    },
    playbackPositionMs = playback_position_ms,
)
