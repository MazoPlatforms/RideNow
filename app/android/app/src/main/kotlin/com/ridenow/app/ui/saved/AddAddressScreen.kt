package com.ridenow.app.ui.saved

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
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.GroupCard
import com.ridenow.app.ui.components.NavRow
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RowDivider
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

private val Suggestions = listOf(
    "Helix Garden Plaza" to "Sector 9, Garden Loop", "Helix City Hall" to "Sector 2, Civic Square", "Hello Bakery & Café" to "Sector 7, Meridian Walk",
    "Nova Terminal, Gate 4" to "Sector 12, Orbit Ring", "Orbit Ring Station" to "Sector 1, Ring Road", "Zenith Café" to "Sector 7, Meridian Walk",
)

/** Save a Home, Work or other place. [onSave] receives the label and address. */
@Composable
fun AddAddressScreen(initialLabel: String, onBack: () -> Unit, onSave: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var label by rememberSaveable { mutableStateOf(initialLabel) }
    var query by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val hits = if (q.isEmpty()) emptyList() else Suggestions.filter { it.first.contains(q, true) || it.second.contains(q, true) }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        ScreenTop("Add ${label.lowercase()} address", onBack, subtitle = "Save it once for one-tap rides.")
        BasicTextField(
            query, { query = it; address = "" }, singleLine = true, textStyle = TextStyle(color = c.fg, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(56.dp),
            decorationBox = { inner ->
                Row(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Search, c.accent); Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) { if (query.isEmpty()) Text("Search address", color = c.muted, fontSize = 16.sp); inner() }
                }
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Spacer(Modifier.height(12.dp))
            GroupCard { NavRow(RideIcons.Pin, "Use current location", { address = "128 Meridian Walk, Sector 7"; query = "" }, subtitle = "128 Meridian Walk, Sector 7") }
            if (hits.isNotEmpty()) GroupCard(Modifier.padding(top = 10.dp)) {
                hits.forEachIndexed { i, (t, s) -> if (i > 0) RowDivider(); NavRow(RideIcons.Pin, t, { address = "$t, $s"; query = "" }, subtitle = s) }
            }
            if (address.isNotEmpty()) Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Check, c.accent); Spacer(Modifier.width(12.dp)); Text(address, color = c.fg, fontSize = 15.sp)
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Home", "Work", "Other").forEach { l ->
                    val on = l == label
                    Text(l, color = c.fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.height(40.dp).wrapContentHeight().clip(RoundedCornerShape(20.dp)).background(if (on) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent).border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(20.dp)).clickable(role = Role.RadioButton) { label = l }.padding(horizontal = 18.dp, vertical = 9.dp))
                }
            }
        }
        RideButton("Save address", { onSave(label, address) }, enabled = address.isNotEmpty(), modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AddAddressPreview() = RideNowTheme(darkTheme = true) { AddAddressScreen("Home", {}, { _, _ -> }) }
