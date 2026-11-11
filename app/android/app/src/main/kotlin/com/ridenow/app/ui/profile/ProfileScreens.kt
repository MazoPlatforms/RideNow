package com.ridenow.app.ui.profile

import com.ridenow.app.ui.components.localizedLabel

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import android.app.DatePickerDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.GroupCard
import com.ridenow.app.ui.components.NavRow
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RideTextField
import com.ridenow.app.ui.components.RowDivider
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.settings.AppearanceSheet
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.theme.ThemeMode
import com.ridenow.app.ui.util.entrance
import kotlinx.coroutines.launch
import java.time.LocalDate

fun initialsOf(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "R" }

@Composable
private fun Avatar(name: String, size: Dp, badge: Boolean = false) {
    val c = RideTheme.colors
    Box(Modifier.size(size)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) {
            Text(initialsOf(name), color = c.accent, fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.SemiBold)
        }
        if (badge) Box(Modifier.align(Alignment.BottomEnd).size(size * 0.34f).clip(CircleShape).background(BrandYellow).border(3.dp, c.bg, CircleShape), contentAlignment = Alignment.Center) {
            RideIcon(RideIcons.Camera, Ink, size = size * 0.17f)
        }
    }
}

/** Profile hub: card, quick tiles and settings groups. */
@Composable
fun ProfileScreen(
    name: String, phone: String, mode: ThemeMode, onMode: (ThemeMode) -> Unit, onBack: () -> Unit, onEdit: () -> Unit,
    onOpen: (String) -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier,
) {
    val textSettingsProfile = stringResource(R.string.settings_profile)
    val textMenuSaved = stringResource(R.string.menu_saved)
    val text4Places = stringResource(R.string.text_text_4_places)
    val textMenuPayments = stringResource(R.string.menu_payments)
    val text1Method = stringResource(R.string.text_text_1_method)
    val textRideHistory2 = stringResource(R.string.text_ride_history_2)
    val textViewRides = stringResource(R.string.text_view_rides)
    val textPersonalInformation = stringResource(R.string.text_personal_information)
    val textNamePhoneEmail = stringResource(R.string.text_name_phone_email)
    val textMenuNotifications = stringResource(R.string.menu_notifications)
    val textManageYourPreferences = stringResource(R.string.text_manage_your_preferences)
    val textSettingsLanguage = stringResource(R.string.settings_language)
    val textSettingsPrivacy = stringResource(R.string.settings_privacy)
    val textSettingsPrivacySub = stringResource(R.string.settings_privacy_sub)
    val textMenuHelp = stringResource(R.string.menu_help)
    val textSettingsHelpSub = stringResource(R.string.settings_help_sub)
    val textSettingsAbout = stringResource(R.string.settings_about)
    val textSettingsAboutSub = stringResource(R.string.settings_about_sub)
    val textLogOut = stringResource(R.string.text_log_out)

    val c = RideTheme.colors
    var sheet by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(textSettingsProfile, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onEdit).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(name, 56.dp, badge = true); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { Text(name, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text(phone, color = c.muted, fontSize = 13.sp) }
                RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(RideIcons.Pin, textMenuSaved, text4Places, Modifier.weight(1f)) { onOpen("Saved addresses") }
                Tile(RideIcons.Card, textMenuPayments, text1Method, Modifier.weight(1f)) { onOpen("Payments") }
                Tile(RideIcons.Clock, textRideHistory2, textViewRides, Modifier.weight(1f)) { onOpen("Your Rides") }
            }
            GroupCard(Modifier.padding(top = 12.dp)) {
                NavRow(RideIcons.Person, textPersonalInformation, onEdit, subtitle = textNamePhoneEmail); RowDivider()
                NavRow(RideIcons.Bell, textMenuNotifications, { onOpen("Notification settings") }, subtitle = textManageYourPreferences); RowDivider()
                NavRow(RideIcons.Globe, textSettingsLanguage, { onOpen("Language") }, subtitle = localizedLabel("English")); RowDivider()
                NavRow(RideIcons.Moon, stringResource(R.string.settings_appearance), { sheet = true }, subtitle = stringResource(mode.labelRes))
            }
            GroupCard(Modifier.padding(top = 10.dp)) {
                NavRow(RideIcons.Shield, textSettingsPrivacy, { onOpen("Privacy & Security") }, subtitle = textSettingsPrivacySub); RowDivider()
                NavRow(RideIcons.Help, textMenuHelp, { onOpen("Help & Support") }, subtitle = textSettingsHelpSub); RowDivider()
                NavRow(RideIcons.Info, textSettingsAbout, { onOpen("Legal & About") }, subtitle = textSettingsAboutSub)
            }
            Row(
                Modifier.padding(top = 12.dp, bottom = 16.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(c.danger.copy(alpha = 0.12f)).clickable(role = Role.Button, onClick = onLogout),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) { RideIcon(RideIcons.Logout, c.danger); Spacer(Modifier.width(10.dp)); Text(textLogOut, color = c.danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
    if (sheet) AppearanceSheet(mode, onMode) { sheet = false }
}

@Composable
private fun Tile(icon: List<String>, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val c = RideTheme.colors
    Column(modifier.height(92.dp).clip(RoundedCornerShape(18.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        RideIcon(icon, c.accent)
        Column { Text(title, color = c.fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(sub, color = c.muted, fontSize = 11.sp, maxLines = 1) }
    }
}

private val Genders = listOf("Female", "Male", "Non-binary", "Prefer not to say")

/** Edit profile with the optional extras: gender, date of birth and city. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(name: String, email: String, phone: String, onBack: () -> Unit, onSave: (String, String) -> Unit, onChangePhone: () -> Unit, modifier: Modifier = Modifier) {
    val textEditProfile = stringResource(R.string.text_edit_profile)
    val textKeepYourInformationUpToDate = stringResource(R.string.text_keep_your_information_up_to_date)
    val textDetailsName = stringResource(R.string.details_name)
    val textFriendPhone = stringResource(R.string.friend_phone)
    val textDetailsEmail = stringResource(R.string.details_email)
    val textAdditionalInformation = stringResource(R.string.text_additional_information)
    val textGenderOptional = stringResource(R.string.text_gender_optional)
    val textDateOfBirthOptional = stringResource(R.string.text_date_of_birth_optional)
    val textSelectADate = stringResource(R.string.text_select_a_date)
    val textCurrentCity = stringResource(R.string.text_current_city)
    val textSaveChanges = stringResource(R.string.text_save_changes)
    val textGender = stringResource(R.string.text_gender)
    val textOptionalYouCanChangeThisAnyTime = stringResource(R.string.text_optional_you_can_change_this_any_time)

    val c = RideTheme.colors
    val context = LocalContext.current
    var n by rememberSaveable { mutableStateOf(name) }
    var e by rememberSaveable { mutableStateOf(email) }
    var gender by rememberSaveable { mutableStateOf("Prefer not to say") }
    var dob by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var genderSheet by rememberSaveable { mutableStateOf(false) }
    val emailOk = e.isBlank() || Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$").matches(e.trim())
    val canSave = n.trim().length >= 2 && emailOk
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        ScreenTop(textEditProfile, onBack, subtitle = textKeepYourInformationUpToDate)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) { Avatar(n, 88.dp, badge = true) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RideTextField(n, { n = it.take(60) }, textDetailsName, RideIcons.Person, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                PickRow(RideIcons.Phone, textFriendPhone, phone, onChangePhone)
                RideTextField(e, { e = it.take(80) }, textDetailsEmail, RideIcons.Mail, isError = !emailOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            }
            Text(textAdditionalInformation, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PickRow(RideIcons.Person, textGenderOptional, localizedLabel(gender)) { genderSheet = true }
                PickRow(RideIcons.Calendar, textDateOfBirthOptional, dob.ifEmpty { textSelectADate }) {
                    val d = LocalDate.now().minusYears(25)
                    DatePickerDialog(context, { _, y, m, day -> dob = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth)
                        .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }
                RideTextField(city, { city = it.take(60) }, textCurrentCity, RideIcons.Pin, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done))
            }
            Spacer(Modifier.height(16.dp))
        }
        RideButton(textSaveChanges, { onSave(n.trim(), e.trim()) }, enabled = canSave, modifier = Modifier.padding(vertical = 12.dp))
    }
    if (genderSheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(onDismissRequest = { genderSheet = false }, sheetState = state, containerColor = c.bg, contentColor = c.fg, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                Text(textGender, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(textOptionalYouCanChangeThisAnyTime, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                GroupCard {
                    Genders.forEachIndexed { i, g ->
                        if (i > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.RadioButton) { gender = g; scope.launch { state.hide() }.invokeOnCompletion { genderSheet = false } }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(localizedLabel(g), color = c.fg, fontSize = 15.sp, fontWeight = if (g == gender) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            if (g == gender) RideIcon(RideIcons.Check, c.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(icon: List<String>, label: String, value: String, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(icon, c.accent); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(label, color = c.muted, fontSize = 11.sp); Text(value, color = c.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ProfilePreview() = RideNowTheme(darkTheme = true) { ProfileScreen(stringResource(R.string.text_alex_morgan), "+1 555 012 3456", ThemeMode.System, {}, {}, {}, {}, {}) }
