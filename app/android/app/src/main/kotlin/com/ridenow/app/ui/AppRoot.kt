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
import com.ridenow.app.ui.booking.ConfirmScreen
import com.ridenow.app.ui.booking.FindingDriverScreen
import com.ridenow.app.ui.booking.RideOptionsScreen
import com.ridenow.app.ui.booking.ScheduleScreen
import com.ridenow.app.ui.home.HomeScreen
import com.ridenow.app.ui.permissions.LocationPermissionScreen
import com.ridenow.app.ui.permissions.NotificationPermissionScreen
import com.ridenow.app.ui.search.SearchScreen
import com.ridenow.app.ui.splash.SplashScreen
import com.ridenow.app.ui.post.RateDriverScreen
import com.ridenow.app.ui.post.ReportProblemScreen
import com.ridenow.app.ui.post.RideCompleteScreen
import com.ridenow.app.ui.booking.Vehicles
import com.ridenow.app.ui.booking.fareFor
import com.ridenow.app.ui.trip.TripScreen
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.welcome.WelcomeScreen

enum class Screen { Splash, Welcome, Phone, Otp, Details, Location, Notifications, Home, Search, Options, Schedule, Confirm, Finding, Trip, Complete, Rate, Report, Stub }

@Composable
fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.Splash) }
    var phone by rememberSaveable { mutableStateOf("") }
    var stub by rememberSaveable { mutableStateOf("") }
    var dest by rememberSaveable { mutableStateOf("") }
    var isReturn by rememberSaveable { mutableStateOf(false) }
    var vehicle by rememberSaveable { mutableStateOf(1) }
    var rating by rememberSaveable { mutableStateOf(0) }
    var scheduled by rememberSaveable { mutableStateOf<String?>(null) }
    var scheduleFrom by rememberSaveable { mutableStateOf(Screen.Home) }
    var stubBack by rememberSaveable { mutableStateOf(Screen.Home) }
    val open = { label: String -> stubBack = screen; stub = label; screen = Screen.Stub }
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
                LocationPermissionScreen(onDone = { screen = Screen.Notifications })
            }
            Screen.Notifications -> {
                BackHandler { screen = Screen.Location }
                NotificationPermissionScreen(onDone = { screen = Screen.Home })
            }
            Screen.Home -> HomeScreen(
                onMenu = { open("Menu drawer") }, onNotifications = { open("Notifications") },
                onSearch = { screen = Screen.Search }, onLater = { scheduleFrom = Screen.Home; screen = Screen.Schedule },
                onEditPickup = { open("Change pickup") }, onSaved = { open("$it address") }, onRecent = { dest = it; isReturn = false; screen = Screen.Options },
            )
            Screen.Search -> {
                BackHandler { screen = Screen.Home }
                SearchScreen(
                    onBack = { scheduled = null; screen = Screen.Home },
                    scheduled = scheduled,
                    onPlace = { place, ret -> dest = place.name; isReturn = ret; screen = Screen.Options },
                    onFriend = { open("Book for a friend") }, onEditPickup = { open("Change pickup") },
                    onSaved = { open("Saved addresses") }, onMap = { open("Set location on map") }, onAdd = { open("Add $it address") },
                )
            }
            Screen.Options -> {
                BackHandler { screen = Screen.Search }
                RideOptionsScreen(
                    destination = dest, returnTrip = isReturn, initial = vehicle, onBack = { screen = Screen.Search },
                    onContinue = { vehicle = it; screen = Screen.Confirm }, onLater = { vehicle = it; scheduleFrom = Screen.Options; screen = Screen.Schedule },
                )
            }
            Screen.Confirm -> {
                BackHandler { screen = Screen.Options }
                ConfirmScreen(
                    dest, vehicle, isReturn, whenLabel = scheduled, onBack = { screen = Screen.Options },
                    onBook = { if (scheduled != null) open("Scheduled ride details") else screen = Screen.Finding },
                )
            }
            Screen.Schedule -> {
                BackHandler { screen = scheduleFrom }
                ScheduleScreen(onBack = { screen = scheduleFrom }, onContinue = { label ->
                    scheduled = label
                    screen = if (scheduleFrom == Screen.Options) Screen.Confirm else Screen.Search
                })
            }
            Screen.Finding -> FindingDriverScreen(dest, vehicle, isReturn, onFound = { screen = Screen.Trip }, onCancel = { scheduled = null; screen = Screen.Home })
            Screen.Trip -> {
                BackHandler { }
                TripScreen(
                    dest, vehicle, onCancelled = { scheduled = null; screen = Screen.Home }, onComplete = { rating = 0; screen = Screen.Complete },
                    onShare = { open("Share live trip") }, onSafety = { open("Safety center") },
                )
            }
            Screen.Complete -> {
                BackHandler { }
                RideCompleteScreen(
                    dest, Vehicles[vehicle], Vehicles[vehicle].fareFor(isReturn), rating,
                    onReceipt = { open("Receipt PDF") }, onRate = { screen = Screen.Rate }, onReport = { screen = Screen.Report },
                    onDone = { scheduled = null; rating = 0; screen = Screen.Home },
                )
            }
            Screen.Rate -> {
                BackHandler { screen = Screen.Complete }
                RateDriverScreen(onBack = { screen = Screen.Complete }, onSubmit = { rating = it; screen = Screen.Complete })
            }
            Screen.Report -> ReportProblemScreen(onBack = { screen = Screen.Complete }, onDone = { screen = Screen.Complete })
            Screen.Stub -> {
                BackHandler { screen = stubBack }
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
