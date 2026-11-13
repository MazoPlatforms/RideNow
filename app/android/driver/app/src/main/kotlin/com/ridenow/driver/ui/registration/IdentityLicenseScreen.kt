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

/** Step 2 of 6: National Identity Card and driving license on one screen. [onDone] gets the license summary. */
@Composable
fun IdentityLicenseScreen(onBack: () -> Unit, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var idOk by rememberSaveable { mutableStateOf(false) }
    var licenseOk by rememberSaveable { mutableStateOf(false) }
    var summary by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(2, 6, stringResource(R.string.idl_tag), stringResource(R.string.idl_title), stringResource(R.string.idl_subtitle), onBack)
            FormSection(stringResource(R.string.idl_section_id), first = true)
            IdentityContent(onValid = { idOk = it })
            FormSection(stringResource(R.string.idl_section_license))
            LicenseContent(onValid = { licenseOk = it }, onSummary = { summary = it })
            Spacer(Modifier.height(12.dp))
        }
        RideButton(stringResource(R.string.id_continue), onClick = { onDone(summary) }, enabled = idOk && licenseOk, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun IdentityLicensePreview() = RideNowTheme(darkTheme = true) { IdentityLicenseScreen({}, {}) }
