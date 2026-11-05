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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.ridenow.app.auth.AuthRepository
import com.ridenow.app.auth.RiderProfile
import com.ridenow.app.ui.auth.DetailsScreen
import com.ridenow.app.ui.auth.OtpScreen
import com.ridenow.app.ui.auth.PhoneScreen
import com.ridenow.app.ui.home.HomeScreen
import com.ridenow.app.ui.permissions.LocationPermissionScreen
import com.ridenow.app.ui.permissions.NotificationPermissionScreen
import com.ridenow.app.ui.search.SearchScreen
import com.ridenow.app.ui.splash.SplashScreen
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.welcome.WelcomeScreen

enum class Screen { Splash, Welcome, Phone, Otp, Details, Location, Notifications, Home, Search, Stub }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val auth = remember(context) { AuthRepository(context) }
    var screen by rememberSaveable { mutableStateOf(Screen.Splash) }
    var phone by rememberSaveable { mutableStateOf("") }
    var challengeId by rememberSaveable { mutableStateOf("") }
    var stub by rememberSaveable { mutableStateOf("") }
    var verifiedProfile by remember { mutableStateOf<RiderProfile?>(null) }
    var restoredDestination by remember { mutableStateOf(Screen.Welcome) }
    var restoreChecked by remember { mutableStateOf(false) }
    var splashFinished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            if (auth.restoreSession() != null) {
                val profile = auth.profile()
                restoredDestination = if (profile.name.isBlank()) Screen.Details else Screen.Home
            }
        } catch (_: Exception) {
            restoredDestination = Screen.Welcome
        }
        restoreChecked = true
    }
    LaunchedEffect(splashFinished, restoreChecked) {
        if (splashFinished && restoreChecked) screen = restoredDestination
    }
    val open = { label: String -> stub = label; screen = Screen.Stub }
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
                    onVerify = { id, code ->
                        auth.verifyOtp(id, code)
                        verifiedProfile = auth.profile()
                    },
                    onVerified = {
                        screen = if (verifiedProfile?.name?.isNotBlank() == true) Screen.Home else Screen.Details
                    },
                )
            }
            Screen.Details -> {
                BackHandler { screen = Screen.Otp }
                DetailsScreen(
                    onBack = { screen = Screen.Otp },
                    onDone = { name, email, photo ->
                        auth.saveProfile(name, email, photo)
                        screen = Screen.Location
                    },
                )
            }
            Screen.Location -> {
                BackHandler { screen = Screen.Details }
                LocationPermissionScreen(onDone = { screen = Screen.Notifications })
            }
            Screen.Notifications -> {
                BackHandler { screen = Screen.Location }
                NotificationPermissionScreen(onDone = { screen = Screen.Home })
            }
            Screen.Home -> HomeScreen(
                onMenu = { open("Menu drawer") }, onNotifications = { open("Notifications") },
                onSearch = { screen = Screen.Search }, onLater = { open("Schedule a ride") },
                onEditPickup = { open("Change pickup") }, onSaved = { open("$it address") }, onRecent = { open("Ride options to $it") },
            )
            Screen.Search -> {
                BackHandler { screen = Screen.Home }
                SearchScreen(
                    onBack = { screen = Screen.Home },
                    onPlace = { place, ret -> open(if (ret) "Ride options (return) to ${place.name}" else "Ride options to ${place.name}") },
                    onFriend = { open("Book for a friend") }, onEditPickup = { open("Change pickup") },
                    onSaved = { open("Saved addresses") }, onMap = { open("Set location on map") }, onAdd = { open("Add $it address") },
                )
            }
            Screen.Stub -> {
                BackHandler { screen = if (stub.startsWith("Ride options") || stub.startsWith("Add ") || stub in listOf("Saved addresses", "Set location on map", "Book for a friend")) Screen.Search else Screen.Home }
                Placeholder("$stub screen goes here")
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
