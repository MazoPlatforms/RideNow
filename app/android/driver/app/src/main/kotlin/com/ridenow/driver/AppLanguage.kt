package com.ridenow.driver

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Wraps the base context with the language the user picked on the Language screen (default: English). */
fun applyAppLanguage(base: Context): Context {
    val code = base.getSharedPreferences("ridenowdriver", 0).getString("lang", "en") ?: "en"
    val locale = Locale.forLanguageTag(code)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}
