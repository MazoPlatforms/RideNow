package com.ridenow.driver.ui.registration

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.Orb
import com.ridenow.driver.ui.components.RideButton
import com.ridenow.driver.ui.components.RideButtonStyle
import com.ridenow.driver.ui.components.RideIcon
import com.ridenow.driver.ui.components.RideIcons
import com.ridenow.driver.ui.components.StepHeader
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

/**
 * Standalone screen: ask to show notifications so trip requests reach the driver with the app closed.
 * Android 13+ shows a system prompt; before that notifications are on by default.
 * [onDone] receives whether notifications ended up allowed.
 */
@Composable
fun NotificationPermissionScreen(onBack: () -> Unit, onDone: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    fun enabled() = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted || enabled()) onDone(true) else denied = true
    }
    fun allow() {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || enabled() -> onDone(enabled())
            denied -> {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                denied = false
            }
            else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(0, 0, "", stringResource(R.string.notif_title), stringResource(R.string.notif_subtitle), onBack)
            Box(Modifier.fillMaxWidth().padding(top = 20.dp).entrance(200), contentAlignment = Alignment.Center) {
                Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(c.ring1, size.minDimension / 2, style = Stroke(1.dp.toPx()))
                        drawCircle(c.ring2, size.minDimension * 0.36f, style = Stroke(1.dp.toPx()))
                    }
                    Orb(56.dp) { RideIcon(RideIcons.Bell, c.onFill, size = 28.dp) }
                }
            }
            Column(Modifier.padding(top = 14.dp).entrance(260)) {
                HorizontalDivider(color = c.line)
                Feature(RideIcons.Car, R.string.notif_feature_requests, R.string.notif_feature_requests_sub)
                Feature(RideIcons.Msg, R.string.notif_feature_messages, R.string.notif_feature_messages_sub)
                Feature(RideIcons.Shield, R.string.notif_feature_earnings, R.string.notif_feature_earnings_sub)
            }
            if (denied) Text(stringResource(R.string.notif_denied), color = c.warn, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Column(Modifier.padding(top = 10.dp, bottom = 12.dp).entrance(320)) {
            Text(stringResource(R.string.notif_note), color = c.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), textAlign = TextAlign.Center)
            RideButton(stringResource(if (denied) R.string.bg_open_settings else R.string.notif_allow), onClick = { allow() })
            RideButton(stringResource(R.string.notif_not_now), { onDone(enabled()) }, style = RideButtonStyle.Text, modifier = Modifier.padding(top = 4.dp))
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
private fun NotificationDarkPreview() = RideNowTheme(darkTheme = true) { NotificationPermissionScreen({}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NotificationLightPreview() = RideNowTheme(darkTheme = false) { NotificationPermissionScreen({}, {}) }
