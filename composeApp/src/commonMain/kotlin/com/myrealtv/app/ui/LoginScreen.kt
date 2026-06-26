package com.myrealtv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.myrealtv.app.data.ProviderCredentials
import com.myrealtv.app.data.Repository
import com.myrealtv.app.ui.components.TvButton
import com.myrealtv.app.ui.components.TvFocusableCard
import com.myrealtv.app.ui.components.TvTextField
import com.myrealtv.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    repository: Repository,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val pbClient = repository.pbClient

    // Xtream Codes Form States
    var xtreamName by remember { mutableStateOf("") }
    var xtreamUrl by remember { mutableStateOf("https://") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }
    var xtreamMessage by remember { mutableStateOf("") }
    var isXtreamLoading by remember { mutableStateOf(false) }

    // local providers list
    var linkedProviders by remember { mutableStateOf<List<ProviderCredentials>>(emptyList()) }

    // Load available providers on entry
    val loadProviders = {
        coroutineScope.launch {
            linkedProviders = pbClient.getProviderCredentials()
        }
    }

    LaunchedEffect(Unit) {
        loadProviders()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Left Panel: List of saved IPTV providers
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text(
                text = "MyRealTV",
                style = TvTypography.Title,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "IPTV Player Client",
                style = TvTypography.Subtitle,
                color = AccentColorLight,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
                    .padding(24.dp)
            ) {
                Text(
                    text = "Saved Provider Profiles",
                    style = TvTypography.Subtitle.copy(fontSize = 18.sp),
                    color = AccentColorLight,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (linkedProviders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved providers found.\nAdd one using the form on the right.",
                            style = TvTypography.Detail,
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(linkedProviders) { provider ->
                            TvFocusableCard(
                                onClick = {
                                    coroutineScope.launch {
                                        isXtreamLoading = true
                                        repository.setProvider(provider)
                                        val loginRes = repository.xtreamClient.login(provider)
                                        isXtreamLoading = false
                                        if (loginRes?.userInfo != null) {
                                            com.myrealtv.app.saveLocalString(
                                                "last_active_provider",
                                                kotlinx.serialization.json.Json.encodeToString(ProviderCredentials.serializer(), provider)
                                            )
                                            onLoginSuccess()
                                        } else {
                                            xtreamMessage = "Cannot connect to stream server: ${provider.name}"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { isFocused ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = provider.name,
                                            style = TvTypography.Body.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = provider.url,
                                            style = TvTypography.Detail
                                        )
                                    }
                                    Text(
                                        text = "Connect >",
                                        style = TvTypography.Button.copy(fontSize = 14.sp),
                                        color = if (isFocused) AccentColorLight else TextColorSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right Panel: Link New IPTV Provider
        Column(
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight()
        ) {
            Text(
                text = "Add IPTV Credentials",
                style = TvTypography.Title,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
                    .padding(24.dp)
            ) {
                Text(
                    text = "Link Xtream Codes IPTV Provider",
                    style = TvTypography.Subtitle.copy(fontSize = 18.sp),
                    color = AccentColorLight
                )

                TvTextField(
                    value = xtreamName,
                    onValueChange = { xtreamName = it },
                    label = "Provider Name (e.g. My Provider)"
                )
                TvTextField(
                    value = xtreamUrl,
                    onValueChange = { xtreamUrl = it },
                    label = "Server Stream URL (e.g. http://iptv.server:80)"
                )
                TvTextField(
                    value = xtreamUser,
                    onValueChange = { xtreamUser = it },
                    label = "Username"
                )
                TvTextField(
                    value = xtreamPass,
                    onValueChange = { xtreamPass = it },
                    label = "Password",
                    isPassword = true
                )

                if (xtreamMessage.isNotEmpty()) {
                    Text(
                        text = xtreamMessage,
                        color = if (xtreamMessage.startsWith("Success")) AccentColorLight else AlertColor,
                        style = TvTypography.Detail
                    )
                }

                if (isXtreamLoading) {
                    CircularProgressIndicator(color = AccentColorLight)
                } else {
                    TvButton(
                        text = "Verify & Save Provider",
                        onClick = {
                            coroutineScope.launch {
                                isXtreamLoading = true
                                xtreamMessage = ""
                                val tempCreds = ProviderCredentials(
                                    name = xtreamName,
                                    url = xtreamUrl,
                                    username = xtreamUser,
                                    password = xtreamPass
                                )
                                val loginRes = repository.xtreamClient.login(tempCreds)
                                if (loginRes?.userInfo != null) {
                                    // Save to local cache lists
                                    val saved = pbClient.addProviderCredentials(
                                        name = xtreamName,
                                        providerUrl = xtreamUrl,
                                        username = xtreamUser,
                                        password = xtreamPass
                                    )
                                    isXtreamLoading = false
                                    if (saved != null) {
                                        xtreamMessage = "Success! Provider profile saved."
                                        com.myrealtv.app.saveLocalString(
                                            "last_active_provider",
                                            kotlinx.serialization.json.Json.encodeToString(ProviderCredentials.serializer(), saved)
                                        )
                                        repository.setProvider(saved)
                                        onLoginSuccess()
                                    } else {
                                        xtreamMessage = "Failed to save provider locally."
                                    }
                                } else {
                                    isXtreamLoading = false
                                    xtreamMessage = "Cannot login to Xtream server. Verify parameters."
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
