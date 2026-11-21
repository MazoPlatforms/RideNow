package com.ridenow.driver.ui.profile

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.DriverStore
import com.ridenow.driver.ui.theme.RideTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.ridenow.driver.ui.auth.PhoneAuthApi
import com.ridenow.driver.ui.auth.PhoneAuthException
import com.ridenow.driver.ui.registration.pickedDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

private val LANGS = listOf("English", "Sinhala", "Tamil", "Arabic", "Spanish", "French", "German", "Japanese", "Hindi")

data class DriverProfileEdits(
    val name: String, val email: String, val address: String, val language: String,
    val dateOfBirth: String, val identityNumber: String, val photo: Bitmap?, val photoChanged: Boolean,
)

private fun String.compactPhone(): String = filter { it.isDigit() || it == '+' }

/** Edit profile: photo, personal details and extra details. Name and phone are required. */
@Composable
fun EditProfileScreen(
    store: DriverStore, name: String, phone: String, email: String, address: String,
    onBack: () -> Unit, onSave: suspend (DriverProfileEdits) -> Unit, onPhoneVerified: (String) -> Unit,
    onReviewIdentityChange: (String, String) -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failureText = stringResource(R.string.phone_change_failed)
    val waitText = stringResource(R.string.phone_change_wait)
    val saveError = stringResource(R.string.driver_profile_save_failed)
    val onReviewText = stringResource(R.string.driver_identity_review_required)
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var changeId by remember { mutableStateOf("") }
    var targetPhone by remember { mutableStateOf("") }
    var currentCode by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var resendIn by remember { mutableIntStateOf(0) }
    var replacement by remember { mutableStateOf<Bitmap?>(null) }
    var photoChanged by remember { mutableStateOf(false) }
    var n by rememberSaveable { mutableStateOf(name) }
    var p by rememberSaveable { mutableStateOf(phone) }
    var e by rememberSaveable { mutableStateOf(email) }
    var a by rememberSaveable { mutableStateOf(address) }
    var dob by rememberSaveable { mutableStateOf(store.dob) }
    var nic by rememberSaveable { mutableStateOf(store.nic) }
    var lang by rememberSaveable { mutableStateOf(store.prefLanguage) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var langSheet by remember { mutableStateOf(false) }
    val openPicker = rememberPhotoPicker(str(R.string.edit_photo_title)) { _, bmp ->
        replacement = bmp
        photoChanged = true
    }
    val visiblePhoto = if (photoChanged) replacement else store.photo
    LaunchedEffect(changeId) {
        if (changeId.isNotBlank()) {
            resendIn = 60
            while (resendIn > 0) { delay(1000); resendIn-- }
        }
    }
    fun save() {
        if (saving) return
        tried = true
        if (n.trim().length < 2 || p.count { it.isDigit() } < 7 ||
            (e.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(e.trim()).matches())) return
        failure = null
        if (dob != store.dob || nic.trim() != store.nic) {
            failure = onReviewText
            return
        }
        saving = true
        scope.launch {
            try {
                if (p.compactPhone() != phone.compactPhone()) {
                    val change = PhoneAuthApi.requestPhoneChange(context, p.trim())
                    changeId = change.getString("changeId")
                    targetPhone = change.getString("phone")
                } else {
                    onSave(DriverProfileEdits(n.trim(), e.trim(), a.trim(), lang, dob, nic.trim(), replacement, photoChanged))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = if ((error as? PhoneAuthException)?.status == 429) waitText else saveError
            } finally {
                saving = false
            }
        }
    }
    fun verifyChange(old: String, next: String) {
        if (saving || old.length != 6 || next.length != 6) return
        saving = true
        failure = null
        scope.launch {
            try {
                val verified = PhoneAuthApi.verifyPhoneChange(context, changeId, old, next)
                changeId = ""
                currentCode = ""; newCode = ""
                onPhoneVerified(verified)
                p = verified
                onSave(DriverProfileEdits(n.trim(), e.trim(), a.trim(), lang, dob, nic.trim(), replacement, photoChanged))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure = if (changeId.isBlank()) saveError else failureText
                currentCode = ""; newCode = ""
            } finally {
                saving = false
            }
        }
    }
    val nameBad = tried && n.isBlank()
    val phoneBad = tried && p.count { it.isDigit() } < 7
    val emailBad = tried && e.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(e.trim()).matches()

    SubScreen(
        str(R.string.edit_profile_title), onBack, modifier,
        bottom = {
            RideButton(str(R.string.edit_save), ::save, enabled = !saving, loading = saving)
        },
    ) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                ProfilePhoto(store, n, 96.dp, photo = visiblePhoto)
                Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(c.fg).border(2.dp, c.bg, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Camera, c.bg, size = 16.dp) }
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(str(R.string.edit_photo_title), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(str(R.string.edit_photo_hint), color = c.muted, fontSize = 13.sp)
                Row(
                    Modifier.padding(top = 10.dp).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { openPicker(0, visiblePhoto != null) }.heightIn(min = 44.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { RideIcon(RideIcons.Camera, c.fg, size = 18.dp); Text(str(R.string.edit_upload), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp)) }
            }
        }
        SectionTitle(str(R.string.profile_section_personal))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RideTextField(n, { n = it }, str(R.string.edit_name) + " *", RideIcons.Person, isError = nameBad)
            RideTextField(p, { p = it }, str(R.string.edit_phone) + " *", RideIcons.Phone, isError = phoneBad, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            RideTextField(e, { e = it }, str(R.string.profile_email), RideIcons.Mail, isError = emailBad, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            RideTextField(a, { a = it }, str(R.string.profile_address), RideIcons.Pin)
        }
        SectionTitle(str(R.string.edit_additional))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            RideSelectField(str(R.string.edit_dob), dob, str(R.string.edit_dob_hint), RideIcons.Calendar, onClick = { datePicker = true })
            RideTextField(nic, { nic = it }, str(R.string.edit_nic), RideIcons.IdCard)
            RideSelectField(str(R.string.edit_lang), lang, "", RideIcons.Globe, onClick = { langSheet = true })
        }
        if (changeId.isBlank()) {
            failure?.let { Text(it, color = c.danger, modifier = Modifier.padding(vertical = 8.dp)) }
            if (failure == onReviewText) RideButton(stringResource(R.string.driver_start_review), {
                onReviewIdentityChange(dob, nic.trim())
            })
        }
    }
    if (changeId.isNotBlank()) RideSheet(stringResource(R.string.phone_change_title),
        { if (!saving) { changeId = ""; currentCode = ""; newCode = ""; failure = null } },
        stringResource(R.string.phone_change_both_message),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RideTextField(currentCode, { if (!saving) { currentCode = it.filter { c -> c in '0'..'9' }.take(6); verifyChange(currentCode, newCode) } },
                stringResource(R.string.phone_change_current_code, phone), RideIcons.Lock, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            RideTextField(newCode, { if (!saving) { newCode = it.filter { c -> c in '0'..'9' }.take(6); verifyChange(currentCode, newCode) } },
                stringResource(R.string.phone_change_new_code, targetPhone), RideIcons.Lock, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            failure?.let { Text(it, color = c.danger) }
            RideButton(stringResource(R.string.phone_change_verify), { verifyChange(currentCode, newCode) },
                enabled = !saving && currentCode.length == 6 && newCode.length == 6, loading = saving)
            RideButton(
                if (resendIn > 0) stringResource(R.string.phone_change_resend_in, resendIn) else stringResource(R.string.phone_change_resend),
                {
                    if (resendIn == 0 && !saving) {
                        saving = true
                        failure = null
                        scope.launch {
                            try {
                                changeId = PhoneAuthApi.requestPhoneChange(context, targetPhone).getString("changeId")
                                currentCode = ""; newCode = ""
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                failure = if ((error as? PhoneAuthException)?.status == 429) waitText else failureText
                            } finally { saving = false }
                        }
                    }
                }, enabled = resendIn == 0 && !saving, style = RideButtonStyle.Text,
            )
        }
    }
    val selectedDob = remember(dob) {
        runCatching { LocalDate.parse(dob).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }
    if (datePicker) RideDatePickerDialog(selectedDob, null, todayUtcMillis(), { datePicker = false }, {
        dob = pickedDay(it); datePicker = false
    })
    if (langSheet) RideSheet(str(R.string.edit_lang), { langSheet = false }) { close ->
        GroupCard {
            LANGS.forEachIndexed { i, l ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(l == lang, role = Role.RadioButton) { lang = l; close() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l, color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f)); RadioDot(l == lang)
                }
            }
        }
    }
}
