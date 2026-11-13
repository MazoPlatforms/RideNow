package com.ridenow.driver.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

/** Driver profile: identity card, personal details and vehicle. [onEdit] opens the edit screen. */
@Composable
fun DriverProfileScreen(
    name: String, phone: String, email: String, address: String,
    vehicle: String, availability: String, active: Boolean,
    onBack: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    val none = stringResource(R.string.profile_none)
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(stringResource(R.string.profile_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp).entrance(60)) {
            IdentityCard(name.ifBlank { none }, phone, active, Modifier.padding(top = 12.dp))

            SectionHeader(stringResource(R.string.profile_section_personal), Modifier.padding(top = 24.dp), actionText = stringResource(R.string.profile_edit), onAction = onEdit)
            GroupCard(Modifier.padding(top = 10.dp)) {
                DetailRow(RideIcons.Person, stringResource(R.string.profile_full_name), name.ifBlank { none })
                RowDivider()
                DetailRow(RideIcons.Phone, stringResource(R.string.profile_phone), phone)
                RowDivider()
                DetailRow(RideIcons.Mail, stringResource(R.string.profile_email), email.ifBlank { none })
                RowDivider()
                DetailRow(RideIcons.Pin, stringResource(R.string.profile_address), address.ifBlank { none })
            }

            SectionHeader(stringResource(R.string.profile_section_vehicle), Modifier.padding(top = 24.dp))
            GroupCard(Modifier.padding(top = 10.dp)) {
                DetailRow(RideIcons.Car, stringResource(R.string.profile_vehicle), vehicle.ifBlank { none })
                RowDivider()
                DetailRow(RideIcons.Calendar, stringResource(R.string.profile_availability), availability.ifBlank { none })
            }
        }
    }
}

@Composable
private fun IdentityCard(name: String, phone: String, active: Boolean, modifier: Modifier) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(c.field).border(1.dp, c.border, shape).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), color = c.accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(name, color = c.fg, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(phone, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            stringResource(if (active) R.string.profile_status_active else R.string.profile_status_pending),
            color = if (active) c.accent else c.warn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(CircleShape).background((if (active) c.accent else c.warn).copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier, actionText: String? = null, onAction: (() -> Unit)? = null) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (actionText != null && onAction != null) {
            Row(
                Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RideIcon(RideIcons.Pencil, c.accent, size = 14.dp)
                Text(actionText, color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(icon: List<String>, label: String, value: String) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(icon, c.muted, size = 18.dp)
        Text(label, color = c.muted, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text(value, color = c.fg, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1.4f))
    }
}
