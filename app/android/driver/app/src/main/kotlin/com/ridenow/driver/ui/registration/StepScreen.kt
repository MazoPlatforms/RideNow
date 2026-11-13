package com.ridenow.driver.ui.registration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.RideButton
import com.ridenow.driver.ui.components.StepHeader
import com.ridenow.driver.ui.theme.RideTheme

/** One registration step: header, its form, and a pinned Continue button. */
@Composable
fun StepScreen(
    step: Int, total: Int, tag: String, title: String, subtitle: String,
    onBack: () -> Unit, enabled: Boolean, onContinue: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().formBottomInsets().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(step, total, tag, title, subtitle, onBack)
            content()
            Spacer(Modifier.height(12.dp))
        }
        RideButton(stringResource(R.string.id_continue), onClick = onContinue, enabled = enabled, modifier = Modifier.padding(vertical = 12.dp))
    }
}

/**
 * Bottom padding for form screens. Uses the larger of the navigation bar and the keyboard, instead of adding both
 * (which pushed the form and Continue button too high while the keyboard was open).
 */
fun Modifier.formBottomInsets(): Modifier =
    windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
