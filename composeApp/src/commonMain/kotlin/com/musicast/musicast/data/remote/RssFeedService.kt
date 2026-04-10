package com.musicast.musicast.data.remote

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem

class RssFeedService(private val rssParser: RssParser) {

    suspend fun fetchFeed(feedUrl: String): FeedResult {
        val channel = rssParser.getRssChannel(feedUrl)
        return FeedResult(
            title = channel.title ?: "Unknown Podcast",
            description = channel.description ?: "",
            artworkUrl = channel.image?.url ?: channel.itunesChannelData?.image,
            episodes = channel.items.mapNotNull { item -> item.toFeedEpisode() },
        )
    }

    private fun RssItem.toFeedEpisode(): FeedEpisode? {
        val audioUrl = audio
            ?: rawEnclosure?.url?.takeIf { rawEnclosure?.type?.startsWith("audio") != false }
            ?: return null

        return FeedEpisode(
            guid = guid ?: audioUrl,
            title = title ?: "Untitled",
            description = description ?: "",
            audioUrl = audioUrl,
            durationMs = itunesItemData?.duration?.toDurationMs(),
            publishDate = pubDate?.toEpochMs(),
        )
    }
}

data class FeedResult(
    val title: String,
    val description: String,
    val artworkUrl: String?,
    val episodes: List<FeedEpisode>,
)

data class FeedEpisode(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val durationMs: Long?,
    val publishDate: Long?,
)
