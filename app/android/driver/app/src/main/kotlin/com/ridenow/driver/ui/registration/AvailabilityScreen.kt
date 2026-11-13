package com.ridenow.driver.ui.registration

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Step 9 of 11: full or part time, days of the week, usual hours and a shift reminder. */
@Composable
fun AvailabilityScreen(onBack: () -> Unit, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var workType by rememberSaveable { mutableIntStateOf(0) } // 0 full time, 1 part time
    var days by rememberSaveable { mutableIntStateOf(0b0011111) } // bit 0 = Monday
    var start by rememberSaveable { mutableIntStateOf(7 * 60) }
    var end by rememberSaveable { mutableIntStateOf(19 * 60) }
    var remind by rememberSaveable { mutableStateOf(true) }
    var picking by rememberSaveable { mutableIntStateOf(0) } // 0 none, 1 start, 2 end

    val sameTime = start == end
    val canContinue = days != 0 && !sameTime

    val summary = stringResource(R.string.review_avail, stringResource(if (workType == 0) R.string.avail_full else R.string.avail_part), Integer.bitCount(days), formatMinutes(context, start), formatMinutes(context, end))
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(9, 11, stringResource(R.string.avail_tag), stringResource(R.string.avail_title), stringResource(R.string.avail_subtitle), onBack)
            Column(Modifier.padding(top = 20.dp).entrance(200), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Label(R.string.avail_work_type)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WorkTypeCard(RideIcons.Clock, R.string.avail_full, R.string.avail_full_sub, workType == 0, { workType = 0 }, Modifier.weight(1f))
                        WorkTypeCard(RideIcons.Calendar, R.string.avail_part, R.string.avail_part_sub, workType == 1, { workType = 1 }, Modifier.weight(1f))
                    }
                }
                Column {
                    Label(R.string.avail_days)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 0 until 7) {
                            val day = DayOfWeek.of(i + 1)
                            DayChip(
                                day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                                days and (1 shl i) != 0, { days = days xor (1 shl i) },
                            )
                        }
                    }
                    if (days == 0) Text(stringResource(R.string.avail_days_error), color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RideSelectField(stringResource(R.string.avail_start), formatMinutes(context, start), "", RideIcons.Clock, Modifier.weight(1f), onClick = { picking = 1 })
                        RideSelectField(stringResource(R.string.avail_end), formatMinutes(context, end), "", RideIcons.Clock, Modifier.weight(1f), onClick = { picking = 2 })
                    }
                    if (sameTime) Text(stringResource(R.string.avail_time_error), color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                RideSwitchRow(stringResource(R.string.avail_remind), remind, { remind = it })
            }
            Spacer(Modifier.height(12.dp))
        }
        RideButton(stringResource(R.string.id_continue), onClick = { onDone(summary) }, enabled = canContinue, modifier = Modifier.padding(vertical = 12.dp))
    }

    when (picking) {
        1 -> RideTimePickerDialog(start, { picking = 0 }, { start = it; picking = 0 })
        2 -> RideTimePickerDialog(end, { picking = 0 }, { end = it; picking = 0 })
    }
}

@Composable
private fun Label(res: Int) =
    Text(stringResource(res), color = RideTheme.colors.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))

@Composable
private fun WorkTypeCard(icon: List<String>, titleRes: Int, subRes: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 92.dp).clip(shape).background(if (selected) c.accent.copy(alpha = 0.14f) else c.field)
            .border(1.dp, if (selected) c.accent else c.border, shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        RideIcon(icon, c.accent, size = 22.dp)
        Text(stringResource(titleRes), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text(stringResource(subRes), color = c.muted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun DayChip(letter: String, fullName: String, selected: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (selected) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.border, CircleShape)
            .selectable(selected, role = Role.Checkbox, onClick = onClick).semantics { contentDescription = fullName },
        contentAlignment = Alignment.Center,
    ) { Text(letter, color = if (selected) c.accent else c.fg, fontSize = 14.sp) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AvailabilityDarkPreview() = RideNowTheme(darkTheme = true) { AvailabilityScreen({}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AvailabilityLightPreview() = RideNowTheme(darkTheme = false) { AvailabilityScreen({}, {}) }
