package com.ridenow.app.ui.more

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.ridenow.app.ui.booking.Vehicles
import com.ridenow.app.ui.booking.money
import com.ridenow.app.ui.components.*
import com.ridenow.app.ui.theme.RideTheme
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

// ---------- Pay in cash ----------

@Composable
fun PayCashScreen(ctx: MoreCtx, onDone: () -> Unit, onReport: () -> Unit) {
    val c = RideTheme.colors
    var step by remember { mutableIntStateOf(1) }
    val amount = money(Vehicles[ctx.vehicle.coerceIn(0, Vehicles.lastIndex)].fare)
    LaunchedEffect(Unit) { delay(3500); step = 2; delay(1800); onDone() }
    BackHandler { }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("TRIP ENDED · CASH", color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 32.dp))
            Text("Pay Mira in cash.", color = c.fg, fontSize = 32.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            Column(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AMOUNT DUE", color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
                Text(amount, color = c.accent, fontSize = 52.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 6.dp))
                Text("Exact change helps your driver.", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf("Arrived", "Pay driver", "Confirmed").forEachIndexed { i, label ->
                    val done = i < step; val now = i == step
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) c.accent else Color.Transparent).border(2.dp, if (done || now) c.accent else c.border, CircleShape), contentAlignment = Alignment.Center) {
                            if (done) RideIcon(RideIcons.Check, c.onAccent, size = 16.dp)
                        }
                        Text(label, color = if (done || now) c.fg else c.muted, fontSize = 16.sp, fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(start = 14.dp))
                    }
                }
            }
            if (step < 2) Row(Modifier.padding(top = 28.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar("MT", 40.dp); Text("  Waiting for Mira to confirm payment", color = c.fg, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        RideButton("Report a payment problem", onReport, Modifier.padding(vertical = 12.dp), style = RideButtonStyle.Text)
    }
}

// ---------- Ride details ----------

@Composable
fun RideDetailsScreen(ctx: MoreCtx, onBack: () -> Unit, onReceipt: () -> Unit, onReport: () -> Unit) {
    val c = RideTheme.colors
    MoreScaffold("Ride details", onBack, bottom = { RideButton("Download receipt", onReceipt); RideButton("Report a trip problem", onReport, style = RideButtonStyle.Text) }) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Completed", color = c.onAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 10.dp, vertical = 4.dp))
            Text("  Tue, 3 Nov · 8.4 km · 19 min", color = c.muted, fontSize = 13.sp)
        }
        RouteBlock("Current location", ctx.dest, Modifier.padding(top = 16.dp))
        GroupCard(Modifier.padding(top = 12.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar("MT"); Column(Modifier.padding(start = 12.dp).weight(1f)) { Text("Mira Tanaka", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text("Orbit · Silver · RN 2100", color = c.muted, fontSize = 12.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Star, c.accent, size = 14.dp); Text("  You rated 4", color = c.muted, fontSize = 12.sp) }
            }
        }
        SectionLabel("FARE BREAKDOWN")
        GroupCard { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { FareLines() } }
    }
}

@Composable
private fun FareLines() {
    InfoRow("Base fare", "$3.50"); InfoRow("Distance · 8.4 km", "$8.40"); InfoRow("Time · 19 min", "$3.80"); InfoRow("Taxes and fees", "$1.10")
    HorizontalDivider(color = RideTheme.colors.line, modifier = Modifier.padding(vertical = 4.dp))
    InfoRow("Total · Cash on delivery", "$16.80", strong = true)
}

// ---------- Receipt (PDF) ----------

object ReceiptPdf {
    private const val Name = "RideNow-receipt-8F3K2Q.pdf"

    fun save(context: Context, drop: String): Uri? {
        val doc = PdfDocument()
        return try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val cv = page.canvas
            val title = Paint().apply { textSize = 28f; isFakeBoldText = true }
            val body = Paint().apply { textSize = 14f }
            val muted = Paint().apply { textSize = 12f; color = 0xFF666666.toInt() }
            var y = 70f
            cv.drawText("RideNow", 50f, y, title); y += 28f
            cv.drawText("Trip receipt · #8F3K2Q · Tue, 3 Nov", 50f, y, muted); y += 40f
            listOf("Pickup" to "Current location", "Drop" to drop, "Driver" to "Mira Tanaka · Orbit · RN 2100").forEach { (k, v) -> cv.drawText(k, 50f, y, muted); cv.drawText(v, 150f, y, body); y += 26f }
            y += 16f
            listOf("Base fare" to "$3.50", "Distance · 8.4 km" to "$8.40", "Time · 19 min" to "$3.80", "Taxes and fees" to "$1.10").forEach { (k, v) -> cv.drawText(k, 50f, y, body); cv.drawText(v, 480f, y, body); y += 24f }
            y += 10f
            cv.drawText("Total · Paid in cash", 50f, y, title.apply { textSize = 16f }); cv.drawText("$16.80", 470f, y, title)
            doc.finishPage(page)
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, Name); put(MediaStore.Downloads.MIME_TYPE, "application/pdf"); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.also { u -> context.contentResolver.openOutputStream(u)?.use { doc.writeTo(it) } }
            } else {
                val f = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), Name)
                FileOutputStream(f).use { doc.writeTo(it) }
                Uri.fromFile(f)
            }
        } catch (e: Exception) { null } finally { doc.close() }
    }
}

@Composable
fun ReceiptScreen(ctx: MoreCtx, onBack: () -> Unit) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var saved by remember { mutableStateOf<Uri?>(null) }
    MoreScaffold("Receipt", onBack, subtitle = "Trip #8F3K2Q · Tue, 3 Nov", bottom = {
        if (saved == null) RideButton("Save as PDF", {
            val u = ReceiptPdf.save(context, ctx.dest)
            if (u == null) context.toast("Could not save the receipt") else { saved = u; context.toast("Saved to Downloads") }
        }) else RideButton("Open PDF", {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(saved, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { context.toast("No PDF viewer found") }
        })
    }) {
        RouteBlock("Current location", ctx.dest, Modifier.padding(top = 16.dp))
        SectionLabel("FARE")
        GroupCard { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { FareLines() } }
        Text("Paid in cash to Mira Tanaka.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

// ---------- Scheduled ride ----------

private fun parts(label: String?): Triple<String, String, String> {
    val p = (label ?: "Wed, 4 Nov, 8:30 AM").split(", ")
    val dm = p.getOrElse(1) { "4 Nov" }.split(" ")
    return Triple(p.getOrElse(0) { "Wed" }.uppercase(), dm.joinToString(" "), p.getOrElse(2) { "8:30 AM" })
}

@Composable
private fun ScheduledBody(ctx: MoreCtx, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val (dow, dm, time) = parts(ctx.scheduled)
    val v = Vehicles[ctx.vehicle.coerceIn(0, Vehicles.lastIndex)]
    Column(modifier) {
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(c.accent.copy(alpha = 0.14f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(dow, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold); Text(dm.substringBefore(' '), color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text(dm.substringAfter(' ', ""), color = c.muted, fontSize = 11.sp)
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) { Text(time, color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text("Pickup tomorrow", color = c.muted, fontSize = 13.sp) }
            Text("Confirmed", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        RouteBlock("Current location", ctx.dest, Modifier.padding(top = 12.dp))
        GroupCard(Modifier.padding(top = 12.dp)) { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            InfoRow("${v.name} · ${v.seats} seats", money(v.fare)); InfoRow("Cash · estimated fare", ""); InfoRow("Reminder", "15 min before")
        } }
        Text("We will notify you when a driver is assigned.", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun ScheduledDetailsScreen(ctx: MoreCtx, onBack: () -> Unit, onEdit: () -> Unit, onCancel: () -> Unit) {
    MoreScaffold("Scheduled ride", onBack, subtitle = "Your upcoming trip.", bottom = { RideButton("Edit ride", onEdit); DangerButton("Cancel ride", onCancel, Modifier.padding(top = 8.dp)) }) { ScheduledBody(ctx) }
}

@Composable
fun CancelScheduledScreen(ctx: MoreCtx, onKeep: () -> Unit, onCancelled: () -> Unit) {
    val c = RideTheme.colors
    val reasons = listOf("Plans changed", "Booked by mistake", "Found another ride")
    var reason by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(onBack = onKeep)
    Box(Modifier.fillMaxSize()) {
        MoreScaffold("Scheduled ride", onKeep, subtitle = "Your upcoming trip.") { ScheduledBody(ctx) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        SheetCard(Modifier.align(Alignment.BottomCenter), "Cancel scheduled ride?", "${ctx.scheduled ?: "Wed, 4 Nov, 8:30 AM"} to ${ctx.dest}") {
            Column(Modifier.padding(top = 8.dp)) { reasons.forEachIndexed { i, r -> if (i > 0) RowDivider(); RadioRow(r, reason == i, { reason = i }) } }
            RideButton("Keep my ride", onKeep, Modifier.padding(top = 12.dp))
            DangerButton("Cancel ride", onCancelled, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun EditScheduledScreen(ctx: MoreCtx, onBack: () -> Unit, onSave: (String) -> Unit, onRoute: () -> Unit) {
    val c = RideTheme.colors
    var date by rememberSaveable { mutableLongStateOf(LocalDate.now().plusDays(1).toEpochDay()) }
    var hour by rememberSaveable { mutableIntStateOf(8) }
    var minute by rememberSaveable { mutableIntStateOf(30) }
    var pm by rememberSaveable { mutableStateOf(false) }
    var veh by rememberSaveable { mutableIntStateOf(ctx.vehicle) }
    var rem by rememberSaveable { mutableIntStateOf(2) }
    var sheet by remember { mutableStateOf("") }
    val d = LocalDate.ofEpochDay(date)
    val label = pickLabel(d, hour, minute, pm)
    Box(Modifier.fillMaxSize()) {
        MoreScaffold("Edit scheduled ride", onBack, subtitle = "Change the details of your upcoming trip.", bottom = {
            RideButton("Save changes", { onSave(label) }); RideButton("Discard changes", onBack, style = RideButtonStyle.Text)
        }) {
            GroupCard(Modifier.padding(top = 16.dp)) {
                NavRow(RideIcons.Calendar, "Date", { sheet = "date" }, subtitle = label.substringBeforeLast(", "))
                RowDivider(); NavRow(RideIcons.Clock, "Time", { sheet = "time" }, subtitle = "%d:%02d %s".format(hour, minute, if (pm) "PM" else "AM"))
                RowDivider(); NavRow(RideIcons.Pin, "Pickup", onRoute, subtitle = "Current location")
                RowDivider(); NavRow(RideIcons.Flag, "Drop", onRoute, subtitle = ctx.dest)
                RowDivider(); NavRow(RideIcons.Car, "Vehicle", { sheet = "vehicle" }, subtitle = "${Vehicles[veh.coerceIn(0, Vehicles.lastIndex)].name} · ${money(Vehicles[veh.coerceIn(0, Vehicles.lastIndex)].fare)}")
                RowDivider(); NavRow(RideIcons.Bell, "Reminder", { sheet = "reminder" }, subtitle = ReminderOptions[rem])
            }
            Text("The fare may change if you edit the route or vehicle.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        when (sheet) {
            "date" -> CalendarSheet(d, { date = it.toEpochDay(); sheet = "" }, { sheet = "" })
            "time" -> SheetScreen({ sheet = "" }, "Time") {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..12).forEach { h -> Chip("$h", h == hour, { hour = h }, Modifier.widthIn(min = 48.dp)) } }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 15, 30, 45).forEach { m -> Chip("%02d".format(m), m == minute, { minute = m }) }; Spacer(Modifier.width(8.dp))
                    Chip("AM", !pm, { pm = false }); Chip("PM", pm, { pm = true })
                }
                RideButton("Save", { sheet = "" }, Modifier.padding(top = 16.dp))
            }
            "vehicle" -> ChoiceSheet("Vehicle", Vehicles.map { it.name }, veh, { veh = it; sheet = "" }, { sheet = "" }, Vehicles.map { money(it.fare) })
            "reminder" -> ChoiceSheet("Reminder", ReminderOptions, rem, { rem = it; sheet = "" }, { sheet = "" })
        }
    }
}
