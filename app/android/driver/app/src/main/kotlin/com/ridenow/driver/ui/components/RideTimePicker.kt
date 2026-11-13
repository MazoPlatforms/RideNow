package com.ridenow.driver.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ridenow.driver.R
import com.ridenow.driver.ui.theme.RideTheme
import java.util.Calendar

/** "7:00 AM" or "07:00", following the phone's 12 or 24 hour setting. [minutes] counts from midnight. */
fun formatMinutes(context: Context, minutes: Int): String {
    val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60) }
    return DateFormat.getTimeFormat(context).format(cal.time)
}

/** Clock dialog for choosing a time of day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideTimePickerDialog(minutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val state = rememberTimePickerState(minutes / 60, minutes % 60, DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.bg,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.dialog_ok), color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel), color = c.fg) } },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.field, selectorColor = c.fill, containerColor = c.bg,
                    clockDialSelectedContentColor = c.onFill, clockDialUnselectedContentColor = c.fg,
                    periodSelectorSelectedContainerColor = c.accent.copy(alpha = 0.14f), periodSelectorSelectedContentColor = c.accent,
                    timeSelectorSelectedContainerColor = c.accent.copy(alpha = 0.14f), timeSelectorSelectedContentColor = c.accent,
                ),
            )
        },
    )
}
