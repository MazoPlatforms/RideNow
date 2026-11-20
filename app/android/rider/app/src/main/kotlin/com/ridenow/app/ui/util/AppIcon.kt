package com.ridenow.app.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.ridenow.app.R

/** Launcher icon choices. Each one is an activity-alias in the manifest; only one is enabled at a time. */
enum class AppIconChoice(val alias: String, val labelRes: Int, val colorRes: Int) {
    Classic("LauncherDefault", R.string.icon_classic, R.color.ridenow_launcher_background),
    Graphite("LauncherGraphite", R.string.icon_graphite, R.color.icon_graphite),
    Midnight("LauncherMidnight", R.string.icon_midnight, R.color.icon_midnight),
}

fun currentAppIcon(context: Context): AppIconChoice =
    runCatching { AppIconChoice.valueOf(context.getSharedPreferences("ridenow", 0).getString("app_icon", null) ?: "Classic") }.getOrDefault(AppIconChoice.Classic)

/** Enables the chosen alias and disables the others. The app keeps running; the launcher refreshes the icon in a moment. */
fun setAppIcon(context: Context, choice: AppIconChoice) {
    val pm = context.packageManager
    // Enable the new alias first so the app never has zero launcher entries.
    pm.setComponentEnabledSetting(ComponentName(context.packageName, "com.ridenow.app.${choice.alias}"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    AppIconChoice.entries.filter { it != choice }.forEach {
        pm.setComponentEnabledSetting(ComponentName(context.packageName, "com.ridenow.app.${it.alias}"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
    context.getSharedPreferences("ridenow", 0).edit().putString("app_icon", choice.name).apply()
}
