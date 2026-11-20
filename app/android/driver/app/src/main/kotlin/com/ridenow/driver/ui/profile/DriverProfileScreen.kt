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
    SubScreen(
        str(R.string.profile_title), onBack, modifier,
    ) {
        Row(Modifier.padding(top = 8.dp).entrance(0), verticalAlignment = Alignment.CenterVertically) {
            Box {
                ProfilePhoto(store, name, 92.dp)
                Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(c.fg).border(2.dp, c.bg, CircleShape).clickable(role = Role.Button, onClick = onEdit), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Camera, c.bg, size = 16.dp) }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name.ifBlank { none }, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.weight(1f, fill = false).clickable(onClick = onEdit))
                    val statusText = str(if (active) R.string.profile_status_active else R.string.profile_status_pending)
                    val statusColor = if (active) c.good else c.warn
                    Box(
                        Modifier.padding(start = 8.dp).size(22.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.18f)).semantics { contentDescription = statusText },
                        contentAlignment = Alignment.Center,
                    ) { RideIcon(if (active) RideIcons.Check else RideIcons.Clock, statusColor, size = 13.dp) }
                    RideIcon(RideIcons.Chevron, c.fg, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
                }
                if (store.rating > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    RideIcon(RideIcons.Star, c.fill, size = 15.dp)
                    Text(" ${store.rating}", color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(" " + str(R.string.rider_rides, store.totalRides), color = c.muted, fontSize = 13.sp)
                }
                if (store.memberSince.isNotEmpty()) Text(str(R.string.profile_member_since, store.memberSince), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
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
        GroupCard(Modifier.padding(bottom = 20.dp)) {
            shown.forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                DocRow(d, onDocs)
            }
        }
    }
}

/** One document per full-width row, so long titles in any language have room and the status chip never squeezes the text. */
@Composable
private fun DocRow(d: VehicleDoc, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(docIcon(d.type), c.accent, size = 22.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(docTitle(d.type), color = c.fg, fontSize = 15.sp, lineHeight = 20.sp)
            d.expiry?.let { Text(str(R.string.doc_expires, it), color = c.muted, fontSize = 12.sp, lineHeight = 17.sp) }
        }
        DocStatusChip(d.status)
        RideIcon(RideIcons.Chevron, c.muted, size = 16.dp, modifier = Modifier.padding(start = 8.dp))
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
