package com.ridenow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.auth.DetailsScreen
import com.ridenow.app.ui.auth.OtpScreen
import com.ridenow.app.ui.auth.PhoneScreen
import com.ridenow.app.ui.splash.SplashScreen
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.welcome.WelcomeScreen
import com.ridenow.app.auth.AuthRepository

enum class Screen { Splash, Welcome, Phone, Otp, Details, Location }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val auth = androidx.compose.runtime.remember(context) { AuthRepository(context) }
    var screen by rememberSaveable { mutableStateOf(Screen.Splash) }
    var phone by rememberSaveable { mutableStateOf("") }
    var challengeId by rememberSaveable { mutableStateOf("") }
    var restoredSession by remember { mutableStateOf(false) }
    var restoreChecked by remember { mutableStateOf(false) }
    var splashFinished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        restoredSession = try { auth.restoreSession() != null } catch (_: Exception) { false }
        restoreChecked = true
    }
    LaunchedEffect(splashFinished, restoreChecked) {
        if (splashFinished && restoreChecked) screen = if (restoredSession) Screen.Location else Screen.Welcome
    }
    Crossfade(targetState = screen, animationSpec = tween(400), label = "root") { current ->
        when (current) {
            Screen.Splash -> SplashScreen(onFinished = { splashFinished = true })
            Screen.Welcome -> WelcomeScreen(
                onGetStarted = { screen = Screen.Phone },
                onHaveAccount = { screen = Screen.Phone },
                onHelp = { /* Help & Support screen */ },
            )
            Screen.Phone -> {
                BackHandler { screen = Screen.Welcome }
                PhoneScreen(
                    onBack = { screen = Screen.Welcome },
                    onRequestCode = { auth.requestOtp(it) },
                    onCodeSent = { number, challenge -> phone = number; challengeId = challenge; screen = Screen.Otp },
                )
            }
            Screen.Otp -> {
                BackHandler { screen = Screen.Phone }
                OtpScreen(
                    phone = phone,
                    initialChallengeId = challengeId,
                    onBack = { screen = Screen.Phone },
                    onRequestCode = { auth.requestOtp(it) },
                    onVerify = { id, code -> auth.verifyOtp(id, code) },
                    onVerified = { screen = Screen.Details },
                )
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
