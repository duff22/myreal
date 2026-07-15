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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.onKeyEvent
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

@Composable
fun Modifier.tvDpadClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier {
    var dpadPressed by remember { mutableStateOf(false) }
    var dpadPressTime by remember { mutableStateOf(0L) }
    var hasTriggeredLongClick by remember { mutableStateOf(false) }
    
    return this.onKeyEvent { keyEvent ->
        val nativeEvent = keyEvent.nativeKeyEvent
        val keyCode = nativeEvent.keyCode
        val isDpadCenter = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER
        
        if (isDpadCenter) {
            if (nativeEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                if (!dpadPressed) {
                    dpadPressed = true
                    dpadPressTime = System.currentTimeMillis()
                    hasTriggeredLongClick = false
                } else {
                    val elapsed = System.currentTimeMillis() - dpadPressTime
                    if (elapsed >= 600 && !hasTriggeredLongClick) {
                        hasTriggeredLongClick = true
                        onLongClick()
                    }
                }
            } else if (nativeEvent.action == android.view.KeyEvent.ACTION_UP) {
                dpadPressed = false
                if (!hasTriggeredLongClick) {
                    onClick()
                }
                hasTriggeredLongClick = false
            }
            true
        } else {
            false
        }
    }.focusable(interactionSource = interactionSource)
}
