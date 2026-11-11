package com.ridenow.driver.ui.theme

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

/** RideNow Driver mint, used for fills (buttons, orb) in both themes. */
val BrandMint = Color(0xFF7CF5D0)
/** Near-black ink used on top of the mint and as the dark background. */
val Ink = Color(0xFF07090D)

@Immutable
data class RideColors(
    val bg: Color, val fg: Color, val muted: Color, val line: Color, val border: Color,
    val accent: Color, val onAccent: Color, val fill: Color, val onFill: Color,
    val street: Color, val street2: Color, val ring1: Color, val ring2: Color, val field: Color,
    val danger: Color, val warn: Color, val isDark: Boolean,
)

val DarkRideColors = RideColors(
    bg = Ink, fg = Color(0xFFEEF2F6), muted = Color(0xFF9AA6B2),
    line = Color(0xFF1E2733), border = Color(0xFF2A3646), accent = BrandMint, onAccent = Color(0xFF04130E),
    fill = BrandMint, onFill = Color(0xFF04130E),
    street = Color(0xFF1C2633), street2 = Color(0xFF26334A), ring1 = Color(0xFF1E2733), ring2 = Color(0xFF2A3646),
    field = Color(0xFF0D1219), danger = Color(0xFFFF9C94), warn = Color(0xFFFFC857), isDark = true,
)
/** On light backgrounds the accent is a deeper green so text and icons keep 4.5:1 contrast. Fills stay mint. */
val LightRideColors = RideColors(
    bg = Color(0xFFF6F8FA), fg = Color(0xFF0B1117), muted = Color(0xFF4F5B67),
    line = Color(0xFFDDE3E9), border = Color(0xFFC3CCD6), accent = Color(0xFF0A7A5F), onAccent = Color.White,
    fill = BrandMint, onFill = Color(0xFF04130E),
    street = Color.White, street2 = Color.White, ring1 = Color(0xFFD5DCE3), ring2 = Color(0xFFBFC9D3), field = Color.White,
    danger = Color(0xFFB3261E), warn = Color(0xFF8A5A00), isDark = false,
)

val LocalRideColors = staticCompositionLocalOf { DarkRideColors }

object RideTheme {
    val colors: RideColors @Composable @ReadOnlyComposable get() = LocalRideColors.current
}

@Composable
fun RideNowTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) DarkRideColors else LightRideColors
    val scheme = if (darkTheme) darkColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
    else lightColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
    CompositionLocalProvider(LocalRideColors provides c) { MaterialTheme(colorScheme = scheme, content = content) }
}

/** Appearance setting: follow the phone, or force light or dark. */
enum class ThemeMode(val labelRes: Int) { System(com.ridenow.driver.R.string.theme_system), Light(com.ridenow.driver.R.string.theme_light), Dark(com.ridenow.driver.R.string.theme_dark) }
