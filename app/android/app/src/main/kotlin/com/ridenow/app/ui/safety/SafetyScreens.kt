package com.ridenow.app.ui.safety

import com.ridenow.app.ui.components.offsetByStartDirection

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
import android.content.Intent
import androidx.compose.foundation.selection.toggleable
import android.net.Uri
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.booking.VehicleArt
import com.ridenow.app.ui.components.GroupCard
import com.ridenow.app.ui.components.NavRow
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RideTextField
import com.ridenow.app.ui.components.RowDivider
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

data class Contact(val name: String, val phone: String)

/** Local emergency number. Replace with a per-country lookup before release. */
private const val EmergencyNumber = "112"

@Composable
private fun Switch(on: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val c = RideTheme.colors
    Box(
        Modifier.size(width = 48.dp, height = 28.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.accent else c.line).then(if (on) Modifier else Modifier.border(1.dp, c.border, RoundedCornerShape(14.dp)))
            .toggleable(on, role = Role.Switch, onValueChange = onChange).semantics { contentDescription = label },
    ) { Box(Modifier.padding(if (on) 4.dp else 3.dp).offsetByStartDirection(if (on) 20.dp else 0.dp).size(20.dp).clip(CircleShape).background(if (on) c.onAccent else c.muted)) }
}

/** Emergency call, ride details and safety tools. */
@Composable
fun SafetyCenterScreen(contacts: Int, onBack: () -> Unit, onShare: () -> Unit, onContacts: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier) {
    val textMenuSafety = stringResource(R.string.menu_safety)
    val textEmergency = stringResource(R.string.text_emergency)
    val textCallLocalEmergencyServicesNow = stringResource(R.string.text_call_local_emergency_services_now)
    val textCallEmergency = stringResource(R.string.text_call_emergency)
    val textOrbitSilver = stringResource(R.string.text_orbit_silver)
    val textDriverMiraTanaka = stringResource(R.string.text_driver_mira_tanaka)
    val textSafetyTools = stringResource(R.string.text_safety_tools)
    val textTripShareLive = stringResource(R.string.trip_share_live)
    val textTrustedContacts = stringResource(R.string.text_trusted_contacts)
    val textNoneYet = stringResource(R.string.text_none_yet)
    val textValueContactValueOne = stringResource(R.string.extra_1_s_contact)
    val textValueContactValueMany = stringResource(R.string.extra_1_s_contacts)
    val textReportASafetyIssue = stringResource(R.string.text_report_a_safety_issue)
    val textAudioRecording = stringResource(R.string.text_audio_recording)
    val textOn = stringResource(R.string.text_on)
    val textOff = stringResource(R.string.text_off)
    val textCheckInAlerts = stringResource(R.string.text_check_in_alerts)

    val c = RideTheme.colors
    val context = LocalContext.current
    var audio by rememberSaveable { mutableStateOf(false) }
    var checkIns by rememberSaveable { mutableStateOf(true) }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(textMenuSafety, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.danger.copy(alpha = 0.10f)).border(1.dp, c.danger, RoundedCornerShape(20.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Shield, c.danger, size = 24.dp); Spacer(Modifier.width(12.dp))
                    Column { Text(textEmergency, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(textCallLocalEmergencyServicesNow, color = c.muted, fontSize = 13.sp) }
                }
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.danger)
                        .clickable(role = Role.Button) { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$EmergencyNumber")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { RideIcon(RideIcons.Call, if (c.isDark) androidx.compose.ui.graphics.Color(0xFF2B0A0A) else androidx.compose.ui.graphics.Color.White, size = 18.dp); Spacer(Modifier.width(8.dp)); Text(textCallEmergency, color = if (c.isDark) androidx.compose.ui.graphics.Color(0xFF2B0A0A) else androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(72.dp).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                VehicleArt(1, 64.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(textOrbitSilver, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(textDriverMiraTanaka, color = c.muted, fontSize = 12.sp) }
                Text(stringResource(R.string.sample_vehicle_plate), color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, modifier = Modifier.border(1.dp, c.border, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
            }
            SectionLabel(textSafetyTools)
            GroupCard {
                NavRow(RideIcons.Share, textTripShareLive, onShare); RowDivider()
                NavRow(RideIcons.Person, textTrustedContacts, onContacts, subtitle = if (contacts == 0) textNoneYet else (if (contacts != 1) textValueContactValueMany else textValueContactValueOne).format(contacts)); RowDivider()
                NavRow(RideIcons.Flag, textReportASafetyIssue, onReport); RowDivider()
                NavRow(RideIcons.Phone, textAudioRecording, { audio = !audio }, subtitle = if (audio) textOn else textOff, trailing = { Switch(audio, textAudioRecording) { audio = it } }); RowDivider()
                NavRow(RideIcons.Bell, textCheckInAlerts, { checkIns = !checkIns }, subtitle = if (checkIns) textOn else textOff, trailing = { Switch(checkIns, textCheckInAlerts) { checkIns = it } })
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Add, view and remove trusted contacts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedContactsScreen(contacts: List<Contact>, onAdd: (Contact) -> Unit, onRemove: (Contact) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val textTrustedContacts = stringResource(R.string.text_trusted_contacts)
    val textPeopleWhoCanFollowYourLiveTrip = stringResource(R.string.text_people_who_can_follow_your_live_trip)
    val textNoTrustedContactsYetAddSomeoneYouTrustToFollowYourTrips = stringResource(R.string.text_no_trusted_contacts_yet_add_someone_you_trust_to_follow_your_trips)
    val textRemoveValue = stringResource(R.string.text_remove_value)
    val textAddAContact = stringResource(R.string.text_add_a_contact)
    val textName = stringResource(R.string.text_name)
    val textFriendPhone = stringResource(R.string.friend_phone)
    val textSaveContact = stringResource(R.string.text_save_contact)

    val c = RideTheme.colors
    var sheet by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(textTrustedContacts, onBack, subtitle = textPeopleWhoCanFollowYourLiveTrip)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            if (contacts.isEmpty()) Text(textNoTrustedContactsYetAddSomeoneYouTrustToFollowYourTrips, color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 24.dp))
            else GroupCard(Modifier.padding(top = 16.dp)) {
                contacts.forEachIndexed { i, k ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().height(68.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) { Text(k.name.take(2).uppercase(), color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text(k.name, color = c.fg, fontSize = 15.sp); Text(k.phone, color = c.muted, fontSize = 12.sp) }
                        Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { onRemove(k) }.semantics { contentDescription = textRemoveValue.format(k.name) }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.Trash, c.muted) }
                    }
                }
            }
        }
        RideButton(textAddAContact, { sheet = true }, modifier = Modifier.padding(vertical = 12.dp))
    }
    if (sheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = state, containerColor = c.bg, contentColor = c.fg, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                Text(textAddAContact, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RideTextField(name, { name = it.take(40) }, textName, RideIcons.Person, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                    RideTextField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(15) }, textFriendPhone, RideIcons.Phone, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done))
                }
                RideButton(textSaveContact, { onAdd(Contact(name.trim(), phone)); sheet = false }, enabled = name.isNotBlank() && phone.length >= 6, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SafetyPreview() = RideNowTheme(darkTheme = true) { SafetyCenterScreen(2, {}, {}, {}, {}) }
