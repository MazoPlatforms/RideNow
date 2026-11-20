package com.ridenow.driver.ui.vehicle

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.*
import com.ridenow.driver.ui.profile.DocStatusChip
import com.ridenow.driver.ui.profile.docIcon
import com.ridenow.driver.ui.profile.docTitle
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

private val VehicleCategory.label: Int
    get() = when (this) {
        VehicleCategory.TUK -> R.string.vt_tuk
        VehicleCategory.CAR -> R.string.vt_car
        VehicleCategory.BIKE -> R.string.vt_bike
        VehicleCategory.VAN -> R.string.vt_van
    }

@Composable
internal fun VehicleInfo.detailsTypeLabel(): String = when (val kind = category) {
    VehicleCategory.TUK -> str(R.string.vehicle_details_three_wheeler)
    null -> type
    else -> str(kind.label)
}

@Composable
private fun VehicleIllustration(category: VehicleCategory?, modifier: Modifier) {
    val colors = RideTheme.colors
    val artwork = when (category) {
        VehicleCategory.TUK -> R.drawable.vehicle_tuk_tuk
        VehicleCategory.CAR -> if (colors.isDark) R.drawable.vehicle_car else R.drawable.vehicle_car_charcoal
        VehicleCategory.BIKE -> R.drawable.vehicle_motorcycle
        VehicleCategory.VAN -> if (colors.isDark) R.drawable.vehicle_van else R.drawable.vehicle_van_navy
        null -> null
    }
    if (artwork == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            RideIcon(RideIcons.Car, colors.muted, size = 36.dp)
        }
    } else {
        Image(
            painter = painterResource(artwork),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun VehicleHero(v: VehicleInfo, modifier: Modifier = Modifier, plate: Boolean = false) {
    val c = RideTheme.colors
    GroupCard(modifier) {
        if (v.number.isBlank()) {
            Text(str(R.string.profile_none), color = c.muted, modifier = Modifier.padding(14.dp))
        } else {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                VehicleIllustration(v.category, Modifier.size(width = 130.dp, height = 90.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    val model = listOf(v.make, v.model).filter(String::isNotBlank).joinToString(" ")
                    Text(model.ifBlank { v.number }, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(v.detailsTypeLabel(), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    if (plate) {
                        Text(v.number, color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier
                            .padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(c.line)
                            .padding(horizontal = 12.dp, vertical = 5.dp))
                    } else if (v.approved) {
                        StatusChip(str(R.string.profile_status_active), Tone.Good, Modifier.padding(top = 8.dp), dot = true)
                    }
                }
            }
        }
    }
}

@Composable
fun VehicleDetailsScreen(store: DriverStore, onBack: () -> Unit, onDocs: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val v = store.vehicle
    val fields = listOf(
        Triple(RideIcons.Car, R.string.vd_type, v.detailsTypeLabel()),
        Triple(RideIcons.Doc, R.string.veh_number, v.number),
        Triple(RideIcons.Palette, R.string.vd_color, v.color),
        Triple(RideIcons.Gear, R.string.veh_make, v.make),
        Triple(RideIcons.Gear, R.string.vd_model, v.model),
        Triple(RideIcons.Calendar, R.string.veh_year_make, v.year),
        Triple(RideIcons.Fuel, R.string.veh_fuel, v.fuel),
    ).filter { it.third.isNotBlank() }
    val vehicleDocuments = store.docs.filter { it.type != DocType.License }
    SubScreen(str(R.string.vd_title), onBack, modifier) {
        VehicleHero(v, Modifier.entrance(0))
        if (fields.isNotEmpty()) {
            SectionTitle(str(R.string.veh_info))
            GroupCard {
                fields.forEachIndexed { index, (icon, label, value) ->
                    if (index > 0) RowDivider()
                    DetailRow(icon, str(label), value)
                }
            }
        }
        if (vehicleDocuments.isNotEmpty()) {
            SectionTitle(str(R.string.docs_section), action = str(R.string.view_all), onAction = onDocs)
            GroupCard(Modifier.padding(bottom = 8.dp)) {
                vehicleDocuments.forEachIndexed { index, doc ->
                    if (index > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onDocs).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RideIcon(docIcon(doc.type), c.fg, size = 20.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(docTitle(doc.type), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            doc.expiry?.let { Text(str(R.string.doc_expires, it), color = c.muted, fontSize = 12.sp) }
                        }
                        DocStatusChip(doc.status)
                        RideIcon(RideIcons.Chevron, c.muted, size = 15.dp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun VehicleDocumentsScreen(store: DriverStore, onBack: () -> Unit, onHelp: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var detail by remember { mutableStateOf<DocType?>(null) }
    SubScreen(
        str(R.string.docs_title), onBack, modifier,
        trailing = { CircleButton(RideIcons.Help, str(R.string.acct_help), onHelp, filled = false) },
    ) {
        VehicleHero(store.vehicle, Modifier.padding(top = 4.dp), plate = true)
        Column(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (store.docs.isEmpty()) {
                Text(str(R.string.doc_not_uploaded), color = c.muted, modifier = Modifier.padding(14.dp))
            }
            store.docs.forEach { doc ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button) { detail = doc.type }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RideIcon(docIcon(doc.type), c.accent, size = 30.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(docTitle(doc.type), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        val expiry = doc.expiry
                        if (expiry != null) Text(str(R.string.doc_expires, expiry), color = c.muted, fontSize = 12.sp)
                        DocStatusChip(doc.status, Modifier.padding(top = 4.dp))
                    }
                    if (doc.type == DocType.Photos) Text(doc.photos.toString(), color = c.fg, fontSize = 13.sp)
                    RideIcon(RideIcons.Chevron, c.muted, size = 15.dp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
    store.docs.firstOrNull { it.type == detail }?.let { doc ->
        RideSheet(docTitle(doc.type), { detail = null }) {
            DocStatusChip(doc.status)
            doc.expiry?.let { Text(str(R.string.doc_expires, it), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}
