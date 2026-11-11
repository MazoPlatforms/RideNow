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
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

/** Choose where to be picked up. [places] are label-to-address pairs (current location, Home, Work, others). */
@Composable
fun ChangePickupScreen(current: String, places: List<Pair<String, String>>, onBack: () -> Unit, onConfirm: (String) -> Unit, onMap: () -> Unit, modifier: Modifier = Modifier) {
    val textChangePickup = stringResource(R.string.change_pickup)
    val textPickupTitle = stringResource(R.string.pickup_title)
    val textSearchChooseMap = stringResource(R.string.search_choose_map)

    val c = RideTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(current) }
    val q = query.trim()
    val shown = places.filter { q.isEmpty() || it.first.contains(q, true) || it.second.contains(q, true) }
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
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Spacer(Modifier.height(14.dp))
            if (shown.isEmpty()) Text(stringResource(R.string.pickup_none, q), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
            else GroupCard {
                shown.forEachIndexed { i, (label, address) ->
                    if (i > 0) RowDivider()
                    val on = address == picked
                    NavRow(
                        when (label) { "Home" -> RideIcons.Home; "Work" -> RideIcons.Work; "Current location" -> RideIcons.Pin; else -> RideIcons.Clock },
                        localizedLabel(label), { picked = address }, subtitle = address,
                        trailing = { Box(Modifier.size(20.dp).clip(CircleShape).then(if (on) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.muted, CircleShape)), contentAlignment = Alignment.Center) { if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(c.onAccent)) } },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            GroupCard { NavRow(RideIcons.Map, textSearchChooseMap, onMap) }
            Spacer(Modifier.height(16.dp))
        }
        RideButton(stringResource(R.string.pickup_confirm), { onConfirm(picked) }, modifier = Modifier.padding(vertical = 12.dp))
    }
}

/** Book a ride for someone else. [onDone] receives the friend's name. */
@Composable
fun FriendScreen(onBack: () -> Unit, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    val textFriendTitle = stringResource(R.string.friend_title)
    val textFriendSub = stringResource(R.string.friend_sub)
    val textFriendName = stringResource(R.string.friend_name)
    val textFriendPhone = stringResource(R.string.friend_phone)
    val textFriendShareSms = stringResource(R.string.friend_share_sms)

    val c = RideTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var sms by rememberSaveable { mutableStateOf(true) }
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
        RideButton(stringResource(R.string.ride_continue), { onDone(name.trim()) }, enabled = name.isNotBlank() && phone.length >= 6, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FriendPreview() = RideNowTheme(darkTheme = true) { FriendScreen({}, {}) }
