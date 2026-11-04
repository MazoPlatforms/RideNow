package com.ridenow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ridenow.app.ui.AppRoot
import com.ridenow.app.ui.theme.RideNowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash hands off instantly to the animated Compose splash (same background colour).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RideNowTheme { AppRoot() } }
    }
}
