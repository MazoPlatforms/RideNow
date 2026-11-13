package com.ridenow.driver.ui.registration

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme

/** Step 5 of 6: availability and service area on one screen. [onDone] gets the availability and area summaries. */
@Composable
fun WorkPlanScreen(onBack: () -> Unit, onDone: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var availabilityOk by rememberSaveable { mutableStateOf(false) }
    var areaOk by rememberSaveable { mutableStateOf(false) }
    var availabilitySummary by rememberSaveable { mutableStateOf("") }
    var areaSummary by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(5, 6, stringResource(R.string.plan_tag), stringResource(R.string.plan_title), stringResource(R.string.plan_subtitle), onBack)
            FormSection(stringResource(R.string.plan_section_availability), first = true)
            AvailabilityContent(onValid = { availabilityOk = it }, onSummary = { availabilitySummary = it })
            FormSection(stringResource(R.string.plan_section_area))
            ServiceAreaContent(onValid = { areaOk = it }, onSummary = { areaSummary = it })
            Spacer(Modifier.height(12.dp))
        }
        RideButton(stringResource(R.string.id_continue), onClick = { onDone(availabilitySummary, areaSummary) }, enabled = availabilityOk && areaOk, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WorkPlanPreview() = RideNowTheme(darkTheme = true) { WorkPlanScreen({}, { _, _ -> }) }
