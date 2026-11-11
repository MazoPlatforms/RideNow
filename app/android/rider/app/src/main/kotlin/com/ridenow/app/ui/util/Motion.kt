package com.ridenow.app.ui.util

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** True when the user has turned system animations off. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Fade and rise into place after [delayMs]. Does nothing when motion is reduced. */
@Composable
fun Modifier.entrance(delayMs: Int = 0, offsetDp: Int = 18): Modifier {
    val reduce = rememberReducedMotion()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            delay(delayMs.toLong())
            progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetDp.dp.toPx()
    }
}
