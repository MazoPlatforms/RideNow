package com.ridenow.driver.ui

import com.ridenow.driver.R
import androidx.compose.ui.res.stringResource

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.ui.auth.OtpScreen
import com.ridenow.driver.ui.auth.PhoneScreen
import com.ridenow.driver.ui.auth.formatPhoneForDisplay
import com.ridenow.driver.ui.intro.IntroScreen
import com.ridenow.driver.ui.registration.BackgroundLocationScreen
import com.ridenow.driver.ui.registration.IdentityScreen
import com.ridenow.driver.ui.registration.LicenseScreen
import com.ridenow.driver.ui.registration.PersonalInfoScreen
import com.ridenow.driver.ui.splash.SplashScreen
import com.ridenow.driver.ui.registration.TermsScreen
import com.ridenow.driver.ui.welcome.WelcomeScreen
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.theme.ThemeMode

enum class Screen { Splash, Intro, Welcome, Phone, Otp, Terms, Background, Personal, Identity, License, Vehicle }

/** Survives activity recreation within one app process, so the splash does not replay on rotation or theme changes. */
private object SessionFlags { var splashDone = false }

@Composable
fun AppRoot(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.Splash) }
    var phone by rememberSaveable { mutableStateOf("") }
    val shownPhone = formatPhoneForDisplay(phone.ifBlank { "+1 555 012 3456" })
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
                Screen.Welcome -> WelcomeScreen(onGetStarted = { screen = Screen.Phone }, onHaveAccount = { screen = Screen.Phone }, onHelp = { })
                Screen.Phone -> {
                    BackHandler { screen = Screen.Welcome }
                    PhoneScreen(onBack = { screen = Screen.Welcome }, onCodeSent = { phone = it; screen = Screen.Otp })
                }
                Screen.Otp -> {
                    BackHandler { screen = Screen.Phone }
                    OtpScreen(phone = shownPhone, onBack = { screen = Screen.Phone }, onVerified = { screen = Screen.Terms })
                }
                Screen.Terms -> {
                    BackHandler { screen = Screen.Otp }
                    TermsScreen(
                        onBack = { screen = Screen.Otp },
                        onAccept = { prefs.edit().putBoolean("terms_accepted", true).apply(); screen = Screen.Background },
                        onDecline = { screen = Screen.Welcome },
                    )
                }
                Screen.Background -> {
                    BackHandler { screen = Screen.Terms }
                    BackgroundLocationScreen(onBack = { screen = Screen.Terms }, onAllowed = { screen = Screen.Personal }, onSkip = { screen = Screen.Personal })
                }
                Screen.Personal -> {
                    BackHandler { screen = Screen.Background }
                    PersonalInfoScreen(onBack = { screen = Screen.Background }, onDone = { screen = Screen.Identity })
                }
                Screen.Identity -> {
                    BackHandler { screen = Screen.Personal }
                    IdentityScreen(onBack = { screen = Screen.Personal }, onDone = { screen = Screen.License })
                }
                Screen.License -> {
                    BackHandler { screen = Screen.Identity }
                    LicenseScreen(onBack = { screen = Screen.Identity }, onDone = { screen = Screen.Vehicle })
                }
                Screen.Vehicle -> {
                    // Placeholder until the Vehicle details screen is built.
                    BackHandler { screen = Screen.License }
                    val c = RideTheme.colors
                    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.next_placeholder), color = c.muted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
