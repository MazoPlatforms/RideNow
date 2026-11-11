package com.ridenow.app.ui.saved

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.*
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.draw.drawBehind
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

/** Home and Work are always shown; other places can be removed. Edits and adds open placeholders for now. */
@Composable
fun SavedAddressesScreen(home: String?, work: String?, others: androidx.compose.runtime.snapshots.SnapshotStateList<Pair<String, String>>, onBack: () -> Unit, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    val textSearchSaved = stringResource(R.string.search_saved)
    val textHomeAndWorkAreAlwaysOneTapAway = stringResource(R.string.text_home_and_work_are_always_one_tap_away)
    val textMenuHome = stringResource(R.string.home_saved_home)
    val textNotSetYet = stringResource(R.string.text_not_set_yet)
    val textHomeSavedWork = stringResource(R.string.home_saved_work)
    val textOtherPlaces = stringResource(R.string.text_other_places)
    val textNoFavoritesYet = stringResource(R.string.text_no_favorites_yet)
    val textSavePlacesYouVisitOftenForFasterPickups = stringResource(R.string.text_save_places_you_visit_often_for_faster_pickups)
    val textMoreOptionsForValue = stringResource(R.string.text_more_options_for_value)
    val textEdit = stringResource(R.string.text_edit)
    val textRemove = stringResource(R.string.text_remove)
    val textAddNewAddress = stringResource(R.string.text_add_new_address)

    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(textSearchSaved, onBack, subtitle = textHomeAndWorkAreAlwaysOneTapAway)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Slot(RideIcons.Home, textMenuHome, home ?: textNotSetYet, set = home != null) { onEdit("Home address") }
                Slot(RideIcons.Work, textHomeSavedWork, work ?: textNotSetYet, set = work != null) { onEdit("Work address") }
            }
            SectionLabel(textOtherPlaces)
            if (others.isEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(vertical = 30.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "heart").animateFloat(0.7f, 1.15f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearOutSlowInEasing)), label = "p")
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(72.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = (1.15f - pulse).coerceIn(0f, 0.6f) }.border(1.5.dp, c.accent, CircleShape))
                    Box(Modifier.size(52.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Heart, c.accent, size = 26.dp) }
                }
                Text(textNoFavoritesYet, color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
                Text(textSavePlacesYouVisitOftenForFasterPickups, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
            others.forEachIndexed { i, (title, sub) ->
                var menu by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Heart, c.accent, size = 18.dp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text(title, color = c.fg, fontSize = 15.sp); Text(sub, color = c.muted, fontSize = 12.sp) }
                        Box {
                            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }.semantics { contentDescription = textMoreOptionsForValue.format(title) }, contentAlignment = Alignment.Center) { RideIcon(RideIcons.More, c.muted) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(textEdit) }, onClick = { menu = false; onEdit(title) })
                                DropdownMenuItem(text = { Text(textRemove) }, onClick = { menu = false; others.removeAt(i) })
                            }
                        }
                    }
                    HorizontalDivider(color = c.line)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        RideButton(textAddNewAddress, { onEdit("Add address") }, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun Slot(icon: List<String>, title: String, sub: String, set: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    val dash = c.border
    Row(
        Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(20.dp)).background(if (set) c.field else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (set) Modifier.border(1.dp, c.border, RoundedCornerShape(20.dp)) else Modifier.drawBehind { drawRoundRect(dash, cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))) })
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { RideIcon(icon, c.accent) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(sub, color = c.muted, fontSize = 13.sp) }
        RideIcon(if (set) RideIcons.Pencil else RideIcons.Plus, c.accent)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SavedPreview() = RideNowTheme(darkTheme = true) { SavedAddressesScreen(stringResource(R.string.text_text_128_meridian_walk), null, remember { mutableStateListOf("Zenith Café" to "Sector 7") }, {}, {}) }
