package com.ridenow.app.ui.search

import com.ridenow.app.ui.components.localizedLabel

import androidx.compose.ui.res.stringResource
import com.ridenow.app.R
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.GroupCard
import com.ridenow.app.ui.components.NavRow
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RideSwitch
import com.ridenow.app.ui.components.RideTextField
import com.ridenow.app.ui.components.RowDivider
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.map.LatLng
import com.ridenow.app.ui.map.SifricApi
import com.ridenow.app.ui.map.Suggestion
import kotlinx.coroutines.delay
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

/** Choose where to be picked up. [places] are label-to-address pairs (current location, Home, Work, others). */
@Composable
fun ChangePickupScreen(
    current: String, places: List<Pair<String, String>>, onBack: () -> Unit,
    onConfirm: (String, LatLng?) -> Unit, onMap: () -> Unit, modifier: Modifier = Modifier,
    allowCurrentLocation: () -> Boolean = { true },
    near: LatLng? = null,
) {
    val context = LocalContext.current
    val textChangePickup = stringResource(R.string.change_pickup)
    val textPickupTitle = stringResource(R.string.pickup_title)
    val textSearchChooseMap = stringResource(R.string.search_choose_map)
    val textCurrentLocation = stringResource(R.string.search_current)

    val c = RideTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable(current, places) {
        mutableStateOf(places.firstOrNull { it.first == "Current location" && current == textCurrentLocation }?.second ?: current)
    }
    var pickedPoint by remember { mutableStateOf<LatLng?>(null) }
    val q = query.trim()
    val localMatches = places.filter { q.isEmpty() || it.first.contains(q, true) || it.second.contains(q, true) }
    var remote by remember { mutableStateOf<List<Suggestion>?>(null) }
    var searchFailed by remember { mutableStateOf(false) }
    LaunchedEffect(q, near) {
        remote = null
        searchFailed = false
        if (q.length >= 2) {
            delay(250)
            val matches = SifricApi.autocomplete(context, q, near)
            searchFailed = matches == null
            remote = matches.orEmpty()
        }
    }
    val remoteMatches = remote.orEmpty().filterNot { suggestion ->
        localMatches.any { (label, address) ->
            label.equals(suggestion.name, ignoreCase = true) || address.equals(suggestion.name, ignoreCase = true)
        }
    }
    val loading = q.length >= 2 && remote == null && !searchFailed
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        ScreenTop(textChangePickup, onBack, subtitle = textPickupTitle)
        BasicTextField(
            query, { query = it }, singleLine = true, textStyle = TextStyle(color = c.fg, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(56.dp),
            decorationBox = { inner ->
                Row(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Search, c.accent); Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) { if (query.isEmpty()) Text(stringResource(R.string.pickup_search), color = c.muted, fontSize = 16.sp); inner() }
                }
            },
        )
        Spacer(Modifier.height(14.dp))
        GroupCard { NavRow(RideIcons.Map, textSearchChooseMap, onMap) }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            if (localMatches.isNotEmpty()) GroupCard {
                localMatches.forEachIndexed { i, (label, address) ->
                    if (i > 0) RowDivider()
                    val on = address == picked
                    NavRow(
                        when (label) { "Home" -> RideIcons.Home; "Work" -> RideIcons.Work; "Current location" -> RideIcons.Pin; else -> RideIcons.Clock },
                        localizedLabel(label), { if (label != "Current location" || allowCurrentLocation()) { picked = address; pickedPoint = null } },
                        subtitle = address.takeUnless { label == "Current location" },
                        trailing = { Box(Modifier.size(20.dp).clip(CircleShape).then(if (on) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.muted, CircleShape)), contentAlignment = Alignment.Center) { if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(c.onAccent)) } },
                    )
                }
            }
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.5.dp)
                }
                searchFailed -> Text(stringResource(R.string.sifric_search_unavailable), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                remoteMatches.isNotEmpty() -> GroupCard {
                    remoteMatches.forEachIndexed { i, suggestion ->
                        if (i > 0) RowDivider()
                        val address = suggestion.area.takeIf(String::isNotBlank)?.let { "${suggestion.name}, $it" } ?: suggestion.name
                        val on = picked == address
                        NavRow(
                            RideIcons.Pin, suggestion.name,
                            { picked = address; pickedPoint = suggestion.point },
                            subtitle = suggestion.area.takeIf(String::isNotBlank),
                            trailing = { Box(Modifier.size(20.dp).clip(CircleShape).then(if (on) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.muted, CircleShape)), contentAlignment = Alignment.Center) { if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(c.onAccent)) } },
                        )
                    }
                }
                q.length >= 2 && localMatches.isEmpty() -> Text(stringResource(R.string.pickup_none, q), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        RideButton(stringResource(R.string.pickup_confirm), { onConfirm(picked, pickedPoint) }, modifier = Modifier.padding(vertical = 12.dp))
    }
}

data class RidePassenger(val name: String, val phone: String, val shareBySms: Boolean)

/** Collects the passenger details used for this booking only. */
@Composable
fun FriendScreen(onBack: () -> Unit, onDone: (RidePassenger) -> Unit, initial: RidePassenger? = null, modifier: Modifier = Modifier) {
    val textFriendTitle = stringResource(R.string.friend_title)
    val textFriendSub = stringResource(R.string.friend_sub)
    val textFriendName = stringResource(R.string.friend_name)
    val textFriendPhone = stringResource(R.string.friend_phone)
    val textFriendShareSms = stringResource(R.string.friend_share_sms)

    val c = RideTheme.colors
    var name by rememberSaveable(initial?.name) { mutableStateOf(initial?.name.orEmpty()) }
    var phone by rememberSaveable(initial?.phone) { mutableStateOf(initial?.phone.orEmpty()) }
    var sms by rememberSaveable(initial?.shareBySms) { mutableStateOf(initial?.shareBySms ?: true) }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        ScreenTop(textFriendTitle, onBack, subtitle = textFriendSub)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RideTextField(name, { name = it.take(40) }, textFriendName, RideIcons.Person, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                RideTextField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(15) }, textFriendPhone, RideIcons.Phone, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done))
            }
            Row(Modifier.padding(top = 14.dp).fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Message, c.accent); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { Text(stringResource(R.string.friend_share_sms), color = c.fg, fontSize = 15.sp); Text(stringResource(R.string.friend_share_sub), color = c.muted, fontSize = 12.sp) }
                RideSwitch(sms, textFriendShareSms) { sms = it }
            }
            Row(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
                RideIcon(RideIcons.Info, c.muted, size = 18.dp); Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.friend_no_app), color = c.muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        RideButton(stringResource(R.string.ride_continue), { onDone(RidePassenger(name.trim(), phone.trim(), sms)) }, enabled = name.trim().length >= 2 && phone.count(Char::isDigit) >= 7, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FriendPreview() = RideNowTheme(darkTheme = true) { FriendScreen({}, {}) }
