package com.ridenow.app.ui.share

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
import android.net.Uri
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RideSwitch
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.safety.Contact
import com.ridenow.app.ui.rides.RideShareApi
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Pick trusted contacts, copy the live-trip link, or send it with the phone's share sheet. */
@Composable
fun ShareTripScreen(contacts: List<Contact>, rideId: String?, onBack: () -> Unit, onManage: () -> Unit, modifier: Modifier = Modifier) {
    val textTripShareLive = stringResource(R.string.trip_share_live)
    val textTrustedContactsCanFollowYourRouteInRealTime = stringResource(R.string.text_trusted_contacts_can_follow_your_route_in_real_time)
    val textAdd = stringResource(R.string.text_add)
    val textCopied = stringResource(R.string.text_copied)
    val textCopy = stringResource(R.string.text_copy)
    val textShareAutomaticallyOnEveryRide = stringResource(R.string.text_share_automatically_on_every_ride)
    val textShareLink = stringResource(R.string.text_share_link)
    val textShareWithValueContactValueOne = stringResource(R.string.extra_share_with_1_s_contact)
    val textShareWithValueContactValueMany = stringResource(R.string.extra_share_with_1_s_contacts)
    val textFollowMyRidenowTripValue = stringResource(R.string.text_follow_my_ridenow_trip_value)

    val c = RideTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { context.getSharedPreferences("ridenow", 0) }
    var picked by remember { mutableStateOf(contacts.map { it.phone }.toSet()) }
    var copied by remember { mutableStateOf(false) }
    var auto by rememberSaveable { mutableStateOf(preferences.getBoolean("prepare_trip_share", false)) }
    var link by remember(rideId) { mutableStateOf<String?>(null) }
    var expires by remember(rideId) { mutableStateOf<String?>(null) }
    var error by remember(rideId) { mutableStateOf<String?>(null) }
    var busy by remember(rideId) { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(textTripShareLive, onBack, subtitle = textTrustedContactsCanFollowYourRouteInRealTime)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Row(Modifier.padding(top = 20.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                contacts.forEach { k ->
                    val on = k.phone in picked
                    Column(Modifier.width(64.dp).clickable(role = Role.Checkbox) { picked = if (on) picked - k.phone else picked + k.phone }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(56.dp)) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(if (on) c.accent.copy(alpha = 0.14f) else c.field).border(if (on) 1.5.dp else 1.dp, if (on) c.accent else c.border, CircleShape), contentAlignment = Alignment.Center) {
                                Text(k.name.take(2).uppercase(), color = if (on) c.accent else c.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (on) Box(Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(c.accent).border(2.dp, c.bg, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Check, c.onAccent, size = 11.dp) }
                        }
                        Text(k.name, color = c.fg, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                Column(Modifier.width(64.dp).clickable(role = Role.Button, onClick = onManage), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(CircleShape).border(1.5.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Plus, c.accent) }
                    Text(textAdd, color = c.fg, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Row(Modifier.padding(top = 20.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(listOf("M8.5 11.5a3 3 0 0 0 4.2 0l2.5-2.5a3 3 0 0 0-4.2-4.2L10 5.8", "M11.5 8.5a3 3 0 0 0-4.2 0L4.8 11A3 3 0 0 0 9 15.2l1-1"), c.accent); Spacer(Modifier.width(12.dp))
                Text(link ?: stringResource(R.string.trip_share_create_prompt), color = c.fg, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.heightIn(min = 48.dp).clickable(enabled = link != null, role = Role.Button) { link?.let { clipboard.setText(AnnotatedString(it)); copied = true } }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(if (copied) textCopied else textCopy, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            expires?.let { Text(stringResource(R.string.trip_share_expires, it), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            Row(Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(textShareAutomaticallyOnEveryRide, color = c.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
                RideSwitch(auto, textShareAutomaticallyOnEveryRide) {
                    auto = it
                    preferences.edit().putBoolean("prepare_trip_share", it).apply()
                }
            }
            error?.let { Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        RideButton(
            if (link == null) stringResource(R.string.trip_share_create_link) else if (picked.isEmpty()) textShareLink else (if (picked.size != 1) textShareWithValueContactValueMany else textShareWithValueContactValueOne).format(picked.size),
            {
                if (link == null && rideId != null) {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val result = RideShareApi.create(context, rideId)
                            link = result.url
                            expires = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(result.expiresAt)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = context.getString(R.string.trip_share_create_failed) }
                        finally { busy = false }
                    }
                } else if (link != null) {
                    val message = textFollowMyRidenowTripValue.format(link)
                    val selected = contacts.filter { it.phone in picked }.map { it.phone }
                    val send = if (selected.isNotEmpty()) Intent(Intent.ACTION_SENDTO,
                        Uri.fromParts("smsto", selected.joinToString(";"), null)).putExtra("sms_body", message)
                    else Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
                    runCatching { context.startActivity(Intent.createChooser(send, textTripShareLive)) }
                        .onFailure { error = context.getString(R.string.trip_share_send_failed) }
                } else error = context.getString(R.string.trip_share_no_active_ride)
            },
            enabled = !busy,
            loading = busy,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SharePreview() = RideNowTheme(darkTheme = true) { ShareTripScreen(listOf(Contact(stringResource(R.string.text_ana), "1"), Contact(stringResource(R.string.text_ben), "2")), null, {}, {}) }
