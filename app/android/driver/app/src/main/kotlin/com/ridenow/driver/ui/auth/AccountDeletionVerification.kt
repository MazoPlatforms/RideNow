package com.ridenow.driver.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ridenow.driver.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AccountDeletionVerification(phone: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var challengeId by remember { mutableStateOf("") }
    var twoStepRequired by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val title = stringResource(R.string.account_delete_verify_title)
    val canConfirm = !busy && challengeId.isNotEmpty() && code.length == 6 &&
        (!twoStepRequired || factor.matches(Regex("^(?:[0-9]{6}|[a-fA-F0-9]{24}|(?:[a-fA-F0-9]{4}-){5}[a-fA-F0-9]{4})$")))

    fun showError(exception: Exception) {
        error = when ((exception as? PhoneAuthException)?.code) {
            "ACCOUNT_DELETION_RIDES_PENDING" -> R.string.account_delete_rides_pending
            "ACCOUNT_DELETION_PAYMENTS_PENDING" -> R.string.account_delete_payments_pending
            "ACCOUNT_DELETION_CODE_INVALID" -> R.string.account_delete_code_invalid
            "OTP_RATE_LIMIT", "RATE_LIMITED" -> R.string.account_delete_wait
            else -> R.string.account_delete_error
        }
    }

    fun send() {
        if (busy || seconds > 0) return
        busy = true
        error = null
        scope.launch {
            try {
                val response = PhoneAuthApi.requestAccountDeletion(context)
                challengeId = response.getString("challengeId")
                twoStepRequired = response.getBoolean("twoStepRequired")
                code = ""
                factor = ""
                seconds = 60
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                showError(exception)
            } finally {
                busy = false
            }
        }
    }

    fun confirm() {
        if (!canConfirm) return
        busy = true
        error = null
        scope.launch {
            try {
                PhoneAuthApi.confirmAccountDeletion(context, challengeId, code, factor.takeIf { twoStepRequired })
                code = ""
                factor = ""
                onDeleted()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                showError(exception)
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(challengeId) {
        if (challengeId.isNotEmpty()) while (seconds > 0) { delay(1_000); seconds-- }
    }
    val back = { if (!busy) onBack() }
    val fields: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.account_delete_whatsapp, phone))
            if (challengeId.isNotEmpty()) {
                VerificationCodeField(code, { code = it }, stringResource(R.string.account_delete_otp), !busy, true)
                if (twoStepRequired) VerificationCodeField(factor, { factor = it },
                    stringResource(R.string.two_step_factor), !busy, false)
            }
            TextButton(onClick = ::send, enabled = !busy && seconds == 0) {
                Text(if (seconds > 0) stringResource(R.string.account_delete_resend_wait, seconds)
                    else stringResource(if (challengeId.isEmpty()) R.string.account_delete_send else R.string.account_delete_resend))
            }
            if (busy) Text(stringResource(R.string.account_delete_working))
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
    }
    AlertDialog(
        properties = secureFactorDialog,
        onDismissRequest = back,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { fields() } },
        confirmButton = { TextButton(onClick = ::confirm, enabled = canConfirm) { Text(stringResource(R.string.del_button)) } },
        dismissButton = { TextButton(onClick = back, enabled = !busy) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
