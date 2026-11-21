package com.ridenow.driver.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.auth.SecondFactorDialog
import com.ridenow.driver.ui.auth.VerificationCodeField
import com.ridenow.driver.ui.auth.secureFactorDialog
import org.json.JSONObject
import com.ridenow.driver.ui.auth.PhoneAuthApi
import com.ridenow.driver.ui.auth.PhoneAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.rememberBoolPref

@Composable
private fun Intro(text: String) {
    Text(text, color = RideTheme.colors.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
}

/** Devices where the driver is signed in, with a way to log out of the others. */
@Composable
fun ManageDevicesScreen(onBack: () -> Unit, onSessionExpired: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.sec_devices_title)
    val failure = stringResource(R.string.device_load_failed)
    val logout = stringResource(R.string.sec_log_out)
    val logoutAll = stringResource(R.string.sec_log_out_all)
    val loggedOutAll = stringResource(R.string.sec_logged_out_others)
    val unknown = stringResource(R.string.device_unknown)
    val riderApp = stringResource(R.string.device_rider_app)
    val driverApp = stringResource(R.string.device_driver_app)
    var devices by remember { mutableStateOf<List<PhoneAuthApi.SignedInDevice>?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val others = devices.orEmpty().filterNot { it.current }
    val current = devices.orEmpty().firstOrNull { it.current }

    fun label(device: PhoneAuthApi.SignedInDevice) = device.name ?: unknown

    fun details(device: PhoneAuthApi.SignedInDevice): String {
        val appName = when (device.app) {
            "RIDER" -> riderApp
            "DRIVER" -> driverApp
            else -> unknown
        }
        val time = runCatching {
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(java.util.Date.from(java.time.Instant.parse(device.updatedAt)))
        }.getOrDefault("")
        return listOf(appName, device.id.takeLast(6).uppercase(), time).filter { it.isNotBlank() }.joinToString(" · ")
    }

    LaunchedEffect(reload) {
        busy = true
        error = false
        try {
            devices = PhoneAuthApi.listDevices(context, register = devices == null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            if ((exception as? PhoneAuthException)?.status == 401) onSessionExpired() else error = true
        } finally {
            busy = false
        }
    }

    SubScreen(title, onBack, modifier, bottom = {
        if (others.isNotEmpty() && !busy) SoftButton(logoutAll, { confirm = "" }, danger = true)
    }) {
        Intro(stringResource(R.string.sec_devices_sub))
        if (busy && devices == null) Text(stringResource(R.string.device_loading), color = c.muted, modifier = Modifier.padding(top = 12.dp))
        if (error) {
            Text(failure, color = c.danger, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { reload++ }, enabled = !busy) { Text(stringResource(R.string.device_retry)) }
        }
        current?.let { device ->
            SectionTitle(stringResource(R.string.sec_this_device))
            GroupCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Phone, c.accent)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(label(device), color = c.fg, fontSize = 15.sp)
                        Text(details(device), color = c.muted, fontSize = 12.sp)
                    }
                    Text(stringResource(R.string.sec_current), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (devices != null) {
            SectionTitle(stringResource(R.string.sec_other_devices))
            if (others.isEmpty()) Text(stringResource(R.string.sec_no_other_devices), color = c.muted, fontSize = 14.sp)
            else GroupCard {
                others.forEachIndexed { i, device ->
                    if (i > 0) RowDivider()
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RideIcon(RideIcons.Phone, c.muted)
                        Column(Modifier.padding(start = 14.dp).weight(1f).padding(vertical = 12.dp)) {
                            Text(label(device), color = c.fg, fontSize = 15.sp)
                            Text(details(device), color = c.muted, fontSize = 12.sp)
                        }
                        val description = stringResource(R.string.device_logout_named, label(device))
                        Box(
                            Modifier.heightIn(min = 48.dp).clickable(enabled = !busy, role = Role.Button) { confirm = device.id }
                                .padding(horizontal = 16.dp).semantics { contentDescription = description },
                            contentAlignment = Alignment.Center,
                        ) { Text(logout, color = c.danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    confirm?.let { target ->
        val message = if (target.isEmpty()) logoutAll else
            stringResource(R.string.device_logout_named, others.firstOrNull { it.id == target }?.let(::label) ?: unknown)
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(message) },
            text = { Text(stringResource(R.string.device_logout_confirmation)) },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.device_cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    busy = true
                    error = false
                    scope.launch {
                        try {
                            PhoneAuthApi.revokeDevice(context, target.ifEmpty { null })
                            devices = devices?.filter { it.current || (target.isNotEmpty() && it.id != target) }
                            if (target.isEmpty()) toast(context, loggedOutAll)
                            devices = PhoneAuthApi.listDevices(context)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (exception: Exception) {
                            if ((exception as? PhoneAuthException)?.status == 401) onSessionExpired() else error = true
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(logout) }
            },
        )
    }
}

@Composable
private fun MethodRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = c.fg, fontSize = 15.sp); Text(sub, color = c.muted, fontSize = 12.sp) }
        RadioDot(selected)
    }
}

@Composable
fun TwoStepScreen(phone: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.sec_2fa_title)
    val failureText = stringResource(R.string.two_step_settings_error)
    val refresh = stringResource(R.string.device_retry)
    val recoveryTitle = stringResource(R.string.sec_recovery)
    val backupText = stringResource(R.string.sec_recovery_sub)
    val copyText = stringResource(R.string.sec_copy)
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var recoveryRemaining by remember { mutableIntStateOf(0) }
    var setupSecret by remember { mutableStateOf<String?>(null) }
    var recoveryCodes by remember { mutableStateOf<List<String>?>(null) }
    var operation by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    fun load() {
        if (busy) return
        busy = true
        error = false
        scope.launch {
            try {
                val info = PhoneAuthApi.twoStepSettings(context)
                enabled = info.getBoolean("enabled")
                recoveryRemaining = info.getInt("recoveryRemaining")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = true
            } finally {
                busy = false
            }
        }
    }

    fun beginSetup() {
        if (busy) return
        busy = true
        error = false
        scope.launch {
            try {
                setupSecret = PhoneAuthApi.twoStepSettings(context, "/setup").getString("secret")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = true
            } finally {
                busy = false
            }
        }
    }

    fun showCodes(response: JSONObject) {
        val codes = response.getJSONArray("recoveryCodes")
        recoveryCodes = List(codes.length()) { codes.getString(it) }
        recoveryRemaining = codes.length()
    }

    LaunchedEffect(Unit) { load() }
    SubScreen(title, onBack, modifier) {
        Intro(stringResource(R.string.sec_2fa_sub))
        if (error) {
            Text(failureText, color = c.danger, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = ::load, enabled = !busy) { Text(refresh) }
        }
        if (enabled == null && !error) Text(stringResource(R.string.two_step_loading), color = c.muted, modifier = Modifier.padding(top = 12.dp))
        enabled?.let { on ->
            GroupCard(Modifier.padding(top = 16.dp)) {
                ToggleRow(RideIcons.Shield, title, on, { if (!busy) { if (on) operation = "/disable" else beginSetup() } }, subtitle = stringResource(R.string.two_step_optional))
            }
        }
        SectionTitle(stringResource(R.string.sec_method))
        GroupCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.two_step_whatsapp, phone), color = c.fg)
                Text(stringResource(R.string.two_step_authenticator_method), color = c.muted)
            }
        }
        SectionTitle(stringResource(R.string.sec_backup))
        GroupCard {
            if (enabled == true && !busy) NavRow(RideIcons.Doc, recoveryTitle, { operation = "/recovery" },
                subtitle = stringResource(R.string.two_step_remaining, recoveryRemaining))
            else Text(backupText, color = c.muted, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
    setupSecret?.let { secret ->
        var whatsappCode by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(
            properties = secureFactorDialog,
            onDismissRequest = { if (!busy) setupSecret = null },
            title = { Text(stringResource(R.string.two_step_setup_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.two_step_setup_help))
                    SelectionContainer { Text(secret) }
                    TextButton(enabled = !busy, onClick = { copySecurityText(context, secret) }) { Text(copyText) }
                    VerificationCodeField(whatsappCode, { whatsappCode = it }, stringResource(R.string.two_step_whatsapp_code), !busy, true)
                    VerificationCodeField(code, { code = it }, stringResource(R.string.two_step_app_code), !busy, true)
                    if (invalid) Text(stringResource(R.string.two_step_setup_error), color = c.danger)
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { setupSecret = null }) { Text(stringResource(R.string.two_step_cancel)) } },
            confirmButton = {
                TextButton(enabled = !busy && whatsappCode.length == 6 && code.length == 6, onClick = {
                    busy = true
                    invalid = false
                    scope.launch {
                        try {
                            val response = PhoneAuthApi.twoStepSettings(context, "/enable",
                                JSONObject().put("whatsappCode", whatsappCode).put("code", code))
                            enabled = response.getBoolean("enabled")
                            setupSecret = null
                            showCodes(response)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            invalid = true
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.two_step_confirm)) }
            },
        )
    }
    operation?.let { action ->
        val operationTitle = stringResource(if (action == "/disable") R.string.two_step_disable else R.string.two_step_replace)
        SecondFactorDialog(operationTitle, onDismiss = { operation = null }) { code ->
            val response = PhoneAuthApi.twoStepSettings(context, action, JSONObject().put("code", code))
            enabled = response.getBoolean("enabled")
            operation = null
            if (enabled == true) showCodes(response) else recoveryRemaining = 0
        }
    }
    recoveryCodes?.let { codes ->
        AlertDialog(
            properties = secureFactorDialog,
            onDismissRequest = { recoveryCodes = null },
            title = { Text(recoveryTitle) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.two_step_save_codes))
                    Text(codes.joinToString("\n"))
                }
            },
            confirmButton = { TextButton(onClick = { copySecurityText(context, codes.joinToString("\n")) }) { Text(copyText) } },
            dismissButton = { TextButton(onClick = { recoveryCodes = null }) { Text(stringResource(R.string.sec_close)) } },
        )
    }
}

private fun copySecurityText(context: android.content.Context, value: String) {
    val clip = android.content.ClipData.newPlainText("RideNow", value)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/** Choose what to include and request a copy of the driver's data. */
@Composable
fun DownloadDataScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val items = listOf(
        Triple(RideIcons.Person, R.string.settings_profile, R.string.sec_dl_profile_sub), Triple(RideIcons.Clock, R.string.sec_dl_rides, R.string.sec_dl_rides_sub),
        Triple(RideIcons.Wallet, R.string.sec_dl_earnings, R.string.sec_dl_earnings_sub), Triple(RideIcons.Car, R.string.sec_dl_vehicle, R.string.sec_dl_vehicle_sub),
    )
    val on = remember { mutableStateListOf(true, true, true, true) }
    var requested by rememberSaveable { mutableStateOf(false) }
    if (requested) {
        SubScreen(str(R.string.priv_download), onBack, modifier, bottom = { RideButton(str(R.string.sec_done), onBack) }) {
            InfoBanner(RideIcons.Check, str(R.string.sec_request_received), str(R.string.sec_notify_ready), Modifier.padding(top = 8.dp), Tone.Good)
        }
        return
    }
    SubScreen(str(R.string.priv_download), onBack, modifier, bottom = {
        RideButton(str(R.string.sec_request_copy), { requested = true }, enabled = on.any { it })
        Text(str(R.string.sec_notify_ready), color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }) {
        Intro(str(R.string.sec_dl_sub))
        SectionTitle(str(R.string.sec_include))
        GroupCard(Modifier.padding(bottom = 16.dp)) {
            items.forEachIndexed { i, (icon, t, s) -> if (i > 0) RowDivider(); ToggleRow(icon, str(t), on[i], { on[i] = it }, subtitle = str(s)) }
        }
    }
}
