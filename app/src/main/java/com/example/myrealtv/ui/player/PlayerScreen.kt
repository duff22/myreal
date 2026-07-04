package com.example.myrealtv.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.myrealtv.data.ServiceLocator
import com.example.myrealtv.data.local.PlaybackHistory
import com.example.myrealtv.ui.theme.tvFocusHighlight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Formatter
import java.util.Locale

private enum class TrackDialogType { SUBTITLE, AUDIO, VIDEO }

private data class TrackOption(
    val label: String,
    val isSelected: Boolean,
    val onClick: () -> Unit
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamId: String,
    streamUrl: String,
    title: String,
    isSeries: Boolean,
    seriesId: String? = null,
    episodeNum: Int? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userId = remember { ServiceLocator.getActiveUserId() ?: "default_user" }
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var bufferedPosition by remember { mutableStateOf(0L) }
    
    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var lastSyncTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    var activeTrackDialog by remember { mutableStateOf<TrackDialogType?>(null) }
    
    val rootFocusRequester = remember { FocusRequester() }
    val firstButtonFocusRequester = remember { FocusRequester() }
    var isRootFocused by remember { mutableStateOf(true) }
    
    LaunchedEffect(streamUrl) {
        val mediaItem = MediaItem.fromUri(streamUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        
        val lastSaved = ServiceLocator.database.playbackHistoryDao()
            .getPlaybackHistoryForStream(userId, streamId)
        if (lastSaved != null && lastSaved.lastPosition > 0) {
            exoPlayer.seekTo(lastSaved.lastPosition.toLong())
        }
        
        // Auto-request focus on the root player Box on startup
        rootFocusRequester.requestFocus()
    }
    
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    scope.launch {
                        syncPlaybackState(
                            userId = userId,
                            streamId = streamId,
                            currentPos = exoPlayer.currentPosition,
                            duration = exoPlayer.duration,
                            isSeries = isSeries,
                            seriesId = seriesId,
                            episodeNum = episodeNum
                        )
                    }
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val currentPos = exoPlayer.currentPosition
            val duration = exoPlayer.duration
            exoPlayer.release()
            
            scope.launch {
                syncPlaybackState(
                    userId = userId,
                    streamId = streamId,
                    currentPos = currentPos,
                    duration = duration,
                    isSeries = isSeries,
                    seriesId = seriesId,
                    episodeNum = episodeNum
                )
            }
        }
    }
    
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(10000)
            currentPosition = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration
            bufferedPosition = exoPlayer.bufferedPosition
            
            val history = PlaybackHistory(
                userId = userId,
                streamId = streamId,
                lastPosition = currentPosition.toInt(),
                totalDuration = totalDuration.toInt(),
                isDismissed = false,
                updatedAt = System.currentTimeMillis(),
                isSeries = isSeries,
                seriesId = seriesId,
                episodeNum = episodeNum
            )
            ServiceLocator.database.playbackHistoryDao().insert(history)
            
            val now = System.currentTimeMillis()
            if (now - lastSyncTime >= 120000) {
                lastSyncTime = now
                try {
                    ServiceLocator.syncApi.syncPlaybackHistory(history)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    LaunchedEffect(showControls, isPlaying) {
        while (showControls && isPlaying) {
            currentPosition = exoPlayer.currentPosition
            bufferedPosition = exoPlayer.bufferedPosition
            delay(1000)
        }
    }
    
    LaunchedEffect(showControls) {
        if (showControls) {
            while (System.currentTimeMillis() - lastInteractionTime < 6000) {
                delay(1000)
            }
            showControls = false
        }
    }
    
    fun onUserInteract() {
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onUserInteract() }
            .focusRequester(rootFocusRequester)
            .onFocusChanged { isRootFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            onUserInteract()
                            val newPos = minOf(totalDuration, exoPlayer.currentPosition + 30000)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            onUserInteract()
                            val newPos = maxOf(0L, exoPlayer.currentPosition - 30000)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            onUserInteract()
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            onUserInteract()
                            exoPlayer.play()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            onUserInteract()
                            exoPlayer.pause()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isRootFocused) {
                                onUserInteract()
                                val newPos = maxOf(0L, exoPlayer.currentPosition - 30000)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                                true
                            } else {
                                false
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isRootFocused) {
                                onUserInteract()
                                val newPos = minOf(totalDuration, exoPlayer.currentPosition + 30000)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                                true
                            } else {
                                false
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!showControls) {
                                onUserInteract()
                                true
                            } else {
                                if (isRootFocused) {
                                    firstButtonFocusRequester.requestFocus()
                                    onUserInteract()
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!showControls) {
                                onUserInteract()
                                true
                            } else {
                                false
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER -> {
                            if (!showControls) {
                                onUserInteract()
                                true
                            } else {
                                if (isRootFocused) {
                                    onUserInteract()
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(32.dp)
            ) {
                // Top Info & Exit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSeries && episodeNum != null) {
                            Text(
                                text = "Episode $episodeNum",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        }
                    }
                    val exitInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = { onBack() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        interactionSource = exitInteraction,
                        modifier = Modifier
                            .tvFocusHighlight(exitInteraction, CircleShape)
                            .focusable(interactionSource = exitInteraction)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                                    firstButtonFocusRequester.requestFocus()
                                    onUserInteract()
                                    true
                                } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                                    rootFocusRequester.requestFocus()
                                    onUserInteract()
                                    true
                                } else false
                            }
                    ) {
                        Text("Exit", color = Color.White)
                    }
                }
                
                // Bottom control panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Time Progress Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringForTime(currentPosition),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        
                        LinearProgressIndicator(
                            progress = { if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp),
                            color = Color(0xFF00D2FF),
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        
                        Text(
                            text = stringForTime(totalDuration),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    
                    // Options Row: CC/Subtitles, Audio tracks, Video feeds
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Subtitles / CC Button
                        val ccInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                onUserInteract()
                                activeTrackDialog = TrackDialogType.SUBTITLE
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            interactionSource = ccInteraction,
                            modifier = Modifier
                                .focusRequester(firstButtonFocusRequester)
                                .tvFocusHighlight(ccInteraction, RoundedCornerShape(20.dp))
                                .focusable(interactionSource = ccInteraction)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                                        rootFocusRequester.requestFocus()
                                        onUserInteract()
                                        true
                                    } else false
                                }
                        ) {
                            Text("Subtitles / CC", color = Color.White)
                        }

                        // 2. Audio Tracks Button
                        val audioInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                onUserInteract()
                                activeTrackDialog = TrackDialogType.AUDIO
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            interactionSource = audioInteraction,
                            modifier = Modifier
                                .tvFocusHighlight(audioInteraction, RoundedCornerShape(20.dp))
                                .focusable(interactionSource = audioInteraction)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                                        rootFocusRequester.requestFocus()
                                        onUserInteract()
                                        true
                                    } else false
                                }
                        ) {
                            Text("Audio Tracks", color = Color.White)
                        }

                        // 3. Video Feeds Button
                        val videoInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                onUserInteract()
                                activeTrackDialog = TrackDialogType.VIDEO
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            interactionSource = videoInteraction,
                            modifier = Modifier
                                .tvFocusHighlight(videoInteraction, RoundedCornerShape(20.dp))
                                .focusable(interactionSource = videoInteraction)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                                        rootFocusRequester.requestFocus()
                                        onUserInteract()
                                        true
                                    } else false
                                }
                        ) {
                            Text("Video Feeds", color = Color.White)
                        }
                    }
                }
            }
        }
        
        // Render stream tracks configuration menus
        if (activeTrackDialog != null) {
            val dialogType = activeTrackDialog!!
            val tracks = exoPlayer.currentTracks
            
            val options = remember(tracks, dialogType) {
                val list = mutableListOf<TrackOption>()
                when (dialogType) {
                    TrackDialogType.SUBTITLE -> {
                        list.add(TrackOption(
                            label = "Off",
                            isSelected = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_TEXT),
                            onClick = {
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                    .build()
                                activeTrackDialog = null
                            }
                        ))
                        for (group in tracks.groups) {
                            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val label = format.label ?: format.language ?: "Subtitles #${list.size}"
                                    val isSelected = !exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_TEXT) && group.isTrackSelected(i)
                                    list.add(TrackOption(
                                        label = label.uppercase(),
                                        isSelected = isSelected,
                                        onClick = {
                                            val override = androidx.media3.common.TrackSelectionOverride(
                                                group.mediaTrackGroup,
                                                i
                                            )
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                                .setOverrideForType(override)
                                                .build()
                                            activeTrackDialog = null
                                        }
                                    ))
                                }
                            }
                        }
                    }
                    TrackDialogType.AUDIO -> {
                        for (group in tracks.groups) {
                            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val label = format.label ?: format.language ?: "Audio Track #${list.size + 1}"
                                    val isSelected = group.isTrackSelected(i)
                                    list.add(TrackOption(
                                        label = label.uppercase(),
                                        isSelected = isSelected,
                                        onClick = {
                                            val override = androidx.media3.common.TrackSelectionOverride(
                                                group.mediaTrackGroup,
                                                i
                                            )
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
                                                .setOverrideForType(override)
                                                .build()
                                            activeTrackDialog = null
                                        }
                                    ))
                                }
                            }
                        }
                    }
                    TrackDialogType.VIDEO -> {
                        for (group in tracks.groups) {
                            if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val resStr = if (format.width > 0 && format.height > 0) "${format.width}x${format.height}" else "Auto"
                                    val label = format.label ?: "Resolution: $resStr"
                                    val isSelected = group.isTrackSelected(i)
                                    list.add(TrackOption(
                                        label = label.uppercase(),
                                        isSelected = isSelected,
                                        onClick = {
                                            val override = androidx.media3.common.TrackSelectionOverride(
                                                group.mediaTrackGroup,
                                                i
                                            )
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                                                .setOverrideForType(override)
                                                .build()
                                            activeTrackDialog = null
                                        }
                                    ))
                                }
                            }
                        }
                    }
                }
                list
            }
            
            val dialogTitle = when (dialogType) {
                TrackDialogType.SUBTITLE -> "Subtitles & Captions"
                TrackDialogType.AUDIO -> "Select Audio Language"
                TrackDialogType.VIDEO -> "Select Video Feed"
            }
            
            AlertDialog(
                onDismissRequest = { activeTrackDialog = null },
                title = { Text(dialogTitle, color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (options.isEmpty()) {
                            Text("No tracks available for this content.", color = Color.LightGray)
                        } else {
                            options.forEach { option ->
                                val optInteraction = remember { MutableInteractionSource() }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = optInteraction,
                                            indication = null,
                                            onClick = option.onClick
                                        )
                                        .tvFocusHighlight(optInteraction, RoundedCornerShape(8.dp))
                                        .focusable(interactionSource = optInteraction)
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(option.label, color = Color.White, fontSize = 16.sp)
                                    if (option.isSelected) {
                                        Text("✓", color = Color(0xFF00D2FF), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    val dismissInteraction = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { activeTrackDialog = null },
                        interactionSource = dismissInteraction,
                        modifier = Modifier
                            .tvFocusHighlight(dismissInteraction, RoundedCornerShape(8.dp))
                            .focusable(interactionSource = dismissInteraction)
                    ) {
                        Text("Close", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

private suspend fun syncPlaybackState(
    userId: String,
    streamId: String,
    currentPos: Long,
    duration: Long,
    isSeries: Boolean,
    seriesId: String?,
    episodeNum: Int?
) {
    val history = PlaybackHistory(
        userId = userId,
        streamId = streamId,
        lastPosition = currentPos.toInt(),
        totalDuration = duration.toInt(),
        isDismissed = false,
        updatedAt = System.currentTimeMillis(),
        isSeries = isSeries,
        seriesId = seriesId,
        episodeNum = episodeNum
    )
    ServiceLocator.database.playbackHistoryDao().insert(history)
    try {
        ServiceLocator.syncApi.syncPlaybackHistory(history)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun stringForTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    val formatBuilder = java.lang.StringBuilder()
    val formatter = java.util.Formatter(formatBuilder, java.util.Locale.getDefault())
    return if (hours > 0) {
        formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
    } else {
        formatter.format("%02d:%02d", minutes, seconds).toString()
    }
}
