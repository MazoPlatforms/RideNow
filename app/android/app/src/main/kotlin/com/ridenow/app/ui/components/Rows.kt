package com.ridenow.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.ridenow.app.ui.theme.RideTheme

/** Back button, large title and optional subtitle used at the top of list-style screens. */
@Composable
fun ScreenTop(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    val c = RideTheme.colors
    Column(modifier) {
        Box(Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
            RideIcon(RideIcons.Back, c.fg, size = 22.dp)
        }
        Text(title, color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = RideTheme.colors
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)), content = content)
}

@Composable
fun RowDivider() = HorizontalDivider(color = RideTheme.colors.line)

/** Icon, title, optional subtitle and a chevron (or [trailing]). */
@Composable
fun NavRow(
    icon: List<String>, title: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null,
    danger: Boolean = false, trailing: (@Composable () -> Unit)? = null,
) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(icon, if (danger) c.danger else c.accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) c.danger else c.fg, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (trailing != null) trailing() else RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = RideTheme.colors.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = modifier.padding(top = 20.dp, bottom = 8.dp))
}
