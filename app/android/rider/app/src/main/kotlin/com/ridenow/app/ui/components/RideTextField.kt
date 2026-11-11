package com.ridenow.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.theme.RideTheme

/** Field with a leading icon and a small label above the value. */
@Composable
fun RideTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: List<String>,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val c = RideTheme.colors
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(when { isError -> c.danger; focused -> c.accent; else -> c.border }, label = "border")
    Row(
        modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(c.field)
            .border(1.dp, border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RideIcon(icon, c.accent)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, color = c.muted, fontSize = 11.sp)
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp).onFocusChanged { focused = it.isFocused; onFocusChange(it.isFocused) },
            )
        }
    }
}
