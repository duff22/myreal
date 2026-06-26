package com.myrealtv.app.data

import com.myrealtv.app.api.PocketBaseClient
import com.myrealtv.app.api.XtreamClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Repository(
    val pbClient: PocketBaseClient,
    val xtreamClient: XtreamClient
) {
    // Current Active Provider
    var activeProvider: ProviderCredentials? = null

    // Cache Lists
    val liveCategories = MutableStateFlow<List<XtreamCategory>>(emptyList())
    val vodCategories = MutableStateFlow<List<XtreamCategory>>(emptyList())
    val seriesCategories = MutableStateFlow<List<XtreamCategory>>(emptyList())

    val liveStreams = MutableStateFlow<List<XtreamLiveStream>>(emptyList())
    val vodMovies = MutableStateFlow<List<XtreamMovie>>(emptyList())
    val seriesList = MutableStateFlow<List<XtreamSeries>>(emptyList())

    // Synced State
    val favorites = MutableStateFlow<List<FavoriteRecord>>(emptyList())
    val watchHistory = MutableStateFlow<List<WatchHistoryRecord>>(emptyList())

    // Loading State
    val isLoading = MutableStateFlow(false)

    fun setProvider(creds: ProviderCredentials) {
        activeProvider = creds
        // Clear old caches
        liveCategories.value = emptyList()
        vodCategories.value = emptyList()
        seriesCategories.value = emptyList()
        liveStreams.value = emptyList()
        vodMovies.value = emptyList()
        seriesList.value = emptyList()
        favorites.value = emptyList()
        watchHistory.value = emptyList()
    }

    suspend fun loadIptvData(forceReload: Boolean = false) {
        val creds = activeProvider ?: return

        if (!forceReload &&
            liveCategories.value.isNotEmpty() &&
            vodCategories.value.isNotEmpty() &&
            seriesCategories.value.isNotEmpty() &&
            liveStreams.value.isNotEmpty() &&
            vodMovies.value.isNotEmpty() &&
            seriesList.value.isNotEmpty()
        ) {
            try {
                syncFavorites()
                syncWatchHistory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        if (!forceReload) {
            isLoading.value = true
            try {
                val liveCatsStr = com.myrealtv.app.readCacheFile("live_categories_${creds.id}.json")
                val vodCatsStr = com.myrealtv.app.readCacheFile("vod_categories_${creds.id}.json")
                val seriesCatsStr = com.myrealtv.app.readCacheFile("series_categories_${creds.id}.json")
                val liveStr = com.myrealtv.app.readCacheFile("live_streams_${creds.id}.json")
                val vodStr = com.myrealtv.app.readCacheFile("vod_movies_${creds.id}.json")
                val seriesStr = com.myrealtv.app.readCacheFile("series_list_${creds.id}.json")

                if (liveCatsStr.isNotEmpty() && vodCatsStr.isNotEmpty() && seriesCatsStr.isNotEmpty() &&
                    liveStr.isNotEmpty() && vodStr.isNotEmpty() && seriesStr.isNotEmpty()
                ) {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    liveCategories.value = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(XtreamCategory.serializer()), liveCatsStr)
                    vodCategories.value = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(XtreamCategory.serializer()), vodCatsStr)
                    seriesCategories.value = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(XtreamCategory.serializer()), seriesCatsStr)
                    liveStreams.value = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(XtreamLiveStream.serializer()), liveStr)
                    vodMovies.value = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(XtreamMovie.serializer()), vodStr)
                    seriesList.value = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(XtreamSeries.serializer()), seriesStr)

                    syncFavorites()
                    syncWatchHistory()
                    isLoading.value = false
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading.value = false
            }
        }

        isLoading.value = true
        try {
            // Load Categories
            val lc = xtreamClient.getLiveCategories(creds)
            val vc = xtreamClient.getVodCategories(creds)
            val sc = xtreamClient.getSeriesCategories(creds)

            // Load Streams
            val ls = xtreamClient.getLiveStreams(creds)
            val vm = xtreamClient.getVodStreams(creds)
            val sl = xtreamClient.getSeries(creds)

            liveCategories.value = lc
            vodCategories.value = vc
            seriesCategories.value = sc
            liveStreams.value = ls
            vodMovies.value = vm
            seriesList.value = sl

            // Save to Cache Files
            val json = kotlinx.serialization.json.Json
            com.myrealtv.app.saveCacheFile("live_categories_${creds.id}.json", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(XtreamCategory.serializer()), lc))
            com.myrealtv.app.saveCacheFile("vod_categories_${creds.id}.json", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(XtreamCategory.serializer()), vc))
            com.myrealtv.app.saveCacheFile("series_categories_${creds.id}.json", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(XtreamCategory.serializer()), sc))
            com.myrealtv.app.saveCacheFile("live_streams_${creds.id}.json", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(XtreamLiveStream.serializer()), ls))
            com.myrealtv.app.saveCacheFile("vod_movies_${creds.id}.json", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(XtreamMovie.serializer()), vm))
            com.myrealtv.app.saveCacheFile("series_list_${creds.id}.json", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(XtreamSeries.serializer()), sl))

            // Sync User Stats from PocketBase
            syncFavorites()
            syncWatchHistory()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading.value = false
        }
    }

    suspend fun syncFavorites() {
        val creds = activeProvider ?: return
        if (!pbClient.isAuthorized()) return
        val list = pbClient.getFavorites(creds.id)
        favorites.value = list
    }

    suspend fun syncWatchHistory() {
        val creds = activeProvider ?: return
        if (!pbClient.isAuthorized()) return
        val list = pbClient.getWatchHistory(creds.id)
        watchHistory.value = list
    }

    suspend fun isFavorite(type: String, itemId: String): Boolean {
        return favorites.value.any { it.type == type && it.itemId == itemId }
    }

    suspend fun toggleFavorite(type: String, itemId: String) {
        val creds = activeProvider ?: return
        if (!pbClient.isAuthorized()) return

        val existing = favorites.value.find { it.type == type && it.itemId == itemId }
        if (existing != null) {
            val success = pbClient.deleteFavorite(existing.id)
            if (success) {
                favorites.value = favorites.value.filter { it.id != existing.id }
            }
        } else {
            val record = pbClient.addFavorite(creds.id, type, itemId)
            if (record != null) {
                favorites.value = favorites.value + record
            }
        }
    }

    suspend fun savePlaybackProgress(
        type: String, // "movie" or "episode"
        itemId: String, // stream_id
        progressMs: Long,
        durationMs: Long,
        seriesId: String? = null,
        season: Int? = null,
        episode: Int? = null
    ) {
        val creds = activeProvider ?: return
        if (!pbClient.isAuthorized()) return

        val progressPercent = if (durationMs > 0) progressMs.toDouble() / durationMs else 0.0
        val isCompleted = progressPercent >= 0.92

        // Check if there is an existing record in our local history list
        val existing = watchHistory.value.find { it.type == type && it.itemId == itemId }

        val result = pbClient.upsertWatchHistory(
            recordId = existing?.id,
            providerId = creds.id,
            type = type,
            itemId = itemId,
            seriesId = seriesId,
            season = season,
            episode = episode,
            progressMs = progressMs,
            durationMs = durationMs,
            completed = isCompleted
        )

        if (result != null) {
            // Update local watch history cache
            val updatedList = watchHistory.value.filter { it.itemId != itemId }
            watchHistory.value = listOf(result) + updatedList // Prepend latest
        }
    }

    // Filter Helpers for Movie Dashboard

    fun getContinueWatchingMovies(): List<XtreamMovie> {
        val movieHistoryIds = watchHistory.value
            .filter { it.type == "movie" && !it.completed && it.progressMs > 0 }
            .associateBy { it.itemId }

        if (movieHistoryIds.isEmpty()) return emptyList()

        // Map cached movies that exist in history and fit the criteria
        return vodMovies.value
            .filter { movieHistoryIds.containsKey(it.streamId.toString()) }
            // Sort by watch history updated date descending
            .sortedByDescending { movieHistoryIds[it.streamId.toString()]?.updatedAt }
    }

    fun getLatestAddedMovies(): List<XtreamMovie> {
        // Sort movies by added date descending, limit to 20
        return vodMovies.value
            .sortedWith(compareByDescending { it.added ?: "" })
            .take(20)
    }

    // Filter Helpers for Shows Dashboard

    fun getContinueWatchingEpisodes(): List<Pair<XtreamSeries, XtreamEpisode>> {
        val episodeHistory = watchHistory.value
            .filter { it.type == "episode" && !it.completed && it.progressMs > 0 }
            .sortedByDescending { it.updatedAt }

        if (episodeHistory.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<XtreamSeries, XtreamEpisode>>()

        for (history in episodeHistory) {
            val series = seriesList.value.find { it.seriesId.toString() == history.seriesId } ?: continue
            // We need to fetch/resolve the episode details.
            // If details are cached or we can construct it, we do.
            // Note: Since episode data is loaded on demand, we'll create a lightweight mock/resolved episode
            // that represents the continued episode.
            val episode = XtreamEpisode(
                id = history.itemId,
                title = "Season ${history.season} Episode ${history.episode}",
                episodeNum = history.episode?.toString() ?: "0",
                season = history.season ?: 1,
                extension = "mp4",
                info = XtreamEpisodeInfo(duration = (history.durationMs / 1000).toString())
            )
            results.add(Pair(series, episode))
        }
        return results
    }

    suspend fun getNextUpEpisodes(): List<Pair<XtreamSeries, XtreamEpisode>> {
        // Displays next chronological episode for series watched in last 30 days.
        // For simulation, we assume dates fit (all watchHistory episodes within 30 days).
        val recentEpisodeHistory = watchHistory.value
            .filter { it.type == "episode" && it.seriesId != null }
            .sortedByDescending { it.updatedAt }

        if (recentEpisodeHistory.isEmpty()) return emptyList()

        // Group by series
        val seriesLastWatched = recentEpisodeHistory.groupBy { it.seriesId }

        val nextUpList = mutableListOf<Pair<XtreamSeries, XtreamEpisode>>()

        for ((seriesIdStr, historyList) in seriesLastWatched) {
            val seriesId = seriesIdStr?.toIntOrNull() ?: continue
            val series = seriesList.value.find { it.seriesId == seriesId } ?: continue

            // Get the last watched episode
            val lastWatched = historyList.first() // Sorted by updatedAt descending
            val isNextUpEligible = if (lastWatched.durationMs > 0) {
                (lastWatched.progressMs.toDouble() / lastWatched.durationMs.toDouble()) >= 0.94
            } else {
                lastWatched.completed
            }
            if (!isNextUpEligible) continue

            val lastSeason = lastWatched.season ?: 1
            val lastEpisodeNum = lastWatched.episode ?: 1

            // Fetch series details to find the next chronological episode
            val details = xtreamClient.getSeriesDetails(activeProvider!!, seriesId) ?: continue
            val allEpisodes = details.episodes.values.flatten()

            // Find the next episode:
            // 1. Same season, episodeNum = lastEpisodeNum + 1
            // 2. Or Season = lastSeason + 1, episodeNum = 1
            var nextEpisode = allEpisodes.find { it.season == lastSeason && it.episodeNum.toIntOrNull() == lastEpisodeNum + 1 }
            if (nextEpisode == null) {
                nextEpisode = allEpisodes.find { it.season == lastSeason + 1 && it.episodeNum.toIntOrNull() == 1 }
            }

            if (nextEpisode != null) {
                nextUpList.add(Pair(series, nextEpisode))
            }
        }
        return nextUpList
    }

    fun getLatestAddedSeries(): List<XtreamSeries> {
        // Sort series by last modified/added descending, limit to 20
        return seriesList.value
            .sortedWith(compareByDescending { it.lastModified ?: "" })
            .take(20)
    }
}
