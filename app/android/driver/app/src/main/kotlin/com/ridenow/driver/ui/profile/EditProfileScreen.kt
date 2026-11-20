package com.ridenow.driver.ui.profile

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val LANGS = listOf("English", "Sinhala", "Tamil", "Arabic", "Spanish", "French", "German", "Japanese", "Hindi")

/** Edit profile: photo, personal details and extra details. Name and phone are required. */
@Composable
fun EditProfileScreen(
    store: DriverStore, name: String, phone: String, email: String, address: String,
    onBack: () -> Unit, onSave: (name: String, phone: String, email: String, address: String) -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
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
    val openPicker = rememberPhotoPicker(str(R.string.edit_photo_title)) { _, bmp -> store.photo = bmp }
    val nameBad = tried && n.isBlank()
    val phoneBad = tried && p.count { it.isDigit() } < 7
    val emailBad = tried && e.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(e.trim()).matches()

    SubScreen(
        str(R.string.edit_profile_title), onBack, modifier,
        bottom = {
            RideButton(str(R.string.edit_save), {
                tried = true
                if (n.isNotBlank() && p.count { it.isDigit() } >= 7 && !emailBad && !(e.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(e.trim()).matches())) {
                    store.dob = dob; store.nic = nic.trim(); store.prefLanguage = lang
                    onSave(n.trim(), p.trim(), e.trim(), a.trim())
                }
            })
        },
    ) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                ProfilePhoto(store, n, 96.dp)
                Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(c.fg).border(2.dp, c.bg, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Camera, c.bg, size = 16.dp) }
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(str(R.string.edit_photo_title), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(str(R.string.edit_photo_hint), color = c.muted, fontSize = 13.sp)
                Row(
                    Modifier.padding(top = 10.dp).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { openPicker(0, store.photo != null) }.heightIn(min = 44.dp).padding(horizontal = 16.dp),
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
    }
    if (datePicker) RideDatePickerDialog(null, null, todayUtcMillis(), { datePicker = false }, {
        dob = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(it)); datePicker = false
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
