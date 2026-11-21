package com.ridenow.app.ui.notifications

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ridenow.app.R
import com.ridenow.app.ui.auth.PhoneAuthApi
import com.ridenow.app.ui.theme.RideTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

internal class NotificationOptions(
    val values: JSONObject?, val busy: Boolean, val failed: Boolean,
    val reload: () -> Unit, val update: (String, Boolean) -> Unit,
) {
    fun isOn(name: String) = values?.optBoolean(name) == true
}

@Composable
internal fun rememberNotificationOptions(): NotificationOptions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun execute(action: suspend () -> JSONObject) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try { values = action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    fun reload() = execute { PhoneAuthApi.authorized(context, "GET", "/v1/me/notification-preferences?app=RIDER") { it } }
    LaunchedEffect(Unit) { reload() }
    return NotificationOptions(values, busy, failed, ::reload) { key, enabled ->
        if (values != null) execute {
            val body = JSONObject().put("app", "RIDER").put("key", key).put("enabled", enabled)
            PhoneAuthApi.authorized(context, "PATCH", "/v1/me/notification-preferences", body) { it }
        }
    }
}

@Composable
internal fun NotificationOptionsStatus(options: NotificationOptions) {
    if (options.failed) {
        Text(stringResource(R.string.saved_settings_failed), color = RideTheme.colors.danger)
        TextButton(onClick = options.reload, enabled = !options.busy) { Text(stringResource(R.string.device_retry)) }
    } else if (options.values == null) Text(stringResource(R.string.saved_settings_loading))
}
