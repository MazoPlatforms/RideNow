package com.ridenow.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Everything the rider can change on the Appearance screen. Stored in the app's preferences file so the choice
 * survives restarts. Reads are Compose state, so screens update at once.
 */
class AppearanceState(private val context: Context) {
    private val prefs get() = context.getSharedPreferences("ridenow", 0)

    var mode by mutableStateOf(ThemeMode.System)
        private set
    var accent by mutableStateOf(AccentChoice.Yellow)
        private set
    var mapStyle by mutableStateOf(MapStyle.Default)
        private set
    var systemFont by mutableStateOf(true)
        private set
    var textSize by mutableStateOf(TextSize.Normal)
        private set

    init { reload() }

    /** Reads every choice from preferences again (also used after the account data is cleared). */
    fun reload() {
        mode = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "System") }.getOrDefault(ThemeMode.System)
        accent = runCatching { AccentChoice.valueOf(prefs.getString("accent", null) ?: "Yellow") }.getOrDefault(AccentChoice.Yellow)
        mapStyle = runCatching { MapStyle.valueOf(prefs.getString("map_style", null) ?: "Default") }.getOrDefault(MapStyle.Default)
        systemFont = prefs.getBoolean("system_font", true)
        textSize = runCatching { TextSize.valueOf(prefs.getString("text_size", null) ?: "Normal") }.getOrDefault(TextSize.Normal)
    }

    fun setMode(v: ThemeMode) { mode = v; prefs.edit().putString("theme_mode", v.name).apply() }
    fun setAccent(v: AccentChoice) { accent = v; prefs.edit().putString("accent", v.name).apply() }
    fun setMapStyle(v: MapStyle) { mapStyle = v; prefs.edit().putString("map_style", v.name).apply() }
    fun setSystemFont(v: Boolean) { systemFont = v; prefs.edit().putBoolean("system_font", v).apply() }
    fun setTextSize(v: TextSize) { textSize = v; prefs.edit().putString("text_size", v.name).apply() }

    /** Null means "follow the system font size". */
    val fontScale: Float? get() = if (systemFont) null else textSize.scale
}

/** The app-wide [AppearanceState], provided by MainActivity. */
val LocalAppearance = staticCompositionLocalOf<AppearanceState?> { null }
