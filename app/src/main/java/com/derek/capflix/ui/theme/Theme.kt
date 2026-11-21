package com.derek.capflix.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.derek.capflix.R

// The Cap family: same night, cream and coral as Cap Tunes.
// Cap Flix leads with Bolt Yellow, the color of a marquee.
val Night = Color(0xFF0B0B0E)
val Panel = Color(0xFF16161B)
val PanelHigh = Color(0xFF22222A)
val Bolt = Color(0xFFFFD23F)
val Coral = Color(0xFFFF5A36)
val Cream = Color(0xFFF4F1EA)
val Muted = Color(0xFFA6A6B0)
val Faint = Color(0xFF5E5E68)

val Bungee = FontFamily(Font(R.font.bungee_regular))

private val scheme = darkColorScheme(
    primary = Bolt,
    onPrimary = Night,
    secondary = Coral,
    onSecondary = Color.White,
    background = Night,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Muted,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerHighest = PanelHigh,
    outline = Faint,
)

private val typography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)

@Composable
fun CapFlixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}
