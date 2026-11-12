package com.ridenow.driver.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ridenow.driver.R
import com.ridenow.driver.ui.theme.RideTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val Utc = TimeZone.getTimeZone("UTC")

/** "dd / MM / yyyy" for a UTC-midnight date picked in the date picker. */
fun formatPickedDate(utcMillis: Long): String =
    SimpleDateFormat("dd / MM / yyyy", Locale.getDefault()).apply { timeZone = Utc }.format(Date(utcMillis))

/** Today at UTC midnight, the same basis the Material date picker uses. */
fun todayUtcMillis(): Long {
    val now = Calendar.getInstance()
    return Calendar.getInstance(Utc).apply {
        clear(); set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Date picker limited to [minMillis]..[maxMillis] (both optional). [onClear] adds a Clear button for optional dates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDatePickerDialog(
    initial: Long?, minMillis: Long?, maxMillis: Long?, onDismiss: () -> Unit, onConfirm: (Long) -> Unit, onClear: (() -> Unit)? = null,
) {
    val c = RideTheme.colors
    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial, initialDisplayedMonthMillis = initial ?: maxMillis ?: minMillis,
        yearRange = (year - 100)..(year + 30),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = (minMillis == null || utcTimeMillis >= minMillis) && (maxMillis == null || utcTimeMillis <= maxMillis)
        },
    )
    val colors = DatePickerDefaults.colors(containerColor = c.bg)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let(onConfirm) }, enabled = state.selectedDateMillis != null) { Text(stringResource(R.string.dialog_ok), color = c.accent) } },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = onClear) { Text(stringResource(R.string.dialog_clear), color = c.muted) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel), color = c.fg) }
            }
        },
        colors = colors,
    ) { DatePicker(state = state, colors = colors) }
}
