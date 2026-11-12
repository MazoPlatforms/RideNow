package com.ridenow.driver.ui.registration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.RideButton
import com.ridenow.driver.ui.components.RideButtonStyle
import com.ridenow.driver.ui.components.RideCheckbox
import com.ridenow.driver.ui.components.RideIcon
import com.ridenow.driver.ui.components.RideIcons
import com.ridenow.driver.ui.components.StepHeader
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

/**
 * Step 1 of 11: accept the terms and agree to continuous location verification.
 * Continue stays disabled until both boxes are ticked.
 */
@Composable
fun TermsScreen(
    onBack: () -> Unit, onAccept: () -> Unit, onDecline: () -> Unit,
    onReadTerms: () -> Unit = {}, onReadPrivacy: () -> Unit = {}, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    var terms by rememberSaveable { mutableStateOf(false) }
    var location by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        StepHeader(1, 11, stringResource(R.string.terms_tag), stringResource(R.string.terms_title), stringResource(R.string.terms_subtitle), onBack)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 18.dp).entrance(200)) {
            HorizontalDivider(color = c.line)
            DocRow(RideIcons.Doc, R.string.terms_tos, R.string.terms_tos_sub, onReadTerms)
            DocRow(RideIcons.Shield, R.string.terms_privacy, R.string.terms_privacy_sub, onReadPrivacy)
            DocRow(RideIcons.Pin, R.string.terms_location, R.string.terms_location_sub, null)
            Column(Modifier.padding(top = 14.dp)) {
                RideCheckbox(terms, stringResource(R.string.terms_accept_check), { terms = it })
                RideCheckbox(location, stringResource(R.string.terms_location_check), { location = it })
            }
        }

        Column(Modifier.padding(top = 12.dp, bottom = 12.dp).entrance(260)) {
            RideButton(stringResource(R.string.terms_continue), onAccept, enabled = terms && location)
            RideButton(stringResource(R.string.terms_decline), onDecline, style = RideButtonStyle.Text, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DocRow(icon: List<String>, titleRes: Int, subRes: Int, onRead: (() -> Unit)?) {
    val c = RideTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(icon, c.accent)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(stringResource(titleRes), color = c.fg, fontSize = 14.sp)
                Text(stringResource(subRes), color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (onRead != null) {
                Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onRead).padding(start = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.terms_read), color = c.accent, fontSize = 14.sp)
                }
            }
        }
        HorizontalDivider(color = c.line)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TermsDarkPreview() = RideNowTheme(darkTheme = true) { TermsScreen({}, {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TermsLightPreview() = RideNowTheme(darkTheme = false) { TermsScreen({}, {}, {}) }
