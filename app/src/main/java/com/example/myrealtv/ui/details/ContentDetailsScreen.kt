package com.example.myrealtv.ui.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.myrealtv.data.remote.XcEpisode
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.myrealtv.Player
import com.example.myrealtv.data.ServiceLocator
import com.example.myrealtv.ui.theme.tvFocusHighlight

// Data structure to hold active resume dialog metadata
private data class ResumeDialogData(
    val title: String,
    val streamId: String,
    val playUrl: String,
    val lastPosition: Int,
    val totalDuration: Int,
    val isSeries: Boolean,
    val seriesId: String? = null,
    val episodeNum: Int? = null
)

@Composable
fun WatchedIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 20.dp
) {
    val fontSize = if (size < 18.dp) 9.sp else 12.sp
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF00D2FF)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✓",
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ContentDetailsScreen(
    itemId: String,
    contentType: String,
    nextEpisodeId: String? = null,
    onItemClick: (androidx.navigation3.runtime.NavKey) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: ContentDetailsViewModel = viewModel(
        key = "details_${contentType}_$itemId",
        initializer = {
            ContentDetailsViewModel(itemId, contentType)
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showResumeDialogItem by remember { mutableStateOf<ResumeDialogData?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        when (val uiState = state) {
            is ContentDetailsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFEF4444))
                }
            }
            is ContentDetailsUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.message,
                            color = Color(0xFFEF4444),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        val interactionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = { viewModel.loadDetails() },
                            interactionSource = interactionSource,
                            modifier = Modifier.tvFocusHighlight(interactionSource, RoundedCornerShape(8.dp))
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            is ContentDetailsUiState.MovieSuccess -> {
                MovieSuccessContent(
                    uiState = uiState,
                    onPlayClick = {
                        val hasHistory = uiState.watchHistory != null && uiState.watchHistory.lastPosition > 0
                        if (hasHistory) {
                            showResumeDialogItem = ResumeDialogData(
                                title = uiState.title,
                                streamId = uiState.streamId,
                                playUrl = uiState.playUrl,
                                lastPosition = uiState.watchHistory!!.lastPosition,
                                totalDuration = uiState.watchHistory.totalDuration,
                                isSeries = false
                            )
                        } else {
                            onItemClick(
                                Player(
                                    streamId = uiState.streamId,
                                    streamUrl = uiState.playUrl,
                                    title = uiState.title,
                                    isSeries = false
                                )
                            )
                        }
                    },
                    onClearHistory = { viewModel.clearWatchHistory(uiState.streamId) },
                    onToggleWatched = { viewModel.toggleMovieWatched(uiState.streamId) },
                    onBack = onBack
                )
            }
            is ContentDetailsUiState.SeriesSuccess -> {
                SeriesSuccessContent(
                    uiState = uiState,
                    nextEpisodeId = nextEpisodeId,
                    onEpisodeClick = { episode, url ->
                        val epHistory = uiState.episodeHistory[episode.streamId]
                        val hasHistory = epHistory != null && epHistory.lastPosition > 0
                        if (hasHistory) {
                            showResumeDialogItem = ResumeDialogData(
                                title = "${uiState.title} - ${episode.title ?: "Episode ${episode.episodeNum}"}",
                                streamId = episode.streamId,
                                playUrl = url,
                                lastPosition = epHistory!!.lastPosition,
                                totalDuration = epHistory.totalDuration,
                                isSeries = true,
                                seriesId = uiState.seriesId,
                                episodeNum = episode.episodeNum
                            )
                        } else {
                            onItemClick(
                                Player(
                                    streamId = episode.streamId,
                                    streamUrl = url,
                                    title = "${uiState.title} - ${episode.title ?: "Episode ${episode.episodeNum}"}",
                                    isSeries = true,
                                    seriesId = uiState.seriesId,
                                    episodeNum = episode.episodeNum
                                )
                            )
                        }
                    },
                    onToggleSeriesWatched = { viewModel.toggleSeriesWatched(uiState.seriesId) },
                    onToggleSeasonWatched = { episodes -> viewModel.toggleSeasonWatched(episodes) },
                    onToggleEpisodeWatched = { streamId, episodeNum -> viewModel.toggleEpisodeWatched(streamId, episodeNum) },
                    onBack = onBack
                )
            }
        }

        // Render Play / Resume Dialog popup if needed
        if (showResumeDialogItem != null) {
            val dialogData = showResumeDialogItem!!
            val progressStr = formatPosition(dialogData.lastPosition)
            val durationStr = formatPosition(dialogData.totalDuration)

            AlertDialog(
                onDismissRequest = { showResumeDialogItem = null },
                title = { Text("Resume Playback?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Would you like to resume from $progressStr / $durationStr or start from the beginning?",
                        color = Color.LightGray
                    )
                },
                confirmButton = {
                    val resumeInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            showResumeDialogItem = null
                            onItemClick(
                                Player(
                                    streamId = dialogData.streamId,
                                    streamUrl = dialogData.playUrl,
                                    title = dialogData.title,
                                    isSeries = dialogData.isSeries,
                                    seriesId = dialogData.seriesId,
                                    episodeNum = dialogData.episodeNum
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        interactionSource = resumeInteraction,
                        modifier = Modifier.tvFocusHighlight(resumeInteraction, RoundedCornerShape(8.dp))
                    ) {
                        Text("Resume")
                    }
                },
                dismissButton = {
                    val startInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            showResumeDialogItem = null
                            viewModel.clearWatchHistory(dialogData.streamId)
                            onItemClick(
                                Player(
                                    streamId = dialogData.streamId,
                                    streamUrl = dialogData.playUrl,
                                    title = dialogData.title,
                                    isSeries = dialogData.isSeries,
                                    seriesId = dialogData.seriesId,
                                    episodeNum = dialogData.episodeNum
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        interactionSource = startInteraction,
                        modifier = Modifier.tvFocusHighlight(startInteraction, RoundedCornerShape(8.dp))
                    ) {
                        Text("Play from Start")
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

// Utility to clean provider redundancy from episode titles
private fun cleanEpisodeTitle(rawTitle: String?, episodeNum: Int): String {
    val title = rawTitle ?: ""
    if (title.isBlank()) return "Episode $episodeNum"
    
    val regex = Regex("(?i)\\b(s\\d+e\\d+|ep\\d+|episode\\s*\\d+)\\s*-\\s*(.+)")
    val match = regex.find(title)
    if (match != null) {
        return "Episode $episodeNum: ${match.groupValues[2].trim()}"
    }
    
    val parts = title.split(" - ")
    if (parts.size >= 2) {
        val lastPart = parts.last().trim()
        if (!lastPart.matches(Regex("(?i)s\\d+e\\d+"))) {
            return "Episode $episodeNum: $lastPart"
        }
    }
    
    return "Episode $episodeNum: $title"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieSuccessContent(
    uiState: ContentDetailsUiState.MovieSuccess,
    onPlayClick: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleWatched: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = uiState.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.25f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF0F0F0F)),
                        startY = 0f,
                        endY = 1000f
                    )
                )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Focusable Poster to toggle movie watched state
                val posterInteraction = remember { MutableInteractionSource() }
                val isMovieWatched = uiState.watchedStates["movie_${uiState.streamId}"] == true || 
                        (uiState.watchHistory?.let { it.lastPosition.toFloat() / it.totalDuration.toFloat() >= 0.92f } ?: false)

                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(300.dp)
                        .combinedClickable(
                            interactionSource = posterInteraction,
                            indication = null,
                            onClick = {},
                            onLongClick = onToggleWatched
                        )
                        .tvFocusHighlight(posterInteraction, RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = uiState.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                    )
                    
                    if (isMovieWatched) {
                        WatchedIndicator(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            size = 24.dp
                        )
                    }
                }

                // Meta Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Favorites and Title Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.title,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        
                        val favInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = { /* Favorites functionality placeholder */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                            interactionSource = favInteraction,
                            modifier = Modifier.tvFocusHighlight(favInteraction, RoundedCornerShape(8.dp))
                        ) {
                            Text("❤️ Add to Favorites", color = Color.White)
                        }
                    }

                    // Badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!uiState.rating.isNullOrBlank()) {
                            Text(
                                text = "⭐ ${uiState.rating}",
                                color = Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        if (!uiState.year.isNullOrBlank()) {
                            Text(text = uiState.year, color = Color.LightGray, fontSize = 16.sp)
                        }
                        if (!uiState.genre.isNullOrBlank()) {
                            Text(text = uiState.genre, color = Color.LightGray, fontSize = 16.sp)
                        }
                    }

                    // Description
                    if (!uiState.description.isNullOrBlank()) {
                        Text(
                            text = uiState.description,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Cast / Director
                    if (!uiState.director.isNullOrBlank()) {
                        Text(
                            text = "Director: ${uiState.director}",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    }
                    if (!uiState.cast.isNullOrBlank()) {
                        Text(
                            text = "Cast: ${uiState.cast}",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Buttons Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasHistory = uiState.watchHistory != null && uiState.watchHistory.lastPosition > 0
                        
                        // Play/Resume Button
                        val playInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = onPlayClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            interactionSource = playInteraction,
                            modifier = Modifier.tvFocusHighlight(playInteraction, RoundedCornerShape(8.dp))
                        ) {
                            if (hasHistory) {
                                val progress = formatPosition(uiState.watchHistory!!.lastPosition)
                                val duration = formatPosition(uiState.watchHistory.totalDuration)
                                Text("Resume ($progress / $duration)")
                            } else {
                                Text("Play")
                            }
                        }

                        // Clear History
                        if (uiState.watchHistory != null) {
                            val clearInteraction = remember { MutableInteractionSource() }
                            Button(
                                onClick = onClearHistory,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                interactionSource = clearInteraction,
                                modifier = Modifier.tvFocusHighlight(clearInteraction, RoundedCornerShape(8.dp))
                            ) {
                                Text("Clear Watch History", color = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesSuccessContent(
    uiState: ContentDetailsUiState.SeriesSuccess,
    nextEpisodeId: String? = null,
    onEpisodeClick: (XcEpisode, String) -> Unit,
    onToggleSeriesWatched: () -> Unit,
    onToggleSeasonWatched: (List<XcEpisode>) -> Unit,
    onToggleEpisodeWatched: (String, Int) -> Unit,
    onBack: () -> Unit
) {
    val targetSeasonKey = remember(uiState.seasons, uiState.episodes, nextEpisodeId) {
        if (!nextEpisodeId.isNullOrBlank()) {
            uiState.episodes.entries.find { (_, list) ->
                list.any { it.streamId == nextEpisodeId }
            }?.key
        } else null
    }

    var selectedSeasonKey by remember(uiState.seasons, targetSeasonKey) {
        mutableStateOf(targetSeasonKey ?: uiState.seasons.firstOrNull() ?: "")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = uiState.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.25f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF0F0F0F)),
                        startY = 0f,
                        endY = 1000f
                    )
                )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Series Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Focusable Poster to toggle series watched state
                val posterInteraction = remember { MutableInteractionSource() }
                val isSeriesWatched = uiState.watchedStates["series_${uiState.seriesId}"] == true

                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(300.dp)
                        .combinedClickable(
                            interactionSource = posterInteraction,
                            indication = null,
                            onClick = {},
                            onLongClick = onToggleSeriesWatched
                        )
                        .tvFocusHighlight(posterInteraction, RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = uiState.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                    )
                    
                    if (isSeriesWatched) {
                        WatchedIndicator(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            size = 24.dp
                        )
                    }
                }

                // Details Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.title,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        
                        val favInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = { /* Favorites functionality placeholder */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                            interactionSource = favInteraction,
                            modifier = Modifier.tvFocusHighlight(favInteraction, RoundedCornerShape(8.dp))
                        ) {
                            Text("❤️ Add to Favorites", color = Color.White)
                        }
                    }

                    // Badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!uiState.rating.isNullOrBlank()) {
                            Text(
                                text = "⭐ ${uiState.rating}",
                                color = Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        if (!uiState.year.isNullOrBlank()) {
                            Text(text = uiState.year, color = Color.LightGray, fontSize = 16.sp)
                        }
                        Text(
                            text = "${uiState.seasons.size} Seasons",
                            color = Color.LightGray,
                            fontSize = 16.sp
                        )
                        if (!uiState.genre.isNullOrBlank()) {
                            Text(text = uiState.genre, color = Color.LightGray, fontSize = 16.sp)
                        }
                    }

                    // Description
                    if (!uiState.description.isNullOrBlank()) {
                        Text(
                            text = uiState.description,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Cast / Director
                    if (!uiState.director.isNullOrBlank()) {
                        Text(
                            text = "Director: ${uiState.director}",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    }
                    if (!uiState.cast.isNullOrBlank()) {
                        Text(
                            text = "Cast: ${uiState.cast}",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 2. Horizontal Seasons selector
        if (uiState.seasons.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Seasons",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(uiState.seasons, key = { it }) { seasonKey ->
                            val isSelected = seasonKey == selectedSeasonKey
                            val interactionSource = remember { MutableInteractionSource() }

                            // Calculate if all episodes in this season are watched
                            val seasonEpisodes = uiState.episodes[seasonKey] ?: emptyList()
                            val isSeasonWatched = seasonEpisodes.isNotEmpty() && seasonEpisodes.all { ep ->
                                uiState.watchedStates["episode_${ep.streamId}"] == true || 
                                (uiState.episodeHistory[ep.streamId]?.let { it.lastPosition.toFloat() / it.totalDuration.toFloat() >= 0.92f } ?: false)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) Color(0xFFEF4444) else Color(0x33FFFFFF)
                                    )
                                    .combinedClickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = { selectedSeasonKey = seasonKey },
                                        onLongClick = { onToggleSeasonWatched(seasonEpisodes) }
                                    )
                                    .tvFocusHighlight(interactionSource, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Season $seasonKey",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSeasonWatched) {
                                        WatchedIndicator(size = 14.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Horizontal Episodes list
        val seasonEpisodes = uiState.episodes[selectedSeasonKey] ?: emptyList()
        if (seasonEpisodes.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Episodes",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(seasonEpisodes, key = { it.streamId }) { episode ->
                            val epHistory = uiState.episodeHistory[episode.streamId]
                            val progressPercent = if (epHistory != null && epHistory.totalDuration > 0) {
                                (epHistory.lastPosition.toFloat() / epHistory.totalDuration.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            val username = ServiceLocator.getHouseholdId() ?: ""
                            val password = ServiceLocator.getPassword()
                            val baseUrl = ServiceLocator.xtreamBaseUrl
                            val ext = episode.container_extension ?: "mp4"
                            val playUrl = "${baseUrl}series/$username/$password/${episode.streamId}.$ext"

                            val interactionSource = remember { MutableInteractionSource() }
                            val isEpWatched = uiState.watchedStates["episode_${episode.streamId}"] == true || progressPercent >= 0.92f

                            val focusRequester = remember { FocusRequester() }
                            val isTargetEp = episode.streamId == nextEpisodeId

                            LaunchedEffect(isTargetEp) {
                                if (isTargetEp) {
                                    focusRequester.requestFocus()
                                }
                            }

                            // Netflix-style Horizontal Episode Card (with combinedClickable for long press select to toggle watched)
                            Box(
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .width(220.dp)
                                    .height(165.dp)
                                    .combinedClickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = { onEpisodeClick(episode, playUrl) },
                                        onLongClick = { onToggleEpisodeWatched(episode.streamId, episode.episodeNum) }
                                    )
                                    .tvFocusHighlight(interactionSource, RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp)),
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Thumbnail with Progress bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(115.dp)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .background(Color.Black)
                                    ) {
                                        AsyncImage(
                                            model = episode.info?.movie_image ?: uiState.posterUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (isEpWatched) {
                                            WatchedIndicator(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                            )
                                        }

                                        // Red watched progress bar
                                        if (progressPercent > 0.05f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .background(Color.DarkGray)
                                                    .align(Alignment.BottomCenter)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(progressPercent)
                                                        .fillMaxHeight()
                                                        .background(Color(0xFFEF4444))
                                                )
                                            }
                                        }
                                    }

                                    // Episode Simplified Title
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = cleanEpisodeTitle(episode.title, episode.episodeNum),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Utility formats for duration/positions
private fun formatPosition(ms: Int): String {
    val seconds = ms / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}
