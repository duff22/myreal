package com.myrealtv.app.ui.components

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

import androidx.media3.exoplayer.DefaultLoadControl

object LivePlayerHolder {
    private var exoPlayer: ExoPlayer? = null
    var currentUrl: String? = null
    var isFullscreenActive: Boolean = false

    fun getOrCreatePlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    5000,  // minBufferMs
                    15000, // maxBufferMs
                    1500,  // bufferForPlaybackMs
                    2500   // bufferForPlaybackAfterRebufferMs
                )
                .build()
            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setLoadControl(loadControl)
                .build()
        }
        return exoPlayer!!
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        currentUrl = null
        isFullscreenActive = false
    }
}
