package com.ridenow.driver.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Everything the driver can change on the Appearance screen. Stored in the same preferences file as the rest of
 * the app so the choice survives restarts. Reads are Compose state, so screens update at once.
 */
class AppearanceState(private val context: Context) {
    private val prefs = context.getSharedPreferences("ridenowdriver", 0)

    var mode by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "System") }.getOrDefault(ThemeMode.System))
        private set
    var accent by mutableStateOf(runCatching { AccentChoice.valueOf(prefs.getString("accent", null) ?: "Yellow") }.getOrDefault(AccentChoice.Yellow))
        private set
    var mapStyle by mutableStateOf(runCatching { MapStyle.valueOf(prefs.getString("map_style", null) ?: "Default") }.getOrDefault(MapStyle.Default))
        private set
    var systemFont by mutableStateOf(prefs.getBoolean("system_font", true))
        private set
    var textSize by mutableStateOf(runCatching { TextSize.valueOf(prefs.getString("text_size", null) ?: "Normal") }.getOrDefault(TextSize.Normal))
        private set

    fun setMode(v: ThemeMode) { mode = v; prefs.edit().putString("theme_mode", v.name).apply() }
    fun setAccent(v: AccentChoice) { accent = v; prefs.edit().putString("accent", v.name).apply() }
    fun setMapStyle(v: MapStyle) { mapStyle = v; prefs.edit().putString("map_style", v.name).apply() }
    fun setSystemFont(v: Boolean) { systemFont = v; prefs.edit().putBoolean("system_font", v).apply() }
    fun setTextSize(v: TextSize) { textSize = v; prefs.edit().putString("text_size", v.name).apply() }

    /** Null means "follow the system font size". */
    val fontScale: Float? get() = if (systemFont) null else textSize.scale
}
