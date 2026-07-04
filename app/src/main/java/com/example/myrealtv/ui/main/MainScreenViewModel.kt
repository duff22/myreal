package com.example.myrealtv.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myrealtv.data.ServiceLocator
import com.example.myrealtv.data.local.PlaybackHistory
import com.example.myrealtv.data.local.HomeCatalogCache
import com.example.myrealtv.data.local.WatchedState
import com.example.myrealtv.data.model.ResolvedItem
import com.example.myrealtv.data.remote.XcVodStream
import com.example.myrealtv.data.remote.XcSeries
import com.example.myrealtv.data.remote.XcEpisode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val message: String) : MainScreenUiState
    data class Success(
        val configRows: List<ResolvedRow>,
        val movieRows: List<ResolvedRow>,
        val seriesRows: List<ResolvedRow>,
        val continueWatching: List<ContinueWatchingItem>,
        val nextUp: List<ResolvedItem>,
        val watchedStates: Map<String, Boolean>
    ) : MainScreenUiState
}

data class ResolvedRow(
    val id: String,
    val title: String,
    val items: List<ResolvedItem>
)

data class ContinueWatchingItem(
    val history: PlaybackHistory,
    val item: ResolvedItem
)

private data class PreprocessedMovie(
    val stream: XcVodStream,
    val cleanName: String,
    val year: Int?
)

private data class PreprocessedSeries(
    val series: XcSeries,
    val cleanName: String,
    val year: Int?
)

class MainScreenViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    companion object {
        private val YEAR_REGEX = Regex("\\b(19|20)\\d{2}\\b")
    }

    init {
        loadData()
    }

    private fun cleanTitle(title: String): String {
        var s = title.lowercase()
        s = s.replace(Regex("[\\[(].*?[\\])]"), "")
        s = s.replace(Regex("\\b(4k|1080p|720p|hdtv|bluray|x264|x265|hevc|web-dl|webrip|uhd)\\b"), "")
        s = s.replace(Regex("\\b(s\\d+(e\\d+)?|season\\s*\\d+|ep\\s*\\d+|episode\\s*\\d+)\\b"), "")
        s = s.replace(Regex("\\b(dual|audio|multi|subbed|dubbed|sub|dub|hindi|english|french|spanish)\\b"), "")
        s = s.replace(Regex("\\b(19|20)\\d{2}\\b"), "")
        s = s.replace(Regex("[^a-z0-9\\s]"), "")
        s = s.replace(Regex("\\s+"), " ")
        return s.trim()
    }

    private fun extractYear(name: String): Int? {
        val match = YEAR_REGEX.find(name)
        return match?.value?.toInt()
    }

    fun loadData() {
        if (_uiState.value is MainScreenUiState.Success) {
            refreshLocalStates()
            return
        }

        viewModelScope.launch {
            _uiState.value = MainScreenUiState.Loading
            
            val userId = ServiceLocator.getActiveUserId() ?: "default_user"
            val username = ServiceLocator.getHouseholdId() ?: ""
            val password = ServiceLocator.getPassword()
            val baseUrl = ServiceLocator.xtreamBaseUrl

            // 1. Instantly load from local Room Cache
            var initialHomeRows = emptyList<ResolvedRow>()
            try {
                val cache = ServiceLocator.database.homeCatalogCacheDao().getCache(username)
                if (cache != null) {
                    val typeToken = object : TypeToken<List<ResolvedRow>>() {}.type
                    initialHomeRows = Gson().fromJson(cache.serializedRowsJson, typeToken)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Load local playback history
            val localHistory = try {
                ServiceLocator.database.playbackHistoryDao().getPlaybackHistory(userId)
            } catch (e: Exception) {
                emptyList()
            }
            
            val cachedItemsMap = initialHomeRows.flatMap { it.items }.associateBy { it.id }
            
            // Continue watching row (MOVIES ONLY, between 10% and 92%)
            val continueWatchingList = localHistory
                .filter { !it.isSeries && !it.isDismissed }
                .filter {
                    val pct = if (it.totalDuration > 0) it.lastPosition.toFloat() / it.totalDuration.toFloat() else 0f
                    pct >= 0.10f && pct <= 0.92f
                }
                .mapNotNull { history ->
                    val cachedItem = cachedItemsMap[history.streamId]
                    if (cachedItem != null) ContinueWatchingItem(history, cachedItem) else null
                }

            val localWatchedList = try {
                ServiceLocator.database.watchedStateDao().getWatchedStates(userId)
            } catch (e: Exception) {
                emptyList()
            }
            val initialWatchedMap = localWatchedList.associate { it.itemId to it.status }

            val initialNextUp = calculateNextUp(userId, localHistory, initialWatchedMap, baseUrl, username, password)

            if (initialHomeRows.isNotEmpty()) {
                _uiState.value = MainScreenUiState.Success(
                    configRows = initialHomeRows,
                    movieRows = emptyList(),
                    seriesRows = emptyList(),
                    continueWatching = continueWatchingList,
                    nextUp = initialNextUp,
                    watchedStates = initialWatchedMap
                )
            }

            // Sync remote watched states from VPS
            try {
                val remoteWatched = ServiceLocator.syncApi.getWatchedStates(userId)
                for (state in remoteWatched) {
                    ServiceLocator.database.watchedStateDao().insert(state)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Perform background loading and catalog synchronization
            try {
                // Fetch Remote Config Rows
                val configResponse = try {
                    ServiceLocator.configApi.getConfig()
                } catch (e: Exception) {
                    null
                }
                val mdbRows = configResponse?.rows ?: emptyList()

                // Fetch Xtream Codes streams
                val providerMovies = try {
                    ServiceLocator.xtreamApi.getVodStreams(username, password)
                } catch (e: Exception) {
                    emptyList()
                }

                val providerSeries = try {
                    ServiceLocator.xtreamApi.getSeries(username, password)
                } catch (e: Exception) {
                    emptyList()
                }

                // Fetch Xtream Codes Categories
                val movieCategories = try {
                    ServiceLocator.xtreamApi.getVodCategories(username, password)
                } catch (e: Exception) {
                    emptyList()
                }

                val seriesCategories = try {
                    ServiceLocator.xtreamApi.getSeriesCategories(username, password)
                } catch (e: Exception) {
                    emptyList()
                }

                val resultState = withContext(Dispatchers.Default) {
                    val preprocessedMovies = providerMovies.map { stream ->
                        val name = stream.name ?: ""
                        PreprocessedMovie(stream, cleanTitle(name), extractYear(name))
                    }

                    val preprocessedSeries = providerSeries.map { series ->
                        val name = series.name ?: ""
                        PreprocessedSeries(series, cleanTitle(name), extractYear(name))
                    }

                    val resolvedConfigRows = mutableListOf<ResolvedRow>()
                    val allResolvedItemsMap = mutableMapOf<String, ResolvedItem>()

                    // Match items for HOME tab using MDBList list links
                    for (row in mdbRows) {
                        val rawUrl = row.listUrl.trim()
                        val targetUrl = if (rawUrl.contains("mdblist.com/lists/", ignoreCase = true) &&
                            !rawUrl.endsWith("/json", ignoreCase = true) &&
                            !rawUrl.endsWith("/json/", ignoreCase = true)) {
                            if (rawUrl.endsWith("/")) "${rawUrl}json/" else "$rawUrl/json/"
                        } else {
                            rawUrl
                        }

                        val sanitizedType = when {
                            row.type.startsWith("movie", ignoreCase = true) -> "movie"
                            row.type.startsWith("show", ignoreCase = true) || row.type.equals("series", ignoreCase = true) -> "series"
                            else -> "movie"
                        }

                        val mdbListItems = try {
                            ServiceLocator.mdbListApi.getListItems(targetUrl)
                        } catch (e: Exception) {
                            emptyList()
                        }

                        val matchedItems = mutableListOf<ResolvedItem>()
                        for (item in mdbListItems) {
                            val cleanMdbTitle = cleanTitle(item.title)
                            
                            if (sanitizedType == "movie") {
                                val matched = preprocessedMovies.find { prep ->
                                    val titleMatches = prep.cleanName == cleanMdbTitle
                                    if (titleMatches) {
                                        if (item.year != null && prep.year != null) {
                                            prep.year == item.year
                                        } else {
                                            true
                                        }
                                    } else {
                                        false
                                    }
                                }
                                
                                if (matched != null) {
                                    val stream = matched.stream
                                    val resolvedUrl = "${baseUrl}movie/$username/$password/${stream.stream_id}.${stream.container_extension ?: "mp4"}"
                                    val resolvedItem = ResolvedItem(
                                        id = stream.stream_id.toString(),
                                        title = stream.name ?: item.title,
                                        poster = stream.stream_icon,
                                        url = resolvedUrl,
                                        type = "movie"
                                    )
                                    matchedItems.add(resolvedItem)
                                    allResolvedItemsMap[resolvedItem.id] = resolvedItem
                                }
                            } else if (sanitizedType == "series") {
                                val matched = preprocessedSeries.find { prep ->
                                    val titleMatches = prep.cleanName == cleanMdbTitle
                                    if (titleMatches) {
                                        if (item.year != null && prep.year != null) {
                                            prep.year == item.year
                                        } else {
                                            true
                                        }
                                    } else {
                                        false
                                    }
                                }
                                
                                if (matched != null) {
                                    val series = matched.series
                                    val resolvedUrl = "${baseUrl}series/$username/$password/${series.series_id}.mp4"
                                    val resolvedItem = ResolvedItem(
                                        id = series.series_id.toString(),
                                        title = series.name ?: item.title,
                                        poster = series.cover,
                                        url = resolvedUrl,
                                        type = "series",
                                        seriesId = series.series_id.toString()
                                    )
                                    matchedItems.add(resolvedItem)
                                    allResolvedItemsMap[resolvedItem.id] = resolvedItem
                                }
                            }
                        }
                        
                        if (matchedItems.isNotEmpty()) {
                            resolvedConfigRows.add(ResolvedRow(row.id, row.title, matchedItems))
                        }
                    }

                    // Save the newly resolved Home rows to Database Cache
                    if (resolvedConfigRows.isNotEmpty()) {
                        try {
                            val serialized = Gson().toJson(resolvedConfigRows)
                            ServiceLocator.database.homeCatalogCacheDao().insertCache(
                                HomeCatalogCache(username, serialized)
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Build MOVIES tab rows
                    val movieRows = movieCategories.map { category ->
                        val categoryStreams = providerMovies.filter { it.category_id == category.category_id }
                        val resolvedItems = categoryStreams.map { stream ->
                            val resolvedUrl = "${baseUrl}movie/$username/$password/${stream.stream_id}.${stream.container_extension ?: "mp4"}"
                            val resolvedItem = ResolvedItem(
                                id = stream.stream_id.toString(),
                                title = stream.name ?: "Unknown Movie",
                                poster = stream.stream_icon,
                                url = resolvedUrl,
                                type = "movie"
                            )
                            allResolvedItemsMap[resolvedItem.id] = resolvedItem
                            resolvedItem
                        }
                        ResolvedRow(category.category_id, category.category_name, resolvedItems)
                    }.filter { it.items.isNotEmpty() }

                    // Build TV SHOWS tab rows
                    val seriesRows = seriesCategories.map { category ->
                        val categorySeries = providerSeries.filter { it.category_id == category.category_id }
                        val resolvedItems = categorySeries.map { series ->
                            val resolvedUrl = "${baseUrl}series/$username/$password/${series.series_id}.mp4"
                            val resolvedItem = ResolvedItem(
                                id = series.series_id.toString(),
                                title = series.name ?: "Unknown Series",
                                poster = series.cover,
                                url = resolvedUrl,
                                type = "series",
                                seriesId = series.series_id.toString()
                            )
                            allResolvedItemsMap[resolvedItem.id] = resolvedItem
                            resolvedItem
                        }
                        ResolvedRow(category.category_id, category.category_name, resolvedItems)
                    }.filter { it.items.isNotEmpty() }

                    // Fetch latest playback history records from local database
                    val latestHistory = ServiceLocator.database.playbackHistoryDao().getPlaybackHistory(userId)

                    // Fetch latest watched states map
                    val updatedWatchedList = try {
                        ServiceLocator.database.watchedStateDao().getWatchedStates(userId)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val updatedWatchedMap = updatedWatchedList.associate { it.itemId to it.status }

                    // Continue watching row (MOVIES ONLY, between 10% and 92%, and NOT marked as watched)
                    val updatedContinueWatchingList = latestHistory
                        .filter { !it.isSeries && !it.isDismissed }
                        .filter { updatedWatchedMap["movie_${it.streamId}"] != true }
                        .filter {
                            val pct = if (it.totalDuration > 0) it.lastPosition.toFloat() / it.totalDuration.toFloat() else 0f
                            pct >= 0.10f && pct <= 0.92f
                        }
                        .mapNotNull { history ->
                            val resolved = allResolvedItemsMap[history.streamId] ?: run {
                                val stream = providerMovies.find { it.stream_id.toString() == history.streamId }
                                if (stream != null) {
                                    val resolvedUrl = "${baseUrl}movie/$username/$password/${stream.stream_id}.${stream.container_extension ?: "mp4"}"
                                    ResolvedItem(
                                        id = stream.stream_id.toString(),
                                        title = stream.name ?: "Unknown Movie",
                                        poster = stream.stream_icon,
                                        url = resolvedUrl,
                                        type = "movie"
                                    )
                                } else {
                                    null
                                }
                            }
                            if (resolved != null) ContinueWatchingItem(history, resolved) else null
                        }

                    val nextUpList = calculateNextUp(userId, latestHistory, updatedWatchedMap, baseUrl, username, password)

                    MainScreenUiState.Success(
                        configRows = resolvedConfigRows,
                        movieRows = movieRows,
                        seriesRows = seriesRows,
                        continueWatching = updatedContinueWatchingList,
                        nextUp = nextUpList,
                        watchedStates = updatedWatchedMap
                    )
                }

                _uiState.value = resultState
            } catch (e: Exception) {
                if (initialHomeRows.isEmpty()) {
                    _uiState.value = MainScreenUiState.Error(e.localizedMessage ?: "Failed to synchronize catalog.")
                }
            }
        }
    }

    fun toggleWatchedState(itemId: String, isSeries: Boolean) {
        viewModelScope.launch {
            val userId = ServiceLocator.getActiveUserId() ?: "default_user"
            val dbKey = if (isSeries) "series_${itemId}" else "movie_${itemId}"
            val current = ServiceLocator.database.watchedStateDao().getWatchedState(userId, dbKey)
            val nextStatus = !(current?.status ?: false)

            if (isSeries) {
                // Fetch series info to get all episodes
                val username = ServiceLocator.getHouseholdId() ?: ""
                val password = ServiceLocator.getPassword()
                val seriesDetails = try {
                    ServiceLocator.xtreamApi.getSeriesInfo(
                        username = username,
                        pass = password,
                        seriesId = itemId.toInt()
                    )
                } catch (e: Exception) {
                    null
                }

                if (seriesDetails != null) {
                    val episodesMap = seriesDetails.episodes ?: emptyMap()
                    val allEpisodes = episodesMap.values.flatten()
                    
                    // Toggle all episodes in the series
                    for (ep in allEpisodes) {
                        val epState = WatchedState(userId, "episode_${ep.streamId}", nextStatus)
                        ServiceLocator.database.watchedStateDao().insert(epState)
                        try {
                            ServiceLocator.syncApi.syncWatchedState(epState)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        // Add placeholder playback history so Next Up tracks this series
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
                }
            }

            // Toggle show or movie itself
            val state = WatchedState(userId, dbKey, nextStatus)
            ServiceLocator.database.watchedStateDao().insert(state)
            try {
                ServiceLocator.syncApi.syncWatchedState(state)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            loadData()
        }
    }

    fun dismissContinueWatching(streamId: String) {
        viewModelScope.launch {
            val userId = ServiceLocator.getActiveUserId() ?: "default_user"
            ServiceLocator.database.playbackHistoryDao().dismissPlaybackHistory(userId, streamId)
            
            val history = ServiceLocator.database.playbackHistoryDao().getPlaybackHistoryForStream(userId, streamId)
            if (history != null) {
                try {
                    ServiceLocator.syncApi.syncPlaybackHistory(history)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            loadData()
        }
    }

    private fun refreshLocalStates() {
        val currentState = _uiState.value as? MainScreenUiState.Success ?: return
        viewModelScope.launch {
            try {
                val userId = ServiceLocator.getActiveUserId() ?: "default_user"
                val username = ServiceLocator.getHouseholdId() ?: ""
                val password = ServiceLocator.getPassword()
                val baseUrl = ServiceLocator.xtreamBaseUrl

                // Fetch latest playback history records from local database
                val latestHistory = ServiceLocator.database.playbackHistoryDao().getPlaybackHistory(userId)

                // Fetch latest watched states map
                val updatedWatchedList = try {
                    ServiceLocator.database.watchedStateDao().getWatchedStates(userId)
                } catch (e: Exception) {
                    emptyList()
                }
                val updatedWatchedMap = updatedWatchedList.associate { it.itemId to it.status }

                // 1. Re-calculate continue watching using resolved items
                val updatedContinueWatchingList = latestHistory
                    .filter { !it.isSeries && !it.isDismissed }
                    .filter { updatedWatchedMap["movie_${it.streamId}"] != true }
                    .filter {
                        val pct = if (it.totalDuration > 0) it.lastPosition.toFloat() / it.totalDuration.toFloat() else 0f
                        pct >= 0.10f && pct <= 0.92f
                    }
                    .mapNotNull { history ->
                        val resolved = currentState.configRows.flatMap { it.items }.find { it.id == history.streamId }
                            ?: currentState.movieRows.flatMap { it.items }.find { it.id == history.streamId }
                        if (resolved != null) ContinueWatchingItem(history, resolved) else null
                    }

                val nextUpList = calculateNextUp(userId, latestHistory, updatedWatchedMap, baseUrl, username, password)

                _uiState.value = currentState.copy(
                    continueWatching = updatedContinueWatchingList,
                    nextUp = nextUpList,
                    watchedStates = updatedWatchedMap
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun calculateNextUp(
        userId: String,
        latestHistory: List<PlaybackHistory>,
        updatedWatchedMap: Map<String, Boolean>,
        baseUrl: String,
        username: String,
        password: String
    ): List<ResolvedItem> {
        val seriesHistory = latestHistory.filter { it.isSeries && !it.isDismissed }
        val nextUpList = mutableListOf<ResolvedItem>()

        val activeSeriesIds = (
            seriesHistory.mapNotNull { it.seriesId } +
            updatedWatchedMap.filter { it.value }.keys
                .filter { it.startsWith("series_") }
                .map { it.removePrefix("series_") }
        ).distinct()

        for (seriesId in activeSeriesIds) {
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
                val info = seriesDetails.info
                val episodesMap = seriesDetails.episodes ?: emptyMap()
                
                data class EpisodeWithSeason(val episode: XcEpisode, val seasonKey: String)
                val allEpisodesWithSeason = episodesMap.flatMap { (seasonKey, list) ->
                    list.map { EpisodeWithSeason(it, seasonKey) }
                }.sortedWith(
                    compareBy<EpisodeWithSeason> { it.seasonKey.toIntOrNull() ?: 1 }
                    .thenBy { it.episode.episodeNum }
                )

                if (allEpisodesWithSeason.isEmpty()) continue

                val seriesEpHistory = seriesHistory.filter { it.seriesId == seriesId }.associateBy { it.streamId }

                var nextUpEp: EpisodeWithSeason? = null
                
                val partiallyWatchedEp = allEpisodesWithSeason.find { ews ->
                    val history = seriesEpHistory[ews.episode.streamId]
                    val isWatched = updatedWatchedMap["episode_${ews.episode.streamId}"] == true ||
                            (history?.let { it.lastPosition.toFloat() / it.totalDuration.toFloat() >= 0.92f } ?: false)
                    !isWatched && (history?.let { it.lastPosition > 0 } ?: false)
                }
                
                if (partiallyWatchedEp != null) {
                    nextUpEp = partiallyWatchedEp
                } else {
                    val lastWatchedIndex = allEpisodesWithSeason.indexOfLast { ews ->
                        val history = seriesEpHistory[ews.episode.streamId]
                        updatedWatchedMap["episode_${ews.episode.streamId}"] == true ||
                                (history?.let { it.lastPosition.toFloat() / it.totalDuration.toFloat() >= 0.92f } ?: false)
                    }
                    
                    val nextIndex = if (lastWatchedIndex == -1) 0 else lastWatchedIndex + 1
                    if (nextIndex < allEpisodesWithSeason.size) {
                        nextUpEp = allEpisodesWithSeason[nextIndex]
                    }
                }

                if (nextUpEp != null) {
                    val ext = nextUpEp.episode.container_extension ?: "mp4"
                    val playUrl = "${baseUrl}series/$username/$password/${nextUpEp.episode.streamId}.$ext"
                    nextUpList.add(
                        ResolvedItem(
                            id = nextUpEp.episode.streamId,
                            title = "${info?.name ?: "Series"} - S${nextUpEp.seasonKey}E${nextUpEp.episode.episodeNum}: ${nextUpEp.episode.title ?: ""}",
                            poster = info?.cover,
                            url = playUrl,
                            type = "series",
                            seriesId = seriesId,
                            episodeNum = nextUpEp.episode.episodeNum
                        )
                    )
                }
            }
        }
        return nextUpList
    }
}
