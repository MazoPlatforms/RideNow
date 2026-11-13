package com.ridenow.driver.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.ui.theme.RideTheme

/** Heading for one part of a merged step. Later sections get a divider above so the parts read as separate blocks. */
@Composable
fun FormSection(title: String, first: Boolean = false, hint: String? = null, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Column(modifier.fillMaxWidth().padding(top = if (first) 22.dp else 28.dp)) {
        if (!first) HorizontalDivider(color = c.line)
        Text(
            title, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(top = if (first) 0.dp else 20.dp).semantics { heading() },
        )
        if (hint != null) Text(hint, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
