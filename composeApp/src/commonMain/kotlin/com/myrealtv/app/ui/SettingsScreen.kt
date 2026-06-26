package com.myrealtv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myrealtv.app.data.Repository
import com.myrealtv.app.ui.components.TvButton
import com.myrealtv.app.ui.components.TvTextField
import com.myrealtv.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repository: Repository,
    onChangeProvider: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val pbClient = repository.pbClient
    val provider = repository.activeProvider

    // PocketBase states
    var pbUrl by remember { mutableStateOf("https://pb.myrealtv.app") }
    var pbEmail by remember { mutableStateOf("user@example.com") }
    var pbPassword by remember { mutableStateOf("password123") }
    var pbMessage by remember { mutableStateOf("") }
    var isPbLoading by remember { mutableStateOf(false) }

    // Recomposition helper
    var isAuthorized by remember { mutableStateOf(pbClient.isAuthorized() && !pbClient.isOfflineMode) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left Column: Active IPTV Provider Info
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Settings",
                style = TvTypography.Subtitle,
                color = AccentColorLight
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Active IPTV Provider",
                    style = TvTypography.Subtitle.copy(fontSize = 18.sp),
                    color = AccentColorLight
                )
                if (provider != null) {
                    Text(text = "Name: ${provider.name}", style = TvTypography.Body)
                    Text(text = "URL: ${provider.url}", style = TvTypography.Detail)
                    Text(text = "User: ${provider.username}", style = TvTypography.Detail)
                } else {
                    Text(text = "No active provider linked.", style = TvTypography.Body)
                }

                Spacer(modifier = Modifier.height(16.dp))
                TvButton(
                    text = "Switch IPTV Provider",
                    onClick = onChangeProvider
                )
            }
        }

        // Right Column: Optional Profile Cloud Sync
        Column(
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Profile Cloud Sync (Optional)",
                style = TvTypography.Subtitle,
                color = AccentColorLight
            )

            if (isAuthorized) {
                // Connected sync state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceColor)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync Status: CONNECTED",
                        style = TvTypography.Body.copy(fontWeight = FontWeight.Bold),
                        color = AccentColorLight
                    )
                    Text(
                        text = "Your bookmarks, favorites, and VOD resume playhead states are synchronizing automatically to your server profile.",
                        style = TvTypography.Detail
                    )
                    Text(
                        text = "Profile User ID: ${pbClient.userId}",
                        style = TvTypography.Detail
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    TvButton(
                        text = "Disconnect Sync Profile",
                        onClick = {
                            pbClient.logout()
                            isAuthorized = false
                            coroutineScope.launch {
                                // Re-sync locally to clear memory structures linked to cloud
                                repository.syncFavorites()
                                repository.syncWatchHistory()
                            }
                        },
                        isAlert = true
                    )
                }
            } else {
                // Disconnected/local sync state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceColor)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync Status: OFFLINE (Local Mode)",
                        style = TvTypography.Body.copy(fontWeight = FontWeight.Bold),
                        color = TextColorSecondary
                    )
                    Text(
                        text = "Connect a self-hosted PocketBase account to synchronize your viewing data across devices.",
                        style = TvTypography.Detail
                    )

                    TvTextField(
                        value = pbUrl,
                        onValueChange = { pbUrl = it },
                        label = "PocketBase Server URL"
                    )
                    TvTextField(
                        value = pbEmail,
                        onValueChange = { pbEmail = it },
                        label = "Email Address"
                    )
                    TvTextField(
                        value = pbPassword,
                        onValueChange = { pbPassword = it },
                        label = "Password",
                        isPassword = true
                    )

                    if (pbMessage.isNotEmpty()) {
                        Text(
                            text = pbMessage,
                            color = AlertColor,
                            style = TvTypography.Detail
                        )
                    }

                    if (isPbLoading) {
                        CircularProgressIndicator(color = AccentColorLight)
                    } else {
                        TvButton(
                            text = "Connect & Enable Cloud Sync",
                            onClick = {
                                coroutineScope.launch {
                                    isPbLoading = true
                                    pbMessage = ""
                                    val success = pbClient.authenticate(pbUrl, pbEmail, pbPassword)
                                    isPbLoading = false
                                    if (success) {
                                        isAuthorized = true
                                        pbMessage = "Connected successfully!"
                                        // Synchronize existing lists
                                        repository.syncFavorites()
                                        repository.syncWatchHistory()
                                    } else {
                                        pbMessage = "Authentication failed. Verify entries."
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
