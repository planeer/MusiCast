package com.musicast.musicast.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.musicast.musicast.db.PodcastDatabase
import com.musicast.musicast.domain.model.AnalysisStatus
import com.musicast.musicast.domain.model.AudioSegment
import com.musicast.musicast.domain.model.ContentType
import com.musicast.musicast.domain.model.Episode
import com.musicast.musicast.domain.model.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalDataSource(private val db: PodcastDatabase) {

    fun getAllPodcasts(): Flow<List<Podcast>> =
        db.podcastDatabaseQueries.selectAllPodcasts()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toDomain() } }

    fun getPodcastById(id: Long): Flow<Podcast?> =
        db.podcastDatabaseQueries.selectPodcastById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() }

    fun getEpisodesByPodcast(podcastId: Long): Flow<List<Episode>> =
        db.podcastDatabaseQueries.selectEpisodesByPodcast(podcastId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toDomain() } }

    fun getEpisodeById(id: Long): Flow<Episode?> =
        db.podcastDatabaseQueries.selectEpisodeById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() }

    fun insertPodcast(
        title: String,
        description: String,
        feedUrl: String,
        artworkUrl: String?,
        lastUpdated: Long,
    ): Long {
        db.podcastDatabaseQueries.insertPodcast(
            title = title,
            description = description,
            feed_url = feedUrl,
            artwork_url = artworkUrl,
            last_updated = lastUpdated,
        )
        return db.podcastDatabaseQueries.lastInsertId().executeAsOne()
    }

    fun updatePodcast(id: Long, title: String, description: String, artworkUrl: String?, lastUpdated: Long) {
        db.podcastDatabaseQueries.updatePodcast(
            title = title,
            description = description,
            artwork_url = artworkUrl,
            last_updated = lastUpdated,
            id = id,
        )
    }

    fun deletePodcast(id: Long) {
        db.podcastDatabaseQueries.deletePodcast(id)
    }

    fun insertEpisode(
        podcastId: Long,
        guid: String,
        title: String,
        description: String,
        audioUrl: String,
        durationMs: Long?,
        publishDate: Long?,
    ) {
        db.podcastDatabaseQueries.insertEpisode(
            podcast_id = podcastId,
            guid = guid,
            title = title,
            description = description,
            audio_url = audioUrl,
            duration_ms = durationMs,
            publish_date = publishDate,
        )
    }

    fun updateDownloadPath(episodeId: Long, path: String?) {
        db.podcastDatabaseQueries.updateDownloadPath(download_path = path, id = episodeId)
    }

    fun updateAnalysisStatus(episodeId: Long, status: AnalysisStatus) {
        db.podcastDatabaseQueries.updateAnalysisStatus(analysis_status = status.name, id = episodeId)
    }

    fun updatePlaybackPosition(episodeId: Long, positionMs: Long) {
        db.podcastDatabaseQueries.updatePlaybackPosition(playback_position_ms = positionMs, id = episodeId)
    }

    fun markAsPlayed(episodeId: Long) {
        db.podcastDatabaseQueries.markAsPlayed(episodeId)
    }

    fun saveSegments(episodeId: Long, segments: List<AudioSegment>) {
        val data = segments.joinToString(";") { "${it.startMs}:${it.endMs}:${it.type.name}" }
        db.podcastDatabaseQueries.updateSegmentsData(segments_data = data, id = episodeId)
    }

    fun loadSegments(episodeId: Long): List<AudioSegment>? {
        val data = db.podcastDatabaseQueries.selectSegmentsData(episodeId)
            .executeAsOneOrNull()
            ?.segments_data ?: return null
        if (data.isBlank()) return null
        return data.split(";").mapNotNull { part ->
            val tokens = part.split(":")
            if (tokens.size == 3) {
                AudioSegment(
                    startMs = tokens[0].toLongOrNull() ?: return@mapNotNull null,
                    endMs = tokens[1].toLongOrNull() ?: return@mapNotNull null,
                    type = try { ContentType.valueOf(tokens[2]) } catch (_: Exception) { return@mapNotNull null },
                )
            } else null
        }
    }

    fun clearSegmentsData(episodeId: Long) {
        db.podcastDatabaseQueries.updateSegmentsData(segments_data = null, id = episodeId)
    }

    fun getSetting(key: String): String? {
        return db.podcastDatabaseQueries.getSetting(key).executeAsOneOrNull()
    }

    fun setSetting(key: String, value: String) {
        db.podcastDatabaseQueries.setSetting(key, value_ = value)
    }
}
