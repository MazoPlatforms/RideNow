package com.ridenow.driver.ui.registration

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

private val DigitsRegex = Regex("^\\d{6,20}$")

/**
 * Step 6 of 6: bank account for payouts. Can be skipped, but the driver cannot go online without it.
 * [onSaved] runs with valid details, [onSkip] when the driver chooses to add it later.
 */
@Composable
fun BankAccountScreen(onBack: () -> Unit, onSaved: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val focus = LocalFocusManager.current
    var holder by rememberSaveable { mutableStateOf("") }
    var bank by rememberSaveable { mutableStateOf("") }
    var branch by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var numberFocused by remember { mutableStateOf(false) }
    var confirmFocused by remember { mutableStateOf(false) }

    val numberOk = DigitsRegex.matches(number)
    val confirmOk = confirm == number
    val canSave = holder.trim().length >= 2 && bank.trim().length >= 2 && branch.trim().isNotEmpty() && numberOk && confirmOk && confirm.isNotEmpty()
    val showNumberError = number.isNotEmpty() && !numberOk && !numberFocused
    val showConfirmError = confirm.isNotEmpty() && !confirmOk && !confirmFocused

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(9, 9, stringResource(R.string.bank_tag), stringResource(R.string.bank_title), stringResource(R.string.bank_subtitle), onBack)
            Column(Modifier.padding(top = 20.dp).entrance(200), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.warn.copy(alpha = 0.12f)).border(1.dp, c.warn, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    RideIcon(RideIcons.Alert, c.warn, size = 18.dp)
                    Text(stringResource(R.string.bank_banner), color = c.fg, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 10.dp))
                }
                RideTextField(
                    holder, { holder = it.take(60) }, stringResource(R.string.bank_holder), RideIcons.Person,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                RideTextField(
                    bank, { bank = it.take(50) }, stringResource(R.string.bank_name), RideIcons.Work,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                RideTextField(
                    branch, { branch = it.take(50) }, stringResource(R.string.bank_branch), RideIcons.Pin,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                RideTextField(
                    number, { number = it.filter { ch -> ch.isDigit() }.take(20) }, stringResource(R.string.bank_number), RideIcons.Card,
                    isError = showNumberError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    onFocusChange = { numberFocused = it },
                )
                if (showNumberError) Text(stringResource(R.string.bank_number_error), color = c.danger, fontSize = 13.sp)
                RideTextField(
                    confirm, { confirm = it.filter { ch -> ch.isDigit() }.take(20) }, stringResource(R.string.bank_confirm), RideIcons.Card,
                    isError = showConfirmError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    onFocusChange = { confirmFocused = it },
                )
                if (showConfirmError) Text(stringResource(R.string.bank_confirm_error), color = c.danger, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.Top) {
                    RideIcon(RideIcons.Lock, c.muted, size = 16.dp)
                    Text(stringResource(R.string.bank_note), color = c.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.padding(vertical = 12.dp)) {
            RideButton(stringResource(R.string.bank_save), onSaved, enabled = canSave)
            RideButton(stringResource(R.string.bank_skip), onSkip, style = RideButtonStyle.Text, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BankDarkPreview() = RideNowTheme(darkTheme = true) { BankAccountScreen({}, {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BankLightPreview() = RideNowTheme(darkTheme = false) { BankAccountScreen({}, {}, {}) }
