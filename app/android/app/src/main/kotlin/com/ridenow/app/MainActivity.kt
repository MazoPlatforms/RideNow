package com.ridenow.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ridenow.app.ui.AppRoot
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(applyAppLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash hands off instantly to the animated Compose splash (same background colour).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        migrateAppLanguageToSystem(this)
        setContent {
            var mode by rememberSaveable { mutableStateOf(ThemeMode.System) }
            val dark = when (mode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
            // Keep status and navigation bar icons readable when the theme is forced.
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
            }
            RideNowTheme(darkTheme = dark) { AppRoot(mode) { mode = it } }
        }
    }
}
