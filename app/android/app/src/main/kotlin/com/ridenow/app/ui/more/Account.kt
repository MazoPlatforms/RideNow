package com.ridenow.app.ui.more

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource

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
    val textChangePhoneNumber = stringResource(R.string.text_change_phone_number)
    val textWeWillSendACodeToYourNewNumber = stringResource(R.string.text_we_will_send_a_code_to_your_new_number)
    val textPhoneSend = stringResource(R.string.phone_send)
    val textCountryCodeValueValue = stringResource(R.string.text_country_code_value_value)
    val textFriendPhone = stringResource(R.string.friend_phone)
    val textYourCurrentNumberStaysActiveUntilYouVerifyTheNewOne = stringResource(R.string.text_your_current_number_stays_active_until_you_verify_the_new_one)

    val c = RideTheme.colors
    val context = LocalContext.current
    var iso by rememberSaveable { mutableStateOf("US") }
    var digits by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val country = com.ridenow.app.ui.auth.Countries.byIso(iso)
    val valid = digits.length >= country.min

    MoreScaffold(textChangePhoneNumber, onBack, subtitle = textWeWillSendACodeToYourNewNumber, bottom = {
        RideButton(textPhoneSend, {
            context.prefs().edit().putString("pending_phone", country.dial + digits).putString("pending_iso", country.iso).apply()
            onSend()
        }, enabled = valid)
    }) {
        Row(Modifier.padding(top = 20.dp).height(60.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.fillMaxHeight().width(96.dp).clip(RoundedCornerShape(16.dp)).background(c.field)
                    .border(1.dp, c.border, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { showPicker = true }.padding(horizontal = 14.dp)
                    .semantics { contentDescription = textCountryCodeValueValue.format(country.displayName, country.dial) },
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
                if (digits.isEmpty()) Text(textFriendPhone, color = c.muted, fontSize = 16.sp)
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
        Text(textYourCurrentNumberStaysActiveUntilYouVerifyTheNewOne, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 14.dp))
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
    val textPhoneNumberUpdated = stringResource(R.string.text_phone_number_updated)
    val textVerifyNewNumber = stringResource(R.string.text_verify_new_number)
    val textEnterThe6DigitCodeSentToValue = stringResource(R.string.text_enter_the_6_digit_code_sent_to_value)
    val textVerify = stringResource(R.string.text_verify)
    val textResendCodeInValue = stringResource(R.string.text_resend_code_in_value)
    val textOtpResend = stringResource(R.string.otp_resend)

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
            context.toast(textPhoneNumberUpdated)
            onVerified(pendingFull)
        }
    }

    MoreScaffold(textVerifyNewNumber, onBack, subtitle = textEnterThe6DigitCodeSentToValue.format(shown), bottom = {
        RideButton(textVerify, { verifying = true }, enabled = code.length == 6 && !verifying, loading = verifying)
    }) {
        Box(Modifier.padding(top = 24.dp)) {
            CodeField(code, { v -> code = v; if (v.length == 6) verifying = true }, 6)
        }
        if (left > 0) Text(textResendCodeInValue.format(mmss(left)), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
        else Text(textOtpResend, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).clickable(role = Role.Button) { round++; code = "" }.wrapContentHeight(Alignment.CenterVertically))
    }
}

// ---------- Current city ----------

@Composable
fun CityPickerScreen(onBack: () -> Unit) {
    val textOrbitCity = stringResource(R.string.text_orbit_city)
    val textHelixCity = stringResource(R.string.text_helix_city)
    val textNovaHarbor = stringResource(R.string.text_nova_harbor)
    val textAuroraBay = stringResource(R.string.text_aurora_bay)
    val textCurrentCity = stringResource(R.string.text_current_city)
    val textSearchForYourCity = stringResource(R.string.text_search_for_your_city)
    val textSave = stringResource(R.string.text_save)
    val textCitySaved = stringResource(R.string.text_city_saved)
    val textSearchCity = stringResource(R.string.text_search_city)
    val textUseCurrentLocation = stringResource(R.string.text_use_current_location)
    val textCouldNotDetectYourCity = stringResource(R.string.text_could_not_detect_your_city)
    val textDetecting = stringResource(R.string.text_detecting)
    val textDetectAutomatically = stringResource(R.string.text_detect_automatically)
    val textResults = stringResource(R.string.text_results)
    val textSelected = stringResource(R.string.text_selected)
    val textNoMatchingCity = stringResource(R.string.text_no_matching_city)

    val c = RideTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf(context.prefs().getString("city", textOrbitCity) ?: textOrbitCity) }
    var detecting by remember { mutableStateOf(false) }
    val all = listOf(textOrbitCity, textHelixCity, textNovaHarbor, textAuroraBay)
    val shown = all.filter { it.contains(query.trim(), ignoreCase = true) }
    MoreScaffold(textCurrentCity, onBack, subtitle = textSearchForYourCity, bottom = { RideButton(textSave, { context.prefs().edit().putString("city", city).apply(); context.toast(textCitySaved); onBack() }) }) {
        RideTextField(query, { query = it.take(40) }, textSearchCity, RideIcons.Search, Modifier.padding(top = 16.dp))
        GroupCard(Modifier.padding(top = 12.dp)) {
            NavRow(RideIcons.Pin, textUseCurrentLocation, {
                if (!detecting) { detecting = true; scope.launch { val found = withContext(Dispatchers.IO) { detectCity(context) }; detecting = false; if (found != null) city = found else context.toast(textCouldNotDetectYourCity) } }
            }, subtitle = if (detecting) textDetecting else textDetectAutomatically, trailing = {})
        }
        SectionLabel(textResults)
        GroupCard {
            val list = if (city !in all && query.isBlank()) listOf(city) + shown else shown
            list.forEachIndexed { i, n -> if (i > 0) RowDivider(); Column(Modifier.padding(horizontal = 16.dp)) { RadioRow(n, city == n, { city = n }, if (city == n) textSelected else null) } }
            if (list.isEmpty()) Text(textNoMatchingCity, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
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
    val textHelixGardenPlaza = stringResource(R.string.text_helix_garden_plaza)
    val textTodayCash = stringResource(R.string.text_today_cash)
    val textAuroraMedicalCentre = stringResource(R.string.text_aurora_medical_centre)
    val textMon2NovCash = stringResource(R.string.text_mon_2_nov_cash)
    val textSat31OctCancelled = stringResource(R.string.text_sat_31_oct_cancelled)
    val textNovaTerminalGate4 = stringResource(R.string.text_nova_terminal_gate_4)
    val textThu22OctCash = stringResource(R.string.text_thu_22_oct_cash)
    val textOrbitRingStation = stringResource(R.string.text_orbit_ring_station)
    val textSun18OctCash = stringResource(R.string.text_sun_18_oct_cash)
    val textSettingsTransactions = stringResource(R.string.settings_transactions)
    val textYourPaymentsAndRideTransactions = stringResource(R.string.text_your_payments_and_ride_transactions)
    val textThisWeek = stringResource(R.string.text_this_week)
    val textEarlier = stringResource(R.string.text_earlier)

    val c = RideTheme.colors
    val week = listOf(Tx(textHelixGardenPlaza, textTodayCash, "$16.80"), Tx(textAuroraMedicalCentre, textMon2NovCash, "$12.40"), Tx(textHelixGardenPlaza, textSat31OctCancelled, "$0.00", true))
    val earlier = listOf(Tx(textNovaTerminalGate4, textThu22OctCash, "$9.60"), Tx(textOrbitRingStation, textSun18OctCash, "$14.20"))
    MoreScaffold(textSettingsTransactions, onBack, subtitle = textYourPaymentsAndRideTransactions) {
        listOf(textThisWeek to week, textEarlier to earlier).forEach { (label, rows) ->
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
    val textRideUpdates = stringResource(R.string.text_ride_updates)
    val textDriverMatchingArrivalTripUpdatesAndCompletion = stringResource(R.string.text_driver_matching_arrival_trip_updates_and_completion)
    val textPromotionsOffers = stringResource(R.string.text_promotions_offers)
    val textDiscountsSpecialOffersAndNewFeatures = stringResource(R.string.text_discounts_special_offers_and_new_features)
    val textScheduledRides = stringResource(R.string.text_scheduled_rides)
    val textRemindersAndUpdatesForScheduledRides = stringResource(R.string.text_reminders_and_updates_for_scheduled_rides)
    val textMenuPayments = stringResource(R.string.menu_payments)
    val textPaymentConfirmationsRefundsAndTransactionUpdates = stringResource(R.string.text_payment_confirmations_refunds_and_transaction_updates)
    val textAccountUpdates = stringResource(R.string.text_account_updates)
    val textProfileChangesSecurityAlertsAndImportantAccountInformation = stringResource(R.string.text_profile_changes_security_alerts_and_important_account_information)
    val textNotificationSettings = stringResource(R.string.text_notification_settings)
    val textChooseWhatNotificationsYouWantToReceive = stringResource(R.string.text_choose_what_notifications_you_want_to_receive)
    val textPushNotifications = stringResource(R.string.text_push_notifications)
    val textReceiveImportantUpdatesRideAlertsAndAccountNotifications = stringResource(R.string.text_receive_important_updates_ride_alerts_and_account_notifications)
    val textRideNotifications = stringResource(R.string.text_ride_notifications)
    val textAccountNotifications = stringResource(R.string.text_account_notifications)
    val textPhoneNotificationSettings = stringResource(R.string.text_phone_notification_settings)
    val textSoundsChannelsAndTheLockScreen = stringResource(R.string.text_sounds_channels_and_the_lock_screen)

    val c = RideTheme.colors
    val context = LocalContext.current
    val push = rememberPref("n_push", true)
    val items = listOf(
        Triple("n_rides", textRideUpdates, textDriverMatchingArrivalTripUpdatesAndCompletion),
        Triple("n_promo", textPromotionsOffers, textDiscountsSpecialOffersAndNewFeatures),
        Triple("n_sched", textScheduledRides, textRemindersAndUpdatesForScheduledRides),
        Triple("n_pay", textMenuPayments, textPaymentConfirmationsRefundsAndTransactionUpdates),
        Triple("n_acct", textAccountUpdates, textProfileChangesSecurityAlertsAndImportantAccountInformation),
    )
    val states = items.map { rememberPref(it.first, it.first != "n_promo") }
    MoreScaffold(textNotificationSettings, onBack, subtitle = textChooseWhatNotificationsYouWantToReceive) {
        GroupCard(Modifier.padding(top = 16.dp)) { ToggleRow(textPushNotifications, textReceiveImportantUpdatesRideAlertsAndAccountNotifications, push.value, { push.set(context, "n_push", it) }) }
        SectionLabel(textRideNotifications)
        GroupCard { listOf(0, 1, 2).forEachIndexed { i, k -> if (i > 0) RowDivider(); ToggleRow(items[k].second, items[k].third, states[k].value && push.value, { states[k].set(context, items[k].first, it) }, enabled = push.value) } }
        SectionLabel(textAccountNotifications)
        GroupCard { listOf(3, 4).forEachIndexed { i, k -> if (i > 0) RowDivider(); ToggleRow(items[k].second, items[k].third, states[k].value && push.value, { states[k].set(context, items[k].first, it) }, enabled = push.value) } }
        GroupCard(Modifier.padding(top = 16.dp)) {
            NavRow(RideIcons.Sliders, textPhoneNotificationSettings, { runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, subtitle = textSoundsChannelsAndTheLockScreen)
        }
    }
}

// ---------- App color, Map style, Language ----------

@Composable
fun AppColorScreen(onBack: () -> Unit) {
    val textAppColor = stringResource(R.string.text_app_color)
    val textChooseYourPreferredAccentColor = stringResource(R.string.text_choose_your_preferred_accent_color)
    val textApply = stringResource(R.string.text_apply)

    val c = RideTheme.colors
    val context = LocalContext.current
    var sel by rememberSaveable { mutableStateOf(AccentPrefs.name) }
    MoreScaffold(textAppColor, onBack, subtitle = textChooseYourPreferredAccentColor, bottom = {
        RideButton(textApply, { AccentPrefs.name = sel; context.prefs().edit().putString("accent", sel).apply(); onBack() })
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
                        Text(localizedLabel(a.name), color = c.fg, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun MapStyleScreen(onBack: () -> Unit) {
    val textBalancedDetail = stringResource(R.string.text_balanced_detail)
    val textRealImagery = stringResource(R.string.text_real_imagery)
    val textFewerLabelsLessClutter = stringResource(R.string.text_fewer_labels_less_clutter)
    val textHighlightsStationsAndLines = stringResource(R.string.text_highlights_stations_and_lines)
    val textMapStyle = stringResource(R.string.text_map_style)
    val textAdjustHowMapsLookInTheApp = stringResource(R.string.text_adjust_how_maps_look_in_the_app)
    val textApply = stringResource(R.string.text_apply)

    val context = LocalContext.current
    val opts = listOf("Default" to textBalancedDetail, "Satellite" to textRealImagery, "Minimal" to textFewerLabelsLessClutter, "Transit" to textHighlightsStationsAndLines)
    var sel by rememberSaveable { mutableStateOf(context.prefs().getString("map_style", "Default") ?: "Default") }
    MoreScaffold(textMapStyle, onBack, subtitle = textAdjustHowMapsLookInTheApp, bottom = { RideButton(textApply, { context.prefs().edit().putString("map_style", sel).apply(); onBack() }) }) {
        GroupCard(Modifier.padding(top = 16.dp)) { opts.forEachIndexed { i, (n, s) -> if (i > 0) RowDivider(); Column(Modifier.padding(horizontal = 16.dp)) { RadioRow(localizedLabel(n), sel == n, { sel = n }, s) } } }
    }
}

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val textEnglish = stringResource(R.string.language_native_english)
    val textEspaOl = stringResource(R.string.language_native_spanish)
    val textSpanish = stringResource(R.string.text_spanish)
    val textFranAis = stringResource(R.string.language_native_french)
    val textFrench = stringResource(R.string.text_french)
    val textDeutsch = stringResource(R.string.language_native_german)
    val textGerman = stringResource(R.string.text_german)
    val textValue = stringResource(R.string.language_native_japanese)
    val textJapanese = stringResource(R.string.text_japanese)
    val textValue2 = stringResource(R.string.language_native_arabic)
    val textArabic = stringResource(R.string.text_arabic)
    val textValue3 = stringResource(R.string.language_native_hindi)
    val textHindi = stringResource(R.string.text_hindi)
    val textSettingsLanguage = stringResource(R.string.settings_language)
    val textChooseTheLanguageForRidenow = stringResource(R.string.text_choose_the_language_for_ridenow)
    val textSave = stringResource(R.string.text_save)

    val context = LocalContext.current
    val langs = listOf(
        Triple("en", textEnglish, textEnglish), Triple("es", textEspaOl, textSpanish), Triple("fr", textFranAis, textFrench),
        Triple("de", textDeutsch, textGerman), Triple("ja", textValue, textJapanese), Triple("ar", textValue2, textArabic),
        Triple("hi", textValue3, textHindi),
    )
    var sel by rememberSaveable { mutableStateOf(context.prefs().getString("lang", "en") ?: "en") }
    val activity = context as? android.app.Activity
    MoreScaffold(textSettingsLanguage, onBack, subtitle = textChooseTheLanguageForRidenow, bottom = {
        RideButton(textSave, {
            val saved = context.prefs().getString("lang", "en") ?: "en"
            if (sel != saved) {
                context.prefs().edit().putString("lang", sel).commit()
                activity?.recreate() // restart the activity so the new language applies everywhere
            } else onBack()
        })
    }) {
        GroupCard(Modifier.padding(top = 16.dp)) {
            langs.forEachIndexed { i, (code, name, english) ->
                if (i > 0) RowDivider()
                Column(Modifier.padding(horizontal = 16.dp)) { RadioRow(name, sel == code, { sel = code }, english) }
            }
        }
    }
}

// ---------- Privacy & Security ----------

@Composable
fun PrivacyHubScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val textSettingsPrivacy = stringResource(R.string.settings_privacy)
    val textControlYourDataAndKeepYourAccountSafe = stringResource(R.string.text_control_your_data_and_keep_your_account_safe)
    val textAccountSecurity = stringResource(R.string.text_account_security)
    val textChangePhoneNumber = stringResource(R.string.text_change_phone_number)
    val textVerifyANewNumberWithACode = stringResource(R.string.text_verify_a_new_number_with_a_code)
    val textManageDevices = stringResource(R.string.text_manage_devices)
    val textViewAndManageYourLoggedInDevices = stringResource(R.string.text_view_and_manage_your_logged_in_devices)
    val textTwoStepVerification = stringResource(R.string.text_two_step_verification)
    val textAddAnExtraLayerOfSecurity = stringResource(R.string.text_add_an_extra_layer_of_security)
    val textPrivacy = stringResource(R.string.text_privacy)
    val textAppDataPermissions = stringResource(R.string.text_app_data_permissions)
    val textManageHowYourDataIsUsed = stringResource(R.string.text_manage_how_your_data_is_used)
    val textLocationData = stringResource(R.string.text_location_data)
    val textControlLocationAccessForBetterService = stringResource(R.string.text_control_location_access_for_better_service)
    val textAccount = stringResource(R.string.text_account)
    val textDownloadMyData = stringResource(R.string.text_download_my_data)
    val textGetACopyOfYourAccountData = stringResource(R.string.text_get_a_copy_of_your_account_data)
    val textDeleteAccount = stringResource(R.string.text_delete_account)
    val textPermanentlyDeleteYourAccount = stringResource(R.string.text_permanently_delete_your_account)

    MoreScaffold(textSettingsPrivacy, onBack, subtitle = textControlYourDataAndKeepYourAccountSafe) {
        SectionLabel(textAccountSecurity)
        GroupCard {
            NavRow(RideIcons.Phone, textChangePhoneNumber, { open("Change phone number") }, subtitle = textVerifyANewNumberWithACode); RowDivider()
            NavRow(RideIcons.Phone, textManageDevices, { open("Manage Devices") }, subtitle = textViewAndManageYourLoggedInDevices); RowDivider()
            NavRow(RideIcons.Shield, textTwoStepVerification, { open("Two-step verification") }, subtitle = textAddAnExtraLayerOfSecurity)
        }
        SectionLabel(textPrivacy)
        GroupCard {
            NavRow(RideIcons.Lock, textAppDataPermissions, { open("App data and permissions") }, subtitle = textManageHowYourDataIsUsed); RowDivider()
            NavRow(RideIcons.Pin, textLocationData, { open("Location data") }, subtitle = textControlLocationAccessForBetterService)
        }
        SectionLabel(textAccount)
        GroupCard {
            NavRow(RideIcons.Download, textDownloadMyData, { open("Download my data") }, subtitle = textGetACopyOfYourAccountData); RowDivider()
            NavRow(RideIcons.Trash, textDeleteAccount, { open("Delete account") }, subtitle = textPermanentlyDeleteYourAccount, danger = true)
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- Manage devices ----------

@Composable
fun ManageDevicesScreen(onBack: () -> Unit) {
    val textTablet = stringResource(R.string.text_tablet)
    val textLastActive2DaysAgo = stringResource(R.string.text_last_active_2_days_ago)
    val textManageDevices = stringResource(R.string.text_manage_devices)
    val textDevicesWhereYouAreSignedIn = stringResource(R.string.text_devices_where_you_are_signed_in)
    val textLogOutOfAllOtherDevices = stringResource(R.string.text_log_out_of_all_other_devices)
    val textLoggedOutOfOtherDevices = stringResource(R.string.text_logged_out_of_other_devices)
    val textThisDevice = stringResource(R.string.text_this_device)
    val textThisPhone = stringResource(R.string.text_this_phone)
    val textActiveNowOrbitCity = stringResource(R.string.text_active_now_orbit_city)
    val textCurrent = stringResource(R.string.text_current)
    val textOtherDevices = stringResource(R.string.text_other_devices)
    val textNoOtherDevicesAreSignedIn = stringResource(R.string.text_no_other_devices_are_signed_in)
    val textLogOutValue = stringResource(R.string.text_log_out_value)
    val textLogOut = stringResource(R.string.text_log_out)

    val c = RideTheme.colors
    val context = LocalContext.current
    val others = remember { mutableStateListOf(textTablet to textLastActive2DaysAgo) }
    MoreScaffold(textManageDevices, onBack, subtitle = textDevicesWhereYouAreSignedIn, bottom = {
        if (others.isNotEmpty()) DangerButton(textLogOutOfAllOtherDevices, { others.clear(); context.toast(textLoggedOutOfOtherDevices) })
    }) {
        SectionLabel(textThisDevice)
        GroupCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Phone, c.accent); Column(Modifier.padding(start = 14.dp).weight(1f)) { Text(textThisPhone, color = c.fg, fontSize = 15.sp); Text(textActiveNowOrbitCity, color = c.muted, fontSize = 12.sp) }
                Text(textCurrent, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        SectionLabel(textOtherDevices)
        if (others.isEmpty()) Text(textNoOtherDevicesAreSignedIn, color = c.muted, fontSize = 14.sp)
        else GroupCard {
            others.forEachIndexed { i, (n, s) ->
                if (i > 0) RowDivider()
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Phone, c.muted); Column(Modifier.padding(start = 14.dp).weight(1f).padding(vertical = 12.dp)) { Text(n, color = c.fg, fontSize = 15.sp); Text(s, color = c.muted, fontSize = 12.sp) }
                    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { others.removeAt(i) }.padding(horizontal = 16.dp).semantics { contentDescription = textLogOutValue.format(n) }, contentAlignment = Alignment.Center) { Text(textLogOut, color = c.danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// ---------- Two-step verification ----------

@Composable
fun TwoStepScreen(onBack: () -> Unit, phone: String) {
    val textTwoStepVerification = stringResource(R.string.text_two_step_verification)
    val textAddAnExtraLayerOfSecurity2 = stringResource(R.string.text_add_an_extra_layer_of_security_2)
    val textRequireACodeOnNewDevices = stringResource(R.string.text_require_a_code_on_new_devices)
    val textAskForACodeWhenSigningInElsewhere = stringResource(R.string.text_ask_for_a_code_when_signing_in_elsewhere)
    val textMethod = stringResource(R.string.text_method)
    val textMessageSms = stringResource(R.string.text_text_message_sms)
    val textCodesSentToValue = stringResource(R.string.text_codes_sent_to_value)
    val textAuthenticatorApp = stringResource(R.string.text_authenticator_app)
    val textUseAOneTimeCodeApp = stringResource(R.string.text_use_a_one_time_code_app)
    val textBackup = stringResource(R.string.text_backup)
    val textRecoveryCodes = stringResource(R.string.text_recovery_codes)
    val textUseTheseIfYouLoseYourPhone = stringResource(R.string.text_use_these_if_you_lose_your_phone)
    val textEachCodeWorksOnceStoreThemSomewhereSafe = stringResource(R.string.text_each_code_works_once_store_them_somewhere_safe)
    val textCopied = stringResource(R.string.text_copied)
    val textCopy = stringResource(R.string.text_copy)
    val textClose = stringResource(R.string.text_close)

    val c = RideTheme.colors
    val context = LocalContext.current
    val on = rememberPref("ts_on", false)
    var method by rememberSaveable { mutableIntStateOf(0) }
    var codes by remember { mutableStateOf<List<String>?>(null) }
    val clip = LocalClipboardManager.current
    MoreScaffold(textTwoStepVerification, onBack, subtitle = textAddAnExtraLayerOfSecurity2) {
        GroupCard(Modifier.padding(top = 16.dp)) { ToggleRow(textRequireACodeOnNewDevices, textAskForACodeWhenSigningInElsewhere, on.value, { on.set(context, "ts_on", it) }) }
        SectionLabel(textMethod)
        GroupCard {
            Column(Modifier.padding(horizontal = 16.dp)) {
                RadioRow(textMessageSms, method == 0, { method = 0 }, textCodesSentToValue.format(phone)); RowDivider()
                RadioRow(textAuthenticatorApp, method == 1, { method = 1 }, textUseAOneTimeCodeApp)
            }
        }
        SectionLabel(textBackup)
        GroupCard { NavRow(RideIcons.Doc, textRecoveryCodes, { codes = List(8) { "%04d-%04d".format((0..9999).random(), (0..9999).random()) } }, subtitle = textUseTheseIfYouLoseYourPhone) }
        Spacer(Modifier.height(16.dp))
    }
    codes?.let { list ->
        AlertDialog(
            onDismissRequest = { codes = null }, title = { Text(textRecoveryCodes) },
            text = { Text(textEachCodeWorksOnceStoreThemSomewhereSafe + list.joinToString("\n")) },
            confirmButton = { TextButton({ clip.setText(AnnotatedString(list.joinToString("\n"))); context.toast(textCopied); codes = null }) { Text(textCopy) } },
            dismissButton = { TextButton({ codes = null }) { Text(textClose) } },
        )
    }
}

// ---------- App data & permissions ----------

@Composable
fun AppDataScreen(onBack: () -> Unit) {
    val textLocation = stringResource(R.string.text_location)
    val textPickupAndLiveTripTracking = stringResource(R.string.text_pickup_and_live_trip_tracking)
    val textWhileUsing = stringResource(R.string.text_while_using)
    val textNotAllowed = stringResource(R.string.text_not_allowed)
    val textMenuNotifications = stringResource(R.string.menu_notifications)
    val textRideAlertsAndUpdates = stringResource(R.string.text_ride_alerts_and_updates)
    val textAllowed = stringResource(R.string.text_allowed)
    val textCamera = stringResource(R.string.text_camera)
    val textPhotoTitle = stringResource(R.string.photo_title)
    val textAskEachTime = stringResource(R.string.text_ask_each_time)
    val textContacts = stringResource(R.string.text_contacts)
    val textTrustedContactsAndFriendRides = stringResource(R.string.text_trusted_contacts_and_friend_rides)
    val textMicrophone = stringResource(R.string.text_microphone)
    val textAudioRecordingDuringTrips = stringResource(R.string.text_audio_recording_during_trips)
    val textAppDataPermissions = stringResource(R.string.text_app_data_permissions)
    val textManageHowYourDataIsUsed2 = stringResource(R.string.text_manage_how_your_data_is_used_2)
    val textPermissions = stringResource(R.string.text_permissions)
    val textDataUse = stringResource(R.string.text_data_use)
    val textPersonalisedOffers = stringResource(R.string.text_personalised_offers)
    val textUseRideActivityToTailorOffers = stringResource(R.string.text_use_ride_activity_to_tailor_offers)
    val textUsageAnalytics = stringResource(R.string.text_usage_analytics)
    val textHelpImproveRidenow = stringResource(R.string.text_help_improve_ridenow)

    val c = RideTheme.colors
    val context = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val notif = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
    val rows = listOf(
        Triple(textLocation, textPickupAndLiveTripTracking, if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) textWhileUsing else textNotAllowed),
        Triple(textMenuNotifications, textRideAlertsAndUpdates, if (notif) textAllowed else textNotAllowed),
        Triple(textCamera, textPhotoTitle, if (granted(Manifest.permission.CAMERA)) textAllowed else textAskEachTime),
        Triple(textContacts, textTrustedContactsAndFriendRides, if (granted(Manifest.permission.READ_CONTACTS)) textAllowed else textAskEachTime),
        Triple(textMicrophone, textAudioRecordingDuringTrips, if (granted(Manifest.permission.RECORD_AUDIO)) textAllowed else textAskEachTime),
    )
    val offers = rememberPref("d_offers", false); val analytics = rememberPref("d_analytics", true)
    MoreScaffold(textAppDataPermissions, onBack, subtitle = textManageHowYourDataIsUsed2) {
        SectionLabel(textPermissions)
        GroupCard {
            rows.forEachIndexed { i, (t, s, st) ->
                if (i > 0) RowDivider()
                NavRow(RideIcons.Lock, t, { context.openAppSettings() }, subtitle = s, trailing = { Text(st, color = c.muted, fontSize = 13.sp) })
            }
        }
        SectionLabel(textDataUse)
        GroupCard {
            ToggleRow(textPersonalisedOffers, textUseRideActivityToTailorOffers, offers.value, { offers.set(context, "d_offers", it) }); RowDivider()
            ToggleRow(textUsageAnalytics, textHelpImproveRidenow, analytics.value, { analytics.set(context, "d_analytics", it) })
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- Location data ----------

@Composable
fun LocationDataScreen(onBack: () -> Unit) {
    val textLocationData = stringResource(R.string.text_location_data)
    val textControlLocationAccessForBetterService2 = stringResource(R.string.text_control_location_access_for_better_service_2)
    val textAlways = stringResource(R.string.text_always)
    val textTrackEvenWhenTheAppIsClosed = stringResource(R.string.text_track_even_when_the_app_is_closed)
    val textWhileUsingTheApp = stringResource(R.string.text_while_using_the_app)
    val textRecommended = stringResource(R.string.text_recommended)
    val textNever = stringResource(R.string.text_never)
    val textYouMustEnterPickupsManually = stringResource(R.string.text_you_must_enter_pickups_manually)
    val textPreciseLocation = stringResource(R.string.text_precise_location)
    val textNeededToPickYouUpAtTheRightSpot = stringResource(R.string.text_needed_to_pick_you_up_at_the_right_spot)
    val textLocationHistory = stringResource(R.string.text_location_history)
    val textClearPlacesSavedFromRides = stringResource(R.string.text_clear_places_saved_from_rides)
    val textLocationHistoryCleared = stringResource(R.string.text_location_history_cleared)
    val textCleared = stringResource(R.string.text_cleared)
    val textSearchClear = stringResource(R.string.search_clear)
    val textPhoneLocationPermission = stringResource(R.string.text_phone_location_permission)
    val textChangeWhatTheSystemAllows = stringResource(R.string.text_change_what_the_system_allows)

    val c = RideTheme.colors
    val context = LocalContext.current
    var mode by rememberSaveable { mutableIntStateOf(context.prefs().getInt("loc_mode", 1)) }
    val precise = rememberPref("loc_precise", true)
    var cleared by remember { mutableStateOf(false) }
    MoreScaffold(textLocationData, onBack, subtitle = textControlLocationAccessForBetterService2) {
        GroupCard(Modifier.padding(top = 16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                RadioRow(textAlways, mode == 0, { mode = 0; context.prefs().edit().putInt("loc_mode", 0).apply() }, textTrackEvenWhenTheAppIsClosed); RowDivider()
                RadioRow(textWhileUsingTheApp, mode == 1, { mode = 1; context.prefs().edit().putInt("loc_mode", 1).apply() }, textRecommended); RowDivider()
                RadioRow(textNever, mode == 2, { mode = 2; context.prefs().edit().putInt("loc_mode", 2).apply() }, textYouMustEnterPickupsManually)
            }
        }
        GroupCard(Modifier.padding(top = 16.dp)) {
            ToggleRow(textPreciseLocation, textNeededToPickYouUpAtTheRightSpot, precise.value, { precise.set(context, "loc_precise", it) }); RowDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(textLocationHistory, color = c.fg, fontSize = 15.sp); Text(textClearPlacesSavedFromRides, color = c.muted, fontSize = 12.sp) }
                Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { cleared = true; context.toast(textLocationHistoryCleared) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text(if (cleared) textCleared else textSearchClear, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        GroupCard(Modifier.padding(top = 16.dp)) { NavRow(RideIcons.Sliders, textPhoneLocationPermission, { context.openAppSettings() }, subtitle = textChangeWhatTheSystemAllows) }
    }
}

// ---------- Download my data ----------

@Composable
fun DownloadDataScreen(onBack: () -> Unit) {
    val textSettingsProfile = stringResource(R.string.settings_profile)
    val textNamePhoneNumberAndEmail = stringResource(R.string.text_name_phone_number_and_email)
    val textRideHistory = stringResource(R.string.text_ride_history)
    val textTripsAndReceipts = stringResource(R.string.text_trips_and_receipts)
    val textMenuPayments = stringResource(R.string.menu_payments)
    val textTransactionsAndPaymentMethods = stringResource(R.string.text_transactions_and_payment_methods)
    val textSavedPlaces = stringResource(R.string.text_saved_places)
    val textMenuSavedSub = stringResource(R.string.menu_saved_sub)
    val textDownloadMyData = stringResource(R.string.text_download_my_data)
    val textDone = stringResource(R.string.text_done)
    val textRequestReceived = stringResource(R.string.text_request_received)
    val textYouWillGetANotificationWhenYourFileIsReadyToDownload = stringResource(R.string.text_you_will_get_a_notification_when_your_file_is_ready_to_download)
    val textGetACopyOfYourAccountData2 = stringResource(R.string.text_get_a_copy_of_your_account_data_2)
    val textRequestDataCopy = stringResource(R.string.text_request_data_copy)
    val textInclude = stringResource(R.string.text_include)

    val c = RideTheme.colors
    val items = listOf(textSettingsProfile to textNamePhoneNumberAndEmail, textRideHistory to textTripsAndReceipts, textMenuPayments to textTransactionsAndPaymentMethods, textSavedPlaces to textMenuSavedSub)
    val on = remember { mutableStateListOf(true, true, true, true) }
    var requested by rememberSaveable { mutableStateOf(false) }
    if (requested) {
        MoreScaffold(textDownloadMyData, onBack, bottom = { RideButton(textDone, onBack) }) { Hero(RideIcons.Check, textRequestReceived, textYouWillGetANotificationWhenYourFileIsReadyToDownload) }
        return
    }
    MoreScaffold(textDownloadMyData, onBack, subtitle = textGetACopyOfYourAccountData2, bottom = {
        RideButton(textRequestDataCopy, { requested = true }, enabled = on.any { it })
        Text(textYouWillGetANotificationWhenYourFileIsReadyToDownload, color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }) {
        SectionLabel(textInclude)
        GroupCard { items.forEachIndexed { i, (t, s) -> if (i > 0) RowDivider(); ToggleRow(t, s, on[i], { on[i] = it }) } }
    }
}

// ---------- Delete account ----------

@Composable
fun DeleteAccountScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    val textYourProfile = stringResource(R.string.text_your_profile)
    val textNamePhoneNumberAndAccountDetails = stringResource(R.string.text_name_phone_number_and_account_details)
    val textMenuSaved = stringResource(R.string.menu_saved)
    val textAllYourSavedLocations = stringResource(R.string.text_all_your_saved_locations)
    val textRideHistory2 = stringResource(R.string.text_ride_history_2)
    val textAllYourPastRidesAndRelatedData = stringResource(R.string.text_all_your_past_rides_and_related_data)
    val textSettingsPayments = stringResource(R.string.settings_payments)
    val textAllSavedPaymentMethods = stringResource(R.string.text_all_saved_payment_methods)
    val textAppPreferences = stringResource(R.string.text_app_preferences)
    val textSettingsNotificationsAndOtherPreferences = stringResource(R.string.text_settings_notifications_and_other_preferences)
    val textDeleteAccount = stringResource(R.string.text_delete_account)
    val textWeAreSorryToSeeYouGoDeletingYourAccountWillPermanentlyRemove = stringResource(R.string.text_we_are_sorry_to_see_you_go_deleting_your_account_will_permanently_remove)
    val textDeleteAccount2 = stringResource(R.string.text_delete_account_2)
    val textThisActionCannotBeUndone = stringResource(R.string.text_this_action_cannot_be_undone)
    val textAllYourDataWillBePermanentlyDeleted = stringResource(R.string.text_all_your_data_will_be_permanently_deleted)
    val textWhatWillBeDeleted = stringResource(R.string.text_what_will_be_deleted)
    val textAnyPendingRidesPaymentsOrRefundsMustBeCompletedBeforeYouCanD = stringResource(R.string.text_any_pending_rides_payments_or_refunds_must_be_completed_before_you_can_d)

    val c = RideTheme.colors
    val items = listOf(textYourProfile to textNamePhoneNumberAndAccountDetails, textMenuSaved to textAllYourSavedLocations, textRideHistory2 to textAllYourPastRidesAndRelatedData, textSettingsPayments to textAllSavedPaymentMethods, textAppPreferences to textSettingsNotificationsAndOtherPreferences)
    MoreScaffold(textDeleteAccount, onBack, subtitle = textWeAreSorryToSeeYouGoDeletingYourAccountWillPermanentlyRemove, bottom = { DangerButton(textDeleteAccount2, onContinue) }) {
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.danger.copy(alpha = 0.12f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(RideIcons.Alert, c.danger); Column(Modifier.padding(start = 12.dp)) { Text(textThisActionCannotBeUndone, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(textAllYourDataWillBePermanentlyDeleted, color = c.muted, fontSize = 13.sp) }
        }
        SectionLabel(textWhatWillBeDeleted)
        GroupCard { items.forEachIndexed { i, (t, s) -> if (i > 0) RowDivider(); Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { Text(t, color = c.fg, fontSize = 15.sp); Text(s, color = c.muted, fontSize = 12.sp) } } }
        Text(textAnyPendingRidesPaymentsOrRefundsMustBeCompletedBeforeYouCanD, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    }
}

@Composable
fun ConfirmDeletionScreen(phone: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val textConfirmDeletion = stringResource(R.string.text_confirm_deletion)
    val textEnterTheCodeSentToValueToDeleteYourAccount = stringResource(R.string.text_enter_the_code_sent_to_value_to_delete_your_account)
    val textDeleteAccount2 = stringResource(R.string.text_delete_account_2)
    val textKeepMyAccount = stringResource(R.string.text_keep_my_account)
    val textResendCodeInValue = stringResource(R.string.text_resend_code_in_value)
    val textOtpResend = stringResource(R.string.otp_resend)
    val textThisPermanentlyDeletesYourDataAndCannotBeUndone = stringResource(R.string.text_this_permanently_deletes_your_data_and_cannot_be_undone)

    val c = RideTheme.colors
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    var round by remember { mutableIntStateOf(0) }
    val left = key(round) { rememberCountdown(24) }
    MoreScaffold(textConfirmDeletion, onBack, subtitle = textEnterTheCodeSentToValueToDeleteYourAccount.format(phone), bottom = {
        DangerButton(textDeleteAccount2, {
            val seen = context.prefs().getBoolean("intro_seen", false)
            context.prefs().edit().clear().putBoolean("intro_seen", seen).apply(); AccentPrefs.name = "Default"; onDeleted()
        }, enabled = code.length == 6)
        RideButton(textKeepMyAccount, onBack, Modifier.padding(top = 8.dp), style = RideButtonStyle.Text)
    }) {
        Box(Modifier.padding(top = 24.dp)) { CodeField(code, { code = it }, 6) }
        if (left > 0) Text(textResendCodeInValue.format(mmss(left)), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
        else Text(textOtpResend, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).clickable(role = Role.Button) { round++; code = "" }.wrapContentHeight(Alignment.CenterVertically))
        Text(textThisPermanentlyDeletesYourDataAndCannotBeUndone, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun AccountDeletedScreen(onClose: () -> Unit) {
    val textClose = stringResource(R.string.text_close)
    val textAccountDeleted = stringResource(R.string.text_account_deleted)
    val textWeAreSorryToSeeYouGoYouCanCreateANewAccountAnyTime = stringResource(R.string.text_we_are_sorry_to_see_you_go_you_can_create_a_new_account_any_time)

    androidx.activity.compose.BackHandler(onBack = onClose)
    MoreScaffold("", onClose, bottom = { RideButton(textClose, onClose) }) { Hero(RideIcons.Check, textAccountDeleted, textWeAreSorryToSeeYouGoYouCanCreateANewAccountAnyTime) }
}
