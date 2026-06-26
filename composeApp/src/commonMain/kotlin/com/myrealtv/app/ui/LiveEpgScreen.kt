package com.myrealtv.app.ui
 
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myrealtv.app.data.EpgProgram
import com.myrealtv.app.data.Repository
import com.myrealtv.app.data.XtreamCategory
import com.myrealtv.app.data.XtreamLiveStream
import com.myrealtv.app.getCurrentTimeMillis
import com.myrealtv.app.formatTimeLabel
import com.myrealtv.app.ui.components.ChannelLogo
import com.myrealtv.app.ui.components.LivePreviewPlayer
import com.myrealtv.app.ui.components.TvCategoryTab
import com.myrealtv.app.ui.components.TvFocusableCard
import com.myrealtv.app.ui.components.TvSidebarTab
import com.myrealtv.app.ui.components.TvButton
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.myrealtv.app.data.XtreamMovie
import com.myrealtv.app.data.XtreamSeries
import com.myrealtv.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
 
@Composable
fun LiveEpgScreen(
    repository: Repository,
    focusArea: FocusArea,
    onFocusAreaChange: (FocusArea) -> Unit,
    categoriesFocusRequester: FocusRequester,
    channelsFocusRequester: FocusRequester,
    epgFocusRequester: FocusRequester, // Maps to first program card row
    lastWatchedLiveStreamId: Int?,
    onClearLastWatchedLiveStreamId: () -> Unit,
    onPlayLive: (Int) -> Unit,
    onPlayCatchup: (Int, String, Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    // UI Data States
    val categories by repository.liveCategories.collectAsState()
    val allStreams by repository.liveStreams.collectAsState()

    val favoritesList by repository.favorites.collectAsState()
    val favoriteStreamIds = remember(favoritesList) {
        favoritesList.filter { it.type == "live" }.map { it.itemId }.toSet()
    }
    val favoritesCategory = remember { XtreamCategory(id = "favorites", name = "Favorites") }
    val displayedCategories = remember(categories) {
        listOf(favoritesCategory) + categories
    }

    var isMenuOpen by remember { mutableStateOf(false) }
    var isVerticalNavigation by remember { mutableStateOf(false) }
    var lockedScrollOffset by remember { mutableStateOf<Int?>(null) }
    var isDpadCenterPressed by remember { mutableStateOf(false) }
    var menuChannel by remember { mutableStateOf<XtreamLiveStream?>(null) }
    var menuProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var menuMatches by remember { mutableStateOf<List<VodMatch>>(emptyList()) }
    var showMatchesDialog by remember { mutableStateOf(false) }
    var menuMessage by remember { mutableStateOf<String?>(null) }
    val menuFocusRequester = remember { FocusRequester() }
 
    var selectedCategory by remember { mutableStateOf<XtreamCategory?>(null) }
    var highlightedChannel by remember { mutableStateOf<XtreamLiveStream?>(null) }
    var currentProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var previewStreamId by remember { mutableStateOf<Int?>(null) }
 
    val verticalListState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val watchedStreamFocusRequester = remember { FocusRequester() }
    val channelFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
 
    // Lazy load EPG cache map
    val epgCache = remember { mutableMapOf<Int, List<EpgProgram>>() }
 
    // EPG grid constants
    val TIMELINE_SLOTS = 48
    val slotWidth = 105.dp
    val channelColWidth = 150.dp
    val gridWidth = slotWidth * TIMELINE_SLOTS
    val totalWidth = channelColWidth + gridWidth
 
    val timelineStartMs = remember {
        val now = getCurrentTimeMillis()
        ((now / 1000) - ((now / 1000) % 1800)) * 1000L
    }

    var selectedTimeOffsetMs by remember(timelineStartMs) {
        mutableStateOf(getCurrentTimeMillis() - timelineStartMs)
    }
    val programFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
 
    val timelineEndMs = timelineStartMs + TIMELINE_SLOTS * 1800 * 1000L
 
    val timeHeaderIntervals = remember(timelineStartMs) {
        val intervals = mutableListOf<String>()
        val startSecs = timelineStartMs / 1000L
        for (i in 0 until TIMELINE_SLOTS) {
            intervals.add(formatTimeLabel((startSecs + i * 1800) * 1000))
        }
        intervals
    }
 
    var currentTimeMs by remember { mutableStateOf(getCurrentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000) // update every 10 seconds
            currentTimeMs = getCurrentTimeMillis()
        }
    }
 
     // Lock horizontal scroll position during vertical navigation to keep timeline aligned
     LaunchedEffect(horizontalScrollState.value, isVerticalNavigation) {
         if (isVerticalNavigation && lockedScrollOffset != null && horizontalScrollState.value != lockedScrollOffset) {
             val prevValue = horizontalScrollState.value
             try {
                 horizontalScrollState.scrollTo(lockedScrollOffset!!)
             } catch (e: Exception) {
                 e.printStackTrace()
             }
             if (horizontalScrollState.value == prevValue) {
                 lockedScrollOffset = null
                 isVerticalNavigation = false
             }
         }
     }

    // Initialize category
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && selectedCategory == null) {
            selectedCategory = categories.first()
        }
    }

    // Reset scroll lock and vertical navigation when selectedCategory changes, and scroll EPG back to top
    LaunchedEffect(selectedCategory) {
        lockedScrollOffset = null
        isVerticalNavigation = false
        if (selectedCategory != null && lastWatchedLiveStreamId == null) {
            try {
                verticalListState.scrollToItem(0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
 
    // Filter channels based on category
    val filteredChannels = remember(selectedCategory, allStreams, favoriteStreamIds) {
        if (selectedCategory == null) {
            emptyList()
        } else if (selectedCategory!!.id == "favorites") {
            allStreams.filter { favoriteStreamIds.contains(it.streamId.toString()) }
        } else {
            allStreams.filter { it.categoryId == selectedCategory!!.id }
        }
    }
 
    // Initialize highlighted channel and request focus if focusArea is Channels
    LaunchedEffect(filteredChannels) {
        if (filteredChannels.isNotEmpty()) {
            if (lastWatchedLiveStreamId == null) {
                highlightedChannel = filteredChannels.first()
                if (focusArea == FocusArea.Channels) {
                    delay(150)
                    for (i in 1..10) {
                        try {
                            channelsFocusRequester.requestFocus()
                            break
                        } catch (e: Exception) {
                            delay(50)
                        }
                    }
                }
            }
        } else {
            highlightedChannel = null
            currentProgram = null
        }
    }
 
    // Update EPG info when highlightedChannel changes
    LaunchedEffect(highlightedChannel, currentTimeMs) {
        if (highlightedChannel != null) {
            val cachedEpg = epgCache[highlightedChannel!!.streamId]
            if (cachedEpg != null) {
                currentProgram = cachedEpg.find { prog ->
                    currentTimeMs in prog.getStartMs()..prog.getEndMs()
                } ?: cachedEpg.firstOrNull()
            }
        } else {
            currentProgram = null
        }
    }
 
    // Sync preview with last watched live stream on entrance
    LaunchedEffect(lastWatchedLiveStreamId) {
        if (lastWatchedLiveStreamId != null) {
            previewStreamId = lastWatchedLiveStreamId
            previewUrl = repository.xtreamClient.buildLiveStreamUrl(
                repository.activeProvider!!,
                lastWatchedLiveStreamId
            )
        }
    }
 
    // Scroll and focus recovery for the watched stream on back button exit
    LaunchedEffect(lastWatchedLiveStreamId, filteredChannels) {
        if (lastWatchedLiveStreamId != null && filteredChannels.isNotEmpty()) {
            val foundChannel = allStreams.find { it.streamId == lastWatchedLiveStreamId }
            if (foundChannel != null) {
                val targetCategory = categories.find { it.id == foundChannel.categoryId }
                if (targetCategory != null && selectedCategory != targetCategory) {
                    selectedCategory = targetCategory
                    // Wait for recomposition to populate filteredChannels
                    return@LaunchedEffect
                }
            }
 
            val idx = filteredChannels.indexOfFirst { it.streamId == lastWatchedLiveStreamId }
            if (idx != -1) {
                try {
                    verticalListState.scrollToItem(idx)
                    // Small delay to ensure the row's LazyRow and its cards are composed
                    delay(200)
                    watchedStreamFocusRequester.requestFocus()
                    onFocusAreaChange(FocusArea.Channels)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    onClearLastWatchedLiveStreamId()
                }
            } else {
                onClearLastWatchedLiveStreamId()
            }
        }
    }
 
 
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter) {
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        isDpadCenterPressed = true
                    } else if (keyEvent.type == KeyEventType.KeyUp) {
                        isDpadCenterPressed = false
                    }
                }
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (focusArea == FocusArea.Channels || focusArea == FocusArea.EpgPrograms) {
                                isVerticalNavigation = true
                                lockedScrollOffset = horizontalScrollState.value
                            }
                        }
                        Key.DirectionLeft, Key.DirectionRight -> {
                            isVerticalNavigation = false
                            lockedScrollOffset = null
                        }
                    }
                }
                if (showMatchesDialog) {
                    if (keyEvent.key == Key.Back) {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            showMatchesDialog = false
                        }
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                if (isMenuOpen) {
                    if (keyEvent.key == Key.Back) {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            isMenuOpen = false
                            // Restore focus
                            val streamId = menuChannel?.streamId
                            if (streamId != null) {
                                coroutineScope.launch {
                                    delay(100)
                                    channelFocusRequesters[streamId]?.requestFocus()
                                }
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                if (keyEvent.key == Key.Back) {
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        val currentFocusedStreamId = highlightedChannel?.streamId
                        if (currentFocusedStreamId != null && previewStreamId != null && currentFocusedStreamId != previewStreamId) {
                            // If focused on a different channel/program than the previewed one, go back to previewed channel
                            val idx = filteredChannels.indexOfFirst { it.streamId == previewStreamId }
                            if (idx != -1) {
                                coroutineScope.launch {
                                    verticalListState.scrollToItem(idx)
                                    delay(200)
                                    try {
                                        if (idx == 0) {
                                            channelsFocusRequester.requestFocus()
                                        } else {
                                            channelFocusRequesters[previewStreamId]?.requestFocus()
                                        }
                                        onFocusAreaChange(FocusArea.Channels)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        } else {
                            // Otherwise, navigate back to category menu or sidebar
                            if (focusArea == FocusArea.Channels || focusArea == FocusArea.EpgPrograms) {
                                onFocusAreaChange(FocusArea.Categories)
                            } else if (focusArea == FocusArea.Categories) {
                                onFocusAreaChange(FocusArea.Sidebar)
                            } else {
                                return@onPreviewKeyEvent false
                            }
                        }
                        true
                    } else {
                        keyEvent.type == KeyEventType.KeyDown
                    }
                } else {
                    false
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // TOP HALF: Split layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3.2f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f/9f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                LivePreviewPlayer(
                    url = previewUrl,
                    modifier = Modifier.fillMaxSize()
                )
            }
 
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = highlightedChannel?.name ?: "No Channel Highlighted",
                    style = TvTypography.Subtitle,
                    color = AccentColorLight
                )
 
                if (currentProgram != null) {
                    val prog = currentProgram!!
                    Text(
                        text = prog.title,
                        style = TvTypography.Title.copy(fontSize = 16.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
 
                    val startTime = formatTimeLabel(prog.getStartMs())
                    val endTime = formatTimeLabel(prog.getEndMs())
                    Text(
                        text = "Time: $startTime - $endTime",
                        style = TvTypography.Detail
                    )
 
                    val startMs = prog.getStartMs()
                    val endMs = prog.getEndMs()
                    if (endMs > startMs) {
                        val progress = ((currentTimeMs - startMs).toFloat() / (endMs - startMs).toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = progress,
                            color = AccentColorLight,
                            trackColor = SurfaceColorHover,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                        )
                    }
 
                    Text(
                        text = prog.description ?: "No description provided.",
                        style = TvTypography.Body.copy(fontSize = 13.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        text = "EPG program description will display here.",
                        style = TvTypography.Body.copy(fontSize = 13.sp),
                        color = TextColorSecondary
                    )
                }
            }
        }
 
        // BOTTOM HALF: Grid EPG (Categories -> Channels Rows with Timelines)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(6.8f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category Slider Column
            AnimatedVisibility(
                visible = (focusArea == FocusArea.Categories || focusArea == FocusArea.Sidebar),
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceColor)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Categories",
                        style = TvTypography.Subtitle.copy(fontSize = 14.sp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = AccentColorLight
                    )
                    val categoriesListState = rememberLazyListState()
                    val categoryFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
                    LazyColumn(
                        state = categoriesListState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            onFocusAreaChange(FocusArea.Sidebar)
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            onFocusAreaChange(FocusArea.Channels)
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                    ) {
                        itemsIndexed(displayedCategories) { index, category ->
                            val isSelected = selectedCategory == category
                            val itemFocusRequester = if (index == 0) categoriesFocusRequester else categoryFocusRequesters.getOrPut(index) { FocusRequester() }
                            TvCategoryTab(
                                text = category.name,
                                isSelected = isSelected,
                                onSelect = {
                                    selectedCategory = category
                                    onFocusAreaChange(FocusArea.Channels)
                                },
                                modifier = Modifier
                                    .focusRequester(itemFocusRequester)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionUp -> {
                                                    if (index == 0) {
                                                        coroutineScope.launch {
                                                            val targetIdx = displayedCategories.size - 1
                                                            categoriesListState.scrollToItem(targetIdx)
                                                            delay(50)
                                                            categoryFocusRequesters[targetIdx]?.requestFocus()
                                                        }
                                                        true
                                                    } else false
                                                }
                                                Key.DirectionDown -> {
                                                    if (index == displayedCategories.size - 1) {
                                                        coroutineScope.launch {
                                                            categoriesListState.scrollToItem(0)
                                                            delay(50)
                                                            categoriesFocusRequester.requestFocus()
                                                        }
                                                        true
                                                    } else false
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            )
                        }
                    }
                }
            }
 
            // Grid Content Panel (Header Times + Channel Timelines)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    Column(modifier = Modifier.width(totalWidth)) {
                        // EPG Time Header Row (Sticky at top of grid)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Box for Channels header (pinned on left)
                            Box(
                                modifier = Modifier
                                    .width(channelColWidth)
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        translationX = horizontalScrollState.value.toFloat()
                                    }
                                    .zIndex(1f)
                                    .background(SurfaceColor)
                                    .padding(start = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Channels",
                                    color = AccentColorLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Times grid (Static Row)
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                timeHeaderIntervals.forEach { timeStr ->
                                    Box(
                                        modifier = Modifier
                                            .width(slotWidth)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = timeStr,
                                            color = TextColorSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
 
                        // Vertical List of Channel Rows
                        if (filteredChannels.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No channels in this category", style = TvTypography.Detail)
                            }
                        } else {
                            LazyColumn(
                                state = verticalListState,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(filteredChannels) { rowIndex, channel ->
                                    val now = currentTimeMs
                                    val density = androidx.compose.ui.platform.LocalDensity.current
                                    var channelEpg by remember(channel.streamId) { mutableStateOf(epgCache[channel.streamId]) }
                                    LaunchedEffect(channel.streamId) {
                                        if (channelEpg == null) {
                                            delay(400) // Debounce rapid scrolling
                                            try {
                                                val list = repository.xtreamClient.getEpg(
                                                    repository.activeProvider!!,
                                                    channel.streamId,
                                                    timelineStartMs,
                                                    timelineEndMs
                                                )
                                                epgCache[channel.streamId] = list
                                                channelEpg = list
                                                if (highlightedChannel == channel) {
                                                    currentProgram = list.find { prog ->
                                                        currentTimeMs in prog.getStartMs()..prog.getEndMs()
                                                    } ?: list.firstOrNull()
                                                }
                                            } catch (e: Throwable) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
 
                                    LaunchedEffect(channelEpg) {
                                        if (channelEpg != null && highlightedChannel == channel) {
                                            val list = channelEpg!!
                                            if (list.isEmpty()) {
                                                val req = if (rowIndex == 0) channelsFocusRequester else channelFocusRequesters[channel.streamId]
                                                if (req != null) {
                                                    for (i in 1..5) {
                                                        delay(50)
                                                        try {
                                                            req.requestFocus()
                                                            break
                                                        } catch (e: Exception) {}
                                                    }
                                                }
                                            } else {
                                                val targetTime = timelineStartMs + selectedTimeOffsetMs
                                                val overlapIndex = list.indexOfFirst { targetTime >= it.getStartMs() && targetTime < it.getEndMs() }
                                                val targetIdx = if (overlapIndex != -1) {
                                                    overlapIndex
                                                } else {
                                                    var bestIndex = 0
                                                    var minDiff = Long.MAX_VALUE
                                                    list.forEachIndexed { idx, p ->
                                                        val start = p.getStartMs()
                                                        val end = p.getEndMs()
                                                        val diff = if (targetTime < start) {
                                                            start - targetTime
                                                        } else {
                                                            targetTime - (end - 1)
                                                        }
                                                        if (diff < minDiff) {
                                                            minDiff = diff
                                                            bestIndex = idx
                                                        }
                                                    }
                                                    bestIndex
                                                }
                                                val targetKey = "${channel.streamId}_$targetIdx"
                                                for (i in 1..5) {
                                                    delay(50)
                                                    try {
                                                        programFocusRequesters[targetKey]?.requestFocus()
                                                        break
                                                    } catch (e: Exception) {}
                                                }
                                            }
                                        }
                                    }
 
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(35.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left part: Fixed Channel Badge
                                        Row(
                                            modifier = Modifier
                                                .width(channelColWidth)
                                                .fillMaxHeight()
                                                .graphicsLayer {
                                                    translationX = horizontalScrollState.value.toFloat()
                                                }
                                                .zIndex(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (highlightedChannel == channel) SurfaceColorHover else BackgroundColor)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ChannelLogo(
                                                url = channel.icon,
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.Black.copy(alpha = 0.2f))
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = channel.name,
                                                color = if (highlightedChannel == channel) AccentColorLight else TextColorPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
 
                                        // Right part: Absolute overlay timeline layout
                                        Box(
                                            modifier = Modifier
                                                .width(gridWidth)
                                                .fillMaxHeight()
                                        ) {
                                            val epgList = channelEpg
                                            if (epgList == null) {
                                                val isWatchedRow = channel.streamId == lastWatchedLiveStreamId
                                                val focusRequesterModifier = when {
                                                    isWatchedRow -> Modifier.focusRequester(watchedStreamFocusRequester)
                                                    rowIndex == 0 -> Modifier.focusRequester(channelsFocusRequester)
                                                    else -> Modifier.focusRequester(channelFocusRequesters.getOrPut(channel.streamId) { FocusRequester() })
                                                }

                                                TvFocusableCard(
                                                    onClick = {
                                                        if (previewStreamId == channel.streamId) {
                                                            onPlayLive(channel.streamId)
                                                        } else {
                                                            previewStreamId = channel.streamId
                                                            previewUrl = repository.xtreamClient.buildLiveStreamUrl(repository.activeProvider!!, channel.streamId)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(18.dp),
                                                    unfocusedColor = Color(0xFF2A2A38),
                                                    focusedColor = Color(0xFF3A3A4E),
                                                    modifier = Modifier
                                                        .width(gridWidth)
                                                        .fillMaxHeight()
                                                        .padding(horizontal = 2.dp)
                                                        .onPreviewKeyEvent { keyEvent ->
                                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                                when (keyEvent.key) {
                                                                    Key.DirectionLeft -> {
                                                                        onFocusAreaChange(FocusArea.Categories)
                                                                        true
                                                                    }
                                                                    Key.DirectionDown -> {
                                                                        val nextIdx = rowIndex + 1
                                                                        val targetIdx = if (nextIdx < filteredChannels.size) nextIdx else 0
                                                                        coroutineScope.launch {
                                                                            try {
                                                                                verticalListState.scrollToItem(targetIdx)
                                                                                delay(30)
                                                                                val nextReq = if (targetIdx == 0) {
                                                                                    channelsFocusRequester
                                                                                } else {
                                                                                    channelFocusRequesters.getOrPut(filteredChannels[targetIdx].streamId) { FocusRequester() }
                                                                                }
                                                                                nextReq.requestFocus()
                                                                            } catch (e: Exception) {
                                                                                e.printStackTrace()
                                                                            }
                                                                        }
                                                                        true
                                                                    }
                                                                    Key.DirectionUp -> {
                                                                        val prevIdx = rowIndex - 1
                                                                        val targetIdx = if (prevIdx >= 0) prevIdx else filteredChannels.size - 1
                                                                        coroutineScope.launch {
                                                                            try {
                                                                                verticalListState.scrollToItem(targetIdx)
                                                                                delay(30)
                                                                                val prevReq = if (targetIdx == 0) {
                                                                                    channelsFocusRequester
                                                                                } else {
                                                                                    channelFocusRequesters.getOrPut(filteredChannels[targetIdx].streamId) { FocusRequester() }
                                                                                }
                                                                                prevReq.requestFocus()
                                                                            } catch (e: Exception) {
                                                                                e.printStackTrace()
                                                                            }
                                                                        }
                                                                        true
                                                                    }
                                                                    else -> false
                                                                }
                                                            } else false
                                                        }
                                                        .then(focusRequesterModifier)
                                                ) { isFocused ->
                                                    if (isFocused) {
                                                        highlightedChannel = channel
                                                        currentProgram = null
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .graphicsLayer {
                                                                translationX = horizontalScrollState.value.toFloat()
                                                            }
                                                            .fillMaxHeight(),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        CircularProgressIndicator(
                                                            color = AccentColorLight,
                                                            modifier = Modifier
                                                                .padding(start = 16.dp)
                                                                .size(20.dp),
                                                            strokeWidth = 2.dp
                                                        )
                                                        Text(
                                                            text = "Loading schedule...",
                                                            fontSize = 11.sp,
                                                            color = TextColorSecondary,
                                                            modifier = Modifier.padding(start = 45.dp)
                                                        )
                                                    }
                                                }
                                            } else if (epgList.isEmpty()) {
                                                val isWatchedRow = channel.streamId == lastWatchedLiveStreamId
                                                val focusRequesterModifier = when {
                                                    isWatchedRow -> Modifier.focusRequester(watchedStreamFocusRequester)
                                                    rowIndex == 0 -> Modifier.focusRequester(channelsFocusRequester)
                                                    else -> Modifier.focusRequester(channelFocusRequesters.getOrPut(channel.streamId) { FocusRequester() })
                                                }
 
                                                TvFocusableCard(
                                                    onClick = {
                                                        if (previewStreamId == channel.streamId) {
                                                            onPlayLive(channel.streamId)
                                                        } else {
                                                            previewStreamId = channel.streamId
                                                            previewUrl = repository.xtreamClient.buildLiveStreamUrl(repository.activeProvider!!, channel.streamId)
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(18.dp),
                                                    unfocusedColor = Color(0xFF2A2A38),
                                                    focusedColor = Color(0xFF3A3A4E),
                                                    modifier = Modifier
                                                        .width(gridWidth)
                                                        .fillMaxHeight()
                                                        .padding(horizontal = 2.dp)
                                                        .onPreviewKeyEvent { keyEvent ->
                                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                                when (keyEvent.key) {
                                                                    Key.DirectionLeft -> {
                                                                        onFocusAreaChange(FocusArea.Categories)
                                                                        true
                                                                    }
                                                                    Key.DirectionDown -> {
                                                                        val nextIdx = rowIndex + 1
                                                                        val targetIdx = if (nextIdx < filteredChannels.size) nextIdx else 0
                                                                        coroutineScope.launch {
                                                                            try {
                                                                                verticalListState.scrollToItem(targetIdx)
                                                                                delay(30)
                                                                                val nextReq = if (targetIdx == 0) {
                                                                                    channelsFocusRequester
                                                                                } else {
                                                                                    channelFocusRequesters.getOrPut(filteredChannels[targetIdx].streamId) { FocusRequester() }
                                                                                }
                                                                                nextReq.requestFocus()
                                                                            } catch (e: Exception) {
                                                                                e.printStackTrace()
                                                                            }
                                                                        }
                                                                        true
                                                                    }
                                                                    Key.DirectionUp -> {
                                                                        val prevIdx = rowIndex - 1
                                                                        val targetIdx = if (prevIdx >= 0) prevIdx else filteredChannels.size - 1
                                                                        coroutineScope.launch {
                                                                            try {
                                                                                verticalListState.scrollToItem(targetIdx)
                                                                                delay(30)
                                                                                val prevReq = if (targetIdx == 0) {
                                                                                    channelsFocusRequester
                                                                                } else {
                                                                                    channelFocusRequesters.getOrPut(filteredChannels[targetIdx].streamId) { FocusRequester() }
                                                                                }
                                                                                prevReq.requestFocus()
                                                                            } catch (e: Exception) {
                                                                                e.printStackTrace()
                                                                            }
                                                                        }
                                                                        true
                                                                    }
                                                                    else -> false
                                                                }
                                                            } else false
                                                        }
                                                        .then(focusRequesterModifier)
                                                ) { isFocused ->
                                                    if (isFocused) {
                                                        highlightedChannel = channel
                                                        currentProgram = null
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .graphicsLayer {
                                                                translationX = horizontalScrollState.value.toFloat()
                                                            }
                                                            .fillMaxHeight()
                                                            .padding(start = 16.dp),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        Text(
                                                            text = "No Schedule. Click to Play Live",
                                                            fontSize = 11.sp,
                                                            color = TextColorSecondary,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            } else {
                                                epgList.forEachIndexed { colIndex, prog ->
                                                    val startMs = prog.getStartMs()
                                                    val endMs = prog.getEndMs()
  
                                                     if (endMs > timelineStartMs && startMs < timelineEndMs) {
                                                         val clampedStart = startMs.coerceAtLeast(timelineStartMs)
                                                         val clampedEnd = endMs.coerceAtMost(timelineEndMs)
  
                                                         val startDiffMins = ((clampedStart - timelineStartMs) / 60000f)
                                                         val durationMins = ((clampedEnd - clampedStart) / 60000f)
  
                                                         val cardLeft = (startDiffMins * 3.5f).dp
                                                         val cardWidth = (durationMins * 3.5f).dp
                                                         val cardLeftPx = with(density) { cardLeft.toPx() }
  
                                                         if (cardWidth > 10.dp) {
                                                             val isCurrent = now in startMs..endMs
                                                             val isPast = endMs < now
                                                             val supportsArchive = channel.tvArchive == 1
  
                                                               val closestProgramIndex = remember(epgList, selectedTimeOffsetMs) {
                                                                   if (epgList.isEmpty()) {
                                                                       -1
                                                                   } else {
                                                                       val targetTime = timelineStartMs + selectedTimeOffsetMs
                                                                       val overlapIndex = epgList.indexOfFirst { targetTime >= it.getStartMs() && targetTime < it.getEndMs() }
                                                                       if (overlapIndex != -1) {
                                                                           overlapIndex
                                                                       } else {
                                                                           var bestIndex = 0
                                                                           var minDiff = Long.MAX_VALUE
                                                                           epgList.forEachIndexed { idx, p ->
                                                                               val start = p.getStartMs()
                                                                               val end = p.getEndMs()
                                                                               val diff = if (targetTime < start) {
                                                                                   start - targetTime
                                                                               } else {
                                                                                   targetTime - (end - 1)
                                                                               }
                                                                               if (diff < minDiff) {
                                                                                   minDiff = diff
                                                                                   bestIndex = idx
                                                                               }
                                                                           }
                                                                           bestIndex
                                                                       }
                                                                   }
                                                               }
                                                               val isFocusTarget = colIndex == closestProgramIndex
                                                               val isFocusable = colIndex == closestProgramIndex

                                                              val progKey = "${channel.streamId}_$colIndex"
                                                              val progFocusRequester = programFocusRequesters.getOrPut(progKey) { FocusRequester() }

                                                              val isWatchedRow = channel.streamId == lastWatchedLiveStreamId
                                                              
                                                              val baseFocusRequesterModifier = when {
                                                                  isWatchedRow && isFocusTarget -> Modifier.focusRequester(watchedStreamFocusRequester)
                                                                  rowIndex == 0 && isFocusTarget -> Modifier.focusRequester(channelsFocusRequester)
                                                                  isFocusTarget -> Modifier.focusRequester(channelFocusRequesters.getOrPut(channel.streamId) { FocusRequester() })
                                                                  else -> Modifier
                                                              }
                                                              val focusRequesterModifier = baseFocusRequesterModifier.focusRequester(progFocusRequester)

                                                              val isPreviewingCurrent = channel.streamId == previewStreamId && isCurrent

                                                              val unfocusedCardColor = when {
                                                                  isPast -> Color(0xFF1E1E28)
                                                                  isPreviewingCurrent -> Color.White
                                                                  else -> Color(0xFF2A2A38)
                                                              }

                                                              val focusedCardColor = when {
                                                                  isPreviewingCurrent -> Color(0xFFE5E5EA)
                                                                  else -> Color(0xFF3A3A4E)
                                                              }

                                                              val cardTextColor = when {
                                                                  isPreviewingCurrent -> BackgroundColor
                                                                  isPast -> TextColorSecondary
                                                                  isCurrent -> AccentColorLight
                                                                  else -> TextColorPrimary
                                                              }

                                                              val cardKeyModifier = Modifier.onPreviewKeyEvent { keyEvent ->
                                                                  if (keyEvent.type == KeyEventType.KeyDown) {
                                                                      when (keyEvent.key) {
                                                                          Key.DirectionRight -> {
                                                                              val nextProg = epgList.getOrNull(colIndex + 1)
                                                                              if (nextProg != null) {
                                                                                  selectedTimeOffsetMs = nextProg.getStartMs() - timelineStartMs
                                                                                  val nextKey = "${channel.streamId}_${colIndex + 1}"
                                                                                  coroutineScope.launch {
                                                                                      delay(50)
                                                                                      try {
                                                                                          programFocusRequesters[nextKey]?.requestFocus()
                                                                                      } catch (e: Exception) {
                                                                                          e.printStackTrace()
                                                                                      }
                                                                                  }
                                                                              }
                                                                              true
                                                                          }
                                                                          Key.DirectionLeft -> {
                                                                              val prevProg = epgList.getOrNull(colIndex - 1)
                                                                              if (prevProg != null) {
                                                                                  selectedTimeOffsetMs = prevProg.getStartMs() - timelineStartMs
                                                                                  val prevKey = "${channel.streamId}_${colIndex - 1}"
                                                                                  coroutineScope.launch {
                                                                                      delay(50)
                                                                                      try {
                                                                                          programFocusRequesters[prevKey]?.requestFocus()
                                                                                      } catch (e: Exception) {
                                                                                          e.printStackTrace()
                                                                                      }
                                                                                  }
                                                                              } else {
                                                                                  onFocusAreaChange(FocusArea.Categories)
                                                                              }
                                                                              true
                                                                          }
                                                                          Key.DirectionDown -> {
                                                                         val nextIdx = rowIndex + 1
                                                                         val targetIdx = if (nextIdx < filteredChannels.size) nextIdx else 0
                                                                         coroutineScope.launch {
                                                                             try {
                                                                                 verticalListState.scrollToItem(targetIdx)
                                                                                 delay(30)
                                                                                 val nextReq = if (targetIdx == 0) {
                                                                                     channelsFocusRequester
                                                                                 } else {
                                                                                     channelFocusRequesters.getOrPut(filteredChannels[targetIdx].streamId) { FocusRequester() }
                                                                                 }
                                                                                 nextReq.requestFocus()
                                                                             } catch (e: Exception) {
                                                                                 e.printStackTrace()
                                                                             }
                                                                         }
                                                                         true
                                                                     }
                                                                     Key.DirectionUp -> {
                                                                         val prevIdx = rowIndex - 1
                                                                         val targetIdx = if (prevIdx >= 0) prevIdx else filteredChannels.size - 1
                                                                         coroutineScope.launch {
                                                                             try {
                                                                                 verticalListState.scrollToItem(targetIdx)
                                                                                 delay(30)
                                                                                 val prevReq = if (targetIdx == 0) {
                                                                                     channelsFocusRequester
                                                                                 } else {
                                                                                     channelFocusRequesters.getOrPut(filteredChannels[targetIdx].streamId) { FocusRequester() }
                                                                                 }
                                                                                 prevReq.requestFocus()
                                                                             } catch (e: Exception) {
                                                                                 e.printStackTrace()
                                                                             }
                                                                         }
                                                                         true
                                                                     }
                                                                          else -> false
                                                                      }
                                                                  } else {
                                                                      false
                                                                  }
                                                              }

                                                             TvFocusableCard(
                                                                 onClick = {
                                                                     if (previewStreamId == channel.streamId) {
                                                                         if (isPast && supportsArchive) {
                                                                             onPlayCatchup(channel.streamId, prog.start ?: "", ((endMs - startMs) / 60000).toInt())
                                                                         } else {
                                                                             onPlayLive(channel.streamId)
                                                                         }
                                                                     } else {
                                                                         previewStreamId = channel.streamId
                                                                         previewUrl = repository.xtreamClient.buildLiveStreamUrl(repository.activeProvider!!, channel.streamId)
                                                                     }
                                                                 },
                                                                 onLongClick = {
                                                                     menuChannel = channel
                                                                     menuProgram = prog
                                                                     isMenuOpen = true
                                                                 },
                                                                 shape = RoundedCornerShape(18.dp),
                                                                 unfocusedColor = unfocusedCardColor,
                                                                 focusedColor = focusedCardColor,
                                                                 enabled = isFocusable,
                                                                 modifier = Modifier
                                                                     .offset(x = cardLeft)
                                                                     .width(cardWidth)
                                                                     .fillMaxHeight()
                                                                     .padding(horizontal = 2.dp)
                                                                     .then(cardKeyModifier)
                                                                     .then(focusRequesterModifier)
                                                             ) { isFocused ->
                                                                 if (isFocused) {
                                                                     highlightedChannel = channel
                                                                     currentProgram = prog
                                                                     if (!isVerticalNavigation) {
                                                                         coroutineScope.launch {
                                                                             val targetScrollPx = cardLeftPx.toInt()
                                                                             horizontalScrollState.animateScrollTo(targetScrollPx)
                                                                         }
                                                                     }
                                                                 }
                                                                 Box(
                                                                     modifier = Modifier
                                                                         .fillMaxSize()
                                                                         .padding(horizontal = 6.dp),
                                                                     contentAlignment = Alignment.CenterStart
                                                                 ) {
                                                                     Text(
                                                                         text = prog.title,
                                                                         fontSize = 11.sp,
                                                                         fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                                         color = cardTextColor,
                                                                         maxLines = 1,
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
                         }
                     }
 
                     // Indicator line that matches app theme and disappears when scrolled past
                     val density = androidx.compose.ui.platform.LocalDensity.current
                     val elapsedMins = (currentTimeMs - timelineStartMs) / 60000f
                     if (elapsedMins >= 0f && elapsedMins <= TIMELINE_SLOTS * 30f) {
                         val lineX = (elapsedMins * 3.5f).dp
                         val lineXPx = with(density) { lineX.toPx() }
                         if (horizontalScrollState.value < lineXPx.toInt()) {
                             val lineOffset = channelColWidth + lineX
                             Box(
                                 modifier = Modifier
                                     .offset(x = lineOffset)
                                     .width(2.dp)
                                     .fillMaxHeight()
                                     .background(AccentColorLight.copy(alpha = 0.8f))
                             )
                         }
                     }
                  }
              }
          }
      }

        // RIGHT SIDE SLIDE-OUT MENU OVERLAY
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(320.dp)
                .zIndex(10f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E28).copy(alpha = 0.95f))
                    .border(BorderStroke(1.dp, Color(0xFF2A2A38)))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = menuProgram?.title ?: "Program Options",
                        style = TvTypography.Subtitle,
                        color = AccentColorLight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val isFavChannel = menuChannel?.let { favoriteStreamIds.contains(it.streamId.toString()) } ?: false
                    
                    // Option 1: Favorite Toggle Channel
                    TvFocusableCard(
                        onClick = {
                            val channel = menuChannel
                            if (channel != null) {
                                coroutineScope.launch {
                                    repository.toggleFavorite("live", channel.streamId.toString())
                                    menuMessage = if (isFavChannel) {
                                        "Removed from favorites"
                                    } else {
                                        "Added to favorites"
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .focusRequester(menuFocusRequester),
                        shape = RoundedCornerShape(8.dp)
                    ) { isFocused ->
                        Text(
                            text = if (isFavChannel) "Remove Channel from Favorites" else "Add Channel to Favorites",
                            color = if (isFocused) AccentColorLight else TextColorPrimary,
                            style = TvTypography.Body,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
                        )
                    }

                    // Option 2: Search VOD
                    TvFocusableCard(
                        onClick = {
                            val prog = menuProgram
                            if (prog != null) {
                                coroutineScope.launch {
                                    val movies = repository.vodMovies.value
                                    val series = repository.seriesList.value
                                    val matches = matchVod(prog.title, movies, series)
                                    menuMatches = matches
                                    
                                    if (matches.isEmpty()) {
                                        menuMessage = "No match found in VOD"
                                    } else if (matches.size == 1) {
                                        val match = matches.first()
                                        val isFav = repository.isFavorite(match.type, match.id)
                                        repository.toggleFavorite(match.type, match.id)
                                        menuMessage = if (isFav) {
                                            "Removed '${match.name}' from VOD favorites"
                                        } else {
                                            "Added '${match.name}' to VOD favorites"
                                        }
                                        isMenuOpen = false
                                    } else {
                                        showMatchesDialog = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { isFocused ->
                        Text(
                            text = "Favorite Program in VOD",
                            color = if (isFocused) AccentColorLight else TextColorPrimary,
                            style = TvTypography.Body,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TvButton(
                        text = "Close",
                        onClick = {
                            isMenuOpen = false
                            coroutineScope.launch {
                                delay(100)
                                val streamId = menuChannel?.streamId
                                if (streamId != null) {
                                    channelFocusRequesters[streamId]?.requestFocus()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // MULTIPLE MATCHES DIALOG
        if (showMatchesDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .zIndex(20f)
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(450.dp)
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceColor)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Multiple matches found in VOD",
                        style = TvTypography.Subtitle,
                        color = AccentColorLight,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    val dialogFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        delay(100)
                        try {
                            dialogFocusRequester.requestFocus()
                        } catch (e: Exception) {}
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(menuMatches) { idx, match ->
                            val isFav = favoritesList.any { it.type == match.type && it.itemId == match.id }
                            TvFocusableCard(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.toggleFavorite(match.type, match.id)
                                        showMatchesDialog = false
                                        isMenuOpen = false
                                    }
                                },
                                modifier = if (idx == 0) Modifier.focusRequester(dialogFocusRequester) else Modifier,
                                shape = RoundedCornerShape(8.dp)
                            ) { isFocused ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (match.type == "movie") Color(0xFFE50914) else Color(0xFF0070F3))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (match.type == "movie") "MOVIE" else "SHOW",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = match.name,
                                        style = TvTypography.Body,
                                        color = if (isFocused) AccentColorLight else TextColorPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isFav) {
                                        Text(
                                            text = "★",
                                            color = Color.Yellow,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TvButton(
                        text = "Cancel",
                        onClick = { showMatchesDialog = false },
                        modifier = Modifier.width(120.dp)
                    )
                }
            }
        }

        // TOAST NOTIFICATION MESSAGE OVERLAY
        if (menuMessage != null) {
            LaunchedEffect(menuMessage) {
                delay(3000)
                menuMessage = null
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(1.dp, AccentColorLight, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = menuMessage ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Focus request for options menu
        LaunchedEffect(isMenuOpen, isDpadCenterPressed) {
            if (isMenuOpen && !isDpadCenterPressed) {
                delay(50)
                try {
                    menuFocusRequester.requestFocus()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
      }
  }

sealed class VodMatch {
    data class Movie(val movie: XtreamMovie) : VodMatch()
    data class Series(val series: XtreamSeries) : VodMatch()

    val name: String
        get() = when (this) {
            is Movie -> movie.name
            is Series -> series.name
        }

    val id: String
        get() = when (this) {
            is Movie -> movie.streamId.toString()
            is Series -> series.seriesId.toString()
        }

    val type: String
        get() = when (this) {
            is Movie -> "movie"
            is Series -> "series"
        }
}

fun matchVod(programTitle: String, movies: List<XtreamMovie>, series: List<XtreamSeries>): List<VodMatch> {
    val query = programTitle.lowercase().trim()
    if (query.isEmpty()) return emptyList()

    val matches = mutableListOf<VodMatch>()

    // Movie matches
    for (movie in movies) {
        val nameLower = movie.name.lowercase().trim()
        if (nameLower.length >= 3 && (nameLower.indexOf(query) >= 0 || query.indexOf(nameLower) >= 0)) {
            matches.add(VodMatch.Movie(movie))
        } else if (nameLower == query) {
            matches.add(VodMatch.Movie(movie))
        }
    }

    // Series matches
    for (s in series) {
        val nameLower = s.name.lowercase().trim()
        if (nameLower.length >= 3 && (nameLower.indexOf(query) >= 0 || query.indexOf(nameLower) >= 0)) {
            matches.add(VodMatch.Series(s))
        } else if (nameLower == query) {
            matches.add(VodMatch.Series(s))
        }
    }

    return matches
}
 
 // In-memory set representation helper
 @Composable
 fun <T> rememberStateSet(): MutableSet<T> {
     return remember { mutableStateListOf<T>() }.let { list ->
         remember(list) {
             object : MutableSet<T> by list.toMutableSet() {
                 override fun add(element: T): Boolean {
                     if (list.contains(element)) return false
                     list.add(element)
                     return true
                 }
                 override fun remove(element: T): Boolean {
                     return list.remove(element)
                 }
                 override fun contains(element: T): Boolean {
                     return list.contains(element)
                 }
             }
         }
     }
 }
 
 fun <T> mutableStateSetOf(): MutableSet<T> {
     return mutableSetOf()
 }
