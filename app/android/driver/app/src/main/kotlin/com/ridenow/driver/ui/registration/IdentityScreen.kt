package com.ridenow.driver.ui.registration

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance
import kotlinx.coroutines.launch

private val NicRegex = Regex("^(\\d{9}[VvXx]|\\d{12})$")

/**
 * Step 4 of 11: NIC number, optional issue and expiry dates, and photos of both sides.
 * Accepts the old 9-digit + V/X format and the new 12-digit format.
 */
@Composable
fun IdentityScreen(onBack: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var nic by rememberSaveable { mutableStateOf("") }
    var issue by rememberSaveable { mutableStateOf<Long?>(null) }
    var expiry by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by rememberSaveable { mutableStateOf(0) } // 0 none, 1 issue, 2 expiry
    var nicFocused by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf<Bitmap?>(null) }
    var back by remember { mutableStateOf<Bitmap?>(null) }

    val nicOk = NicRegex.matches(nic.trim())
    val showNicError = nic.isNotBlank() && !nicOk && !nicFocused
    val orderOk = issue == null || expiry == null || expiry!! > issue!!
    val canContinue = nicOk && orderOk && front != null && back != null
    val today = remember { todayUtcMillis() }

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(4, 11, stringResource(R.string.id_tag), stringResource(R.string.id_title), stringResource(R.string.id_subtitle), onBack)
            Column(Modifier.padding(top = 20.dp).entrance(200), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RideTextField(
                    nic, { nic = it.filter { ch -> ch.isLetterOrDigit() }.take(12) }, stringResource(R.string.id_nic), RideIcons.Card,
                    isError = showNicError,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    onFocusChange = { nicFocused = it },
                )
                if (showNicError) Text(stringResource(R.string.id_nic_error), color = c.danger, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RideSelectField(
                        stringResource(R.string.id_issue), issue?.let { formatPickedDate(it) }.orEmpty(), stringResource(R.string.id_date_placeholder),
                        RideIcons.Calendar, Modifier.weight(1f), onClick = { focus.clearFocus(); picking = 1 },
                    )
                    RideSelectField(
                        stringResource(R.string.id_expiry), expiry?.let { formatPickedDate(it) }.orEmpty(), stringResource(R.string.id_date_placeholder),
                        RideIcons.Calendar, Modifier.weight(1f), onClick = { focus.clearFocus(); picking = 2 },
                    )
                }
                if (!orderOk) Text(stringResource(R.string.id_date_order_error), color = c.danger, fontSize = 13.sp)
                DocPhotoPair(
                    stringResource(R.string.id_front), stringResource(R.string.id_back), DocKind.IdFront, DocKind.IdBack,
                    front, back, { front = it }, { back = it }, Modifier.padding(top = 4.dp),
                )
                Text(stringResource(R.string.id_tip), color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
        RideButton(stringResource(R.string.id_continue), onClick = onDone, enabled = canContinue, modifier = Modifier.padding(vertical = 12.dp))
    }

    when (picking) {
        1 -> RideDatePickerDialog(issue, null, today, { picking = 0 }, { issue = it; picking = 0 }, onClear = { issue = null; picking = 0 })
        2 -> RideDatePickerDialog(expiry, today, null, { picking = 0 }, { expiry = it; picking = 0 }, onClear = { expiry = null; picking = 0 })
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun IdentityDarkPreview() = RideNowTheme(darkTheme = true) { IdentityScreen({}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun IdentityLightPreview() = RideNowTheme(darkTheme = false) { IdentityScreen({}, {}) }
