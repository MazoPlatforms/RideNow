package com.ridenow.driver.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.ui.theme.RideTheme

enum class RideButtonStyle { Primary, Outline, Text }

@Composable
fun RideButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RideButtonStyle = RideButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = RideTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val haptics = LocalHapticFeedback.current
    val primary = style == RideButtonStyle.Primary
    val outline = style == RideButtonStyle.Outline
    Box(
        modifier.fillMaxWidth().height(if (primary || outline) 56.dp else 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(28.dp))
            .background(if (primary) (if (enabled) c.fill else c.line) else Color.Transparent)
            .then(if (outline) Modifier.border(1.dp, c.border, RoundedCornerShape(28.dp)) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = c.onFill, strokeWidth = 2.5.dp)
        } else {
            Text(
                text,
                color = if (primary) (if (enabled) c.onFill else c.muted) else c.fg,
                fontSize = if (primary || outline) 16.sp else 15.sp,
                fontWeight = if (primary || outline) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
