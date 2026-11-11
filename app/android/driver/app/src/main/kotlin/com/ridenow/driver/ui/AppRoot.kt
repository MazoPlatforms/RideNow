package com.ridenow.driver.ui

import com.ridenow.driver.R
import androidx.compose.ui.res.stringResource

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.ridenow.driver.ui.intro.IntroScreen
import com.ridenow.driver.ui.splash.SplashScreen
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.theme.ThemeMode

enum class Screen { Splash, Intro, Welcome }

/** Survives activity recreation within one app process, so the splash does not replay on rotation or theme changes. */
private object SessionFlags { var splashDone = false }

@Composable
fun AppRoot(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.Splash) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ridenowdriver", 0) }
    LaunchedEffect(Unit) {
        if (SessionFlags.splashDone && screen == Screen.Splash) {
            screen = if (prefs.getBoolean("intro_seen", false)) Screen.Welcome else Screen.Intro
        }
    }
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = screen, animationSpec = tween(400), label = "root") { current ->
            when (current) {
                Screen.Splash -> SplashScreen(onFinished = { SessionFlags.splashDone = true; screen = if (prefs.getBoolean("intro_seen", false)) Screen.Welcome else Screen.Intro })
                Screen.Intro -> IntroScreen(onDone = { prefs.edit().putBoolean("intro_seen", true).commit(); screen = Screen.Welcome })
                Screen.Welcome -> {
                    // Placeholder until the Welcome screen is built.
                    val c = RideTheme.colors
                    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.welcome_placeholder), color = c.muted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
