package com.example.myrealtv.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.tvFocusHighlight(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    borderWidth: Dp = 3.dp,
    focusedBorderColor: Color = Color(0xFF00D2FF), // Sleek Cyan highlight
    unfocusedBorderColor: Color = Color.Transparent,
    scaleAmount: Float = 1.0f
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleAmount else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "scale animate"
    )
    
    return this
        .scale(scale)
        .border(
            width = borderWidth,
            color = if (isFocused) focusedBorderColor else unfocusedBorderColor,
            shape = shape
        )
}

@Composable
fun Modifier.tvFocusable(
    shape: Shape,
    borderWidth: Dp = 3.dp,
    focusedBorderColor: Color = Color(0xFF00D2FF),
    unfocusedBorderColor: Color = Color.Transparent,
    scaleAmount: Float = 1.0f
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .tvFocusHighlight(
            interactionSource = interactionSource,
            shape = shape,
            borderWidth = borderWidth,
            focusedBorderColor = focusedBorderColor,
            unfocusedBorderColor = unfocusedBorderColor,
            scaleAmount = scaleAmount
        )
        .focusable(interactionSource = interactionSource)
}
