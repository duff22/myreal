package com.example.myrealtv

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.myrealtv.data.ServiceLocator
import com.example.myrealtv.ui.auth.LoginScreen
import com.example.myrealtv.ui.auth.ProfileScreen
import com.example.myrealtv.ui.main.MainScreen
import com.example.myrealtv.ui.player.PlayerScreen
import com.example.myrealtv.ui.details.ContentDetailsScreen

@Composable
fun MainNavigation() {
    val startKey = when {
        ServiceLocator.getHouseholdId() == null -> Login
        ServiceLocator.getActiveProfile() == null -> Profiles
        else -> MainTabs
    }
    val backStack = rememberNavBackStack(startKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Login> {
                LoginScreen(
                    onLoginSuccess = {
                        backStack.add(Profiles)
                    },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<Profiles> {
                ProfileScreen(
                    onProfileSelected = {
                        backStack.add(MainTabs)
                    },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<MainTabs> {
                MainScreen(
                    onItemClick = { navKey ->
                        backStack.add(navKey)
                    },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<Player> { playerKey ->
                PlayerScreen(
                    streamId = playerKey.streamId,
                    streamUrl = playerKey.streamUrl,
                    title = playerKey.title,
                    isSeries = playerKey.isSeries,
                    seriesId = playerKey.seriesId,
                    episodeNum = playerKey.episodeNum,
                    onBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<ContentDetails> { detailsKey ->
                ContentDetailsScreen(
                    itemId = detailsKey.itemId,
                    contentType = detailsKey.type,
                    nextEpisodeId = detailsKey.nextEpisodeId,
                    onItemClick = { navKey ->
                        backStack.add(navKey)
                    },
                    onBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
        }
    )
}
