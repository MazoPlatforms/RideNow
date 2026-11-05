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
import com.ridenow.app.ui.auth.DetailsScreen
import com.ridenow.app.ui.auth.OtpScreen
import com.ridenow.app.ui.auth.PhoneScreen
import com.ridenow.app.ui.splash.SplashScreen
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.welcome.WelcomeScreen

enum class Screen { Splash, Welcome, Phone, Otp, Details, Location }

@Composable
fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.Splash) }
    var phone by rememberSaveable { mutableStateOf("") }
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
                PhoneScreen(onBack = { screen = Screen.Welcome }, onCodeSent = { phone = it; screen = Screen.Otp })
            }
            Screen.Otp -> {
                BackHandler { screen = Screen.Phone }
                OtpScreen(phone = phone, onBack = { screen = Screen.Phone }, onVerified = { screen = Screen.Details })
            }
            Screen.Details -> {
                BackHandler { screen = Screen.Otp }
                DetailsScreen(onBack = { screen = Screen.Otp }, onDone = { _, _ -> screen = Screen.Location })
            }
            Screen.Location -> {
                BackHandler { screen = Screen.Details }
                Placeholder("Location access screen goes here")
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
