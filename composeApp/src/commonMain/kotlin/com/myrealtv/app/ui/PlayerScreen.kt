package com.myrealtv.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myrealtv.app.data.Repository
import com.myrealtv.app.data.XtreamLiveStream
import com.myrealtv.app.data.EpgProgram
import com.myrealtv.app.ui.components.BackHandler
import com.myrealtv.app.ui.components.MediaPlayer
import com.myrealtv.app.ui.components.TvButton
import com.myrealtv.app.ui.components.ChannelLogo
import com.myrealtv.app.ui.components.TvFocusableCard
import com.myrealtv.app.ui.theme.*
import com.myrealtv.app.getLocalString
import com.myrealtv.app.saveLocalString
import com.myrealtv.app.getCurrentTimeMillis
import com.myrealtv.app.formatTimeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    type: String, // "live", "movie", "episode", "catchup"
    streamId: String,
    seriesId: String?, // startEpgTime if catchup
    season: Int?,      // durationMinutes if catchup
    episode: Int?,
    repository: Repository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val creds = repository.activeProvider ?: return

    val streams by repository.liveStreams.collectAsState()
    var activeStreamId by remember(streamId) { mutableStateOf(streamId) }

    // Resolve URL
    val playbackUrl = remember(activeStreamId) {
        when (type) {
            "live" -> {
                repository.xtreamClient.buildLiveStreamUrl(creds, activeStreamId.toInt())
            }
            "movie" -> {
                val movieObj = repository.vodMovies.value.find { it.streamId.toString() == activeStreamId }
                val ext = movieObj?.extension ?: "mp4"
                repository.xtreamClient.buildMovieUrl(creds, activeStreamId.toInt(), ext)
            }
            "episode" -> {
                repository.xtreamClient.buildEpisodeUrl(creds, activeStreamId, "mp4")
            }
            "catchup" -> {
                val startEpgTime = seriesId ?: ""
                val durationMinutes = season ?: 60
                repository.xtreamClient.buildTimeshiftUrl(creds, activeStreamId.toInt(), startEpgTime, durationMinutes)
            }
            else -> ""
        }
    }

    // Resolve Resume Position (if VOD)
    val initialPositionMs = remember(activeStreamId) {
        if (type == "movie" || type == "episode") {
            val history = repository.watchHistory.value.find { it.itemId == activeStreamId }
            if (history != null && !history.completed) {
                history.progressMs
            } else {
                0L
            }
        } else {
            0L
        }
    }

    // Local tracked playback position
    var currentPositionMs by remember { mutableStateOf(0L) }
    var currentDurationMs by remember { mutableStateOf(0L) }

    // Custom Overlay States
    val playerFocusRequester = remember { FocusRequester() }
    val guideFocusRequester = remember { FocusRequester() }
    var isGuideFocused by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(false) }
    var isOverlayInteractive by remember { mutableStateOf(false) }
    var showDescriptionOverlay by remember { mutableStateOf(false) }
    var rightKeyPressTime by remember { mutableStateOf(0L) }
    var rightKeyTriggered by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableStateOf(getCurrentTimeMillis()) }

    var resolutionStr by remember { mutableStateOf("") }
    var fpsStr by remember { mutableStateOf("") }
    var audioStr by remember { mutableStateOf("") }

    var currentLiveChannel by remember { mutableStateOf<XtreamLiveStream?>(null) }
    var currentLiveProgram by remember { mutableStateOf<EpgProgram?>(null) }

    val categoryId = currentLiveChannel?.categoryId
    val categoryChannels = remember(categoryId, streams) {
        if (categoryId != null) {
            streams.filter { it.categoryId == categoryId }
        } else {
            streams
        }
    }
    val recentChannelsState = remember { mutableStateListOf<Pair<XtreamLiveStream, EpgProgram?>>() }

    // Periodically update progress to PocketBase (every 10 seconds)
    if (type == "movie" || type == "episode") {
        LaunchedEffect(currentPositionMs) {
            delay(10000)
            if (currentPositionMs > 0 && currentDurationMs > 0) {
                coroutineScope.launch {
                    repository.savePlaybackProgress(
                        type = type,
                        itemId = activeStreamId,
                        progressMs = currentPositionMs,
                        durationMs = currentDurationMs,
                        seriesId = seriesId,
                        season = season,
                        episode = episode
                    )
                }
            }
        }
    }

    // Live EPG loader for current channel
    LaunchedEffect(activeStreamId, streams) {
        val channel = streams.find { it.streamId.toString() == activeStreamId }
        currentLiveChannel = channel
        
        if (channel != null && type == "live") {
            val now = getCurrentTimeMillis()
            val startMs = ((now / 1000) - ((now / 1000) % 1800)) * 1000L
            val endMs = startMs + 1800 * 1000L
            try {
                val epgList = repository.xtreamClient.getEpg(
                    repository.activeProvider!!,
                    channel.streamId,
                    startMs,
                    endMs
                )
                currentLiveProgram = epgList.find { now in it.getStartMs()..it.getEndMs() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Live EPG loader for recent channels
    LaunchedEffect(overlayVisible) {
        if (overlayVisible && type == "live") {
            recentChannelsState.clear()
            val raw = getLocalString("recent_live_channels", "")
            val ids = raw.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
            
            val streams = repository.liveStreams.value
            val now = getCurrentTimeMillis()
            val startMs = ((now / 1000) - ((now / 1000) % 1800)) * 1000L
            val endMs = startMs + 1800 * 1000L
            
            for (id in ids) {
                if (id.toString() == activeStreamId) continue
                val channel = streams.find { it.streamId == id } ?: continue
                recentChannelsState.add(Pair(channel, null))
                
                launch {
                    try {
                        val epgList = repository.xtreamClient.getEpg(
                            repository.activeProvider!!,
                            id,
                            startMs,
                            endMs
                        )
                        val currentProg = epgList.find { now in it.getStartMs()..it.getEndMs() }
                        val index = recentChannelsState.indexOfFirst { it.first.streamId == id }
                        if (index != -1) {
                            recentChannelsState[index] = Pair(channel, currentProg)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // Watch duration tracker for recent channels (5 seconds watch requirement)
    if (type == "live") {
        LaunchedEffect(activeStreamId) {
            delay(5000)
            try {
                val raw = getLocalString("recent_live_channels", "")
                val ids = raw.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }.toMutableList()
                ids.remove(activeStreamId.toInt())
                ids.add(0, activeStreamId.toInt())
                val limited = ids.take(8)
                saveLocalString("recent_live_channels", limited.joinToString(","))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 5-second inactivity auto-hide overlay
    LaunchedEffect(overlayVisible, lastInteractionTime) {
        if (overlayVisible) {
            delay(5000)
            overlayVisible = false
        }
    }

    // Restore focus to player when overlay is hidden
    LaunchedEffect(overlayVisible) {
        if (!overlayVisible) {
            isOverlayInteractive = false
            try {
                playerFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Auto-focus the player screen initially
    LaunchedEffect(Unit) {
        delay(300)
        try {
            playerFocusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Capture exit and save progress
    val handleExit = {
        coroutineScope.launch {
            if ((type == "movie" || type == "episode") && currentPositionMs > 0 && currentDurationMs > 0) {
                repository.savePlaybackProgress(
                    type = type,
                    itemId = activeStreamId,
                    progressMs = currentPositionMs,
                    durationMs = currentDurationMs,
                    seriesId = seriesId,
                    season = season,
                    episode = episode
                )
            }
            onBack()
        }
    }

    BackHandler {
        if (overlayVisible) {
            overlayVisible = false
            try {
                playerFocusRequester.requestFocus()
            } catch (e: Exception) {}
        } else {
            handleExit()
        }
    }

    val interactionModifier = Modifier.onPreviewKeyEvent { keyEvent ->
        lastInteractionTime = getCurrentTimeMillis()
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.DirectionCenter, Key.Enter -> {
                    if (type == "live") {
                        if (!overlayVisible) {
                            overlayVisible = true
                            isOverlayInteractive = true
                            showDescriptionOverlay = false
                            true
                        } else if (!isOverlayInteractive) {
                            isOverlayInteractive = true
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                Key.DirectionDown -> {
                    if (type == "live") {
                        if (!overlayVisible) {
                            if (categoryChannels.isNotEmpty()) {
                                val currentIndex = categoryChannels.indexOfFirst { it.streamId.toString() == activeStreamId }
                                if (currentIndex != -1) {
                                    val prevIndex = (currentIndex - 1 + categoryChannels.size) % categoryChannels.size
                                    activeStreamId = categoryChannels[prevIndex].streamId.toString()
                                    showDescriptionOverlay = false
                                }
                            }
                            overlayVisible = true
                            isOverlayInteractive = false
                            true
                        } else if (!isOverlayInteractive) {
                            if (categoryChannels.isNotEmpty()) {
                                val currentIndex = categoryChannels.indexOfFirst { it.streamId.toString() == activeStreamId }
                                if (currentIndex != -1) {
                                    val prevIndex = (currentIndex - 1 + categoryChannels.size) % categoryChannels.size
                                    activeStreamId = categoryChannels[prevIndex].streamId.toString()
                                    showDescriptionOverlay = false
                                }
                            }
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                Key.DirectionUp -> {
                    if (type == "live") {
                        if (!overlayVisible) {
                            if (categoryChannels.isNotEmpty()) {
                                val currentIndex = categoryChannels.indexOfFirst { it.streamId.toString() == activeStreamId }
                                if (currentIndex != -1) {
                                    val nextIndex = (currentIndex + 1) % categoryChannels.size
                                    activeStreamId = categoryChannels[nextIndex].streamId.toString()
                                    showDescriptionOverlay = false
                                }
                            }
                            overlayVisible = true
                            isOverlayInteractive = false
                            true
                        } else if (!isOverlayInteractive) {
                            if (categoryChannels.isNotEmpty()) {
                                val currentIndex = categoryChannels.indexOfFirst { it.streamId.toString() == activeStreamId }
                                if (currentIndex != -1) {
                                    val nextIndex = (currentIndex + 1) % categoryChannels.size
                                    activeStreamId = categoryChannels[nextIndex].streamId.toString()
                                    showDescriptionOverlay = false
                                }
                            }
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                Key.DirectionRight -> {
                    if (!overlayVisible && type == "live") {
                        if (rightKeyPressTime == 0L) {
                            rightKeyPressTime = getCurrentTimeMillis()
                            rightKeyTriggered = false
                        } else if (!rightKeyTriggered && (getCurrentTimeMillis() - rightKeyPressTime > 600)) {
                            rightKeyTriggered = true
                            showDescriptionOverlay = !showDescriptionOverlay
                        }
                        true
                    } else {
                        false
                    }
                }
                Key.Back -> {
                    if (showDescriptionOverlay) {
                        showDescriptionOverlay = false
                        true
                    } else if (overlayVisible) {
                        overlayVisible = false
                        try {
                            playerFocusRequester.requestFocus()
                        } catch (e: Exception) {}
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        } else if (keyEvent.type == KeyEventType.KeyUp) {
            when (keyEvent.key) {
                Key.DirectionRight -> {
                    if (!overlayVisible && type == "live") {
                        rightKeyPressTime = 0L
                        rightKeyTriggered = false
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        } else {
            false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable(enabled = true)
            .then(interactionModifier)
    ) {
        if (playbackUrl.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: Invalid Stream Configuration", color = Color.Red, style = TvTypography.Subtitle)
            }
        } else {
            MediaPlayer(
                type = type,
                url = playbackUrl,
                initialPositionMs = initialPositionMs,
                onProgressUpdate = { progress, duration ->
                    currentPositionMs = progress
                    currentDurationMs = duration
                },
                onFormatInfoUpdate = { res, fps, audio ->
                    resolutionStr = res
                    fpsStr = fps
                    audioStr = audio
                },
                onPlaybackEnded = {
                    coroutineScope.launch {
                        if (type == "movie" || type == "episode") {
                            repository.savePlaybackProgress(
                                type = type,
                                itemId = activeStreamId,
                                progressMs = currentDurationMs,
                                durationMs = currentDurationMs,
                                seriesId = seriesId,
                                season = season,
                                episode = episode
                            )
                        }
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // CUSTOM TRANSPARENT BOTTOM-THIRD OVERLAY FOR LIVE TV
        AnimatedVisibility(
            visible = overlayVisible && type == "live",
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.38f)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(BorderStroke(1.dp, Color(0xFF2A2A38).copy(alpha = 0.5f)))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // TOP ROW: Logo, Description, Progress, Format Badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelLogo(
                            url = currentLiveChannel?.icon,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.2f))
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = currentLiveProgram?.title ?: currentLiveChannel?.name ?: "Live Program",
                                style = TvTypography.Subtitle,
                                color = AccentColorLight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            val descText = currentLiveProgram?.description ?: "No program description available."
                            Text(
                                text = descText,
                                style = TvTypography.Body.copy(fontSize = 12.sp, color = TextColorSecondary),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            val now = getCurrentTimeMillis()
                            val startMs = currentLiveProgram?.getStartMs() ?: 0L
                            val endMs = currentLiveProgram?.getEndMs() ?: 0L
                            val minutesLeft = if (endMs > now) {
                                ((endMs - now) / 60000).coerceAtLeast(0)
                            } else {
                                0
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val progress = if (endMs > startMs) {
                                    ((now - startMs).toFloat() / (endMs - startMs).toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                LinearProgressIndicator(
                                    progress = progress,
                                    color = AccentColorLight,
                                    trackColor = Color(0xFF2A2A38),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "$minutesLeft min left",
                                    fontSize = 11.sp,
                                    color = TextColorSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Format Badge Pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (resolutionStr.isNotEmpty()) FormatPill(resolutionStr)
                            if (fpsStr.isNotEmpty()) FormatPill(fpsStr)
                            if (audioStr.isNotEmpty()) FormatPill(audioStr)
                        }
                    }

                    // BOTTOM ROW: Square Tiles (Guide, Favorites, Recent Channels)
                    val recentsSize = recentChannelsState.size

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tile 1: TV Guide shortcut
                        item {
                            TvFocusableCard(
                                onClick = {
                                    handleExit()
                                },
                                modifier = Modifier
                                    .size(100.dp)
                                    .focusRequester(guideFocusRequester)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionUp, Key.DirectionDown -> true
                                                Key.DirectionLeft -> true
                                                else -> false
                                            }
                                        } else false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                unfocusedColor = Color(0xFF2A2A38),
                                focusedColor = Color(0xFF3A3A4E)
                            ) { isFocused ->
                                LaunchedEffect(isFocused) {
                                    isGuideFocused = isFocused
                                    println("PlayerScreen: guide tile focus changed: $isFocused")
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "📺",
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        text = "TV Guide",
                                        fontSize = 12.sp,
                                        color = if (isFocused) AccentColorLight else TextColorPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Tile 2: Favorite Toggle
                        item {
                            val favoritesList by repository.favorites.collectAsState()
                            val favoriteStreamIds = remember(favoritesList) {
                                favoritesList.filter { it.type == "live" }.map { it.itemId }.toSet()
                            }
                            val isFavChannel = favoriteStreamIds.contains(activeStreamId)
                            val isFavLast = recentsSize == 0

                            TvFocusableCard(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.toggleFavorite("live", activeStreamId)
                                    }
                                },
                                modifier = Modifier
                                    .size(100.dp)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionUp, Key.DirectionDown -> true
                                                Key.DirectionRight -> if (isFavLast) true else false
                                                else -> false
                                            }
                                        } else false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                unfocusedColor = Color(0xFF2A2A38),
                                focusedColor = Color(0xFF3A3A4E)
                            ) { isFocused ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = if (isFavChannel) "★" else "☆",
                                        fontSize = 24.sp,
                                        color = if (isFavChannel) Color.Yellow else TextColorPrimary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        text = if (isFavChannel) "Fav (Added)" else "Favorite",
                                        fontSize = 12.sp,
                                        color = if (isFocused) AccentColorLight else TextColorPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Tiles 3+: Recent Channels
                        itemsIndexed(recentChannelsState) { index, pair ->
                            val recentChannel = pair.first
                            val recentProg = pair.second
                            val isRecentLast = index == recentsSize - 1

                            TvFocusableCard(
                                onClick = {
                                    activeStreamId = recentChannel.streamId.toString()
                                },
                                modifier = Modifier
                                    .size(100.dp)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionUp, Key.DirectionDown -> true
                                                Key.DirectionRight -> if (isRecentLast) true else false
                                                else -> false
                                            }
                                        } else false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                unfocusedColor = Color(0xFF2A2A38),
                                focusedColor = Color(0xFF3A3A4E)
                            ) { isFocused ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                ) {
                                    ChannelLogo(
                                        url = recentChannel.icon,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black.copy(alpha = 0.2f))
                                    )
                                    val description = recentProg?.title ?: recentChannel.name
                                    Text(
                                        text = description,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        color = if (isFocused) AccentColorLight else TextColorSecondary,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .fillMaxWidth()
                                            .then(if (isFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // TOP-RIGHT DESCRIPTION OVERLAY
        AnimatedVisibility(
            visible = showDescriptionOverlay && !overlayVisible && type == "live",
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .width(360.dp)
                .zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(BorderStroke(1.dp, Color(0xFF2A2A38).copy(alpha = 0.8f)))
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = currentLiveChannel?.name ?: "Live Channel",
                        style = TvTypography.Subtitle,
                        color = AccentColorLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentLiveProgram?.title ?: "No Program Information",
                        style = TvTypography.Title.copy(fontSize = 15.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentLiveProgram != null) {
                        val start = formatTimeLabel(currentLiveProgram!!.getStartMs())
                        val end = formatTimeLabel(currentLiveProgram!!.getEndMs())
                        Text(
                            text = "$start - $end",
                            style = TvTypography.Detail,
                            color = TextColorSecondary
                        )
                        Text(
                            text = currentLiveProgram!!.description ?: "No description available.",
                            style = TvTypography.Body.copy(fontSize = 12.sp),
                            color = TextColorSecondary,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "No detailed program description is currently available for this channel.",
                            style = TvTypography.Body.copy(fontSize = 12.sp),
                            color = TextColorSecondary
                        )
                    }
                }
            }
        }
    }

    // Auto-focus guide tile on overlay visibility
    LaunchedEffect(overlayVisible, isOverlayInteractive) {
        if (overlayVisible && isOverlayInteractive) {
            println("PlayerScreen: overlayVisible = true, starting focus retry loop")
            for (i in 1..15) {
                if (isGuideFocused) {
                    println("PlayerScreen: guide tile is focused, exiting retry loop")
                    break
                }
                delay(100)
                try {
                    println("PlayerScreen: requesting guide focus, attempt $i")
                    guideFocusRequester.requestFocus()
                } catch (e: Exception) {
                    println("PlayerScreen: requestFocus exception: ${e.message}")
                }
            }
        }
    }
}

@Composable
fun FormatPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A2A38))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = AccentColorLight,
            fontWeight = FontWeight.Bold
        )
    }
}
