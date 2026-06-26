package com.myrealtv.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BackgroundColor = Color(0xFF0C0C0F)
val SurfaceColor = Color(0xFF16161F)
val SurfaceColorHover = Color(0xFF222230)
val AccentColor = Color(0xFF9F3BFF)
val AccentColorLight = Color(0xFFBD80FF)
val TextColorPrimary = Color(0xFFFFFFFF)
val TextColorSecondary = Color(0xFF8E8E9E)
val AlertColor = Color(0xFFFF3B30)

private val CustomColorScheme = darkColorScheme(
    primary = AccentColor,
    onPrimary = TextColorPrimary,
    background = BackgroundColor,
    onBackground = TextColorPrimary,
    surface = SurfaceColor,
    onSurface = TextColorPrimary,
    error = AlertColor
)

object TvTypography {
    val Title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = TextColorPrimary
    )
    val Subtitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        color = TextColorSecondary
    )
    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        color = TextColorPrimary
    )
    val Button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = TextColorPrimary
    )
    val Detail = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = TextColorSecondary
    )
}

@Composable
fun MyRealTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CustomColorScheme,
        content = content
    )
}
