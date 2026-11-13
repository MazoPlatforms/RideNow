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

/** Step 4 of 6: vehicle documents (with expiry dates) and vehicle photos on one screen. [onDone] gets the photo summary. */
@Composable
fun VehicleMediaScreen(onBack: () -> Unit, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var docsOk by rememberSaveable { mutableStateOf(false) }
    var photosOk by rememberSaveable { mutableStateOf(false) }
    var summary by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(4, 6, stringResource(R.string.vmedia_tag), stringResource(R.string.vmedia_title), stringResource(R.string.vmedia_subtitle), onBack)
            FormSection(stringResource(R.string.vmedia_section_docs), first = true)
            VehicleDocsContent(onValid = { docsOk = it })
            FormSection(stringResource(R.string.vmedia_section_photos), hint = stringResource(R.string.vphoto_subtitle))
            VehiclePhotosContent(onValid = { photosOk = it }, onSummary = { summary = it })
            Spacer(Modifier.height(12.dp))
        }
        RideButton(stringResource(R.string.id_continue), onClick = { onDone(summary) }, enabled = docsOk && photosOk, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VehicleMediaPreview() = RideNowTheme(darkTheme = false) { VehicleMediaScreen({}, {}) }
