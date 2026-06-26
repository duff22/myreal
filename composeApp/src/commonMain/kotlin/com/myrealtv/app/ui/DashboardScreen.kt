package com.myrealtv.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myrealtv.app.data.Repository
import com.myrealtv.app.ui.components.TvSidebarTab
import com.myrealtv.app.ui.components.BackHandler
import com.myrealtv.app.ui.components.TvButton
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.myrealtv.app.ui.theme.*

enum class DashboardTab {
    LiveTv,
    Movies,
    Shows,
    Settings
}

enum class FocusArea {
    Sidebar,
    Categories,
    Channels,
    EpgPrograms,
    Content
}

@Composable
fun DashboardScreen(
    repository: Repository,
    selectedTab: DashboardTab,
    onSelectedTabChange: (DashboardTab) -> Unit,
    lastWatchedLiveStreamId: Int?,
    onClearLastWatchedLiveStreamId: () -> Unit,
    onNavigateToPlayer: (type: String, streamId: String, seriesId: String?, season: Int?, episode: Int?) -> Unit,
    onLogout: () -> Unit,
    onExitApp: () -> Unit
) {
    val isLoading by repository.isLoading.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }

    // Focus state management
    var focusArea by remember {
        mutableStateOf(
            if (selectedTab == DashboardTab.LiveTv) FocusArea.Channels else FocusArea.Content
        )
    }

    BackHandler(enabled = true) {
        if (focusArea != FocusArea.Sidebar) {
            focusArea = FocusArea.Sidebar
        } else {
            showExitDialog = true
        }
    }

    // Focus requesters
    val sidebarFocusRequester = remember { FocusRequester() }
    val categoriesFocusRequester = remember { FocusRequester() }
    val channelsFocusRequester = remember { FocusRequester() }
    val epgFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    // Load IPTV details on entry
    LaunchedEffect(repository.activeProvider) {
        if (repository.activeProvider != null) {
            repository.loadIptvData()
        }
    }

    // Handle focus transitions with a robust retry loop to prevent focus loss during recompositions
    LaunchedEffect(focusArea) {
        for (i in 1..10) {
            try {
                when (focusArea) {
                    FocusArea.Sidebar -> sidebarFocusRequester.requestFocus()
                    FocusArea.Categories -> categoriesFocusRequester.requestFocus()
                    FocusArea.Channels -> {
                        if (lastWatchedLiveStreamId == null) {
                            channelsFocusRequester.requestFocus()
                        }
                    }
                    FocusArea.EpgPrograms -> epgFocusRequester.requestFocus()
                    FocusArea.Content -> contentFocusRequester.requestFocus()
                }
                break
            } catch (e: Exception) {
                delay(50)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentColorLight, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading channels, playlists, and synced history...",
                        style = TvTypography.Subtitle,
                        color = TextColorPrimary
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundColor)
                    .focusProperties { canFocus = !showExitDialog }
            ) {
                // Left Sidebar - slides away unless focusArea == Sidebar
                AnimatedVisibility(
                    visible = (focusArea == FocusArea.Sidebar),
                    enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .background(SurfaceColor)
                            .padding(vertical = 24.dp, horizontal = 12.dp)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight) {
                                    focusArea = if (selectedTab == DashboardTab.LiveTv) FocusArea.Categories else FocusArea.Content
                                    true
                                } else {
                                    false
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MyRealTV",
                            style = TvTypography.Title.copy(fontSize = 24.sp),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                        )

                        TvSidebarTab(
                            text = "Live TV EPG",
                            isSelected = selectedTab == DashboardTab.LiveTv,
                            onSelect = {
                                onSelectedTabChange(DashboardTab.LiveTv)
                                focusArea = FocusArea.Categories
                            },
                            modifier = Modifier.focusRequester(sidebarFocusRequester)
                        )

                        TvSidebarTab(
                            text = "Movies",
                            isSelected = selectedTab == DashboardTab.Movies,
                            onSelect = {
                                onSelectedTabChange(DashboardTab.Movies)
                                focusArea = FocusArea.Content
                            }
                        )

                        TvSidebarTab(
                            text = "TV Shows",
                            isSelected = selectedTab == DashboardTab.Shows,
                            onSelect = {
                                onSelectedTabChange(DashboardTab.Shows)
                                focusArea = FocusArea.Content
                            }
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        TvSidebarTab(
                            text = "Settings",
                            isSelected = selectedTab == DashboardTab.Settings,
                            onSelect = {
                                onSelectedTabChange(DashboardTab.Settings)
                                focusArea = FocusArea.Content
                            }
                        )
                    }
                }

                // Right Main Content Panel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    when (selectedTab) {
                        DashboardTab.LiveTv -> {
                            LiveEpgScreen(
                                repository = repository,
                                focusArea = focusArea,
                                onFocusAreaChange = { focusArea = it },
                                categoriesFocusRequester = categoriesFocusRequester,
                                channelsFocusRequester = channelsFocusRequester,
                                epgFocusRequester = epgFocusRequester,
                                lastWatchedLiveStreamId = lastWatchedLiveStreamId,
                                onClearLastWatchedLiveStreamId = onClearLastWatchedLiveStreamId,
                                onPlayLive = { streamId ->
                                    onNavigateToPlayer("live", streamId.toString(), null, null, null)
                                },
                                onPlayCatchup = { streamId, startEpgTime, durationMinutes ->
                                    onNavigateToPlayer(
                                        "catchup",
                                        streamId.toString(),
                                        startEpgTime,
                                        durationMinutes,
                                        null
                                    )
                                }
                            )
                        }
                        DashboardTab.Movies -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(contentFocusRequester)
                            ) {
                                MoviesDashboard(
                                    repository = repository,
                                    onPlayMovie = { streamId ->
                                        onNavigateToPlayer("movie", streamId.toString(), null, null, null)
                                    }
                                )
                            }
                        }
                        DashboardTab.Shows -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(contentFocusRequester)
                            ) {
                                ShowsDashboard(
                                    repository = repository,
                                    onPlayEpisode = { episodeId, seriesId, season, episode ->
                                        onNavigateToPlayer("episode", episodeId, seriesId, season, episode)
                                    }
                                )
                            }
                        }
                        DashboardTab.Settings -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(contentFocusRequester)
                            ) {
                                SettingsScreen(
                                    repository = repository,
                                    onChangeProvider = onLogout
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showExitDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceColor)
                        .border(2.dp, AccentColorLight, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Exit MyRealTV",
                        style = TvTypography.Title.copy(fontSize = 20.sp),
                        color = TextColorPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "Are you sure you want to exit the application?",
                        style = TvTypography.Body.copy(fontSize = 14.sp),
                        color = TextColorSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val cancelFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            try {
                                cancelFocusRequester.requestFocus()
                            } catch(e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        TvButton(
                            text = "Cancel",
                            onClick = { showExitDialog = false },
                            modifier = Modifier.weight(1f).focusRequester(cancelFocusRequester)
                        )
                        TvButton(
                            text = "Exit",
                            onClick = { onExitApp() },
                            modifier = Modifier.weight(1f),
                            isAlert = true
                        )
                    }
                }
            }
        }
    }
}
