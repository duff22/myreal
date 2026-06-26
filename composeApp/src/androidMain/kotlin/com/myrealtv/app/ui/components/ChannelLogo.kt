package com.myrealtv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import com.myrealtv.app.ui.theme.TextColorSecondary

@Composable
actual fun ChannelLogo(url: String?, modifier: Modifier) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "No Logo",
                tint = TextColorSecondary,
                modifier = Modifier.fillMaxSize(0.6f)
            )
        }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = "Channel Logo",
            contentScale = ContentScale.Fit,
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Loading Logo",
                        tint = TextColorSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxSize(0.6f)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Error Logo",
                        tint = TextColorSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxSize(0.6f)
                    )
                }
            },
            modifier = modifier
        )
    }
}
