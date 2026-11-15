package com.ridenow.driver.ui.notifications

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
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.DriverStore
import com.ridenow.driver.ui.data.Notice
import com.ridenow.driver.ui.data.NoticeKind
import com.ridenow.driver.ui.data.NoticeTone
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

private fun iconFor(key: String): List<String> = when (key) {
    "person" -> RideIcons.Person; "check" -> RideIcons.Check; "navigate" -> RideIcons.Navigate; "pin" -> RideIcons.Pin
    "play" -> RideIcons.Play; "wallet" -> RideIcons.Wallet; else -> RideIcons.Bell
}

private fun toneFor(t: NoticeTone) = when (t) { NoticeTone.Blue -> Tone.Info; NoticeTone.Green -> Tone.Good; NoticeTone.Yellow -> Tone.Brand; NoticeTone.Neutral -> Tone.Neutral }

/** Notification centre: filter pills, a card per notification, and a bin to clear them all. [onOpen] lets the app jump to the related screen. */
@Composable
fun NotificationsScreen(store: DriverStore, onBack: () -> Unit, onOpen: (Notice) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var filter by rememberSaveable { mutableStateOf(0) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val shown = store.notices.filter { filter == 0 || (filter == 1 && it.kind == NoticeKind.Rides) || (filter == 2 && it.kind == NoticeKind.System) }

    SubScreen(
        str(R.string.nc_title), onBack, modifier,
        trailing = { CircleButton(RideIcons.Trash, str(R.string.notif_clear), { if (store.notices.isNotEmpty()) confirm = true }, filled = false) },
    ) {
        FilterPills(listOf(str(R.string.filter_all), str(R.string.notif_rides), str(R.string.notif_system)), filter, { filter = it }, Modifier.padding(top = 4.dp, bottom = 14.dp))
        if (shown.isEmpty()) EmptyState(RideIcons.Bell, str(R.string.notif_empty_title), str(R.string.notif_empty_text))
        else Column {
            val today = shown.filter { !it.age.contains("day") }
            val earlier = shown.filter { it.age.contains("day") }
            @Composable
            fun Group(label: String, items: List<Notice>) {
                if (items.isEmpty()) return
                SectionTitle(label)
                items.forEachIndexed { i, n ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { n.unread = false; onOpen(n) }.padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
                        IconTile(iconFor(n.icon), toneFor(n.tone))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(n.title, color = c.fg, fontSize = 15.sp, fontWeight = if (n.unread) FontWeight.SemiBold else FontWeight.Normal)
                            Text(n.body, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(n.age, color = c.muted, fontSize = 12.sp)
                            if (n.unread) Box(Modifier.padding(top = 8.dp).size(8.dp).clip(CircleShape).background(c.accent).semantics { contentDescription = "Unread" })
                        }
                    }
                }
            }
            Group(str(R.string.notif_today), today)
            Group(str(R.string.notif_earlier), earlier)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = c.bg,
        title = { Text(str(R.string.notif_clear_title), color = c.fg) },
        text = { Text(str(R.string.notif_clear_text), color = c.muted) },
        confirmButton = { TextButton(onClick = { confirm = false; store.clearNotices() }) { Text(str(R.string.notif_clear_confirm), color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(str(R.string.dialog_cancel), color = c.fg) } },
    )
}
