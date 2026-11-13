package com.ridenow.driver.ui.registration

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.LogoMark
import com.ridenow.driver.ui.components.Orb
import com.ridenow.driver.ui.components.RideButton
import com.ridenow.driver.ui.components.RideButtonStyle
import com.ridenow.driver.ui.components.RideIcon
import com.ridenow.driver.ui.components.RideIcons
import com.ridenow.driver.ui.components.StepHeader
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance
import com.ridenow.driver.ui.util.hasLocationPermission

/**
 * Standalone screen: ask for location "all the time" so requests and trip safety work in the background.
 * Android needs two asks: foreground location first, then background. On Android 11+ the second
 * ask opens the app's location settings, where the driver picks "Allow all the time".
 */
@Composable
fun BackgroundLocationScreen(onBack: () -> Unit, onAllowed: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }

    fun hasBackground() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted || hasBackground()) onAllowed() else denied = true
    }
    fun askBackground() { if (hasBackground()) onAllowed() else backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocationPermission(context)) askBackground() else denied = true
    }
    fun allow() {
        if (denied) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            denied = false
        } else if (hasLocationPermission(context)) askBackground()
        else foregroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(0, 0, stringResource(R.string.bg_tag), stringResource(R.string.bg_title), stringResource(R.string.bg_subtitle), onBack)

            Box(Modifier.fillMaxWidth().padding(top = 20.dp).entrance(200), contentAlignment = Alignment.Center) {
                Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(c.ring1, size.minDimension / 2, style = Stroke(1.dp.toPx()))
                        drawCircle(c.ring2, size.minDimension * 0.36f, style = Stroke(1.dp.toPx()))
                    }
                    Orb(56.dp) { RideIcon(RideIcons.Pin, c.onFill, size = 28.dp) }
                }
            }

            Column(Modifier.padding(top = 14.dp).entrance(260)) {
                HorizontalDivider(color = c.line)
                Feature(RideIcons.Pin, R.string.bg_feature_requests, R.string.bg_feature_requests_sub)
                Feature(RideIcons.Car, R.string.bg_feature_trips, R.string.bg_feature_trips_sub)
                Feature(RideIcons.Clock, R.string.bg_feature_stop, R.string.bg_feature_stop_sub)
            }

            // What the always-on notification looks like while online.
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field)
                    .border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp).entrance(320),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogoMark(28.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.bg_notif_title), color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.bg_notif_text), color = c.muted, fontSize = 12.sp)
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
            }
            if (denied) Text(stringResource(R.string.bg_denied), color = c.warn, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
        }

        Column(Modifier.padding(top = 10.dp, bottom = 12.dp).entrance(380)) {
            Text(
                stringResource(R.string.bg_note), color = c.muted, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            RideButton(stringResource(if (denied) R.string.bg_open_settings else R.string.bg_allow), onClick = { allow() })
            RideButton(stringResource(R.string.bg_not_now), onSkip, style = RideButtonStyle.Text, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Feature(icon: List<String>, titleRes: Int, subRes: Int) {
    val c = RideTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(icon, c.accent)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(titleRes), color = c.fg, fontSize = 14.sp)
                Text(stringResource(subRes), color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        HorizontalDivider(color = c.line)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BackgroundLocationDarkPreview() = RideNowTheme(darkTheme = true) { BackgroundLocationScreen({}, {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BackgroundLocationLightPreview() = RideNowTheme(darkTheme = false) { BackgroundLocationScreen({}, {}, {}) }
