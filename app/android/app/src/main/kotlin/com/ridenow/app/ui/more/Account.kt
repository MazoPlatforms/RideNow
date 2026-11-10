package com.ridenow.app.ui.more

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import com.ridenow.app.ui.components.*
import com.ridenow.app.ui.theme.AccentChoices
import com.ridenow.app.ui.theme.AccentPrefs
import com.ridenow.app.ui.theme.RideTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Context.openAppSettings() { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

@Composable
private fun rememberPref(key: String, default: Boolean): MutableState<Boolean> {
    val p = LocalContext.current.prefs()
    val state = remember(key) { mutableStateOf(p.getBoolean(key, default)) }
    return state
}

private fun MutableState<Boolean>.set(context: Context, key: String, value: Boolean) { this.value = value; context.prefs().edit().putBoolean(key, value).apply() }

// ---------- Change phone number ----------

@Composable
fun ChangePhoneScreen(onBack: () -> Unit, onSend: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var iso by rememberSaveable { mutableStateOf("US") }
    var digits by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val country = com.ridenow.app.ui.auth.Countries.byIso(iso)
    val valid = digits.length >= country.min

    MoreScaffold("Change phone number", onBack, subtitle = "We will send a code to your new number.", bottom = {
        RideButton("Send code", {
            context.prefs().edit().putString("pending_phone", country.dial + digits).putString("pending_iso", country.iso).apply()
            onSend()
        }, enabled = valid)
    }) {
        Row(Modifier.padding(top = 20.dp).height(60.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.fillMaxHeight().width(96.dp).clip(RoundedCornerShape(16.dp)).background(c.field)
                    .border(1.dp, c.border, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { showPicker = true }.padding(horizontal = 14.dp)
                    .semantics { contentDescription = "Country code, ${country.name} ${country.dial}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(country.dial, color = c.fg, fontSize = 17.sp)
                Spacer(Modifier.width(6.dp))
                RideIcon(RideIcons.ChevronDown, c.muted, size = 16.dp)
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(c.field)
                    .border(1.dp, if (focused) c.accent else c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (digits.isEmpty()) Text("Phone number", color = c.muted, fontSize = 16.sp)
                BasicTextField(
                    value = digits,
                    onValueChange = { v -> digits = v.filter(Char::isDigit).take(country.max) },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    textStyle = TextStyle(color = c.fg, fontSize = 20.sp, letterSpacing = 0.8.sp),
                    cursorBrush = SolidColor(c.accent),
                    visualTransformation = com.ridenow.app.ui.auth.PhoneTransformation(country.pattern),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
        }
        Text("Your current number stays active until you verify the new one.", color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 14.dp))
    }

    if (showPicker) {
        com.ridenow.app.ui.auth.CountryPickerSheet(
            selected = country,
            onSelect = { picked -> iso = picked.iso; digits = digits.take(picked.max) },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
fun VerifyNewNumberScreen(onBack: () -> Unit, onVerified: (String) -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val prefs = context.prefs()
    val pendingFull = remember { prefs.getString("pending_phone", "") ?: "" }
    val country = remember { com.ridenow.app.ui.auth.Countries.byIso(prefs.getString("pending_iso", "US") ?: "US") }
    val localDigits = pendingFull.removePrefix(country.dial)
    val formatted = remember(localDigits) {
        com.ridenow.app.ui.auth.PhoneTransformation(country.pattern).filter(AnnotatedString(localDigits)).text.text
    }
    val shown = "${country.dial} $formatted"
    var code by rememberSaveable { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    val left = key(round) { rememberCountdown(24) }

    // Same as onboarding: as soon as the 6th digit is in, verify automatically.
    LaunchedEffect(verifying) {
        if (verifying) {
            delay(900)
            context.toast("Phone number updated")
            onVerified(pendingFull)
        }
    }

    MoreScaffold("Verify new number", onBack, subtitle = "Enter the 6-digit code sent to $shown.", bottom = {
        RideButton("Verify", { verifying = true }, enabled = code.length == 6 && !verifying, loading = verifying)
    }) {
        Box(Modifier.padding(top = 24.dp)) {
            CodeField(code, { v -> code = v; if (v.length == 6) verifying = true }, 6)
        }
        if (left > 0) Text("Resend code in ${mmss(left)}", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
        else Text("Resend code", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).clickable(role = Role.Button) { round++; code = "" }.wrapContentHeight(Alignment.CenterVertically))
    }
}

// ---------- Current city ----------

@Composable
fun CityPickerScreen(onBack: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf(context.prefs().getString("city", "Orbit City") ?: "Orbit City") }
    var detecting by remember { mutableStateOf(false) }
    val all = listOf("Orbit City", "Helix City", "Nova Harbor", "Aurora Bay")
    val shown = all.filter { it.contains(query.trim(), ignoreCase = true) }
    MoreScaffold("Current city", onBack, subtitle = "Search for your city.", bottom = { RideButton("Save", { context.prefs().edit().putString("city", city).apply(); context.toast("City saved"); onBack() }) }) {
        RideTextField(query, { query = it.take(40) }, "Search city", RideIcons.Search, Modifier.padding(top = 16.dp))
        GroupCard(Modifier.padding(top = 12.dp)) {
            NavRow(RideIcons.Pin, "Use current location", {
                if (!detecting) { detecting = true; scope.launch { val found = withContext(Dispatchers.IO) { detectCity(context) }; detecting = false; if (found != null) city = found else context.toast("Could not detect your city") } }
            }, subtitle = if (detecting) "Detecting…" else "Detect automatically", trailing = {})
        }
        SectionLabel("RESULTS")
        GroupCard {
            val list = if (city !in all && query.isBlank()) listOf(city) + shown else shown
            list.forEachIndexed { i, n -> if (i > 0) RowDivider(); Column(Modifier.padding(horizontal = 16.dp)) { RadioRow(n, city == n, { city = n }, if (city == n) "Selected" else null) } }
            if (list.isEmpty()) Text("No matching city.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
        }
    }
}

@Suppress("DEPRECATION", "MissingPermission")
private fun detectCity(context: Context): String? = try {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.firstOrNull()
    loc?.let { Geocoder(context).getFromLocation(it.latitude, it.longitude, 1)?.firstOrNull()?.let { a -> a.locality ?: a.subAdminArea } }
} catch (e: Exception) { null }

// ---------- Transaction history ----------

private class Tx(val title: String, val sub: String, val amount: String, val cancelled: Boolean = false)

@Composable
fun TransactionHistoryScreen(onBack: () -> Unit) {
    val c = RideTheme.colors
    val week = listOf(Tx("Helix Garden Plaza", "Today · Cash", "$16.80"), Tx("Aurora Medical Centre", "Mon, 2 Nov · Cash", "$12.40"), Tx("Helix Garden Plaza", "Sat, 31 Oct · Cancelled", "$0.00", true))
    val earlier = listOf(Tx("Nova Terminal, Gate 4", "Thu, 22 Oct · Cash", "$9.60"), Tx("Orbit Ring Station", "Sun, 18 Oct · Cash", "$14.20"))
    MoreScaffold("Transaction History", onBack, subtitle = "Your payments and ride transactions.") {
        listOf("THIS WEEK" to week, "EARLIER" to earlier).forEach { (label, rows) ->
            SectionLabel(label)
            GroupCard {
                rows.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Receipt, c.accent, size = 20.dp) }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(t.title, color = c.fg, fontSize = 15.sp); Text(t.sub, color = c.muted, fontSize = 12.sp) }
                        Text(t.amount, color = if (t.cancelled) c.muted else c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ---------- Notification settings ----------

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val push = rememberPref("n_push", true)
    val items = listOf(
        Triple("n_rides", "Ride Updates", "Driver matching, arrival, trip updates and completion."),
        Triple("n_promo", "Promotions & Offers", "Discounts, special offers and new features."),
        Triple("n_sched", "Scheduled Rides", "Reminders and updates for scheduled rides."),
        Triple("n_pay", "Payments", "Payment confirmations, refunds and transaction updates."),
        Triple("n_acct", "Account Updates", "Profile changes, security alerts and important account information."),
    )
    val states = items.map { rememberPref(it.first, it.first != "n_promo") }
    MoreScaffold("Notification Settings", onBack, subtitle = "Choose what notifications you want to receive.") {
        GroupCard(Modifier.padding(top = 16.dp)) { ToggleRow("Push Notifications", "Receive important updates, ride alerts and account notifications.", push.value, { push.set(context, "n_push", it) }) }
        SectionLabel("RIDE NOTIFICATIONS")
        GroupCard { listOf(0, 1, 2).forEachIndexed { i, k -> if (i > 0) RowDivider(); ToggleRow(items[k].second, items[k].third, states[k].value && push.value, { states[k].set(context, items[k].first, it) }, enabled = push.value) } }
        SectionLabel("ACCOUNT NOTIFICATIONS")
        GroupCard { listOf(3, 4).forEachIndexed { i, k -> if (i > 0) RowDivider(); ToggleRow(items[k].second, items[k].third, states[k].value && push.value, { states[k].set(context, items[k].first, it) }, enabled = push.value) } }
        GroupCard(Modifier.padding(top = 16.dp)) {
            NavRow(RideIcons.Sliders, "Phone notification settings", { runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, subtitle = "Sounds, channels and the lock screen")
        }
    }
}

// ---------- App color, Map style, Language ----------

@Composable
fun AppColorScreen(onBack: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var sel by rememberSaveable { mutableStateOf(AccentPrefs.name) }
    MoreScaffold("App color", onBack, subtitle = "Choose your preferred accent color.", bottom = {
        RideButton("Apply", { AccentPrefs.name = sel; context.prefs().edit().putString("accent", sel).apply(); onBack() })
    }) {
        AccentChoices.chunked(3).forEach { row ->
            Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { a ->
                    val on = sel == a.name
                    val swatch = if (c.isDark) a.dark else a.light
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(c.field).border(if (on) 2.dp else 1.dp, if (on) swatch else c.border, RoundedCornerShape(20.dp))
                            .selectable(on, role = Role.RadioButton) { sel = a.name }.padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(swatch), contentAlignment = Alignment.Center) { if (on) RideIcon(RideIcons.Check, if (c.isDark) Color(0xFF0A0C10) else Color.White) }
                        Text(a.name, color = c.fg, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun MapStyleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val opts = listOf("Default" to "Balanced detail", "Satellite" to "Real imagery", "Minimal" to "Fewer labels, less clutter", "Transit" to "Highlights stations and lines")
    var sel by rememberSaveable { mutableStateOf(context.prefs().getString("map_style", "Default") ?: "Default") }
    MoreScaffold("Map style", onBack, subtitle = "Adjust how maps look in the app.", bottom = { RideButton("Apply", { context.prefs().edit().putString("map_style", sel).apply(); onBack() }) }) {
        GroupCard(Modifier.padding(top = 16.dp)) { opts.forEachIndexed { i, (n, s) -> if (i > 0) RowDivider(); Column(Modifier.padding(horizontal = 16.dp)) { RadioRow(n, sel == n, { sel = n }, s) } } }
    }
}

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val langs = listOf("English" to "English", "Español" to "Spanish", "Français" to "French", "Deutsch" to "German", "日本語" to "Japanese", "العربية" to "Arabic", "हिन्दी" to "Hindi")
    var sel by rememberSaveable { mutableStateOf(context.prefs().getString("lang", "English") ?: "English") }
    MoreScaffold("Language", onBack, subtitle = "Choose the language for RideNow.", bottom = { RideButton("Save", { context.prefs().edit().putString("lang", sel).apply(); onBack() }) }) {
        GroupCard(Modifier.padding(top = 16.dp)) { langs.forEachIndexed { i, (n, e) -> if (i > 0) RowDivider(); Column(Modifier.padding(horizontal = 16.dp)) { RadioRow(n, sel == n, { sel = n }, e) } } }
    }
}

// ---------- Privacy & Security ----------

@Composable
fun PrivacyHubScreen(onBack: () -> Unit, open: (String) -> Unit) {
    MoreScaffold("Privacy & Security", onBack, subtitle = "Control your data and keep your account safe.") {
        SectionLabel("ACCOUNT SECURITY")
        GroupCard {
            NavRow(RideIcons.Phone, "Change phone number", { open("Change phone number") }, subtitle = "Verify a new number with a code"); RowDivider()
            NavRow(RideIcons.Phone, "Manage Devices", { open("Manage Devices") }, subtitle = "View and manage your logged-in devices"); RowDivider()
            NavRow(RideIcons.Shield, "Two-Step Verification", { open("Two-step verification") }, subtitle = "Add an extra layer of security")
        }
        SectionLabel("PRIVACY")
        GroupCard {
            NavRow(RideIcons.Lock, "App Data & Permissions", { open("App data and permissions") }, subtitle = "Manage how your data is used"); RowDivider()
            NavRow(RideIcons.Pin, "Location Data", { open("Location data") }, subtitle = "Control location access for better service")
        }
        SectionLabel("ACCOUNT")
        GroupCard {
            NavRow(RideIcons.Download, "Download My Data", { open("Download my data") }, subtitle = "Get a copy of your account data"); RowDivider()
            NavRow(RideIcons.Trash, "Delete Account", { open("Delete account") }, subtitle = "Permanently delete your account", danger = true)
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- Manage devices ----------

@Composable
fun ManageDevicesScreen(onBack: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val others = remember { mutableStateListOf("Tablet" to "Last active 2 days ago") }
    MoreScaffold("Manage Devices", onBack, subtitle = "Devices where you are signed in.", bottom = {
        if (others.isNotEmpty()) DangerButton("Log out of all other devices", { others.clear(); context.toast("Logged out of other devices") })
    }) {
        SectionLabel("THIS DEVICE")
        GroupCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Phone, c.accent); Column(Modifier.padding(start = 14.dp).weight(1f)) { Text("This phone", color = c.fg, fontSize = 15.sp); Text("Active now · Orbit City", color = c.muted, fontSize = 12.sp) }
                Text("Current", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        SectionLabel("OTHER DEVICES")
        if (others.isEmpty()) Text("No other devices are signed in.", color = c.muted, fontSize = 14.sp)
        else GroupCard {
            others.forEachIndexed { i, (n, s) ->
                if (i > 0) RowDivider()
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Phone, c.muted); Column(Modifier.padding(start = 14.dp).weight(1f).padding(vertical = 12.dp)) { Text(n, color = c.fg, fontSize = 15.sp); Text(s, color = c.muted, fontSize = 12.sp) }
                    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { others.removeAt(i) }.padding(horizontal = 16.dp).semantics { contentDescription = "Log out $n" }, contentAlignment = Alignment.Center) { Text("Log out", color = c.danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// ---------- Two-step verification ----------

@Composable
fun TwoStepScreen(onBack: () -> Unit, phone: String) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val on = rememberPref("ts_on", false)
    var method by rememberSaveable { mutableIntStateOf(0) }
    var codes by remember { mutableStateOf<List<String>?>(null) }
    val clip = LocalClipboardManager.current
    MoreScaffold("Two-Step Verification", onBack, subtitle = "Add an extra layer of security.") {
        GroupCard(Modifier.padding(top = 16.dp)) { ToggleRow("Require a code on new devices", "Ask for a code when signing in elsewhere", on.value, { on.set(context, "ts_on", it) }) }
        SectionLabel("METHOD")
        GroupCard {
            Column(Modifier.padding(horizontal = 16.dp)) {
                RadioRow("Text message (SMS)", method == 0, { method = 0 }, "Codes sent to $phone"); RowDivider()
                RadioRow("Authenticator app", method == 1, { method = 1 }, "Use a one-time code app")
            }
        }
        SectionLabel("BACKUP")
        GroupCard { NavRow(RideIcons.Doc, "Recovery codes", { codes = List(8) { "%04d-%04d".format((0..9999).random(), (0..9999).random()) } }, subtitle = "Use these if you lose your phone") }
        Spacer(Modifier.height(16.dp))
    }
    codes?.let { list ->
        AlertDialog(
            onDismissRequest = { codes = null }, title = { Text("Recovery codes") },
            text = { Text("Each code works once. Store them somewhere safe.\n\n" + list.joinToString("\n")) },
            confirmButton = { TextButton({ clip.setText(AnnotatedString(list.joinToString("\n"))); context.toast("Copied"); codes = null }) { Text("Copy") } },
            dismissButton = { TextButton({ codes = null }) { Text("Close") } },
        )
    }
}

// ---------- App data & permissions ----------

@Composable
fun AppDataScreen(onBack: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val notif = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
    val rows = listOf(
        Triple("Location", "Pickup and live trip tracking", if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) "While using" else "Not allowed"),
        Triple("Notifications", "Ride alerts and updates", if (notif) "Allowed" else "Not allowed"),
        Triple("Camera", "Profile photo", if (granted(Manifest.permission.CAMERA)) "Allowed" else "Ask each time"),
        Triple("Contacts", "Trusted contacts and friend rides", if (granted(Manifest.permission.READ_CONTACTS)) "Allowed" else "Ask each time"),
        Triple("Microphone", "Audio recording during trips", if (granted(Manifest.permission.RECORD_AUDIO)) "Allowed" else "Ask each time"),
    )
    val offers = rememberPref("d_offers", false); val analytics = rememberPref("d_analytics", true)
    MoreScaffold("App Data & Permissions", onBack, subtitle = "Manage how your data is used.") {
        SectionLabel("PERMISSIONS")
        GroupCard {
            rows.forEachIndexed { i, (t, s, st) ->
                if (i > 0) RowDivider()
                NavRow(RideIcons.Lock, t, { context.openAppSettings() }, subtitle = s, trailing = { Text(st, color = c.muted, fontSize = 13.sp) })
            }
        }
        SectionLabel("DATA USE")
        GroupCard {
            ToggleRow("Personalised offers", "Use ride activity to tailor offers", offers.value, { offers.set(context, "d_offers", it) }); RowDivider()
            ToggleRow("Usage analytics", "Help improve RideNow", analytics.value, { analytics.set(context, "d_analytics", it) })
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- Location data ----------

@Composable
fun LocationDataScreen(onBack: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var mode by rememberSaveable { mutableIntStateOf(context.prefs().getInt("loc_mode", 1)) }
    val precise = rememberPref("loc_precise", true)
    var cleared by remember { mutableStateOf(false) }
    MoreScaffold("Location Data", onBack, subtitle = "Control location access for better service.") {
        GroupCard(Modifier.padding(top = 16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                RadioRow("Always", mode == 0, { mode = 0; context.prefs().edit().putInt("loc_mode", 0).apply() }, "Track even when the app is closed"); RowDivider()
                RadioRow("While using the app", mode == 1, { mode = 1; context.prefs().edit().putInt("loc_mode", 1).apply() }, "Recommended"); RowDivider()
                RadioRow("Never", mode == 2, { mode = 2; context.prefs().edit().putInt("loc_mode", 2).apply() }, "You must enter pickups manually")
            }
        }
        GroupCard(Modifier.padding(top = 16.dp)) {
            ToggleRow("Precise location", "Needed to pick you up at the right spot", precise.value, { precise.set(context, "loc_precise", it) }); RowDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Location history", color = c.fg, fontSize = 15.sp); Text("Clear places saved from rides", color = c.muted, fontSize = 12.sp) }
                Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { cleared = true; context.toast("Location history cleared") }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text(if (cleared) "Cleared" else "Clear", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        GroupCard(Modifier.padding(top = 16.dp)) { NavRow(RideIcons.Sliders, "Phone location permission", { context.openAppSettings() }, subtitle = "Change what the system allows") }
    }
}

// ---------- Download my data ----------

@Composable
fun DownloadDataScreen(onBack: () -> Unit) {
    val c = RideTheme.colors
    val items = listOf("Profile" to "Name, phone number and email", "Ride history" to "Trips and receipts", "Payments" to "Transactions and payment methods", "Saved places" to "Home, work and favorites")
    val on = remember { mutableStateListOf(true, true, true, true) }
    var requested by rememberSaveable { mutableStateOf(false) }
    if (requested) {
        MoreScaffold("Download My Data", onBack, bottom = { RideButton("Done", onBack) }) { Hero(RideIcons.Check, "Request received.", "You will get a notification when your file is ready to download.") }
        return
    }
    MoreScaffold("Download My Data", onBack, subtitle = "Get a copy of your account data.", bottom = {
        RideButton("Request data copy", { requested = true }, enabled = on.any { it })
        Text("You will get a notification when your file is ready to download.", color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }) {
        SectionLabel("INCLUDE")
        GroupCard { items.forEachIndexed { i, (t, s) -> if (i > 0) RowDivider(); ToggleRow(t, s, on[i], { on[i] = it }) } }
    }
}

// ---------- Delete account ----------

@Composable
fun DeleteAccountScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    val c = RideTheme.colors
    val items = listOf("Your Profile" to "Name, phone number and account details", "Saved Places" to "All your saved locations", "Ride History" to "All your past rides and related data", "Payment Methods" to "All saved payment methods", "App Preferences" to "Settings, notifications and other preferences")
    MoreScaffold("Delete Account", onBack, subtitle = "We are sorry to see you go. Deleting your account will permanently remove your data.", bottom = { DangerButton("Delete account", onContinue) }) {
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.danger.copy(alpha = 0.12f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(RideIcons.Alert, c.danger); Column(Modifier.padding(start = 12.dp)) { Text("This action cannot be undone.", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text("All your data will be permanently deleted.", color = c.muted, fontSize = 13.sp) }
        }
        SectionLabel("WHAT WILL BE DELETED?")
        GroupCard { items.forEachIndexed { i, (t, s) -> if (i > 0) RowDivider(); Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { Text(t, color = c.fg, fontSize = 15.sp); Text(s, color = c.muted, fontSize = 12.sp) } } }
        Text("Any pending rides, payments or refunds must be completed before you can delete your account.", color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    }
}

@Composable
fun ConfirmDeletionScreen(phone: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    var round by remember { mutableIntStateOf(0) }
    val left = key(round) { rememberCountdown(24) }
    MoreScaffold("Confirm deletion", onBack, subtitle = "Enter the code sent to $phone to delete your account.", bottom = {
        DangerButton("Delete account", {
            val seen = context.prefs().getBoolean("intro_seen", false)
            context.prefs().edit().clear().putBoolean("intro_seen", seen).apply(); AccentPrefs.name = "Default"; onDeleted()
        }, enabled = code.length == 6)
        RideButton("Keep my account", onBack, Modifier.padding(top = 8.dp), style = RideButtonStyle.Text)
    }) {
        Box(Modifier.padding(top = 24.dp)) { CodeField(code, { code = it }, 6) }
        if (left > 0) Text("Resend code in ${mmss(left)}", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
        else Text("Resend code", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).clickable(role = Role.Button) { round++; code = "" }.wrapContentHeight(Alignment.CenterVertically))
        Text("This permanently deletes your data and cannot be undone.", color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun AccountDeletedScreen(onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    MoreScaffold("", onClose, bottom = { RideButton("Close", onClose) }) { Hero(RideIcons.Check, "Account deleted.", "We are sorry to see you go. You can create a new account any time.") }
}
