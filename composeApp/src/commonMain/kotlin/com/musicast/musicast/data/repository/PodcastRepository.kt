package com.musicast.musicast.data.repository

import com.musicast.musicast.data.local.LocalDataSource
import com.musicast.musicast.data.remote.RssFeedService
import com.musicast.musicast.domain.model.Episode
import com.musicast.musicast.domain.model.Podcast
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

class PodcastRepository(
    private val localDataSource: LocalDataSource,
    private val rssFeedService: RssFeedService,
) {
    fun getPodcasts(): Flow<List<Podcast>> = localDataSource.getAllPodcasts()

    fun getPodcastById(id: Long): Flow<Podcast?> = localDataSource.getPodcastById(id)

    fun getEpisodes(podcastId: Long): Flow<List<Episode>> =
        localDataSource.getEpisodesByPodcast(podcastId)

    fun getEpisodeById(id: Long): Flow<Episode?> = localDataSource.getEpisodeById(id)

    suspend fun addPodcast(feedUrl: String): Result<Long> {
        return try {
            val feed = rssFeedService.fetchFeed(feedUrl)
            val now = Clock.System.now().toEpochMilliseconds()

            val podcastId = localDataSource.insertPodcast(
                title = feed.title,
                description = feed.description,
                feedUrl = feedUrl,
                artworkUrl = feed.artworkUrl,
                lastUpdated = now,
            )

            for (episode in feed.episodes) {
                localDataSource.insertEpisode(
                    podcastId = podcastId,
                    guid = episode.guid,
                    title = episode.title,
                    description = episode.description,
                    audioUrl = episode.audioUrl,
                    durationMs = episode.durationMs,
                    publishDate = episode.publishDate,
                )
            }

            Result.success(podcastId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshPodcast(podcastId: Long, feedUrl: String): Result<Unit> {
        return try {
            val feed = rssFeedService.fetchFeed(feedUrl)
            val now = Clock.System.now().toEpochMilliseconds()

            localDataSource.updatePodcast(
                id = podcastId,
                title = feed.title,
                description = feed.description,
                artworkUrl = feed.artworkUrl,
                lastUpdated = now,
            )

            for (episode in feed.episodes) {
                localDataSource.insertEpisode(
                    podcastId = podcastId,
                    guid = episode.guid,
                    title = episode.title,
                    description = episode.description,
                    audioUrl = episode.audioUrl,
                    durationMs = episode.durationMs,
                    publishDate = episode.publishDate,
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deletePodcast(podcastId: Long) {
        localDataSource.deletePodcast(podcastId)
    }

    fun updatePlaybackPosition(episodeId: Long, positionMs: Long) {
        localDataSource.updatePlaybackPosition(episodeId, positionMs)
    }

    fun markAsPlayed(episodeId: Long) {
        localDataSource.markAsPlayed(episodeId)
    }
}
