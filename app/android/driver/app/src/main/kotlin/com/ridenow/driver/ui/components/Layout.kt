package com.ridenow.driver.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** Applies a horizontal offset relative to the current layout direction. */
@Composable
fun Modifier.offsetByStartDirection(x: Dp): Modifier {
    val direction = LocalLayoutDirection.current
    return offset(x = if (direction == LayoutDirection.Rtl) -x else x)
}
