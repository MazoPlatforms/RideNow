package com.ridenow.driver.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.ridenow.driver.R
import com.ridenow.driver.ui.theme.RideTheme

/** The RideNow Driver symbol (R, pin and driver). White and yellow on dark, black and yellow on light. */
@Composable
fun LogoMark(width: Dp, modifier: Modifier = Modifier) =
    Image(
        painter = painterResource(if (RideTheme.colors.isDark) R.drawable.ridenow_mark_dark else R.drawable.ridenow_mark_light),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(width),
    )

/** "RideNow Driver" wordmark, text only (no symbol). [height] is the height of the whole block. */
@Composable
fun Wordmark(height: Dp, modifier: Modifier = Modifier) =
    Image(
        painter = painterResource(if (RideTheme.colors.isDark) R.drawable.ridenow_wordmark_dark else R.drawable.ridenow_wordmark_light),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height).aspectRatio(1016f / 296f),
    )

/** Brand-yellow gradient orb that hosts [content]. */
@Composable
fun Orb(size: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(
        modifier.size(size).clip(CircleShape).background(
            Brush.radialGradient(0f to Color(0xFFFFE27A), 0.45f to Color(0xFFFEC604), 1f to Color(0xFFB88A00)),
        ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
