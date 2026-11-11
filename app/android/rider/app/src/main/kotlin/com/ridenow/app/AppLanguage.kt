package com.ridenow.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

private const val LANGUAGE_PREFS = "ridenow"
private const val LANGUAGE_KEY = "lang"
private const val FRAMEWORK_LANGUAGE_MIGRATED = "framework_language_migrated"
private val translatedLanguages = setOf("en", "es", "fr", "de", "ja", "hi", "ar", "ta", "si")

/** Android 13+ applies the system app-language setting; older versions use the saved preference. */
fun applyAppLanguage(base: Context): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Locale.setDefault(base.resources.configuration.locales[0])
        return base
    }
    val code = base.getSharedPreferences(LANGUAGE_PREFS, 0).getString(LANGUAGE_KEY, "en") ?: "en"
    val locale = Locale.forLanguageTag(code)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}

/** Imports an existing in-app choice once, without overriding later changes made in phone settings. */
fun migrateAppLanguageToSystem(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val prefs = context.getSharedPreferences(LANGUAGE_PREFS, 0)
    if (prefs.getBoolean(FRAMEWORK_LANGUAGE_MIGRATED, false)) return
    val manager = context.getSystemService(LocaleManager::class.java) ?: return
    val saved = prefs.getString(LANGUAGE_KEY, null)
    prefs.edit().putBoolean(FRAMEWORK_LANGUAGE_MIGRATED, true).commit()
    if (saved != null && manager.applicationLocales.isEmpty) {
        manager.applicationLocales = LocaleList.forLanguageTags(saved)
    }
}

/** Reads the platform setting so changes in Android App info also update the in-app language UI. */
fun getAppLanguage(context: Context): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        if (locales != null && !locales.isEmpty) return locales[0].language
        val systemLanguage = context.resources.configuration.locales[0].language
        return systemLanguage.takeIf { it in translatedLanguages } ?: "en"
    }
    return context.getSharedPreferences(LANGUAGE_PREFS, 0).getString(LANGUAGE_KEY, "en") ?: "en"
}

/** Uses the same persisted setting as Android App info, with the existing fallback on older devices. */
fun setAppLanguage(context: Context, code: String) {
    context.getSharedPreferences(LANGUAGE_PREFS, 0).edit()
        .putString(LANGUAGE_KEY, code)
        .putBoolean(FRAMEWORK_LANGUAGE_MIGRATED, true)
        .commit()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(code)
    } else {
        (context as? Activity)?.recreate()
    }
}
