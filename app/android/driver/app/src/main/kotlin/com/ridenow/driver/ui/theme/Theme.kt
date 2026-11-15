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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** RideNow brand yellow, sampled from the supplied logo (same as the Rider app). */
val BrandYellow = Color(0xFFFEC604)
/** Near-black ink used on top of the brand yellow and as the dark background. */
val Ink = Color(0xFF0A0C10)

@Immutable
data class RideColors(
    val bg: Color, val fg: Color, val muted: Color, val line: Color, val border: Color,
    val accent: Color, val onAccent: Color, val fill: Color, val onFill: Color,
    val street: Color, val street2: Color, val ring1: Color, val ring2: Color, val field: Color,
    val danger: Color, val warn: Color, val isDark: Boolean,
    /** Positive status (completed, verified, earnings). Readable on [bg] and on its own tint. */
    val good: Color = Color(0xFF22B35A),
    /** Informational status (in progress, links). */
    val info: Color = Color(0xFF4C9AFF),
)

/** Accent colours the driver can pick in Appearance. [fill] is used for buttons and selected pills. */
enum class AccentChoice(val fill: Color, val onLight: Color, val onFill: Color, val labelRes: Int) {
    Yellow(BrandYellow, Color(0xFF8A5A00), Ink, com.ridenow.driver.R.string.accent_yellow),
    Blue(Color(0xFF3B8BEB), Color(0xFF1F5FBF), Color.White, com.ridenow.driver.R.string.accent_blue),
    Green(Color(0xFF22B35A), Color(0xFF0F7A3A), Color.White, com.ridenow.driver.R.string.accent_green),
    Red(Color(0xFFF0383D), Color(0xFFB3261E), Color.White, com.ridenow.driver.R.string.accent_red),
    Purple(Color(0xFF8E4CE8), Color(0xFF6A2FC0), Color.White, com.ridenow.driver.R.string.accent_purple),
    Orange(Color(0xFFFF8A1F), Color(0xFF9A4A00), Ink, com.ridenow.driver.R.string.accent_orange),
}

/** Map appearance used by the Home, ride and earnings maps. */
enum class MapStyle(val labelRes: Int) {
    Default(com.ridenow.driver.R.string.map_default), Dark(com.ridenow.driver.R.string.map_dark), Satellite(com.ridenow.driver.R.string.map_satellite),
}

/** Text size choices used when "Use system font size" is off. */
enum class TextSize(val scale: Float, val labelRes: Int) {
    Normal(1f, com.ridenow.driver.R.string.text_size_normal), Large(1.15f, com.ridenow.driver.R.string.text_size_large), Larger(1.3f, com.ridenow.driver.R.string.text_size_larger),
}

val DarkRideColors = RideColors(
    bg = Ink, fg = Color(0xFFF2F4F7), muted = Color(0xFF9AA3AE),
    line = Color(0xFF1E2430), border = Color(0xFF2C3442), accent = BrandYellow, onAccent = Ink,
    fill = BrandYellow, onFill = Ink,
    street = Color(0xFF1A202A), street2 = Color(0xFF252D3A), ring1 = Color(0xFF1E2430), ring2 = Color(0xFF2C3442),
    field = Color(0xFF11151C), danger = Color(0xFFFF8F8F), warn = Color(0xFFFFB74D), isDark = true,
    good = Color(0xFF3DDC84), info = Color(0xFF6DB1FF),
)
/** On light backgrounds the accent is a deeper amber so text and icons keep 4.5:1 contrast. Fills stay brand yellow. */
val LightRideColors = RideColors(
    bg = Color(0xFFF6F8FA), fg = Ink, muted = Color(0xFF4F5B67),
    line = Color(0xFFDDE3E9), border = Color(0xFFC3CCD6), accent = Color(0xFF8A5A00), onAccent = Color.White,
    fill = BrandYellow, onFill = Ink,
    street = Color.White, street2 = Color.White, ring1 = Color(0xFFD5DCE3), ring2 = Color(0xFFBFC9D3), field = Color.White,
    danger = Color(0xFFB3261E), warn = Color(0xFF8A4B00), isDark = false,
    good = Color(0xFF0F7A3A), info = Color(0xFF1F5FBF),
)

val LocalRideColors = staticCompositionLocalOf { DarkRideColors }
/** The map style chosen in Appearance. Read by every map drawing. */
val LocalMapStyle = staticCompositionLocalOf { MapStyle.Default }

object RideTheme {
    val colors: RideColors @Composable @ReadOnlyComposable get() = LocalRideColors.current
}

@Composable
fun RideNowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), accent: AccentChoice = AccentChoice.Yellow,
    mapStyle: MapStyle = MapStyle.Default, fontScale: Float? = null, content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkRideColors else LightRideColors
    val c = if (accent == AccentChoice.Yellow) base else base.copy(
        accent = if (darkTheme) accent.fill else accent.onLight, onAccent = if (darkTheme) accent.onFill else Color.White,
        fill = accent.fill, onFill = accent.onFill,
    )
    val scheme = if (darkTheme) darkColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
    else lightColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
    val density = LocalDensity.current
    val scaled = if (fontScale == null) density else Density(density.density, fontScale)
    CompositionLocalProvider(LocalRideColors provides c, LocalMapStyle provides mapStyle, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Appearance setting: follow the phone, or force light or dark. */
enum class ThemeMode(val labelRes: Int) { System(com.ridenow.driver.R.string.theme_system), Light(com.ridenow.driver.R.string.theme_light), Dark(com.ridenow.driver.R.string.theme_dark) }
