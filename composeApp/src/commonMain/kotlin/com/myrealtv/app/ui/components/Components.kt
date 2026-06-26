package com.myrealtv.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myrealtv.app.ui.theme.*

@Composable
fun TvFocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    unfocusedColor: Color = SurfaceColor,
    focusedColor: Color = SurfaceColorHover,
    content: @Composable BoxScope.(Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.06f else 1.0f)
    val borderStroke = if (isFocused) {
        BorderStroke(2.5.dp, AccentColorLight)
    } else {
        BorderStroke(1.dp, Color.Transparent)
    }

    val backgroundColor by animateColorAsState(
        if (isFocused) focusedColor else unfocusedColor
    )

    val coroutineScope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var longPressed by remember { mutableStateOf(false) }
    var isPressedDown by remember { mutableStateOf(false) }

    val keyModifier = Modifier.onPreviewKeyEvent { keyEvent ->
        if (enabled && (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
            if (keyEvent.type == KeyEventType.KeyDown) {
                isPressedDown = true
                if (onLongClick != null) {
                    if (holdJob == null && !longPressed) {
                        holdJob = coroutineScope.launch {
                            delay(800)
                            longPressed = true
                            onLongClick()
                        }
                    }
                }
                true
            } else if (keyEvent.type == KeyEventType.KeyUp) {
                val wasPressed = isPressedDown
                isPressedDown = false
                holdJob?.cancel()
                holdJob = null
                if (!longPressed && wasPressed) {
                    onClick()
                }
                longPressed = false
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(backgroundColor)
            .border(borderStroke, shape)
            .onFocusChanged { isFocused = it.isFocused }
            .then(keyModifier)
            .clickable(enabled = enabled) { onClick() }
            .focusable(enabled = enabled)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        content(isFocused)
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.06f else 1.0f)

    val defaultBg = if (isAlert) AlertColor else AccentColor
    val backgroundColor by animateColorAsState(
        if (isFocused) AccentColorLight else defaultBg
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TvTypography.Button,
            color = TextColorPrimary
        )
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    val scale by animateFloatAsState(if (isFocused || isEditing) 1.03f else 1.0f)
    val borderStrokeColor = when {
        isEditing -> AccentColorLight
        isFocused -> AccentColor
        else -> SurfaceColor
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused || isEditing) SurfaceColorHover else SurfaceColor)
            .border(
                width = if (isFocused || isEditing) 2.5.dp else 1.dp,
                color = borderStrokeColor,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { keyEvent ->
                // If container is focused and not editing, pressing Enter/Center-Dpad goes into edit mode
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)
                ) {
                    if (!isEditing) {
                        isEditing = true
                        true
                    } else {
                        isEditing = false
                        false
                    }
                } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back) {
                    if (isEditing) {
                        isEditing = false
                        true // consume back press, exiting edit mode
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .clickable { isEditing = true }
            .focusable(enabled = !isEditing)
    ) {
        if (isEditing) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                singleLine = true,
                visualTransformation = if (isPassword) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextColorPrimary,
                    unfocusedTextColor = TextColorPrimary,
                    focusedLabelColor = AccentColorLight,
                    unfocusedLabelColor = TextColorSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.Back)) {
                            isEditing = false
                            true
                        } else {
                            false
                        }
                    }
            )
        } else {
            // Static display box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = label,
                    style = TvTypography.Detail.copy(
                        fontSize = 12.sp,
                        color = if (isFocused) AccentColorLight else TextColorSecondary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isPassword && value.isNotEmpty()) "•".repeat(value.length) else value.ifEmpty { "Click OK to edit" },
                    style = TvTypography.Body.copy(
                        color = if (value.isEmpty()) TextColorSecondary else TextColorPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TvSidebarTab(
    text: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.06f else 1.0f)

    val backgroundColor by animateColorAsState(
        when {
            isFocused -> SurfaceColorHover
            isSelected -> SurfaceColor
            else -> Color.Transparent
        }
    )

    val textColor by animateColorAsState(
        when {
            isSelected -> AccentColorLight
            isFocused -> TextColorPrimary
            else -> TextColorSecondary
        }
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onSelect() }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) AccentColorLight else Color.Transparent)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = TvTypography.Subtitle.copy(fontSize = 18.sp),
            color = textColor
        )
    }
}

@Composable
fun TvCategoryTab(
    text: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.06f else 1.0f)

    val backgroundColor by animateColorAsState(
        when {
            isFocused -> SurfaceColorHover
            isSelected -> SurfaceColor
            else -> Color.Transparent
        }
    )

    val textColor by animateColorAsState(
        when {
            isSelected -> AccentColorLight
            isFocused -> TextColorPrimary
            else -> TextColorSecondary
        }
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onSelect() }
            .focusable()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(if (isSelected) AccentColorLight else Color.Transparent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = TvTypography.Subtitle.copy(fontSize = 13.sp),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

