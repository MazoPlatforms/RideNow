package com.ridenow.app.ui.saved

import com.ridenow.app.ui.components.localizedLabel

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.ridenow.app.ui.map.LatLng
import com.ridenow.app.R
import com.ridenow.app.ui.booking.MapPickerScreen
import com.ridenow.app.ui.map.SifricApi
import com.ridenow.app.ui.map.fetchRiderPosition
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ridenow.app.ui.map.Suggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

@Composable
fun AddAddressScreen(
    initialLabel: String, onBack: () -> Unit,
    onSave: suspend (String, String?, String, LatLng?) -> Unit,
    modifier: Modifier = Modifier,
    allowCurrentLocation: () -> Boolean = { true },
    near: LatLng? = null,
    existing: SavedAddress? = null,
    onRemove: (suspend () -> Unit)? = null,
) {
    val textAddValueAddress = stringResource(R.string.text_add_value_address)
    val textSaveItOnceForOneTapRides = stringResource(R.string.text_save_it_once_for_one_tap_rides)
    val textSearchAddress = stringResource(R.string.text_search_address)
    val textUseCurrentLocation = stringResource(R.string.text_use_current_location)
    val textSaveAddress = stringResource(R.string.text_save_address)
    val gpsUnavailable = stringResource(R.string.saved_location_unavailable)
    val errorLabel = stringResource(R.string.saved_settings_failed)
    val pinLabel = stringResource(R.string.map_selected)
    val c = RideTheme.colors
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: initialLabel.uppercase(java.util.Locale.ROOT)) }
    var customLabel by rememberSaveable { mutableStateOf(existing?.label.orEmpty()) }
    var query by rememberSaveable { mutableStateOf(existing?.address.orEmpty()) }
    var coordinate by rememberSaveable { mutableStateOf(existing?.point) }
    var selection by rememberSaveable { mutableStateOf(existing?.address) }
    var placeId by rememberSaveable { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf(emptyList<Suggestion>()) }
    var busy by remember { mutableStateOf(false) }
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    fun fillLocation() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val point = fetchRiderPosition(context) ?: near
                if (point == null) {
                    error = gpsUnavailable
                } else {
                    query = SifricApi.reverse(context, point) ?: "%.6f, %.6f".format(java.util.Locale.ROOT, point.latitude, point.longitude)
                    selection = query
                    placeId = null
                    coordinate = point
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { error = gpsUnavailable }
            finally { busy = false }
        }
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { if (it.values.any { granted -> granted }) fillLocation() else error = gpsUnavailable }
    LaunchedEffect(query, selection) {
        hits = emptyList()
        if (query.trim().length >= 3 && selection == null) {
            delay(350)
            hits = SifricApi.autocomplete(context, query.trim(), near).orEmpty()
        }
    }
    BackHandler { if (!busy) { if (mapOpen) mapOpen = false else onBack() } }
    if (mapOpen) {
        MapPickerScreen(stringResource(R.string.saved_choose_map), textSaveAddress, onBack = { mapOpen = false },
            onConfirm = { address, point ->
                query = if (address == pinLabel) "%.6f, %.6f".format(java.util.Locale.ROOT, point.latitude, point.longitude) else address
                selection = query
                coordinate = point
                placeId = null
                mapOpen = false
            }, onLocationOff = { allowCurrentLocation() })
        return
    }
    val label = when (kind) { "HOME" -> "Home"; "WORK" -> "Work"; else -> "Other" }

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        ScreenTop(textAddValueAddress.format(localizedLabel(label).lowercase()), onBack, subtitle = textSaveItOnceForOneTapRides)
        BasicTextField(
            query, { query = it.take(300); selection = null; coordinate = null; placeId = null }, enabled = !busy, singleLine = true, textStyle = TextStyle(color = c.fg, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(56.dp),
            decorationBox = { inner ->
                Row(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(RideIcons.Search, c.accent); Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) { if (query.isEmpty()) Text(textSearchAddress, color = c.muted, fontSize = 16.sp); inner() }
                }
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Spacer(Modifier.height(12.dp))
            GroupCard {
                NavRow(RideIcons.Pin, textUseCurrentLocation, {
                    if (!busy && allowCurrentLocation()) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        } else fillLocation()
                    }
                })
                RowDivider()
                NavRow(RideIcons.Map, stringResource(R.string.saved_choose_map), { if (!busy) mapOpen = true })
            }
            if (hits.isNotEmpty()) GroupCard(Modifier.padding(top = 10.dp)) {
                hits.forEachIndexed { i, hit ->
                    if (i > 0) RowDivider()
                    NavRow(RideIcons.Pin, hit.name, {
                        if (!busy) {
                            query = listOf(hit.name, hit.area).filter(String::isNotBlank).joinToString(", ").take(300)
                            selection = query
                            coordinate = null
                            placeId = hit.placeId
                        }
                    }, subtitle = hit.area)
                }
            }

            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Home", "Work", "Other").forEach { l ->
                    val on = l == label
                    Text(localizedLabel(l), color = c.fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.height(40.dp).wrapContentHeight().clip(RoundedCornerShape(20.dp)).background(if (on) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent).border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(20.dp)).clickable(enabled = !busy && existing == null, role = Role.RadioButton) { kind = l.uppercase(java.util.Locale.ROOT) }.padding(horizontal = 18.dp, vertical = 9.dp))
                }
            }
            if (kind == "OTHER") OutlinedTextField(customLabel, { customLabel = it.take(80) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
                enabled = !busy, label = { Text(stringResource(R.string.saved_address_label)) })
            if (onRemove != null) TextButton(enabled = !busy, onClick = { confirmRemove = true }) {
                Text(stringResource(R.string.text_remove), color = c.danger)
            }
            error?.let { Text(it, color = c.danger, modifier = Modifier.padding(top = 8.dp)) }
        }
        RideButton(textSaveAddress, {
            if (!busy) {
                busy = true
                error = null
                scope.launch {
                    try {
                        val point = coordinate ?: placeId?.let { SifricApi.placeLatLng(context, it) }
                        onSave(kind, customLabel.trim().takeIf { kind == "OTHER" && it.isNotEmpty() }, query.trim(), point)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = errorLabel
                    } finally { busy = false }
                }
            }
        }, enabled = !busy && query.isNotBlank(), loading = busy, modifier = Modifier.padding(vertical = 12.dp))
    }
    if (confirmRemove && onRemove != null) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text(stringResource(R.string.text_remove)) },
        text = { Text(existing?.address.orEmpty()) },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.two_step_cancel)) } },
        confirmButton = { TextButton(onClick = {
            confirmRemove = false
            busy = true
            scope.launch {
                try { onRemove() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = errorLabel }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.text_remove)) } },
    )

}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AddAddressPreview() = RideNowTheme(darkTheme = true) { AddAddressScreen("Home", {}, { _, _, _, _ -> }) }
