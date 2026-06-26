package com.myrealtv.app.ui.components

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@Composable
actual fun MediaPlayer(
    type: String, // "live", "movie", "episode", "catchup"
    url: String,
    initialPositionMs: Long,
    onProgressUpdate: (progressMs: Long, durationMs: Long) -> Unit,
    onFormatInfoUpdate: ((resolution: String, fps: String, audio: String) -> Unit)?,
    onPlaybackEnded: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current

    // 1. Configure single-threaded HTTP client and set User-Agent to prevent firewalls
    val okHttpClient = remember {
        val dispatcher = Dispatcher().apply {
            maxRequests = 1
            maxRequestsPerHost = 1
        }
        val connectionPool = ConnectionPool(
            maxIdleConnections = 1,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.SECONDS
        )
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "TiviMate") // Firewall bypass
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    // 2. Create custom DataSource Factory with User-Agent set
    val dataSourceFactory = remember(okHttpClient) {
        OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("TiviMate") // Media3 DataSource Factory bypass
    }

    // 3. Create Custom LoadControl to limit buffer size and read-ahead window
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // minBufferMs (buffer at least 15s)
                30000, // maxBufferMs (buffer at most 30s)
                2500,  // bufferForPlaybackMs
                5000   // bufferForPlaybackAfterRebufferMs
            )
            .build()
    }

    // 4. Create and configure ExoPlayer
    val exoPlayer = remember(type) {
        if (type == "live") {
            LivePlayerHolder.isFullscreenActive = true
            LivePlayerHolder.getOrCreatePlayer(context)
        } else {
            ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build()
        }
    }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    // Handle play source and seek on mount/url change
    LaunchedEffect(url) {
        if (type == "live") {
            if (LivePlayerHolder.currentUrl != url) {
                val mediaItem = MediaItem.fromUri(url)
                val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                    .createMediaSource(mediaItem)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                LivePlayerHolder.currentUrl = url
            }
            exoPlayer.volume = 1f // Unmute in fullscreen
            exoPlayer.playWhenReady = true
        } else {
            val mediaItem = MediaItem.fromUri(url)
            val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(mediaItem)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            if (initialPositionMs > 0) {
                exoPlayer.seekTo(initialPositionMs)
            }
            exoPlayer.playWhenReady = true
        }
    }

    // 5. Poller for progress updates (every second)
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (exoPlayer.isPlaying) {
                onProgressUpdate(exoPlayer.currentPosition, exoPlayer.duration)
            }
            if (onFormatInfoUpdate != null) {
                val videoFormat = exoPlayer.videoFormat
                val audioFormat = exoPlayer.audioFormat
                
                val res = if (videoFormat != null && videoFormat.width > 0 && videoFormat.height > 0) {
                    "${videoFormat.width}x${videoFormat.height}"
                } else {
                    "1920x1080"
                }
                
                val fps = if (videoFormat != null && videoFormat.frameRate > 0) {
                    "${videoFormat.frameRate.toInt()} fps"
                } else {
                    "60 fps"
                }
                
                val audio = if (audioFormat != null) {
                    val channels = audioFormat.channelCount
                    val dolby = if (audioFormat.sampleMimeType?.contains("ac3") == true || audioFormat.sampleMimeType?.contains("eac3") == true) "Dolby" else ""
                    val chStr = when (channels) {
                        6 -> "5.1"
                        8 -> "7.1"
                        2 -> "Stereo"
                        1 -> "Mono"
                        else -> if (channels > 0) "$channels Ch" else ""
                    }
                    if (dolby.isNotEmpty()) {
                        if (chStr.isNotEmpty()) "$dolby $chStr" else dolby
                    } else {
                        chStr.ifEmpty { "Stereo" }
                    }
                } else {
                    "Stereo"
                }
                onFormatInfoUpdate(res, fps, audio)
            }
            delay(1000)
        }
    }

    // 6. Listen for completion and handle release
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onPlaybackEnded()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            playerView?.player = null
            playerView = null
            if (type != "live") {
                exoPlayer.release()
            } else {
                LivePlayerHolder.isFullscreenActive = false
                exoPlayer.volume = 0f // Mute for preview player
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = (type != "live")
                if (type == "live") {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                } else {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
                }
                playerView = this
                
                setOnKeyListener { _, keyCode, event ->
                    if (type == "live") return@setOnKeyListener false
                    if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                        val isControllerVisible = isControllerFullyVisible
                        val containerHasFocus = isFocused
                        
                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                            android.view.KeyEvent.KEYCODE_DPAD_UP,
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (!isControllerVisible || containerHasFocus) {
                                    showController()
                                    postDelayed({
                                        val playPauseBtn = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play_pause)
                                        playPauseBtn?.requestFocus()
                                    }, 100)
                                    return@setOnKeyListener true
                                }
                            }
                            android.view.KeyEvent.KEYCODE_BACK -> {
                                if (isControllerVisible) {
                                    hideController()
                                    requestFocus()
                                    return@setOnKeyListener true
                                }
                            }
                        }
                    }
                    false
                }
            }
        },
        update = { view ->
            view.player = exoPlayer
            playerView = view
            if (type != "live") {
                view.requestFocus()
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
