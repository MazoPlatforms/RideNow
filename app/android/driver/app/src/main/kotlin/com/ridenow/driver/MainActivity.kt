package com.ridenow.driver

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ridenow.driver.ui.AppRoot
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.settings.AppLockGate
import com.ridenow.driver.ui.theme.AppearanceState
import com.ridenow.driver.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(applyAppLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash hands off instantly to the animated Compose splash (same background colour).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Edge to edge from the first frame, so the keyboard is handled only by Compose's imePadding().
        enableEdgeToEdge()
        // On Android 11+ the keyboard height arrives as a window inset. The system must not also resize or pan the window,
        // otherwise the form moves twice and leaves a gap above the keyboard. Older versions only send the inset after a
        // resize, so the manifest keeps adjustResize for them.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        com.ridenow.driver.ui.util.NotificationChannels.ensure(applicationContext)
        migrateAppLanguageToSystem(this)
        setContent {
            val appearance = remember { AppearanceState(this@MainActivity) }
            val mode = appearance.mode
            val dark = when (mode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
            // Keep status and navigation bar icons readable when the theme is forced.
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
            }
            RideNowTheme(darkTheme = dark, accent = appearance.accent, mapStyle = appearance.mapStyle, fontScale = appearance.fontScale) {
                AppLockGate { AppRoot(appearance) }
            }
        }
    }
}
