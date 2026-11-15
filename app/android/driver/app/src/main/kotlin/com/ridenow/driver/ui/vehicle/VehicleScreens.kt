package com.ridenow.driver.ui.vehicle

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.*
import com.ridenow.driver.ui.profile.DocStatusChip
import com.ridenow.driver.ui.profile.docIcon
import com.ridenow.driver.ui.profile.docTitle
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

@Composable
private fun VehicleHero(v: VehicleInfo, modifier: Modifier = Modifier, plate: Boolean = false, active: Boolean = true) {
    val c = RideTheme.colors
    GroupCard(modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TukTukArt(Modifier.size(width = 130.dp, height = 90.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(v.model.ifBlank { str(R.string.profile_none) }, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(v.type, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                if (plate) Text(v.number, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(c.line).padding(horizontal = 12.dp, vertical = 5.dp))
                else if (active) StatusChip(str(R.string.profile_status_active), Tone.Good, Modifier.padding(top = 8.dp), dot = true)
            }
        }
    }
}

/** Vehicle details: hero card, information list, first documents and an Update button. */
@Composable
fun VehicleDetailsScreen(store: DriverStore, onBack: () -> Unit, onEdit: () -> Unit, onDocs: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val v = store.vehicle
    val none = str(R.string.profile_none)
    SubScreen(
        str(R.string.vd_title), onBack, modifier,
        trailing = {},
        bottom = { RideButton(str(R.string.veh_update), onEdit) },
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 0.dp), horizontalArrangement = Arrangement.End) {
            Row(Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onEdit).heightIn(min = 36.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Pencil, c.accent, size = 14.dp); Text(str(R.string.profile_edit), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
        }
        VehicleHero(v, Modifier.entrance(0))
        SectionTitle(str(R.string.veh_info))
        GroupCard {
            DetailRow(RideIcons.Car, str(R.string.vd_type), v.type.ifBlank { none }); RowDivider()
            DetailRow(RideIcons.Doc, str(R.string.veh_number), v.number.ifBlank { none }); RowDivider()
            DetailRow(RideIcons.Palette, str(R.string.vd_color), v.color.ifBlank { none }); RowDivider()
            DetailRow(RideIcons.Gear, str(R.string.vd_model), v.model.ifBlank { none }); RowDivider()
            DetailRow(RideIcons.Calendar, str(R.string.veh_year_make), v.year.ifBlank { none }); RowDivider()
            DetailRow(RideIcons.Fuel, str(R.string.veh_fuel), v.fuel.ifBlank { none })
        }
        SectionTitle(str(R.string.docs_section), action = str(R.string.view_all), onAction = onDocs)
        GroupCard(Modifier.padding(bottom = 8.dp)) {
            store.docs.filter { it.type in listOf(DocType.Registration, DocType.Insurance, DocType.Emission) }.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onDocs).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RideIcon(docIcon(d.type), c.fg, size = 22.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(docTitle(d.type), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(d.expiry?.let { str(R.string.doc_expires, it) } ?: str(R.string.doc_not_uploaded), color = c.muted, fontSize = 12.sp)
                    }
                    DocStatusChip(d.status); RideIcon(RideIcons.Chevron, c.muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** Edit vehicle details. Type and fuel are chips; the rest are text fields. */
@Composable
fun EditVehicleScreen(store: DriverStore, onBack: () -> Unit, onSaved: () -> Unit, modifier: Modifier = Modifier) {
    val v = store.vehicle
    val types = listOf(str(R.string.vt_tuk), str(R.string.vt_car), str(R.string.vt_bike), str(R.string.vt_van))
    val fuels = listOf(str(R.string.fuel_petrol), str(R.string.fuel_diesel), str(R.string.fuel_hybrid), str(R.string.fuel_electric), str(R.string.fuel_cng))
    var type by rememberSaveable { mutableStateOf(v.type) }
    var number by rememberSaveable { mutableStateOf(v.number) }
    var color by rememberSaveable { mutableStateOf(v.color) }
    var model by rememberSaveable { mutableStateOf(v.model) }
    var year by rememberSaveable { mutableStateOf(v.year) }
    var fuel by rememberSaveable { mutableStateOf(v.fuel) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val yearBad = year.isNotBlank() && (year.toIntOrNull() ?: 0) !in 1980..2100
    SubScreen(str(R.string.veh_edit_title), onBack, modifier, bottom = {
        RideButton(str(R.string.edit_save), {
            tried = true
            if (number.isNotBlank() && model.isNotBlank() && !yearBad) {
                v.type = type; v.number = number.trim().uppercase(); v.color = color.trim(); v.model = model.trim(); v.year = year.trim(); v.fuel = fuel; onSaved()
            }
        })
    }) {
        Text(str(R.string.vd_type), color = RideTheme.colors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        types.chunked(2).forEach { row -> Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { RideChip(it, type == it, { type = it }, Modifier.weight(1f)) } } }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RideTextField(model, { model = it }, str(R.string.vd_model) + " *", RideIcons.Gear, isError = tried && model.isBlank())
            RideTextField(number, { number = it }, str(R.string.veh_number) + " *", RideIcons.Doc, isError = tried && number.isBlank())
            RideTextField(color, { color = it }, str(R.string.vd_color), RideIcons.Palette)
            RideTextField(year, { year = it.filter { ch -> ch.isDigit() }.take(4) }, str(R.string.veh_year_make), RideIcons.Calendar, isError = yearBad, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Text(str(R.string.veh_fuel), color = RideTheme.colors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        fuels.chunked(3).forEach { row -> Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { RideChip(it, fuel == it, { fuel = it }, Modifier.weight(1f)) }; repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } } }
        Spacer(Modifier.height(8.dp))
    }
}

/** Vehicle documents: a card per document with a thumbnail, expiry and status. Tapping one shows details and lets the driver replace it. */
@Composable
fun VehicleDocumentsScreen(store: DriverStore, onBack: () -> Unit, onHelp: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    var detail by remember { mutableStateOf<DocType?>(null) }
    var chooser by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<DocType?>(null) }
    val uploaded = str(R.string.doc_uploaded)
    val openPicker = rememberPhotoPicker(str(R.string.doc_add_update)) { _, bmp ->
        val t = target
        if (bmp != null && t != null) { store.docs.firstOrNull { it.type == t }?.let { it.status = DocStatus.Pending }; toast(context, uploaded) }
    }
    SubScreen(
        str(R.string.docs_title), onBack, modifier,
        trailing = { CircleButton(RideIcons.Help, str(R.string.acct_help), onHelp, filled = false) },
        bottom = { RideButton(str(R.string.doc_add_update), { chooser = true }) },
    ) {
        VehicleHero(store.vehicle, Modifier.padding(top = 4.dp), plate = true)
        Column(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            store.docs.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(18.dp)).clickable(role = Role.Button) { detail = d.type }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DocThumb(d.type, Modifier.size(width = 76.dp, height = 62.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(docTitle(d.type), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (d.type == DocType.Photos) str(R.string.doc_photo_sides) else d.expiry?.let { str(R.string.doc_expires, it) } ?: str(R.string.doc_not_uploaded),
                            color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                        )
                        DocStatusChip(d.status)
                    }
                    if (d.type == DocType.Photos && d.photos > 0) Text("${d.photos}", color = c.fg, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp)).background(c.line).padding(horizontal = 9.dp, vertical = 4.dp))
                    RideIcon(RideIcons.Chevron, c.muted, size = 16.dp)
                }
            }
        }
    }
    detail?.let { t ->
        val d = store.docs.first { it.type == t }
        RideSheet(docTitle(t), { detail = null }) { close ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                DocThumb(t, Modifier.size(width = 96.dp, height = 76.dp))
                Column(Modifier.padding(start = 14.dp)) {
                    DocStatusChip(d.status)
                    Text(d.expiry?.let { str(R.string.doc_expires, it) } ?: str(R.string.doc_not_uploaded), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            RideButton(str(R.string.doc_update_this), { close(); target = t; openPicker(0, false) })
        }
    }
    if (chooser) RideSheet(str(R.string.doc_add_update), { chooser = false }, str(R.string.doc_choose)) { close ->
        GroupCard {
            store.docs.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                NavRow(docIcon(d.type), docTitle(d.type), { close(); target = d.type; openPicker(0, false) })
            }
        }
    }
}
