package com.ridenow.driver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ridenow.driver.ui.theme.RideTheme

/** Same look as [RideTextField] but shows a value chosen elsewhere (a date picker, a detected place). */
@Composable
fun RideSelectField(
    label: String, value: String, placeholder: String, icon: List<String>,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val base = modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(c.field).border(1.dp, c.border, shape)
    Row(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RideIcon(icon, c.accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                value.ifEmpty { placeholder }, color = if (value.isEmpty()) c.muted else c.fg, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
