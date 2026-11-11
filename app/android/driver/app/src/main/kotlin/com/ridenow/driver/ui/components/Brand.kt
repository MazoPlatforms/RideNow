package com.ridenow.driver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.theme.RideTheme

/** The RideNow orbit mark (ring with a centre dot) in the theme accent. */
@Composable
fun LogoMark(width: Dp, modifier: Modifier = Modifier) = RideMark(RideTheme.colors.accent, modifier, width)

/** Mint gradient orb that hosts [content]. */
@Composable
fun Orb(size: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(
        modifier.size(size).clip(CircleShape).background(
            Brush.radialGradient(0f to Color(0xFFB9FFEA), 0.45f to Color(0xFF7CF5D0), 1f to Color(0xFF2E9C80)),
        ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The orb with the orbit mark in ink, used on the splash. */
@Composable
fun OrbMark(size: Dp, modifier: Modifier = Modifier) =
    Orb(size, modifier) { RideMark(RideTheme.colors.onFill, size = size * 0.5f) }

/** "RideNow Driver" in light weight with open tracking. [height] is the font size in dp. */
@Composable
fun Wordmark(height: Dp, modifier: Modifier = Modifier) =
    Text(
        stringResource(R.string.app_name), color = RideTheme.colors.fg, modifier = modifier,
        fontSize = height.value.sp, fontWeight = FontWeight.Light, letterSpacing = 0.03.em,
    )
