package com.musicast.musicast.ui.navigation

sealed class Screen {
    data object PodcastList : Screen()
    data class EpisodeList(val podcastId: Long, val podcastTitle: String, val artworkUrl: String? = null) : Screen()
    data object Player : Screen()
}
