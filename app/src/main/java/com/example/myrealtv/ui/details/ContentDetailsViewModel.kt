package com.example.myrealtv.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myrealtv.data.ServiceLocator
import com.example.myrealtv.data.local.PlaybackHistory
import com.example.myrealtv.data.local.WatchedState
import com.example.myrealtv.data.remote.XcEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ContentDetailsUiState {
    object Loading : ContentDetailsUiState
    data class Error(val message: String) : ContentDetailsUiState
    data class MovieSuccess(
        val title: String,
        val description: String?,
        val year: String?,
        val rating: String?,
        val genre: String?,
        val cast: String?,
        val director: String?,
        val backdropUrl: String?,
        val posterUrl: String?,
        val playUrl: String,
        val streamId: String,
        val watchHistory: PlaybackHistory?,
        val watchedStates: Map<String, Boolean>
    ) : ContentDetailsUiState
    data class SeriesSuccess(
        val title: String,
        val description: String?,
        val year: String?,
        val rating: String?,
        val genre: String?,
        val cast: String?,
        val director: String?,
        val backdropUrl: String?,
        val posterUrl: String?,
        val seasons: List<String>,
        val episodes: Map<String, List<XcEpisode>>,
        val episodeHistory: Map<String, PlaybackHistory>, // Maps episode streamId to PlaybackHistory
        val seriesId: String,
        val watchedStates: Map<String, Boolean>
    ) : ContentDetailsUiState
}

class ContentDetailsViewModel(
    private val itemId: String,
    private val contentType: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContentDetailsUiState>(ContentDetailsUiState.Loading)
    val uiState: StateFlow<ContentDetailsUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = ContentDetailsUiState.Loading
            try {
                val userId = ServiceLocator.getActiveUserId() ?: "default_user"
                val username = ServiceLocator.getHouseholdId() ?: ""
                val password = ServiceLocator.getPassword()
                val baseUrl = ServiceLocator.xtreamBaseUrl

                // Fetch local watched states
                val watchedStatesList = try {
                    ServiceLocator.database.watchedStateDao().getWatchedStates(userId)
                } catch (e: Exception) {
                    emptyList()
                }
                val watchedStatesMap = watchedStatesList.associate { it.itemId to it.status }

                if (contentType == "movie") {
                    val vodResponse = ServiceLocator.xtreamApi.getVodInfo(
                        username = username,
                        pass = password,
                        vodId = itemId.toInt()
                    )
                    
                    val info = vodResponse.info
                    val movieData = vodResponse.movie_data
                    
                    if (info == null) {
                        _uiState.value = ContentDetailsUiState.Error("Movie details not found.")
                        return@launch
                    }

                    val container = movieData?.container_extension ?: "mp4"
                    val playUrl = "${baseUrl}movie/$username/$password/$itemId.$container"
                    
                    // Fetch local playback history
                    val history = ServiceLocator.database.playbackHistoryDao()
                        .getPlaybackHistoryForStream(userId, itemId)

                    val backdrop = info.backdrop_path?.firstOrNull() ?: info.movie_image
                    
                    _uiState.value = ContentDetailsUiState.MovieSuccess(
                        title = info.name ?: "Unknown Movie",
                        description = info.description,
                        year = info.releasedate?.split("-")?.firstOrNull() ?: info.releasedate,
                        rating = info.rating,
                        genre = info.genre,
                        cast = info.cast,
                        director = info.director,
                        backdropUrl = backdrop,
                        posterUrl = info.movie_image,
                        playUrl = playUrl,
                        streamId = itemId,
                        watchHistory = history,
                        watchedStates = watchedStatesMap
                    )
                } else if (contentType == "series") {
                    val seriesResponse = ServiceLocator.xtreamApi.getSeriesInfo(
                        username = username,
                        pass = password,
                        seriesId = itemId.toInt()
                    )

                    val info = seriesResponse.info
                    val episodesMap = seriesResponse.episodes ?: emptyMap()

                    if (info == null) {
                        _uiState.value = ContentDetailsUiState.Error("TV Series details not found.")
                        return@launch
                    }

                    // Extract unique season numbers, sorted
                    val seasonsList = episodesMap.keys.sortedWith(compareBy { it.toIntOrNull() ?: 999 })

                    // Fetch watch history for all episodes in this series
                    val allHistory = ServiceLocator.database.playbackHistoryDao().getPlaybackHistory(userId)
                    val episodeIds = episodesMap.values.flatten().map { it.streamId }.toSet()
                    val episodeHistory = allHistory.filter { it.streamId in episodeIds }.associateBy { it.streamId }

                    val backdrop = info.backdrop_path?.firstOrNull() ?: info.cover

                    _uiState.value = ContentDetailsUiState.SeriesSuccess(
                        title = info.name ?: "Unknown Series",
                        description = info.plot,
                        year = info.releaseDate?.split("-")?.firstOrNull() ?: info.releaseDate,
                        rating = info.rating,
                        genre = info.genre,
                        cast = info.cast,
                        director = info.director,
                        backdropUrl = backdrop,
                        posterUrl = info.cover,
                        seasons = seasonsList,
                        episodes = episodesMap,
                        episodeHistory = episodeHistory,
                        seriesId = itemId,
                        watchedStates = watchedStatesMap
                    )
                } else {
                    _uiState.value = ContentDetailsUiState.Error("Unsupported content type: $contentType")
                }
            } catch (e: Exception) {
                _uiState.value = ContentDetailsUiState.Error(e.localizedMessage ?: "Failed to load content details.")
            }
        }
    }

    fun toggleMovieWatched(streamId: String) {
        viewModelScope.launch {
            val userId = ServiceLocator.getActiveUserId() ?: "default_user"
            val current = ServiceLocator.database.watchedStateDao().getWatchedState(userId, "movie_${streamId}")
            val nextStatus = !(current?.status ?: false)

            val state = WatchedState(userId, "movie_${streamId}", nextStatus)
            ServiceLocator.database.watchedStateDao().insert(state)
            try {
                ServiceLocator.syncApi.syncWatchedState(state)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            loadDetails()
        }
    }

    fun toggleSeriesWatched(seriesId: String) {
        viewModelScope.launch {
            val userId = ServiceLocator.getActiveUserId() ?: "default_user"
            val current = ServiceLocator.database.watchedStateDao().getWatchedState(userId, "series_${seriesId}")
            val nextStatus = !(current?.status ?: false)

            val state = WatchedState(userId, "series_${seriesId}", nextStatus)
            ServiceLocator.database.watchedStateDao().insert(state)
            try {
                ServiceLocator.syncApi.syncWatchedState(state)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Sync all episodes in series
            val username = ServiceLocator.getHouseholdId() ?: ""
            val password = ServiceLocator.getPassword()
            val seriesDetails = try {
                ServiceLocator.xtreamApi.getSeriesInfo(
                    username = username,
                    pass = password,
                    seriesId = seriesId.toInt()
                )
            } catch (e: Exception) {
                null
            }

            if (seriesDetails != null) {
                val episodesMap = seriesDetails.episodes ?: emptyMap()
                val allEpisodes = episodesMap.values.flatten()
                for (ep in allEpisodes) {
                    val epState = WatchedState(userId, "episode_${ep.streamId}", nextStatus)
                    ServiceLocator.database.watchedStateDao().insert(epState)
                    try {
                        ServiceLocator.syncApi.syncWatchedState(epState)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Add playback history placeholder
                    val history = PlaybackHistory(
                        userId = userId,
                        streamId = ep.streamId,
                        lastPosition = 0,
                        totalDuration = 0,
                        isSeries = true,
                        seriesId = seriesId,
                        episodeNum = ep.episodeNum
                    )
                    ServiceLocator.database.playbackHistoryDao().insert(history)
                    try {
                        ServiceLocator.syncApi.syncPlaybackHistory(history)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            loadDetails()
        }
    }

    fun toggleEpisodeWatched(episodeStreamId: String, episodeNum: Int) {
        viewModelScope.launch {
            val userId = ServiceLocator.getActiveUserId() ?: "default_user"
            val current = ServiceLocator.database.watchedStateDao().getWatchedState(userId, "episode_${episodeStreamId}")
            val nextStatus = !(current?.status ?: false)

            val state = WatchedState(userId, "episode_${episodeStreamId}", nextStatus)
            ServiceLocator.database.watchedStateDao().insert(state)
            try {
                ServiceLocator.syncApi.syncWatchedState(state)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Insert placeholder playback history to resolve Up Next
            val history = PlaybackHistory(
                userId = userId,
                streamId = episodeStreamId,
                lastPosition = 0,
                totalDuration = 0,
                isSeries = true,
                seriesId = itemId,
                episodeNum = episodeNum
            )
            ServiceLocator.database.playbackHistoryDao().insert(history)
            try {
                ServiceLocator.syncApi.syncPlaybackHistory(history)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            loadDetails()
        }
    }

    fun toggleSeasonWatched(episodes: List<XcEpisode>) {
        viewModelScope.launch {
            val userId = ServiceLocator.getActiveUserId() ?: "default_user"
            
            // Check if all episodes in this season are watched
            val watchedStates = episodes.map { ep ->
                ServiceLocator.database.watchedStateDao().getWatchedState(userId, "episode_${ep.streamId}")?.status ?: false
            }
            val allWatched = watchedStates.isNotEmpty() && watchedStates.all { it }
            val nextStatus = !allWatched

            for (ep in episodes) {
                val state = WatchedState(userId, "episode_${ep.streamId}", nextStatus)
                ServiceLocator.database.watchedStateDao().insert(state)
                try {
                    ServiceLocator.syncApi.syncWatchedState(state)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Add placeholder playback history
                val history = PlaybackHistory(
                    userId = userId,
                    streamId = ep.streamId,
                    lastPosition = 0,
                    totalDuration = 0,
                    isSeries = true,
                    seriesId = itemId,
                    episodeNum = ep.episodeNum
                )
                ServiceLocator.database.playbackHistoryDao().insert(history)
                try {
                    ServiceLocator.syncApi.syncPlaybackHistory(history)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            loadDetails()
        }
    }

    fun clearWatchHistory(streamId: String) {
        viewModelScope.launch {
            val userId = ServiceLocator.getActiveUserId() ?: "default_user"
            
            // Delete local history
            ServiceLocator.database.playbackHistoryDao().dismissPlaybackHistory(userId, streamId)
            
            // Also notify the VPS sync API to delete watch state/history
            val history = ServiceLocator.database.playbackHistoryDao().getPlaybackHistoryForStream(userId, streamId)
            if (history != null) {
                try {
                    ServiceLocator.syncApi.syncPlaybackHistory(history.copy(isDismissed = true))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Refresh details state to reflect changes immediately
            loadDetails()
        }
    }
}
