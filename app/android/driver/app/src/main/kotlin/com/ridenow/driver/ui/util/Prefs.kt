package com.ridenow.driver.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val FILE = "ridenowdriver"

/** A Boolean setting kept in preferences. Setting the value writes it at once. */
class BoolPref(private val context: Context, private val key: String, default: Boolean) {
    private val state = mutableStateOf(context.getSharedPreferences(FILE, 0).getBoolean(key, default))
    var value: Boolean
        get() = state.value
        set(v) { state.value = v; context.getSharedPreferences(FILE, 0).edit().putBoolean(key, v).apply() }
}

@Composable
fun rememberBoolPref(key: String, default: Boolean): BoolPref {
    val context = LocalContext.current
    return remember(key) { BoolPref(context, key, default) }
}

/** A String setting kept in preferences. */
class StringPref(private val context: Context, private val key: String, default: String) {
    private val state = mutableStateOf(context.getSharedPreferences(FILE, 0).getString(key, default) ?: default)
    var value: String
        get() = state.value
        set(v) { state.value = v; context.getSharedPreferences(FILE, 0).edit().putString(key, v).apply() }
}

@Composable
fun rememberStringPref(key: String, default: String): StringPref {
    val context = LocalContext.current
    return remember(key) { StringPref(context, key, default) }
}
