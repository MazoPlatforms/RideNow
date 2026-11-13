package com.ridenow.driver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

/**
 * Header shared by the registration steps: back button, logo, segmented progress bar,
 * "STEP n OF total" label, title and subtitle. Pass step = 0 for standalone screens (terms and permissions),
 * which show only the back button, logo, title and subtitle.
 */
@Composable
fun StepHeader(
    step: Int, total: Int, section: String, title: String, subtitle: String,
    onBack: () -> Unit, modifier: Modifier = Modifier,
) {
    val textBack = stringResource(R.string.search_back)
    val c = RideTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(48.dp).entrance(0), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.offsetByStartDirection((-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = textBack },
                contentAlignment = Alignment.Center,
            ) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(36.dp))
        }
        if (step > 0) {
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().entrance(60), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total) { i ->
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i < step) c.fill else c.line))
            }
        }
        Text(
            stringResource(R.string.step_tag, step, total, section), color = c.muted, fontSize = 12.sp, letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 14.dp).entrance(100),
        )
        }
        Column(Modifier.padding(top = if (step > 0) 12.dp else 20.dp).entrance(140)) {
            Text(title, color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, lineHeight = 33.sp, modifier = Modifier.semantics { heading() })
            Text(subtitle, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
