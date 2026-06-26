package com.myrealtv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.myrealtv.app.data.Repository
import com.myrealtv.app.data.XtreamCategory
import com.myrealtv.app.data.XtreamEpisode
import com.myrealtv.app.data.XtreamSeries
import com.myrealtv.app.data.XtreamSeriesDetails
import com.myrealtv.app.ui.components.BackHandler
import com.myrealtv.app.ui.components.ChannelLogo
import com.myrealtv.app.ui.components.TvButton
import com.myrealtv.app.ui.components.TvFocusableCard
import com.myrealtv.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ShowsDashboard(
    repository: Repository,
    onPlayEpisode: (episodeId: String, seriesId: String, season: Int, episode: Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    // States for data sources
    var nextUpEpisodes by remember { mutableStateOf<List<Pair<XtreamSeries, XtreamEpisode>>>(emptyList()) }
    val continueWatching = remember(repository.watchHistory.collectAsState().value) {
        repository.getContinueWatchingEpisodes()
    }
    val latestSeries = remember(repository.seriesList.collectAsState().value) {
        repository.getLatestAddedSeries()
    }

    val categories = repository.seriesCategories.collectAsState().value
    val allSeries = repository.seriesList.collectAsState().value

    // Modal/Dialog selector state
    var selectedSeriesForDetails by remember { mutableStateOf<XtreamSeries?>(null) }
    var seriesDetails by remember { mutableStateOf<XtreamSeriesDetails?>(null) }
    var seriesEpisodesMap by remember { mutableStateOf<Map<String, List<XtreamEpisode>>>(emptyMap()) }
    var selectedSeason by remember { mutableStateOf<String?>(null) }
    var isDetailsLoading by remember { mutableStateOf(false) }

    var activeGridViewCategory by remember { mutableStateOf<XtreamCategory?>(null) }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    // Load Next Up asynchronously
    LaunchedEffect(repository.watchHistory.collectAsState().value) {
        nextUpEpisodes = repository.getNextUpEpisodes()
    }

    // Watch history map for progress overlays
    val historyMap = repository.watchHistory.collectAsState().value
        .filter { it.type == "episode" }
        .associateBy { it.itemId }

    BackHandler(enabled = selectedSeriesForDetails != null || activeGridViewCategory != null || showFavoritesOnly) {
        if (selectedSeriesForDetails != null) {
            selectedSeriesForDetails = null
            seriesDetails = null
            seriesEpisodesMap = emptyMap()
            selectedSeason = null
        } else if (showFavoritesOnly) {
            showFavoritesOnly = false
        } else {
            activeGridViewCategory = null
        }
    }

    if (selectedSeriesForDetails != null) {
        val series = selectedSeriesForDetails!!
        SeriesDetailsPage(
            series = series,
            seriesDetails = seriesDetails,
            selectedSeason = selectedSeason,
            onSeasonSelect = { selectedSeason = it },
            isDetailsLoading = isDetailsLoading,
            historyMap = historyMap,
            favoritesList = repository.favorites.collectAsState().value,
            onToggleFavorite = {
                coroutineScope.launch {
                    repository.toggleFavorite("series", series.seriesId.toString())
                }
            },
            onPlayEpisode = { episode ->
                onPlayEpisode(
                    episode.id,
                    series.seriesId.toString(),
                    episode.season,
                    episode.episodeNum.toIntOrNull() ?: 1
                )
            },
            onBack = {
                selectedSeriesForDetails = null
                seriesDetails = null
                seriesEpisodesMap = emptyMap()
                selectedSeason = null
            }
        )
    } else if (showFavoritesOnly) {
        val favoritesList = repository.favorites.collectAsState().value
        val favoriteSeries = remember(allSeries, favoritesList) {
            val favIds = favoritesList.filter { it.type == "series" }.map { it.itemId }.toSet()
            allSeries.filter { favIds.contains(it.seriesId.toString()) }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favorite TV Shows",
                    style = TvTypography.Title
                )
                TvButton(
                    text = "< Back to Dashboard",
                    onClick = { showFavoritesOnly = false }
                )
            }

            if (favoriteSeries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No favorite TV shows added yet", style = TvTypography.Subtitle, color = TextColorSecondary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    gridItems(favoriteSeries) { series ->
                        SeriesCard(
                            series = series,
                            onClick = {
                                selectedSeriesForDetails = series
                                isDetailsLoading = true
                                coroutineScope.launch {
                                    val details = repository.xtreamClient.getSeriesDetails(repository.activeProvider!!, series.seriesId)
                                    seriesDetails = details
                                    seriesEpisodesMap = details?.episodes ?: emptyMap()
                                    selectedSeason = seriesEpisodesMap.keys.sorted().firstOrNull()
                                    isDetailsLoading = false
                                }
                            }
                        )
                    }
                }
            }
        }
    } else if (activeGridViewCategory != null) {
        val category = activeGridViewCategory!!
        val gridSeries = remember(allSeries, category) {
            allSeries.filter { it.categoryId == category.id }
                .sortedBy { it.name.lowercase() }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TV Shows - ${category.name}",
                    style = TvTypography.Title
                )
                TvButton(
                    text = "< Back to Categories",
                    onClick = { activeGridViewCategory = null }
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                gridItems(gridSeries) { series ->
                    SeriesCard(
                        series = series,
                        onClick = {
                            selectedSeriesForDetails = series
                            isDetailsLoading = true
                            coroutineScope.launch {
                                val details = repository.xtreamClient.getSeriesDetails(repository.activeProvider!!, series.seriesId)
                                seriesDetails = details
                                seriesEpisodesMap = details?.episodes ?: emptyMap()
                                selectedSeason = seriesEpisodesMap.keys.sorted().firstOrNull()
                                isDetailsLoading = false
                            }
                        }
                    )
                }
            }
        }
    } else {
        val categoriesWithSeries = remember(categories, allSeries) {
            categories.filter { cat ->
                allSeries.any { it.categoryId == cat.id }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TV Shows Dashboard",
                        style = TvTypography.Title
                    )
                    TvFocusableCard(
                        onClick = { showFavoritesOnly = true },
                        modifier = Modifier.height(36.dp)
                    ) { isFocused ->
                        Text(
                            text = "★ Favorites",
                            color = if (isFocused) TextColorPrimary else AccentColorLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }

            // Row 1: Next Up
            if (nextUpEpisodes.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Next Up",
                            style = TvTypography.Subtitle,
                            color = AccentColorLight
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            items(nextUpEpisodes) { (series, episode) ->
                                EpisodeCard(
                                    title = series.name,
                                    subtext = "S${episode.season} E${episode.episodeNum}: ${episode.title}",
                                    cover = series.cover,
                                    progress = null,
                                    onClick = {
                                        onPlayEpisode(episode.id, series.seriesId.toString(), episode.season, episode.episodeNum.toIntOrNull() ?: 1)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Row 2: Continue Watching
            if (continueWatching.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Continue Watching Episodes",
                            style = TvTypography.Subtitle,
                            color = AccentColorLight
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            items(continueWatching) { (series, episode) ->
                                val history = historyMap[episode.id]
                                val progress = if (history != null && history.durationMs > 0) {
                                    history.progressMs.toFloat() / history.durationMs.toFloat()
                                } else 0f

                                EpisodeCard(
                                    title = series.name,
                                    subtext = "S${episode.season} E${episode.episodeNum}: ${episode.title}",
                                    cover = series.cover,
                                    progress = progress,
                                    onClick = {
                                        onPlayEpisode(episode.id, series.seriesId.toString(), episode.season, episode.episodeNum.toIntOrNull() ?: 1)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Row 3: Latest Added Media
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Latest Added Media",
                        style = TvTypography.Subtitle,
                        color = AccentColorLight
                    )
                    if (latestSeries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No series available", style = TvTypography.Detail)
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            items(latestSeries) { series ->
                                SeriesCard(
                                    series = series,
                                    onClick = {
                                        selectedSeriesForDetails = series
                                        isDetailsLoading = true
                                        coroutineScope.launch {
                                            val details = repository.xtreamClient.getSeriesDetails(repository.activeProvider!!, series.seriesId)
                                            seriesDetails = details
                                            seriesEpisodesMap = details?.episodes ?: emptyMap()
                                            selectedSeason = seriesEpisodesMap.keys.sorted().firstOrNull()
                                            isDetailsLoading = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Row 4+: Categories Rows
            items(categoriesWithSeries) { category ->
                val categorySeries = remember(allSeries, category) {
                    allSeries.filter { it.categoryId == category.id }
                        .sortedWith(compareByDescending { it.lastModified ?: "" })
                        .take(15)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = category.name,
                            style = TvTypography.Subtitle,
                            color = AccentColorLight
                        )
                        TvFocusableCard(
                            onClick = { activeGridViewCategory = category },
                            modifier = Modifier.height(36.dp)
                        ) { isFocused ->
                            Text(
                                text = "View All >",
                                color = if (isFocused) TextColorPrimary else AccentColorLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        items(categorySeries) { series ->
                            SeriesCard(
                                series = series,
                                onClick = {
                                    selectedSeriesForDetails = series
                                    isDetailsLoading = true
                                    coroutineScope.launch {
                                        val details = repository.xtreamClient.getSeriesDetails(repository.activeProvider!!, series.seriesId)
                                        seriesDetails = details
                                        seriesEpisodesMap = details?.episodes ?: emptyMap()
                                        selectedSeason = seriesEpisodesMap.keys.sorted().firstOrNull()
                                        isDetailsLoading = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedSeriesForDetails != null) {
        // Safe check block (rendered by SeriesDetailsPage fullscreen overlay now)
    }
}

@Composable
fun SeriesDetailsPage(
    series: XtreamSeries,
    seriesDetails: XtreamSeriesDetails?,
    selectedSeason: String?,
    onSeasonSelect: (String) -> Unit,
    isDetailsLoading: Boolean,
    historyMap: Map<String, com.myrealtv.app.data.WatchHistoryRecord>,
    favoritesList: List<com.myrealtv.app.data.FavoriteRecord>,
    onToggleFavorite: () -> Unit,
    onPlayEpisode: (XtreamEpisode) -> Unit,
    onBack: () -> Unit
) {
    val seasonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(series.seriesId, isDetailsLoading) {
        if (!isDetailsLoading) {
            try {
                seasonFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Section: Info Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(3.5f),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Cover Art
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceColorHover)
                ) {
                    val coverUrl = seriesDetails?.info?.cover ?: series.cover
                    ChannelLogo(
                        url = coverUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Metadata Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isFav = remember(favoritesList, series.seriesId) {
                        favoritesList.any { it.type == "series" && it.itemId == series.seriesId.toString() }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = series.name,
                            style = TvTypography.Title.copy(fontSize = 28.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvFocusableCard(
                                onClick = onToggleFavorite,
                                modifier = Modifier.height(36.dp)
                            ) { isFocused ->
                                Text(
                                    text = if (isFav) "★ Remove Favorite" else "☆ Add Favorite",
                                    color = if (isFocused) TextColorPrimary else AccentColorLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            TvFocusableCard(
                                onClick = onBack,
                                modifier = Modifier.height(36.dp)
                            ) { isFocused ->
                                Text(
                                    text = "< Back",
                                    color = if (isFocused) TextColorPrimary else AccentColorLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }

                    if (isDetailsLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentColorLight)
                        }
                    } else {
                        val info = seriesDetails?.info
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val ratingStr = info?.rating?.ifBlank { null } ?: series.rating?.ifBlank { null }
                            val releaseDate = info?.releaseDate?.ifBlank { null }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (ratingStr != null && ratingStr != "0") {
                                    Text(
                                        text = "★ $ratingStr",
                                        color = Color.Yellow,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (releaseDate != null) {
                                    Text(
                                        text = "Released: $releaseDate",
                                        style = TvTypography.Detail,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (!info?.genre.isNullOrBlank()) {
                                Text(
                                    text = "Genre: ${info?.genre}",
                                    style = TvTypography.Body.copy(fontSize = 13.sp, color = TextColorSecondary)
                                )
                            }
                            if (!info?.director.isNullOrBlank()) {
                                Text(
                                    text = "Director: ${info?.director}",
                                    style = TvTypography.Body.copy(fontSize = 13.sp, color = TextColorSecondary)
                                )
                            }
                            if (!info?.cast.isNullOrBlank()) {
                                Text(
                                    text = "Cast: ${info?.cast}",
                                    style = TvTypography.Body.copy(fontSize = 13.sp, color = TextColorSecondary),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val plot = info?.plot ?: "No plot description available."
                            Text(
                                text = plot,
                                style = TvTypography.Body.copy(fontSize = 14.sp),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Bottom Section: Season Tabs & Episode Horizontal List
            if (!isDetailsLoading && seriesDetails != null) {
                val seasons = seriesDetails.episodes.keys.sorted().toList()
                val episodes = seriesDetails.episodes[selectedSeason] ?: emptyList()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(4.5f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Season Tabs Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Seasons: ",
                            style = TvTypography.Subtitle.copy(fontSize = 14.sp),
                            color = AccentColorLight,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(seasons) { seasonNum ->
                                val isSelected = selectedSeason == seasonNum
                                val focusRequesterModifier = if (seasonNum == seasons.firstOrNull()) {
                                    Modifier.focusRequester(seasonFocusRequester)
                                } else {
                                    Modifier
                                }

                                TvFocusableCard(
                                    onClick = { onSeasonSelect(seasonNum) },
                                    modifier = focusRequesterModifier
                                ) { isFocused ->
                                    Text(
                                        text = "Season $seasonNum",
                                        color = if (isSelected) AccentColorLight else if (isFocused) TextColorPrimary else TextColorSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Episodes Row
                    if (episodes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No episodes in this season.", style = TvTypography.Detail)
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            items(episodes) { episode ->
                                val progressObj = historyMap[episode.id]
                                val progress = if (progressObj != null && progressObj.durationMs > 0) {
                                    progressObj.progressMs.toFloat() / progressObj.durationMs.toFloat()
                                } else null

                                EpisodeDetailCard(
                                    episode = episode,
                                    seriesCover = series.cover,
                                    progress = progress,
                                    onClick = { onPlayEpisode(episode) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeDetailCard(
    episode: XtreamEpisode,
    seriesCover: String?,
    progress: Float?,
    onClick: () -> Unit
) {
    TvFocusableCard(
        onClick = onClick,
        modifier = Modifier.size(width = 240.dp, height = 180.dp)
    ) { isFocused ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Episode Screenshot
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceColorHover),
                contentAlignment = Alignment.Center
            ) {
                val imgUrl = episode.info?.image ?: seriesCover
                ChannelLogo(
                    url = imgUrl,
                    modifier = Modifier.fillMaxSize()
                )
                episode.info?.duration?.let { durStr ->
                    val mins = durStr.toIntOrNull()?.let { if (it > 500) it / 60 else it }
                    if (mins != null && mins > 0) {
                        Text(
                            text = "$mins min",
                            color = TextColorPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Episode ${episode.episodeNum}: ${episode.title}",
                    style = TvTypography.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (progress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        color = AccentColorLight,
                        trackColor = SurfaceColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesCard(
    series: XtreamSeries,
    onClick: () -> Unit
) {
    TvFocusableCard(
        onClick = onClick,
        modifier = Modifier.size(width = 160.dp, height = 220.dp)
    ) { isFocused ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceColorHover),
                contentAlignment = Alignment.Center
            ) {
                ChannelLogo(
                    url = series.cover,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    series.rating?.let {
                        if (it.isNotBlank() && it != "0") {
                            Text(
                                text = "★ $it",
                                color = Color.Yellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Text(
                text = series.name,
                style = TvTypography.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EpisodeCard(
    title: String,
    subtext: String,
    cover: String?,
    progress: Float?,
    onClick: () -> Unit
) {
    TvFocusableCard(
        onClick = onClick,
        modifier = Modifier.size(width = 160.dp, height = 220.dp)
    ) { isFocused ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceColorHover),
                contentAlignment = Alignment.Center
            ) {
                ChannelLogo(
                    url = cover,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = title,
                    style = TvTypography.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtext,
                    style = TvTypography.Detail.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (progress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        color = AccentColorLight,
                        trackColor = SurfaceColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}
