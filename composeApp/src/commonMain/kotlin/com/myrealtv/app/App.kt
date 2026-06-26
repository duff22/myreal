package com.myrealtv.app

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.myrealtv.app.api.PocketBaseClient
import com.myrealtv.app.api.XtreamClient
import com.myrealtv.app.data.Repository
import com.myrealtv.app.ui.DashboardScreen
import com.myrealtv.app.ui.DashboardTab
import com.myrealtv.app.ui.LoginScreen
import com.myrealtv.app.ui.PlayerScreen
import com.myrealtv.app.ui.theme.MyRealTvTheme
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

enum class AppScreen {
    Login,
    Dashboard,
    Player
}

data class PlaybackArgs(
    val type: String,
    val streamId: String,
    val seriesId: String?,
    val season: Int?,
    val episode: Int?
)

@Composable
fun App(onExitApp: () -> Unit = {}) {
    // 1. Initialize HTTP Client with Json Serialization
    val httpClient = remember {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    isLenient = true
                })
            }
        }
    }

    // 2. Initialize Core API Clients and Repository
    val pbClient = remember { PocketBaseClient(httpClient) }
    val xtreamClient = remember { XtreamClient(httpClient) }
    val repository = remember { Repository(pbClient, xtreamClient) }

    // 3. Screen Routing State
    var currentScreen by remember { mutableStateOf(AppScreen.Login) }
    var playbackArgs by remember { mutableStateOf<PlaybackArgs?>(null) }
    var selectedTab by remember { mutableStateOf(DashboardTab.LiveTv) }
    var lastWatchedLiveStreamId by remember {
        mutableStateOf(
            getLocalString("last_watched_live_stream_id", "").toIntOrNull()
        )
    }

    var isAutoLoggingIn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val lastActiveJson = getLocalString("last_active_provider", "")
        if (lastActiveJson.isNotEmpty()) {
            try {
                val creds = kotlinx.serialization.json.Json.decodeFromString<com.myrealtv.app.data.ProviderCredentials>(lastActiveJson)
                repository.setProvider(creds)
                val loginRes = repository.xtreamClient.login(creds)
                if (loginRes?.userInfo != null) {
                    currentScreen = AppScreen.Dashboard
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isAutoLoggingIn = false
    }

    MyRealTvTheme {
        if (isAutoLoggingIn) {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .background(com.myrealtv.app.ui.theme.BackgroundColor),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = com.myrealtv.app.ui.theme.AccentColorLight,
                        modifier = androidx.compose.ui.Modifier.size(64.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(
                        modifier = androidx.compose.ui.Modifier.height(16.dp)
                    )
                    androidx.compose.material3.Text(
                        text = "Connecting to streaming provider...",
                        style = com.myrealtv.app.ui.theme.TvTypography.Subtitle,
                        color = com.myrealtv.app.ui.theme.TextColorPrimary
                    )
                }
            }
        } else {
            when (currentScreen) {
                AppScreen.Login -> {
                    LoginScreen(
                        repository = repository,
                        onLoginSuccess = {
                            currentScreen = AppScreen.Dashboard
                        }
                    )
                }
                AppScreen.Dashboard -> {
                    DashboardScreen(
                        repository = repository,
                        selectedTab = selectedTab,
                        onSelectedTabChange = { selectedTab = it },
                        lastWatchedLiveStreamId = lastWatchedLiveStreamId,
                        onClearLastWatchedLiveStreamId = { lastWatchedLiveStreamId = null },
                        onNavigateToPlayer = { type, streamId, seriesId, season, episode ->
                            playbackArgs = PlaybackArgs(type, streamId, seriesId, season, episode)
                            if (type == "live" || type == "catchup") {
                                val id = streamId.toIntOrNull()
                                lastWatchedLiveStreamId = id
                                if (id != null) {
                                    saveLocalString("last_watched_live_stream_id", id.toString())
                                }
                            }
                            currentScreen = AppScreen.Player
                        },
                        onLogout = {
                            pbClient.logout()
                            currentScreen = AppScreen.Login
                        },
                        onExitApp = onExitApp
                    )
                }
                AppScreen.Player -> {
                    val args = playbackArgs
                    if (args != null) {
                        PlayerScreen(
                            type = args.type,
                            streamId = args.streamId,
                            seriesId = args.seriesId,
                            season = args.season,
                            episode = args.episode,
                            repository = repository,
                            onBack = {
                                currentScreen = AppScreen.Dashboard
                                playbackArgs = null
                            }
                        )
                    } else {
                        currentScreen = AppScreen.Dashboard
                    }
                }
            }
        }
    }
}

