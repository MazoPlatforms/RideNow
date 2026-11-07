package com.ridenow.app.ui.profile

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
    val c = RideTheme.colors
    var sheet by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop("Profile", onBack)
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
                Tile(RideIcons.Pin, "Saved Places", "4 places", Modifier.weight(1f)) { onOpen("Saved addresses") }
                Tile(RideIcons.Card, "Payments", "1 method", Modifier.weight(1f)) { onOpen("Payments") }
                Tile(RideIcons.Clock, "Ride History", "View rides", Modifier.weight(1f)) { onOpen("Your Rides") }
            }
            GroupCard(Modifier.padding(top = 12.dp)) {
                NavRow(RideIcons.Person, "Personal Information", onEdit, subtitle = "Name, phone, email"); RowDivider()
                NavRow(RideIcons.Bell, "Notifications", { onOpen("Notification settings") }, subtitle = "Manage your preferences"); RowDivider()
                NavRow(RideIcons.Globe, "Language", { onOpen("Language") }, subtitle = "English"); RowDivider()
                NavRow(RideIcons.Moon, "Appearance", { sheet = true }, subtitle = mode.label)
            }
            GroupCard(Modifier.padding(top = 10.dp)) {
                NavRow(RideIcons.Shield, "Privacy & Security", { onOpen("Privacy & Security") }, subtitle = "Account and data controls"); RowDivider()
                NavRow(RideIcons.Help, "Help & Support", { onOpen("Help & Support") }, subtitle = "Get help or contact us"); RowDivider()
                NavRow(RideIcons.Info, "About", { onOpen("Legal & About") }, subtitle = "App version, terms and policies")
            }
            Row(
                Modifier.padding(top = 12.dp, bottom = 16.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(c.danger.copy(alpha = 0.12f)).clickable(role = Role.Button, onClick = onLogout),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) { RideIcon(RideIcons.Logout, c.danger); Spacer(Modifier.width(10.dp)); Text("Log out", color = c.danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
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
        ScreenTop("Edit profile", onBack, subtitle = "Keep your information up to date.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) { Avatar(n, 88.dp, badge = true) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RideTextField(n, { n = it.take(60) }, "Full name", RideIcons.Person, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                PickRow(RideIcons.Phone, "Phone number", phone, onChangePhone)
                RideTextField(e, { e = it.take(80) }, "Email (optional)", RideIcons.Mail, isError = !emailOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            }
            Text("ADDITIONAL INFORMATION", color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PickRow(RideIcons.Person, "Gender (optional)", gender) { genderSheet = true }
                PickRow(RideIcons.Calendar, "Date of birth (optional)", dob.ifEmpty { "Select a date" }) {
                    val d = LocalDate.now().minusYears(25)
                    DatePickerDialog(context, { _, y, m, day -> dob = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth)
                        .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }
                RideTextField(city, { city = it.take(60) }, "Current city", RideIcons.Pin, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done))
            }
            Spacer(Modifier.height(16.dp))
        }
        RideButton("Save changes", { onSave(n.trim(), e.trim()) }, enabled = canSave, modifier = Modifier.padding(vertical = 12.dp))
    }
    if (genderSheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(onDismissRequest = { genderSheet = false }, sheetState = state, containerColor = c.bg, contentColor = c.fg, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                Text("Gender", color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Optional. You can change this any time.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                GroupCard {
                    Genders.forEachIndexed { i, g ->
                        if (i > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.RadioButton) { gender = g; scope.launch { state.hide() }.invokeOnCompletion { genderSheet = false } }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(g, color = c.fg, fontSize = 15.sp, fontWeight = if (g == gender) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
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
private fun ProfilePreview() = RideNowTheme(darkTheme = true) { ProfileScreen("Alex Morgan", "+1 555 012 3456", ThemeMode.System, {}, {}, {}, {}, {}) }
