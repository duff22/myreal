package com.example.myrealtv.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import com.example.myrealtv.Player
import com.example.myrealtv.Profiles
import com.example.myrealtv.ContentDetails
import com.example.myrealtv.data.ServiceLocator
import com.example.myrealtv.data.model.ResolvedItem
import com.example.myrealtv.ui.theme.tvFocusHighlight

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.focusable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel() }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val tabs = listOf("Home", "Movies", "TV Shows", "Search")
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    var showDismissDialogItem by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    
    val context = LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateDownloadUrl by remember { mutableStateOf<String?>(null) }
    var latestVersionName by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val updateResult = com.example.myrealtv.updater.AppUpdater.checkForUpdate(context)
        if (updateResult.isUpdateAvailable && updateResult.downloadUrl != null) {
            updateDownloadUrl = updateResult.downloadUrl
            latestVersionName = updateResult.latestVersion
            showUpdateDialog = true
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            // Header: Logo & Tabs & User Profile Name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "MyRealTV",
                        color = Color(0xFF00D2FF),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "|  Watching: ${ServiceLocator.getActiveProfile() ?: ""}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        TabItem(
                            title = title,
                             isSelected = selectedTab == index,
                             onClick = { viewModel.selectTab(index) }
                        )
                    }
                    
                    TabItem(
                        title = "Switch Profile",
                        isSelected = false,
                        onClick = { onItemClick(Profiles) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            when (val uiState = state) {
                is MainScreenUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00D2FF))
                    }
                }
                is MainScreenUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = uiState.message, color = Color(0xFFEF4444), fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            val retryInteraction = remember { MutableInteractionSource() }
                            Button(
                                onClick = { viewModel.loadData() },
                                interactionSource = retryInteraction,
                                modifier = Modifier.tvFocusHighlight(retryInteraction, RoundedCornerShape(8.dp))
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is MainScreenUiState.Success -> {
                    val configRows = uiState.configRows
                    val movieRows = uiState.movieRows
                    val seriesRows = uiState.seriesRows
                    val continueWatching = uiState.continueWatching
                    val nextUp = uiState.nextUp
                    val watchedStates = uiState.watchedStates
                    
                    when (selectedTab) {
                        0 -> HomeScreen(
                            configRows = configRows,
                            continueWatching = continueWatching,
                            nextUp = nextUp,
                            watchedStates = watchedStates,
                            onPlayItem = { item ->
                                val targetItemId = if (item.type == "series") (item.seriesId ?: item.id) else item.id
                                val nextEpId = if (item.type == "series" && item.seriesId != null && item.seriesId != item.id) item.id else null
                                onItemClick(
                                    ContentDetails(
                                        itemId = targetItemId,
                                        type = item.type,
                                        nextEpisodeId = nextEpId
                                    )
                                )
                            },
                            onLongClickContinueWatching = { item ->
                                showDismissDialogItem = item
                            },
                            onToggleWatched = { item ->
                                viewModel.toggleWatchedState(item.id, item.type == "series")
                            }
                        )
                        1 -> HomeScreen(
                            configRows = movieRows,
                            continueWatching = emptyList(),
                            nextUp = emptyList(),
                            watchedStates = watchedStates,
                            onPlayItem = { item ->
                                onItemClick(
                                    ContentDetails(
                                        itemId = item.id,
                                        type = "movie"
                                    )
                                )
                            },
                            onLongClickContinueWatching = {},
                            onToggleWatched = { item ->
                                viewModel.toggleWatchedState(item.id, false)
                            }
                        )
                        2 -> HomeScreen(
                            configRows = seriesRows,
                            continueWatching = emptyList(),
                            nextUp = emptyList(),
                            watchedStates = watchedStates,
                            onPlayItem = { item ->
                                onItemClick(
                                    ContentDetails(
                                        itemId = item.id,
                                        type = "series"
                                    )
                                )
                            },
                            onLongClickContinueWatching = {},
                            onToggleWatched = { item ->
                                viewModel.toggleWatchedState(item.id, true)
                            }
                        )
                        3 -> SearchScreen(
                            movieRows = movieRows,
                            seriesRows = seriesRows,
                            watchedStates = watchedStates,
                            searchQuery = viewModel.searchQuery.collectAsStateWithLifecycle().value,
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            onItemClick = { item ->
                                val targetItemId = if (item.type == "series") (item.seriesId ?: item.id) else item.id
                                val nextEpId = if (item.type == "series" && item.seriesId != null && item.seriesId != item.id) item.id else null
                                onItemClick(
                                    ContentDetails(
                                        itemId = targetItemId,
                                        type = item.type,
                                        nextEpisodeId = nextEpId
                                    )
                                )
                            },
                            onToggleWatched = { item ->
                                viewModel.toggleWatchedState(item.id, item.type == "series")
                            }
                        )
                    }
                }
            }
        }
        
        showDismissDialogItem?.let { continueItem ->
            AlertDialog(
                onDismissRequest = { showDismissDialogItem = null },
                confirmButton = {
                    val confirmInteraction = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = {
                            viewModel.dismissContinueWatching(continueItem.item.id)
                            showDismissDialogItem = null
                        },
                        interactionSource = confirmInteraction,
                        modifier = Modifier.tvFocusHighlight(confirmInteraction, RoundedCornerShape(8.dp))
                    ) {
                        Text("Remove", color = Color(0xFFEF4444))
                    }
                },
                dismissButton = {
                    val cancelInteraction = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { showDismissDialogItem = null },
                        interactionSource = cancelInteraction,
                        modifier = Modifier.tvFocusHighlight(cancelInteraction, RoundedCornerShape(8.dp))
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                },
                title = { Text("Remove from Continue Watching?", color = Color.White) },
                text = { Text("Are you sure you want to dismiss ${continueItem.item.title}?", color = Color.White.copy(alpha = 0.7f)) },
                containerColor = Color(0xFF1E293B)
            )
        }

        if (showUpdateDialog && updateDownloadUrl != null) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = { Text("Update Available", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "A new version of MyRealTV ($latestVersionName) is available. Would you like to update now?",
                        color = Color.LightGray
                    )
                },
                confirmButton = {
                    val confirmInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            showUpdateDialog = false
                            com.example.myrealtv.updater.AppUpdater.startDownload(context, updateDownloadUrl!!)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF)),
                        interactionSource = confirmInteraction,
                        modifier = Modifier
                            .tvFocusHighlight(confirmInteraction, RoundedCornerShape(8.dp))
                            .focusable(interactionSource = confirmInteraction)
                    ) {
                        Text("Update Now", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    val dismissInteraction = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { showUpdateDialog = false },
                        interactionSource = dismissInteraction,
                        modifier = Modifier
                            .tvFocusHighlight(dismissInteraction, RoundedCornerShape(8.dp))
                            .focusable(interactionSource = dismissInteraction)
                    ) {
                        Text("Later", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}

@Composable
fun TabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .tvFocusHighlight(interactionSource, RoundedCornerShape(8.dp))
            .background(
                color = if (isSelected) Color(0xFF00D2FF).copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HomeScreen(
    configRows: List<ResolvedRow>,
    continueWatching: List<ContinueWatchingItem>,
    nextUp: List<ResolvedItem>,
    watchedStates: Map<String, Boolean>,
    onPlayItem: (ResolvedItem) -> Unit,
    onLongClickContinueWatching: (ContinueWatchingItem) -> Unit,
    onToggleWatched: (ResolvedItem) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (continueWatching.isNotEmpty()) {
            item {
                Text(
                    text = "Continue Watching",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(continueWatching) { continueItem ->
                        ContinueWatchingCard(
                            continueItem = continueItem,
                            onPlay = { onPlayItem(continueItem.item) },
                            onLongClick = { onLongClickContinueWatching(continueItem) }
                        )
                    }
                }
            }
        }
        
        if (nextUp.isNotEmpty()) {
            item {
                Text(
                    text = "Next Up (New Episodes)",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(nextUp) { item ->
                        val isWatched = if (item.type == "series") {
                            watchedStates["series_${item.id}"] == true
                        } else {
                            watchedStates["movie_${item.id}"] == true
                        }
                        NextUpCard(
                            item = item,
                            isWatched = isWatched,
                            onClick = { onPlayItem(item) },
                            onLongClick = { onToggleWatched(item) }
                        )
                    }
                }
            }
        }
        
        items(configRows) { row ->
            Text(
                text = row.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(row.items) { item ->
                    val isWatched = if (item.type == "series") {
                        watchedStates["series_${item.id}"] == true
                    } else {
                        watchedStates["movie_${item.id}"] == true
                    }
                    MovieCard(
                        item = item,
                        isWatched = isWatched,
                        onClick = { onPlayItem(item) },
                        onLongClick = { onToggleWatched(item) }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    continueItem: ContinueWatchingItem,
    onPlay: () -> Unit,
    onLongClick: () -> Unit
) {
    val progress = continueItem.history.lastPosition.toFloat() / continueItem.history.totalDuration
    val interactionSource = remember { MutableInteractionSource() }
    
    Column(
        modifier = Modifier
            .width(180.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay,
                onLongClick = onLongClick
            )
            .tvFocusHighlight(interactionSource, RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
        ) {
            AsyncImage(
                model = continueItem.item.poster,
                contentDescription = continueItem.item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.BottomCenter),
                color = Color(0xFF00D2FF),
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
        
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = continueItem.item.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "${(progress * 100).toInt()}% watched",
                color = Color(0xFF00D2FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieCard(
    item: ResolvedItem,
    isWatched: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(140.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .tvFocusHighlight(interactionSource, RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            if (isWatched) {
                WatchedIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            minLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun WatchedIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 20.dp
) {
    val fontSize = if (size < 18.dp) 9.sp else 12.sp
    Box(
        modifier = modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
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
fun SearchScreen(
    movieRows: List<ResolvedRow>,
    seriesRows: List<ResolvedRow>,
    watchedStates: Map<String, Boolean>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onItemClick: (ResolvedItem) -> Unit,
    onToggleWatched: (ResolvedItem) -> Unit
) {
    val matchedMovies = remember(searchQuery, movieRows) {
        if (searchQuery.isBlank()) emptyList() else {
            movieRows.flatMap { it.items }
                .distinctBy { it.id }
                .filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }
    
    val matchedSeries = remember(searchQuery, seriesRows) {
        if (searchQuery.isBlank()) emptyList() else {
            seriesRows.flatMap { it.items }
                .distinctBy { it.id }
                .filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val searchInteraction = remember { MutableInteractionSource() }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search Movies & TV Shows...", color = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0x33FFFFFF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color(0xFF00D2FF),
                unfocusedLabelColor = Color.Gray
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .tvFocusHighlight(searchInteraction, RoundedCornerShape(8.dp))
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (searchQuery.isBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Search by typing above",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                }
            } else if (matchedMovies.isEmpty() && matchedSeries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No results found for \"$searchQuery\"",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                if (matchedMovies.isNotEmpty()) {
                    item {
                        Text(
                            text = "Movies",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(matchedMovies) { item ->
                                val isWatched = if (item.type == "series") {
                                    watchedStates["series_${item.id}"] == true
                                } else {
                                    watchedStates["movie_${item.id}"] == true
                                }
                                MovieCard(
                                    item = item,
                                    isWatched = isWatched,
                                    onClick = { onItemClick(item) },
                                    onLongClick = { onToggleWatched(item) }
                                )
                            }
                        }
                    }
                }
                
                if (matchedSeries.isNotEmpty()) {
                    item {
                        Text(
                            text = "TV Shows",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(matchedSeries) { item ->
                                val isWatched = if (item.type == "series") {
                                    watchedStates["series_${item.id}"] == true
                                } else {
                                    watchedStates["movie_${item.id}"] == true
                                }
                                MovieCard(
                                    item = item,
                                    isWatched = isWatched,
                                    onClick = { onItemClick(item) },
                                    onLongClick = { onToggleWatched(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun parseNextUpTitle(fullTitle: String): Pair<String, String> {
    val regex = Regex("(.+?)\\s*-\\s*(S\\d+E\\d+.*)")
    val match = regex.find(fullTitle)
    if (match != null) {
        return Pair(match.groupValues[1].trim(), match.groupValues[2].trim())
    }
    return Pair(fullTitle, "")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NextUpCard(
    item: ResolvedItem,
    isWatched: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    val parsed = remember(item.title) { parseNextUpTitle(item.title) }
    val showTitle = parsed.first
    val sxxexx = remember(item.title) {
        val regex = Regex("S\\d+E\\d+", RegexOption.IGNORE_CASE)
        regex.find(item.title)?.value?.uppercase() ?: ""
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .tvFocusHighlight(interactionSource, RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            if (isWatched) {
                WatchedIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Top row - Show Title (1 line, clean)
        Text(
            text = showTitle,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        // Bottom row - SxxExx
        Text(
            text = sxxexx,
            color = Color(0xFF00D2FF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
