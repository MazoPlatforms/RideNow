package com.ridenow.driver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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

/** Single-choice pill. Used in a Row where each chip takes `Modifier.weight(1f)`. */
@Composable
fun RideChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.heightIn(min = 48.dp).clip(shape).background(if (selected) c.accent.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.border, shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) c.accent else c.fg, fontSize = 14.sp) }
}
