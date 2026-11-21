package com.ridenow.app.ui.more

import com.ridenow.app.getAppLanguage
import com.ridenow.app.setAppLanguage

import com.ridenow.app.R
import com.ridenow.app.ui.auth.SecondFactorDialog
import com.ridenow.app.ui.auth.VerificationCodeField
import com.ridenow.app.ui.auth.secureFactorDialog
import org.json.JSONObject
import com.ridenow.app.ui.auth.AccountDeletionVerification
import com.ridenow.app.ui.saved.RiderSettingsApi
import com.ridenow.app.ui.map.fetchRiderPosition
import com.ridenow.app.ui.notifications.rememberNotificationOptions
import com.ridenow.app.ui.notifications.NotificationOptionsStatus
import com.ridenow.app.ui.auth.PhoneAuthApi
import com.ridenow.app.ui.auth.PhoneAuthException
import kotlinx.coroutines.CancellationException
import com.ridenow.app.ui.permissions.RiderLocationOffScreen
import com.ridenow.app.ui.permissions.deviceLocationEnabled
import com.ridenow.app.ui.permissions.rememberDeviceLocationEnabled
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
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.ridenow.app.ui.theme.LocalAppearance
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
fun ChangePhoneScreen(onBack: () -> Unit, onSend: (String, String) -> Unit) {
    val textChangePhoneNumber = stringResource(R.string.text_change_phone_number)
    val textWeWillSendACodeToYourNewNumber = stringResource(R.string.phone_change_both_message)
    val failureText = stringResource(R.string.phone_change_failed)
    val waitText = stringResource(R.string.phone_change_wait)
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
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    MoreScaffold(textChangePhoneNumber, onBack, subtitle = textWeWillSendACodeToYourNewNumber, bottom = {
        RideButton(textPhoneSend, {
            if (!sending) scope.launch {
                sending = true
                failure = null
                try {
                    val result = PhoneAuthApi.requestPhoneChange(context, country.dial + digits)
                    onSend(result.getString("phone"), result.getString("changeId"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure = if ((error as? PhoneAuthException)?.status == 429) waitText else failureText
                } finally {
                    sending = false
                }
            }
        }, enabled = valid && !sending, loading = sending)
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
        failure?.let { Text(it, color = c.danger, modifier = Modifier.padding(top = 12.dp)) }
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
fun VerifyNewNumberScreen(currentPhone: String, newPhone: String, changeId: String, onBack: () -> Unit,
    onVerified: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = RideTheme.colors
    val updatedText = stringResource(R.string.text_phone_number_updated)
    val failureText = stringResource(R.string.phone_change_failed)
    val waitText = stringResource(R.string.phone_change_wait)
    var currentCode by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var resending by remember { mutableStateOf(false) }
    var activeChangeId by rememberSaveable(changeId) { mutableStateOf(changeId) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val left = key(activeChangeId) { rememberCountdown(60) }

    fun verify(current: String, next: String) {
        if (verifying || resending || current.length != 6 || next.length != 6 || activeChangeId.isBlank()) return
        verifying = true
        errorText = null
        scope.launch {
            try {
                val verified = PhoneAuthApi.verifyPhoneChange(context, activeChangeId, current, next)
                context.toast(updatedText)
                onVerified(verified)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                errorText = failureText
                currentCode = ""; newCode = ""
            } finally {
                verifying = false
            }
        }
    }
    MoreScaffold(stringResource(R.string.text_verify_new_number), onBack,
        subtitle = stringResource(R.string.phone_change_both_message), bottom = {
            RideButton(stringResource(R.string.text_verify), { verify(currentCode, newCode) },
                enabled = currentCode.length == 6 && newCode.length == 6 && !verifying && !resending, loading = verifying)
        }) {
        Text(stringResource(R.string.phone_change_current_code, currentPhone), color = c.fg, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        CodeField(currentCode, { if (!verifying && !resending) { currentCode = it; verify(it, newCode) } }, 6)
        Text(stringResource(R.string.phone_change_new_code, newPhone), color = c.fg, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        CodeField(newCode, { if (!verifying && !resending) { newCode = it; verify(currentCode, it) } }, 6)
        errorText?.let { Text(it, color = c.danger, modifier = Modifier.padding(top = 12.dp)) }
        if (left > 0) Text(stringResource(R.string.text_resend_code_in_value).format(mmss(left)), color = c.muted, modifier = Modifier.padding(top = 16.dp))
        else Text(stringResource(R.string.otp_resend), color = c.accent, modifier = Modifier.padding(top = 8.dp)
            .heightIn(min = 48.dp).clickable(enabled = !verifying && !resending, role = Role.Button) {
                resending = true
                errorText = null
                scope.launch {
                    try {
                        val result = PhoneAuthApi.requestPhoneChange(context, newPhone)
                        activeChangeId = result.getString("changeId")
                        currentCode = ""; newCode = ""
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        errorText = if ((error as? PhoneAuthException)?.status == 429) waitText else failureText
                    } finally {
                        resending = false
                    }
                }
            }.wrapContentHeight(Alignment.CenterVertically))
    }
}

// ---------- Current city ----------

@Suppress("DEPRECATION")
@Composable
fun CityPickerScreen(onBack: () -> Unit, currentCity: String?, onSaved: (String?) -> Unit) {
    val title = stringResource(R.string.text_current_city)
    val c = RideTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationOn = rememberDeviceLocationEnabled()
    var locationRequested by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(currentCity) }
    var changed by rememberSaveable { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<String>()) }
    val noLocation = stringResource(R.string.text_could_not_detect_your_city)
    val onExit = { if (!saving) onBack() }

    fun detect() {
        if (!deviceLocationEnabled(context)) {
            locationRequested = true
            return
        }
        if (detecting || saving) return
        detecting = true
        scope.launch {
            try {
                val found = detectCity(context)
                if (!deviceLocationEnabled(context)) locationRequested = true
                else if (found != null) { selected = found; changed = true; query = "" }
                else context.toast(noLocation)
            } finally { detecting = false }
        }
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { if (it.values.any { granted -> granted }) detect() else context.toast(noLocation) }

    LaunchedEffect(currentCity) { if (!changed) selected = currentCity }
    LaunchedEffect(query) {
        results = emptyList()
        val value = query.trim()
        if (value.length >= 3) {
            delay(350)
            results = withContext(Dispatchers.IO) {
                runCatching { Geocoder(context).getFromLocationName(value, 6).orEmpty() }.getOrDefault(emptyList())
                    .mapNotNull { it.locality ?: it.subAdminArea ?: it.adminArea }.map { it.take(100) }.distinct()
            }
        }
    }
    androidx.activity.compose.BackHandler(onBack = onExit)
    Box(Modifier.fillMaxSize()) {
        MoreScaffold(title, onExit, subtitle = stringResource(R.string.text_search_for_your_city), bottom = {
            RideButton(stringResource(R.string.text_save), {
                saving = true
                failed = false
                scope.launch {
                    try {
                        val saved = RiderSettingsApi.updateCity(context, selected)
                        onSaved(saved)
                        onBack()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { saving = false }
                }
            }, enabled = changed && !saving && !detecting, loading = saving)
        }) {
            RideTextField(query, { query = it.take(100) }, stringResource(R.string.text_search_city), RideIcons.Search, Modifier.padding(top = 16.dp))
            GroupCard(Modifier.padding(top = 12.dp)) {
                NavRow(RideIcons.Pin, stringResource(R.string.text_use_current_location), {
                    if (!deviceLocationEnabled(context)) locationRequested = true
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    } else detect()
                }, subtitle = stringResource(if (detecting) R.string.text_detecting else R.string.text_detect_automatically), trailing = {})
            }
            SectionLabel(stringResource(R.string.text_results))
            val choices = (if (query.isBlank()) listOfNotNull(selected) else results + query.trim()).filter { it.isNotBlank() }.distinct()
            if (choices.isNotEmpty()) GroupCard {
                choices.forEachIndexed { i, city ->
                    if (i > 0) RowDivider()
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        RadioRow(city, selected == city, { if (!saving) { selected = city; changed = true } })
                    }
                }
            }
            if (selected != null) TextButton(onClick = { selected = null; query = ""; changed = true }, enabled = !saving) {
                Text(stringResource(R.string.text_remove))
            }
            if (failed) Text(stringResource(R.string.saved_settings_failed), color = c.danger, modifier = Modifier.padding(top = 8.dp))
        }
        if (locationRequested && !locationOn) {
            val dismiss = { locationRequested = false }
            androidx.activity.compose.BackHandler(onBack = dismiss)
            RiderLocationOffScreen(onManual = dismiss)
        }
    }
    LaunchedEffect(locationOn) { if (locationOn) locationRequested = false }
}

@Suppress("DEPRECATION")
private suspend fun detectCity(context: Context): String? = try {
    val point = fetchRiderPosition(context)
    if (point == null) null else withContext(Dispatchers.IO) {
        Geocoder(context).getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()?.let {
            (it.locality ?: it.subAdminArea ?: it.adminArea)?.take(100)
        }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) { null }


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
    val options = rememberNotificationOptions()
    val items = listOf(
        Triple("rides", textRideUpdates, textDriverMatchingArrivalTripUpdatesAndCompletion),
        Triple("promotions", textPromotionsOffers, textDiscountsSpecialOffersAndNewFeatures),
        Triple("scheduled", textScheduledRides, textRemindersAndUpdatesForScheduledRides),
        Triple("payments", textMenuPayments, textPaymentConfirmationsRefundsAndTransactionUpdates),
        Triple("account", textAccountUpdates, textProfileChangesSecurityAlertsAndImportantAccountInformation),
    )
    val push = options.isOn("push")
    val editable = options.values != null && !options.busy
    MoreScaffold(textNotificationSettings, onBack, subtitle = textChooseWhatNotificationsYouWantToReceive) {
        NotificationOptionsStatus(options)
        if (options.values != null) {
            GroupCard(Modifier.padding(top = 16.dp)) {
                ToggleRow(textPushNotifications, textReceiveImportantUpdatesRideAlertsAndAccountNotifications,
                    push, { options.update("push", it) }, enabled = editable)
            }
            SectionLabel(textRideNotifications)
            GroupCard {
                items.take(3).forEachIndexed { i, (key, title, sub) ->
                    if (i > 0) RowDivider()
                    ToggleRow(title, sub, options.isOn(key) && push, { options.update(key, it) }, enabled = push && editable)
                }
            }
            SectionLabel(textAccountNotifications)
            GroupCard {
                items.drop(3).forEachIndexed { i, (key, title, sub) ->
                    if (i > 0) RowDivider()
                    ToggleRow(title, sub, options.isOn(key) && push, { options.update(key, it) }, enabled = push && editable)
                }
            }
        }
        GroupCard(Modifier.padding(top = 16.dp)) {
            NavRow(RideIcons.Sliders, textPhoneNotificationSettings, { runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, subtitle = textSoundsChannelsAndTheLockScreen)
        }
    }
}

// ---------- Language ----------

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
    val textTamil = stringResource(R.string.text_tamil)
    val textSinhala = stringResource(R.string.text_sinhala)
    val nativeTamil = stringResource(R.string.language_native_tamil)
    val nativeSinhala = stringResource(R.string.language_native_sinhala)
    val textSettingsLanguage = stringResource(R.string.settings_language)
    val textChooseTheLanguageForRidenow = stringResource(R.string.text_choose_the_language_for_ridenow)
    val textSave = stringResource(R.string.text_save)

    val context = LocalContext.current
    val langs = listOf(
        Triple("en", textEnglish, textEnglish), Triple("es", textEspaOl, textSpanish), Triple("fr", textFranAis, textFrench),
        Triple("de", textDeutsch, textGerman), Triple("ja", textValue, textJapanese), Triple("ar", textValue2, textArabic),
        Triple("hi", textValue3, textHindi),
        Triple("ta", nativeTamil, textTamil), Triple("si", nativeSinhala, textSinhala),
    )
    var sel by rememberSaveable { mutableStateOf(getAppLanguage(context)) }
    MoreScaffold(textSettingsLanguage, onBack, subtitle = textChooseTheLanguageForRidenow, bottom = {
        RideButton(textSave, {
            val saved = getAppLanguage(context)
            if (sel != saved) {
                setAppLanguage(context, sel)
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
fun ManageDevicesScreen(onBack: () -> Unit, onSessionExpired: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.text_manage_devices)
    val failure = stringResource(R.string.device_load_failed)
    val logout = stringResource(R.string.text_log_out)
    val logoutAll = stringResource(R.string.text_log_out_of_all_other_devices)
    val loggedOutAll = stringResource(R.string.text_logged_out_of_other_devices)
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

    MoreScaffold(title, onBack, subtitle = stringResource(R.string.text_devices_where_you_are_signed_in), bottom = {
        if (others.isNotEmpty() && !busy) DangerButton(logoutAll, { confirm = "" })
    }) {
        if (busy && devices == null) Text(stringResource(R.string.device_loading), color = c.muted, modifier = Modifier.padding(top = 12.dp))
        if (error) {
            Text(failure, color = c.danger, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { reload++ }, enabled = !busy) { Text(stringResource(R.string.device_retry)) }
        }
        current?.let { device ->
            SectionLabel(stringResource(R.string.text_this_device))
            GroupCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Phone, c.accent)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(label(device), color = c.fg, fontSize = 15.sp)
                        Text(details(device), color = c.muted, fontSize = 12.sp)
                    }
                    Text(stringResource(R.string.text_current), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (devices != null) {
            SectionLabel(stringResource(R.string.text_other_devices))
            if (others.isEmpty()) Text(stringResource(R.string.text_no_other_devices_are_signed_in), color = c.muted, fontSize = 14.sp)
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
                            if (target.isEmpty()) context.toast(loggedOutAll)
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

// ---------- Two-step verification ----------

@Composable
fun TwoStepScreen(onBack: () -> Unit, phone: String) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.text_two_step_verification)
    val failureText = stringResource(R.string.two_step_settings_error)
    val refresh = stringResource(R.string.device_retry)
    val recoveryTitle = stringResource(R.string.text_recovery_codes)
    val backupText = stringResource(R.string.text_use_these_if_you_lose_your_phone)
    val copyText = stringResource(R.string.text_copy)
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
    MoreScaffold(title, onBack, subtitle = stringResource(R.string.text_add_an_extra_layer_of_security_2)) {
        if (error) {
            Text(failureText, color = c.danger, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = ::load, enabled = !busy) { Text(refresh) }
        }
        if (enabled == null && !error) Text(stringResource(R.string.two_step_loading), color = c.muted, modifier = Modifier.padding(top = 12.dp))
        enabled?.let { on ->
            GroupCard(Modifier.padding(top = 16.dp)) {
                ToggleRow(title, stringResource(R.string.two_step_optional), on, { if (!busy) { if (on) operation = "/disable" else beginSetup() } })
            }
        }
        SectionLabel(stringResource(R.string.text_method))
        GroupCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.two_step_whatsapp, phone), color = c.fg)
                Text(stringResource(R.string.two_step_authenticator_method), color = c.muted)
            }
        }
        SectionLabel(stringResource(R.string.text_backup))
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
            dismissButton = { TextButton(onClick = { recoveryCodes = null }) { Text(stringResource(R.string.text_close)) } },
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
    val historyNotice = stringResource(R.string.account_delete_history)
    val textSettingsPayments = stringResource(R.string.settings_payments)
    val textAllSavedPaymentMethods = stringResource(R.string.text_all_saved_payment_methods)
    val textAppPreferences = stringResource(R.string.text_app_preferences)
    val textSettingsNotificationsAndOtherPreferences = stringResource(R.string.text_settings_notifications_and_other_preferences)
    val textDeleteAccount = stringResource(R.string.text_delete_account)
    val summary = stringResource(R.string.account_delete_summary)
    val textDeleteAccount2 = stringResource(R.string.text_delete_account_2)
    val textThisActionCannotBeUndone = stringResource(R.string.text_this_action_cannot_be_undone)
    val retentionNotice = stringResource(R.string.account_delete_retention)
    val textWhatWillBeDeleted = stringResource(R.string.text_what_will_be_deleted)
    val textAnyPendingRidesPaymentsOrRefundsMustBeCompletedBeforeYouCanD = stringResource(R.string.text_any_pending_rides_payments_or_refunds_must_be_completed_before_you_can_d)

    val c = RideTheme.colors
    val items = listOf(textYourProfile to textNamePhoneNumberAndAccountDetails, textMenuSaved to textAllYourSavedLocations, textRideHistory2 to historyNotice, textSettingsPayments to textAllSavedPaymentMethods, textAppPreferences to textSettingsNotificationsAndOtherPreferences)
    MoreScaffold(textDeleteAccount, onBack, subtitle = summary, bottom = { DangerButton(textDeleteAccount2, onContinue) }) {
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.danger.copy(alpha = 0.12f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(RideIcons.Alert, c.danger); Column(Modifier.padding(start = 12.dp)) { Text(textThisActionCannotBeUndone, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(retentionNotice, color = c.muted, fontSize = 13.sp) }
        }
        SectionLabel(textWhatWillBeDeleted)
        GroupCard { items.forEachIndexed { i, (t, s) -> if (i > 0) RowDivider(); Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { Text(t, color = c.fg, fontSize = 15.sp); Text(s, color = c.muted, fontSize = 12.sp) } } }
        Text(textAnyPendingRidesPaymentsOrRefundsMustBeCompletedBeforeYouCanD, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    }
}

@Composable
fun ConfirmDeletionScreen(phone: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    AccountDeletionVerification(phone, onBack, onDeleted = {
        val seen = context.prefs().getBoolean("intro_seen", false)
        context.prefs().edit().clear().putBoolean("intro_seen", seen).apply()
        appearance?.reload()
        onDeleted()
    }) { title, back, busy, canConfirm, confirm, fields ->
        MoreScaffold(title, back, bottom = {
            DangerButton(stringResource(R.string.text_delete_account_2), confirm, enabled = canConfirm)
            RideButton(stringResource(R.string.text_keep_my_account), back, Modifier.padding(top = 8.dp),
                enabled = !busy, style = RideButtonStyle.Text)
        }) {
            Box(Modifier.padding(top = 24.dp)) { fields() }
        }
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
