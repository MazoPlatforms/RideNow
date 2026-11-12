package com.ridenow.driver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.ui.theme.RideTheme

/** Checkbox with a label. The whole row is the touch target (at least 48 dp tall). */
@Composable
fun RideCheckbox(checked: Boolean, text: String, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 1.dp).size(24.dp).clip(shape).background(if (checked) c.fill else Color.Transparent)
                .border(1.5.dp, if (checked) c.fill else c.border, shape),
            contentAlignment = Alignment.Center,
        ) { if (checked) RideIcon(RideIcons.Check, c.onFill, size = 16.dp) }
        Text(text, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 12.dp))
    }
}
