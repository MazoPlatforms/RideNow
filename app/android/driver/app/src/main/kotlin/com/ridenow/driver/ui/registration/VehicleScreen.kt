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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch
import java.util.Calendar

private val PlateRegex = Regex("^[A-Z0-9][A-Z0-9 -]{3,11}$")
private val Types = listOf(R.string.veh_type_car, R.string.veh_type_van, R.string.veh_type_three, R.string.veh_type_bike)
private val Swatches = listOf(
    R.string.color_white to Color(0xFFFFFFFF), R.string.color_black to Color(0xFF111111), R.string.color_silver to Color(0xFFC0C4CC),
    R.string.color_grey to Color(0xFF6B7280), R.string.color_red to Color(0xFFD32F2F), R.string.color_blue to Color(0xFF1E63D6),
    R.string.color_green to Color(0xFF2E7D32), R.string.color_yellow to Color(0xFFFBC02D), R.string.color_orange to Color(0xFFF57C00),
    R.string.color_brown to Color(0xFF6D4C41), R.string.color_other to Color.Transparent,
)

/** Step 3 of 6: vehicle type, make, model, year, color and registration number. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(onBack: () -> Unit, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val focus = LocalFocusManager.current
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val minYear = 1990
    val maxYear = thisYear + 1
    var type by rememberSaveable { mutableStateOf<Int?>(null) }
    var make by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf<Int?>(null) }
    var plate by rememberSaveable { mutableStateOf("") }
    var showColor by rememberSaveable { mutableStateOf(false) }
    var yearFocused by remember { mutableStateOf(false) }
    var plateFocused by remember { mutableStateOf(false) }

    val yearValue = year.toIntOrNull()
    val yearOk = yearValue != null && yearValue in minYear..maxYear
    val plateOk = PlateRegex.matches(plate.trim())
    val showYearError = year.isNotBlank() && !yearOk && !yearFocused
    val showPlateError = plate.isNotBlank() && !plateOk && !plateFocused
    val canContinue = type != null && make.trim().length >= 2 && model.trim().isNotEmpty() && yearOk && color != null && plateOk

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(3, 6, stringResource(R.string.veh_tag), stringResource(R.string.veh_title), stringResource(R.string.veh_subtitle), onBack)
            Column(Modifier.padding(top = 20.dp).entrance(200), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(stringResource(R.string.veh_type), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Types.chunked(2).forEach { rowTypes ->
                        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTypes.forEach { res -> RideChip(stringResource(res), type == res, { type = res }, Modifier.weight(1f)) }
                        }
                    }
                }
                RideTextField(
                    make, { make = it.take(30) }, stringResource(R.string.veh_make), RideIcons.Car,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                RideTextField(
                    model, { model = it.take(30) }, stringResource(R.string.veh_model), RideIcons.Car,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                RideTextField(
                    year, { year = it.filter { ch -> ch.isDigit() }.take(4) }, stringResource(R.string.veh_year), RideIcons.Calendar,
                    isError = showYearError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    onFocusChange = { yearFocused = it },
                )
                if (showYearError) Text(stringResource(R.string.veh_year_error, minYear, maxYear), color = c.danger, fontSize = 13.sp)
                RideSelectField(
                    stringResource(R.string.veh_color), color?.let { stringResource(it) }.orEmpty(), stringResource(R.string.veh_color_placeholder),
                    RideIcons.ChevronDown, onClick = { focus.clearFocus(); showColor = true },
                )
                RideTextField(
                    plate, { plate = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' || ch == ' ' }.take(12) }, stringResource(R.string.veh_plate), RideIcons.Card,
                    isError = showPlateError,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    onFocusChange = { plateFocused = it },
                )
                if (showPlateError) Text(stringResource(R.string.veh_plate_error), color = c.danger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
        RideButton(stringResource(R.string.id_continue), onClick = { onDone("${make.trim()} ${model.trim()} \u00b7 $year") }, enabled = canContinue, modifier = Modifier.padding(vertical = 12.dp))
    }

    if (showColor) ColorSheet(color, onSelect = { color = it; showColor = false }, onDismiss = { showColor = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSheet(selected: Int?, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = RideTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = c.bg, contentColor = c.fg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.veh_color), color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            Swatches.forEach { (label, swatch) ->
                val isSelected = label == selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(isSelected, role = Role.RadioButton) { scope.launch { state.hide() }.invokeOnCompletion { onSelect(label) } }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(swatch).border(1.dp, c.border, CircleShape))
                    Text(stringResource(label), color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    if (isSelected) RideIcon(RideIcons.Check, c.accent)
                }
                HorizontalDivider(color = c.line)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VehicleDarkPreview() = RideNowTheme(darkTheme = true) { VehicleScreen({}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun VehicleLightPreview() = RideNowTheme(darkTheme = false) { VehicleScreen({}, {}) }
