package com.ridenow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ridenow.app.R

/** RideNow brand yellow, sampled from the supplied logo. */
val BrandYellow = Color(0xFFFEC604)
/** Near-black ink used on top of the brand yellow and as the dark background. */
val Ink = Color(0xFF0A0C10)

@Immutable
data class RideColors(
    val bg: Color, val fg: Color, val muted: Color, val line: Color, val border: Color,
    val accent: Color, val onAccent: Color, val street: Color, val street2: Color,
    val ring1: Color, val ring2: Color, val field: Color, val danger: Color, val isDark: Boolean,
)

val DarkRideColors = RideColors(
    bg = Ink, fg = Color(0xFFF2F4F7), muted = Color(0xFF9AA3AE),
    line = Color(0xFF1E2430), border = Color(0xFF2C3442), accent = BrandYellow,
    onAccent = Ink, street = Color(0xFF1A202A), street2 = Color(0xFF252D3A),
    ring1 = Color(0xFF1E2430), ring2 = Color(0xFF2C3442), field = Color(0xFF11151C), danger = Color(0xFFFF8F8F), isDark = true,
)
/** On light backgrounds the accent is a deeper amber so text and icons keep 4.5:1 contrast. */
val LightRideColors = RideColors(
    bg = Color(0xFFF6F8FA), fg = Ink, muted = Color(0xFF4F5B67),
    line = Color(0xFFDDE3E9), border = Color(0xFFC3CCD6), accent = Color(0xFF8A5A00),
    onAccent = Color.White, street = Color.White, street2 = Color.White,
    ring1 = Color(0xFFD5DCE3), ring2 = Color(0xFFBFC9D3), field = Color.White, danger = Color(0xFFB3261E), isDark = false,
)

val LocalRideColors = staticCompositionLocalOf { DarkRideColors }

private val SoraFontFamily = FontFamily(
    Font(R.font.sora_variable, weight = FontWeight.Thin),
    Font(R.font.sora_variable, weight = FontWeight.ExtraLight),
    Font(R.font.sora_variable, weight = FontWeight.Light),
    Font(R.font.sora_variable, weight = FontWeight.Normal),
    Font(R.font.sora_variable, weight = FontWeight.Medium),
    Font(R.font.sora_variable, weight = FontWeight.SemiBold),
    Font(R.font.sora_variable, weight = FontWeight.Bold),
    Font(R.font.sora_variable, weight = FontWeight.ExtraBold),
    Font(R.font.sora_variable, weight = FontWeight.Black),
)

private val SoraTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = SoraFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = SoraFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = SoraFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = SoraFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = SoraFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = SoraFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = SoraFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = SoraFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = SoraFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = SoraFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = SoraFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = SoraFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = SoraFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = SoraFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = SoraFontFamily),
    )
}

object RideTheme {
    val colors: RideColors @Composable @ReadOnlyComposable get() = LocalRideColors.current
}

@Composable
fun RideNowTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) DarkRideColors else LightRideColors
    val scheme = if (darkTheme) darkColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
    else lightColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
    CompositionLocalProvider(LocalRideColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = SoraTypography, content = content)
    }
}
