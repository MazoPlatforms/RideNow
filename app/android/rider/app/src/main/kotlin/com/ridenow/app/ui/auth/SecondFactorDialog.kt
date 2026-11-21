package com.ridenow.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.ridenow.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val secureFactorDialog = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)

@Composable
internal fun VerificationCodeField(value: String, onChange: (String) -> Unit, label: String,
    enabled: Boolean, digitsOnly: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            onChange(if (digitsOnly) next.filter { it in '0'..'9' }.take(6)
                else next.filter { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' || it == '-' }.take(29))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (digitsOnly) KeyboardType.NumberPassword else KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
    )
}

@Composable
internal fun SecondFactorDialog(title: String, onDismiss: () -> Unit, onSubmit: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val submitReady = remember(code) { code.matches(Regex("^(?:[0-9]{6}|[a-fA-F0-9]{24}|(?:[a-fA-F0-9]{4}-){5}[a-fA-F0-9]{4})$")) }
    AlertDialog(
        properties = secureFactorDialog,
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.two_step_factor_help))
                VerificationCodeField(code, { code = it }, stringResource(R.string.two_step_factor), !busy, false)
                if (error) Text(stringResource(R.string.two_step_factor_error))
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.two_step_cancel)) } },
        confirmButton = {
            TextButton(enabled = !busy && submitReady, onClick = {
                busy = true
                error = false
                scope.launch {
                    try {
                        onSubmit(code)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        code = ""
                        error = true
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.two_step_confirm)) }
        },
    )
}
