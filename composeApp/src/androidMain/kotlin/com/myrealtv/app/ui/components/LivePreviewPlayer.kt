package com.myrealtv.app.ui.components
 
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
 
@OptIn(UnstableApi::class)
@Composable
actual fun LivePreviewPlayer(url: String?, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember {
        LivePlayerHolder.getOrCreatePlayer(context)
    }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
 
    LaunchedEffect(url) {
        if (url != null) {
            if (LivePlayerHolder.currentUrl != url) {
                val mediaItem = MediaItem.fromUri(url)
                player.setMediaItem(mediaItem)
                player.prepare()
                LivePlayerHolder.currentUrl = url
            }
            player.volume = 0f // Mute in preview
            player.playWhenReady = true
        }
    }
 
    DisposableEffect(Unit) {
        onDispose {
            playerView?.player = null
            playerView = null
            if (!LivePlayerHolder.isFullscreenActive) {
                player.stop()
                player.clearMediaItems()
                LivePlayerHolder.currentUrl = null
            } else {
                player.volume = 0f
            }
        }
    }
 
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (url == null) {
            Text("Preview Window", color = Color.Gray)
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false // Hide play/pause overlays
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setPlayer(player)
                        playerView = this
                    }
                },
                update = { view ->
                    view.setPlayer(player)
                    playerView = view
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
