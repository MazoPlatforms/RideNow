package com.ridenow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.splash.SplashScreen
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.welcome.WelcomeScreen

enum class Screen { Splash, Welcome, Phone }

@Composable
fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.Splash) }
    Crossfade(targetState = screen, animationSpec = tween(400), label = "root") { current ->
        when (current) {
            Screen.Splash -> SplashScreen(onFinished = { screen = Screen.Welcome })
            Screen.Welcome -> WelcomeScreen(
                onGetStarted = { screen = Screen.Phone },
                onHaveAccount = { screen = Screen.Phone },
                onHelp = { /* Help & Support screen */ },
            )
            Screen.Phone -> {
                BackHandler { screen = Screen.Welcome }
                Placeholder("Phone number screen goes here")
            }
        }
    }
}

/** Temporary stand-in until the next screen is built. */
@Composable
private fun Placeholder(label: String) {
    val c = RideTheme.colors
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Text(label, color = c.muted, fontSize = 14.sp)
    }
}
