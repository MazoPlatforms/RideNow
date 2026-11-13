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

private val LicenseRegex = Regex("^[A-Za-z0-9]{6,12}$")
private val Classes = listOf("A1" to R.string.class_a1, "A" to R.string.class_a, "B1" to R.string.class_b1, "B" to R.string.class_b, "C1" to R.string.class_c1, "D1" to R.string.class_d1)

/** Form section of the merged step: 3 of 9: license number, class picker, expiry date and photos of both sides. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseContent(onValid: (Boolean) -> Unit, onSummary: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var number by rememberSaveable { mutableStateOf("") }
    var cls by rememberSaveable { mutableStateOf<String?>(null) }
    var expiry by rememberSaveable { mutableStateOf<Long?>(null) }
    var showClass by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var numberFocused by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf<Bitmap?>(null) }
    var back by remember { mutableStateOf<Bitmap?>(null) }

    val numberOk = LicenseRegex.matches(number.trim())
    val showNumberError = number.isNotBlank() && !numberOk && !numberFocused
    val canContinue = numberOk && cls != null && expiry != null && front != null && back != null
    val today = remember { todayUtcMillis() }
    val classText = Classes.firstOrNull { it.first == cls }?.let { it.first + " \u00b7 " + stringResource(it.second) }.orEmpty()

    LaunchedEffect(canContinue) { onValid(canContinue) }
    LaunchedEffect(classText) { onSummary(classText) }
    Column(modifier) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RideTextField(
                    number, { number = it.filter { ch -> ch.isLetterOrDigit() }.take(12) }, stringResource(R.string.lic_number), RideIcons.Card,
                    isError = showNumberError,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    onFocusChange = { numberFocused = it },
                )
                if (showNumberError) Text(stringResource(R.string.lic_number_error), color = c.danger, fontSize = 13.sp)
                RideSelectField(stringResource(R.string.lic_class), classText, stringResource(R.string.lic_class_placeholder), RideIcons.ChevronDown, onClick = { focus.clearFocus(); showClass = true })
                RideSelectField(stringResource(R.string.lic_expiry), expiry?.let { formatPickedDate(it) }.orEmpty(), stringResource(R.string.id_date_placeholder), RideIcons.Calendar, onClick = { focus.clearFocus(); showDate = true })
                DocPhotoPair(
                    stringResource(R.string.lic_front), stringResource(R.string.lic_back), DocKind.LicenseFront, DocKind.LicenseBack,
                    front, back, { front = it }, { back = it }, Modifier.padding(top = 4.dp),
                )
            }
    }

    if (showDate) RideDatePickerDialog(expiry, today, null, { showDate = false }, { expiry = it; showDate = false })
    if (showClass) ClassSheet(cls, onSelect = { cls = it; showClass = false }, onDismiss = { showClass = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassSheet(selected: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val c = RideTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = c.bg, contentColor = c.fg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Text(stringResource(R.string.lic_class), color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            Classes.forEach { (code, label) ->
                val isSelected = code == selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(isSelected, role = Role.RadioButton) { scope.launch { state.hide() }.invokeOnCompletion { onSelect(code) } }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(code, color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(48.dp))
                    Text(stringResource(label), color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (isSelected) RideIcon(RideIcons.Check, c.accent)
                }
                HorizontalDivider(color = c.line)
            }
        }
    }
}


/** Step 3 of 9: driving license. [onDone] gets the license summary. */
@Composable
fun LicenseScreen(onBack: () -> Unit, onDone: (String) -> Unit) {
    var ok by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var summary by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    StepScreen(3, 9, stringResource(R.string.lic_tag), stringResource(R.string.lic_title), stringResource(R.string.lic_subtitle), onBack, ok, { onDone(summary) }) {
        LicenseContent(onValid = { ok = it }, onSummary = { summary = it })
    }
}
