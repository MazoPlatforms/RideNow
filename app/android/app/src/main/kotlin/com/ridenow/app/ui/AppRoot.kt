package com.ridenow.app.ui

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

@Composable
fun AppRoot() {
    var splashDone by rememberSaveable { mutableStateOf(false) }
    Crossfade(targetState = splashDone, animationSpec = tween(400), label = "root") { done ->
        if (!done) SplashScreen(onFinished = { splashDone = true }) else NextScreenPlaceholder()
    }
}

/** Replace with the Welcome screen once it is built. */
@Composable
private fun NextScreenPlaceholder() {
    val c = RideTheme.colors
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Text("Welcome screen goes here", color = c.muted, fontSize = 14.sp)
    }
}
