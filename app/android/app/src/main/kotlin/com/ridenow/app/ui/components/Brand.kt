package com.ridenow.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.ridenow.app.R
import com.ridenow.app.ui.theme.RideTheme

/** The R-pin symbol. White and yellow on dark, black and yellow on light. */
@Composable
fun LogoMark(width: Dp, modifier: Modifier = Modifier) {
    val dark = RideTheme.colors.isDark
    Image(
        painter = painterResource(if (dark) R.drawable.ridenow_mark_dark else R.drawable.ridenow_mark_light),
        contentDescription = null,
        modifier = modifier.width(width).aspectRatio(if (dark) 950f / 817f else 949f / 812f),
        contentScale = ContentScale.Fit,
    )
}

/** The "RideNow" wordmark, sized by height. */
@Composable
fun Wordmark(height: Dp, modifier: Modifier = Modifier) {
    val dark = RideTheme.colors.isDark
    Image(
        painter = painterResource(if (dark) R.drawable.ridenow_wordmark_dark else R.drawable.ridenow_wordmark_light),
        contentDescription = "RideNow",
        modifier = modifier.height(height).aspectRatio(if (dark) 929f / 178f else 1057f / 183f),
        contentScale = ContentScale.Fit,
    )
}
