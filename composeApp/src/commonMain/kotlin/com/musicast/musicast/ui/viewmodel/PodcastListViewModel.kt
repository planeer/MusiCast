package com.musicast.musicast.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicast.musicast.data.repository.PodcastRepository
import com.musicast.musicast.domain.model.Podcast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PodcastListState(
    val podcasts: List<Podcast> = emptyList(),
    val isAddingPodcast: Boolean = false,
    val addPodcastUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddDialog: Boolean = false,
)

class PodcastListViewModel(
    private val repository: PodcastRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PodcastListState())
    val state: StateFlow<PodcastListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getPodcasts().collect { podcasts ->
                _state.update { it.copy(podcasts = podcasts) }
            }
        }
    }

    fun showAddDialog() {
        _state.update { it.copy(showAddDialog = true, addPodcastUrl = "", error = null) }
    }

    fun dismissAddDialog() {
        _state.update { it.copy(showAddDialog = false, addPodcastUrl = "", error = null) }
    }

    fun onUrlChanged(url: String) {
        _state.update { it.copy(addPodcastUrl = url, error = null) }
    }

    fun addPodcast() {
        val url = _state.value.addPodcastUrl.trim()
        if (url.isEmpty()) {
            _state.update { it.copy(error = "Please enter a feed URL") }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.addPodcast(url)
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false, showAddDialog = false, addPodcastUrl = "") }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to add podcast") }
                },
            )
        }
    }

    fun deletePodcast(podcastId: Long) {
        repository.deletePodcast(podcastId)
    }
}
