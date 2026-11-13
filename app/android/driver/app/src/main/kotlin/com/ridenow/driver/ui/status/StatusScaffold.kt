package com.ridenow.driver.ui.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.LogoMark
import com.ridenow.driver.ui.components.Orb
import com.ridenow.driver.ui.components.RideButton
import com.ridenow.driver.ui.components.RideButtonStyle
import com.ridenow.driver.ui.components.RideIcon
import com.ridenow.driver.ui.components.RideIcons
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

enum class StatusTone { Accent, Warn, Danger }

/** Shared layout of the application status screens: logo, ringed icon, title, rows and two buttons. */
@Composable
fun StatusScaffold(
    tone: StatusTone, icon: List<String>, title: String, subtitle: String,
    primary: String, onPrimary: () -> Unit, secondary: String?, onSecondary: () -> Unit,
    modifier: Modifier = Modifier, pill: String? = null, rows: @Composable ColumnScope.() -> Unit,
) {
    val c = RideTheme.colors
    val toneColor = when (tone) { StatusTone.Accent -> c.accent; StatusTone.Warn -> c.warn; StatusTone.Danger -> c.danger }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp).entrance(0), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(30.dp)
            Text(stringResource(R.string.app_name), color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().padding(top = 16.dp).entrance(80), contentAlignment = Alignment.Center) {
                Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(c.ring1, size.minDimension / 2, style = Stroke(1.dp.toPx()))
                        drawCircle(c.ring2, size.minDimension * 0.375f, style = Stroke(1.dp.toPx()))
                    }
                    if (tone == StatusTone.Accent) Orb(80.dp) { RideIcon(icon, c.onFill, size = 36.dp) }
                    else Box(Modifier.size(80.dp).clip(CircleShape).background(toneColor.copy(alpha = 0.14f)).border(1.5.dp, toneColor, CircleShape), contentAlignment = Alignment.Center) {
                        RideIcon(icon, toneColor, size = 36.dp)
                    }
                }
            }
            Column(Modifier.padding(top = 12.dp).entrance(160)) {
                if (pill != null) Text(
                    pill, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 10.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
                )
                Text(title, color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Light, lineHeight = 35.sp, modifier = Modifier.semantics { heading() })
                Text(subtitle, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Column(Modifier.padding(top = 18.dp).entrance(240)) {
                HorizontalDivider(color = c.line)
                rows()
            }
        }
        Column(Modifier.padding(top = 10.dp, bottom = 12.dp).entrance(300)) {
            RideButton(primary, onPrimary)
            if (secondary != null) RideButton(secondary, onSecondary, style = RideButtonStyle.Text, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** One line in a status list: icon, title, detail and an optional action link. */
@Composable
fun StatusRow(icon: List<String>, title: String, detail: String, tint: androidx.compose.ui.graphics.Color, action: String? = null, onAction: () -> Unit = {}) {
    val c = RideTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(icon, tint, size = 20.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, color = c.fg, fontSize = 14.sp)
                Text(detail, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (action != null) Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onAction).padding(start = 12.dp), contentAlignment = Alignment.Center) {
                Text(action, color = c.accent, fontSize = 14.sp)
            }
        }
        HorizontalDivider(color = c.line)
    }
}
