package com.ridenow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Design tokens from the RideNow design foundations board. */
@Immutable
data class RideColors(
    val bg: Color, val fg: Color, val muted: Color, val line: Color, val border: Color,
    val accent: Color, val onAccent: Color, val street: Color, val street2: Color,
    val ring1: Color, val ring2: Color, val isDark: Boolean,
)

val DarkRideColors = RideColors(
    bg = Color(0xFF07090D), fg = Color(0xFFEEF2F6), muted = Color(0xFF9AA6B2),
    line = Color(0xFF1E2733), border = Color(0xFF2A3646), accent = Color(0xFF7CF5D0),
    onAccent = Color(0xFF04130E), street = Color(0xFF1C2633), street2 = Color(0xFF26334A),
    ring1 = Color(0xFF1E2733), ring2 = Color(0xFF2A3646), isDark = true,
)
val LightRideColors = RideColors(
    bg = Color(0xFFF6F8FA), fg = Color(0xFF0B1117), muted = Color(0xFF4F5B67),
    line = Color(0xFFDDE3E9), border = Color(0xFFC3CCD6), accent = Color(0xFF0A7A5F),
    onAccent = Color(0xFFFFFFFF), street = Color(0xFFFFFFFF), street2 = Color(0xFFFFFFFF),
    ring1 = Color(0xFFD5DCE3), ring2 = Color(0xFFBFC9D3), isDark = false,
)

/** Mint orb gradient is the same in both themes. */
val OrbGradient = listOf(Color(0xFFB9FFEA), Color(0xFF7CF5D0), Color(0xFF2E9C80))
val OrbInk = Color(0xFF04130E)

val LocalRideColors = staticCompositionLocalOf { DarkRideColors }

object RideTheme {
    val colors: RideColors @Composable @ReadOnlyComposable get() = LocalRideColors.current
}

@Composable
fun RideNowTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) DarkRideColors else LightRideColors
    val scheme = if (darkTheme) darkColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
    else lightColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
    CompositionLocalProvider(LocalRideColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Primary button colour is the same mint in both themes. */
val Mint = Color(0xFF7CF5D0)
