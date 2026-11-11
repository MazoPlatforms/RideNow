package com.ridenow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ridenow.app.R

private val SoraFamily = FontFamily(
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

object RideTheme {
    val colors: RideColors @Composable @ReadOnlyComposable get() = LocalRideColors.current
}

/** One entry in Settings > App color. Light mode uses a deeper shade so text keeps its contrast. */
class AccentChoice(val name: String, val dark: Color, val light: Color)
val AccentChoices = listOf(
    AccentChoice("Mint", Color(0xFF3DDC97), Color(0xFF0B6B45)), AccentChoice("Default", BrandYellow, Color(0xFF8A5A00)),
    AccentChoice("Aqua", Color(0xFF3CC8E6), Color(0xFF0A6C80)), AccentChoice("Violet", Color(0xFFA98BFF), Color(0xFF5B3BC4)),
    AccentChoice("Amber", Color(0xFFFFA726), Color(0xFF8A4B00)), AccentChoice("Coral", Color(0xFFFF7A66), Color(0xFFB03A28)),
)
object AccentPrefs { var name by mutableStateOf("Default") }

@Composable
fun RideNowTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val base = if (darkTheme) DarkRideColors else LightRideColors
    val pick = AccentChoices.firstOrNull { it.name == AccentPrefs.name && it.name != "Default" }
    val c = if (pick == null) base else base.copy(accent = if (darkTheme) pick.dark else pick.light, onAccent = if (darkTheme) Ink else Color.White)
    val scheme = if (darkTheme) darkColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
    else lightColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
    val typography = Typography().let { base ->
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = SoraFamily), displayMedium = base.displayMedium.copy(fontFamily = SoraFamily), displaySmall = base.displaySmall.copy(fontFamily = SoraFamily),
            headlineLarge = base.headlineLarge.copy(fontFamily = SoraFamily), headlineMedium = base.headlineMedium.copy(fontFamily = SoraFamily), headlineSmall = base.headlineSmall.copy(fontFamily = SoraFamily),
            titleLarge = base.titleLarge.copy(fontFamily = SoraFamily), titleMedium = base.titleMedium.copy(fontFamily = SoraFamily), titleSmall = base.titleSmall.copy(fontFamily = SoraFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = SoraFamily), bodyMedium = base.bodyMedium.copy(fontFamily = SoraFamily), bodySmall = base.bodySmall.copy(fontFamily = SoraFamily),
            labelLarge = base.labelLarge.copy(fontFamily = SoraFamily), labelMedium = base.labelMedium.copy(fontFamily = SoraFamily), labelSmall = base.labelSmall.copy(fontFamily = SoraFamily),
        )
    }
    CompositionLocalProvider(LocalRideColors provides c) { MaterialTheme(colorScheme = scheme, typography = typography, content = content) }
}

/** Appearance setting: follow the phone, or force light or dark. */
enum class ThemeMode(val labelRes: Int) { System(com.ridenow.app.R.string.theme_system), Light(com.ridenow.app.R.string.theme_light), Dark(com.ridenow.app.R.string.theme_dark) }
