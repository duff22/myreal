package com.myrealtv.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MediaPlayer(
    type: String, // "live", "movie", "episode", "catchup"
    url: String,
    initialPositionMs: Long,
    onProgressUpdate: (progressMs: Long, durationMs: Long) -> Unit,
    onFormatInfoUpdate: ((resolution: String, fps: String, audio: String) -> Unit)? = null,
    onPlaybackEnded: () -> Unit,
    modifier: Modifier
)
