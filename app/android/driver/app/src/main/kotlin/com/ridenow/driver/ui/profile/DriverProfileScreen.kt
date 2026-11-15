package com.ridenow.driver.ui.profile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.*
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

/** Round profile picture: the chosen photo, or initials when there is none. */
@Composable
fun ProfilePhoto(store: DriverStore, name: String, size: Dp, modifier: Modifier = Modifier) {
    val photo = store.photo
    if (photo != null) Image(photo.asImageBitmap(), null, modifier.size(size).clip(CircleShape).border(2.dp, RideTheme.colors.accent, CircleShape), contentScale = ContentScale.Crop)
    else Avatar(initialsOf(name), size, modifier)
}

/** Driver profile: identity, personal details, vehicle summary and documents. */
@Composable
fun DriverProfileScreen(
    store: DriverStore, name: String, phone: String, email: String, address: String, active: Boolean,
    onBack: () -> Unit, onEdit: () -> Unit, onEditVehicle: () -> Unit, onDocs: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    val none = str(R.string.profile_none)
    val v = store.vehicle
    var menu by remember { mutableStateOf(false) }
    SubScreen(
        str(R.string.profile_title), onBack, modifier,
        trailing = {
            Box {
                CircleButton(RideIcons.More, str(R.string.menu_more), { menu = true }, filled = false)
                DropdownMenu(menu, { menu = false }, containerColor = c.field) {
                    DropdownMenuItem(text = { Text(str(R.string.edit_profile_title), color = c.fg) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(str(R.string.docs_title), color = c.fg) }, onClick = { menu = false; onDocs() })
                }
            }
        },
    ) {
        Row(Modifier.padding(top = 8.dp).entrance(0), verticalAlignment = Alignment.CenterVertically) {
            Box {
                ProfilePhoto(store, name, 92.dp)
                Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(c.fg).border(2.dp, c.bg, CircleShape).clickable(role = Role.Button, onClick = onEdit), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Camera, c.bg, size = 16.dp) }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name.ifBlank { none }, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false).clickable(onClick = onEdit))
                    RideIcon(RideIcons.Chevron, c.fg, size = 16.dp, modifier = Modifier.padding(start = 4.dp))
                }
                if (store.rating > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    RideIcon(RideIcons.Star, c.fill, size = 15.dp)
                    Text(" ${store.rating}", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(" " + str(R.string.rider_rides, store.totalRides), color = c.muted, fontSize = 13.sp)
                }
                if (store.memberSince.isNotEmpty()) Text(str(R.string.profile_member_since, store.memberSince), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            StatusChip(str(if (active) R.string.profile_status_active else R.string.profile_status_pending), if (active) Tone.Good else Tone.Warn, dot = true)
        }
        SectionTitle(str(R.string.profile_section_personal), action = str(R.string.profile_edit), actionIcon = RideIcons.Pencil, onAction = onEdit)
        GroupCard {
            DetailRow(RideIcons.Person, str(R.string.profile_full_name), name.ifBlank { none }); RowDivider()
            DetailRow(RideIcons.Phone, str(R.string.profile_phone), phone); RowDivider()
            DetailRow(RideIcons.Mail, str(R.string.profile_email), email.ifBlank { none }); RowDivider()
            DetailRow(RideIcons.Pin, str(R.string.profile_address), address.ifBlank { none })
        }
        SectionTitle(str(R.string.profile_section_vehicle), action = str(R.string.profile_edit), actionIcon = RideIcons.Pencil, onAction = onEditVehicle)
        GroupCard {
            DetailRow(RideIcons.Car, str(R.string.vd_type), v.type.ifBlank { none }); RowDivider()
            DetailRow(RideIcons.Doc, str(R.string.veh_number), v.number.ifBlank { none }); RowDivider()
            DetailRow(RideIcons.Palette, str(R.string.vd_color), v.color.ifBlank { none }); RowDivider()
            DetailRow(RideIcons.Gear, str(R.string.vd_model), v.model.ifBlank { none }); RowDivider()
            DetailRow(RideIcons.Calendar, str(R.string.vd_year), v.year.ifBlank { none })
        }
        SectionTitle(str(R.string.docs_section), action = str(R.string.view_all), onAction = onDocs)
        val shown = store.docs.filter { it.type != DocType.Photos }.take(4)
        Column(Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { d -> DocCard(d, onDocs, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DocCard(d: VehicleDoc, onClick: () -> Unit, modifier: Modifier) {
    val c = RideTheme.colors
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RideIcon(docIcon(d.type), c.fg, size = 22.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(docTitle(d.type), color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 17.sp)
            DocStatusChip(d.status, Modifier.padding(top = 4.dp))
        }
        RideIcon(RideIcons.Chevron, c.muted, size = 14.dp)
    }
}

@Composable
fun docTitle(t: DocType): String = str(when (t) {
    DocType.License -> R.string.doc_license; DocType.Registration -> R.string.doc_registration; DocType.Insurance -> R.string.doc_insurance
    DocType.Emission -> R.string.doc_emission; DocType.Photos -> R.string.doc_photos
})

fun docIcon(t: DocType): List<String> = when (t) {
    DocType.License -> RideIcons.IdCard; DocType.Registration -> RideIcons.IdCard; DocType.Insurance -> RideIcons.Shield; DocType.Emission -> RideIcons.Doc; DocType.Photos -> RideIcons.Image
}

@Composable
fun DocStatusChip(s: DocStatus, modifier: Modifier = Modifier) = StatusChip(
    str(when (s) { DocStatus.Verified -> R.string.doc_verified; DocStatus.Pending -> R.string.doc_pending; DocStatus.Expired -> R.string.doc_expired }),
    when (s) { DocStatus.Verified -> Tone.Good; DocStatus.Pending -> Tone.Warn; DocStatus.Expired -> Tone.Danger }, modifier, dot = s == DocStatus.Verified,
)
