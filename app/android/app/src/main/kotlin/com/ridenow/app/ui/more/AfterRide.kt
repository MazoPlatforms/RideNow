package com.ridenow.app.ui.more

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource
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
    val textTripEndedCash = stringResource(R.string.text_trip_ended_cash)
    val textPayMiraInCash = stringResource(R.string.text_pay_mira_in_cash)
    val textAmountDue = stringResource(R.string.text_amount_due)
    val textExactChangeHelpsYourDriver = stringResource(R.string.text_exact_change_helps_your_driver)
    val textArrived = stringResource(R.string.text_arrived)
    val textPayDriver = stringResource(R.string.text_pay_driver)
    val textConfirmed = stringResource(R.string.text_confirmed)
    val textWaitingForMiraToConfirmPayment = stringResource(R.string.text_waiting_for_mira_to_confirm_payment)
    val textReportAPaymentProblem = stringResource(R.string.text_report_a_payment_problem)

    val c = RideTheme.colors
    var step by remember { mutableIntStateOf(1) }
    val amount = money(Vehicles[ctx.vehicle.coerceIn(0, Vehicles.lastIndex)].fare)
    LaunchedEffect(Unit) { delay(3500); step = 2; delay(1800); onDone() }
    BackHandler { }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(textTripEndedCash, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 32.dp))
            Text(textPayMiraInCash, color = c.fg, fontSize = 32.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            Column(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(24.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(textAmountDue, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
                Text(amount, color = c.accent, fontSize = 52.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 6.dp))
                Text(textExactChangeHelpsYourDriver, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf(textArrived, textPayDriver, textConfirmed).forEachIndexed { i, label ->
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
                Avatar(stringResource(R.string.driver_initials), 40.dp); Text(textWaitingForMiraToConfirmPayment, color = c.fg, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        RideButton(textReportAPaymentProblem, onReport, Modifier.padding(vertical = 12.dp), style = RideButtonStyle.Text)
    }
}

// ---------- Ride details ----------

@Composable
fun RideDetailsScreen(ctx: MoreCtx, onBack: () -> Unit, onReceipt: () -> Unit, onReport: () -> Unit) {
    val textRideDetails = stringResource(R.string.text_ride_details)
    val textDownloadReceipt = stringResource(R.string.text_download_receipt)
    val textReportATripProblem = stringResource(R.string.text_report_a_trip_problem)
    val textCompleted = stringResource(R.string.text_completed)
    val textTue3Nov84Km19Min = stringResource(R.string.text_tue_3_nov_8_4_km_19_min)
    val textSearchCurrent = stringResource(R.string.search_current)
    val textMiraTanaka = stringResource(R.string.text_mira_tanaka)
    val textOrbitSilverRn2100 = stringResource(R.string.text_orbit_silver_rn_2100)
    val textYouRated4 = stringResource(R.string.text_you_rated_4)
    val textFareBreakdown = stringResource(R.string.text_fare_breakdown)

    val c = RideTheme.colors
    MoreScaffold(textRideDetails, onBack, bottom = { RideButton(textDownloadReceipt, onReceipt); RideButton(textReportATripProblem, onReport, style = RideButtonStyle.Text) }) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(textCompleted, color = c.onAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 10.dp, vertical = 4.dp))
            Text(textTue3Nov84Km19Min, color = c.muted, fontSize = 13.sp)
        }
        RouteBlock(textSearchCurrent, ctx.dest, Modifier.padding(top = 16.dp))
        GroupCard(Modifier.padding(top = 12.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(stringResource(R.string.driver_initials)); Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(textMiraTanaka, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(textOrbitSilverRn2100, color = c.muted, fontSize = 12.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { RideIcon(RideIcons.Star, c.accent, size = 14.dp); Text(textYouRated4, color = c.muted, fontSize = 12.sp) }
            }
        }
        SectionLabel(textFareBreakdown)
        GroupCard { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { FareLines() } }
    }
}

@Composable
private fun FareLines() {
    val textConfirmBase = stringResource(R.string.confirm_base)
    val textDistance84Km = stringResource(R.string.text_distance_8_4_km)
    val textTime19Min = stringResource(R.string.text_time_19_min)
    val textConfirmTaxes = stringResource(R.string.confirm_taxes)
    val textConfirmTotal = stringResource(R.string.confirm_total)

    InfoRow(textConfirmBase, "$3.50"); InfoRow(textDistance84Km, "$8.40"); InfoRow(textTime19Min, "$3.80"); InfoRow(textConfirmTaxes, "$1.10")
    HorizontalDivider(color = RideTheme.colors.line, modifier = Modifier.padding(vertical = 4.dp))
    InfoRow(textConfirmTotal, "$16.80", strong = true)
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
            cv.drawText(context.getString(R.string.app_name), 50f, y, title); y += 28f
            cv.drawText(context.getString(R.string.text_trip_receipt_8f3k2q_tue_3_nov), 50f, y, muted); y += 40f
            listOf(context.getString(R.string.text_pickup) to context.getString(R.string.search_current), context.getString(R.string.text_drop) to drop, context.getString(R.string.text_driver) to context.getString(R.string.text_mira_tanaka_orbit_rn_2100)).forEach { (k, v) -> cv.drawText(k, 50f, y, muted); cv.drawText(v, 150f, y, body); y += 26f }
            y += 16f
            listOf(context.getString(R.string.confirm_base) to "$3.50", context.getString(R.string.text_distance_8_4_km) to "$8.40", context.getString(R.string.text_time_19_min) to "$3.80", context.getString(R.string.confirm_taxes) to "$1.10").forEach { (k, v) -> cv.drawText(k, 50f, y, body); cv.drawText(v, 480f, y, body); y += 24f }
            y += 10f
            cv.drawText(context.getString(R.string.text_total_paid_in_cash), 50f, y, title.apply { textSize = 16f }); cv.drawText("$16.80", 470f, y, title)
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
    val textReceipt = stringResource(R.string.text_receipt)
    val textTrip8f3k2qTue3Nov = stringResource(R.string.text_trip_8f3k2q_tue_3_nov)
    val textSaveAsPdf = stringResource(R.string.text_save_as_pdf)
    val textCouldNotSaveTheReceipt = stringResource(R.string.text_could_not_save_the_receipt)
    val textSavedToDownloads = stringResource(R.string.text_saved_to_downloads)
    val textOpenPdf = stringResource(R.string.text_open_pdf)
    val textNoPdfViewerFound = stringResource(R.string.text_no_pdf_viewer_found)
    val textSearchCurrent = stringResource(R.string.search_current)
    val textFare = stringResource(R.string.text_fare)
    val textPaidInCashToMiraTanaka = stringResource(R.string.text_paid_in_cash_to_mira_tanaka)

    val c = RideTheme.colors
    val context = LocalContext.current
    var saved by remember { mutableStateOf<Uri?>(null) }
    MoreScaffold(textReceipt, onBack, subtitle = textTrip8f3k2qTue3Nov, bottom = {
        if (saved == null) RideButton(textSaveAsPdf, {
            val u = ReceiptPdf.save(context, ctx.dest)
            if (u == null) context.toast(textCouldNotSaveTheReceipt) else { saved = u; context.toast(textSavedToDownloads) }
        }) else RideButton(textOpenPdf, {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(saved, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { context.toast(textNoPdfViewerFound) }
        })
    }) {
        RouteBlock(textSearchCurrent, ctx.dest, Modifier.padding(top = 16.dp))
        SectionLabel(textFare)
        GroupCard { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { FareLines() } }
        Text(textPaidInCashToMiraTanaka, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

// ---------- Scheduled ride ----------

private fun parts(label: String?, fallback: String): Triple<String, String, String> {
    val defaults = fallback.split(", ")
    val p = (label ?: fallback).split(", ")
    val dm = p.getOrElse(1) { defaults[1] }.split(" ")
    return Triple(p.getOrElse(0) { defaults[0] }.uppercase(), dm.joinToString(" "), p.getOrElse(2) { defaults[2] })
}

@Composable
private fun ScheduledBody(ctx: MoreCtx, modifier: Modifier = Modifier) {
    val textPickupTomorrow = stringResource(R.string.text_pickup_tomorrow)
    val textConfirmed = stringResource(R.string.text_confirmed)
    val textSearchCurrent = stringResource(R.string.search_current)
    val textValueValueSeats = stringResource(R.string.text_value_value_seats)
    val textCashEstimatedFare = stringResource(R.string.text_cash_estimated_fare)
    val textReminderTitle = stringResource(R.string.reminder_title)
    val textReminder15 = stringResource(R.string.reminder_15)
    val textWeWillNotifyYouWhenADriverIsAssigned = stringResource(R.string.text_we_will_notify_you_when_a_driver_is_assigned)

    val c = RideTheme.colors
    val (dow, dm, time) = parts(ctx.scheduled, stringResource(R.string.scheduled_sample_date))
    val v = Vehicles[ctx.vehicle.coerceIn(0, Vehicles.lastIndex)]
    Column(modifier) {
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(c.accent.copy(alpha = 0.14f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(dow, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold); Text(dm.substringBefore(' '), color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text(dm.substringAfter(' ', ""), color = c.muted, fontSize = 11.sp)
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) { Text(time, color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text(textPickupTomorrow, color = c.muted, fontSize = 13.sp) }
            Text(textConfirmed, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        RouteBlock(textSearchCurrent, ctx.dest, Modifier.padding(top = 12.dp))
        GroupCard(Modifier.padding(top = 12.dp)) { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            InfoRow(textValueValueSeats.format(v.name, v.seats), money(v.fare)); InfoRow(textCashEstimatedFare, ""); InfoRow(textReminderTitle, textReminder15)
        } }
        Text(textWeWillNotifyYouWhenADriverIsAssigned, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun ScheduledDetailsScreen(ctx: MoreCtx, onBack: () -> Unit, onEdit: () -> Unit, onCancel: () -> Unit) {
    val textScheduledRide = stringResource(R.string.text_scheduled_ride)
    val textYourUpcomingTrip = stringResource(R.string.text_your_upcoming_trip)
    val textEditRide = stringResource(R.string.text_edit_ride)
    val textTripCancelBtn = stringResource(R.string.trip_cancel_btn)

    MoreScaffold(textScheduledRide, onBack, subtitle = textYourUpcomingTrip, bottom = { RideButton(textEditRide, onEdit); DangerButton(textTripCancelBtn, onCancel, Modifier.padding(top = 8.dp)) }) { ScheduledBody(ctx) }
}

@Composable
fun CancelScheduledScreen(ctx: MoreCtx, onKeep: () -> Unit, onCancelled: () -> Unit) {
    val textPlansChanged = stringResource(R.string.text_plans_changed)
    val textReasonMistake = stringResource(R.string.reason_mistake)
    val textFoundAnotherRide = stringResource(R.string.text_found_another_ride)
    val textScheduledRide = stringResource(R.string.text_scheduled_ride)
    val textYourUpcomingTrip = stringResource(R.string.text_your_upcoming_trip)
    val textCancelScheduledRide = stringResource(R.string.text_cancel_scheduled_ride)
    val textValueToValue = stringResource(R.string.text_value_to_value)
    val textTripKeep = stringResource(R.string.trip_keep)
    val textTripCancelBtn = stringResource(R.string.trip_cancel_btn)

    val c = RideTheme.colors
    val reasons = listOf(textPlansChanged, textReasonMistake, textFoundAnotherRide)
    var reason by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(onBack = onKeep)
    Box(Modifier.fillMaxSize()) {
        MoreScaffold(textScheduledRide, onKeep, subtitle = textYourUpcomingTrip) { ScheduledBody(ctx) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        SheetCard(Modifier.align(Alignment.BottomCenter), textCancelScheduledRide, textValueToValue.format(ctx.scheduled ?: stringResource(R.string.scheduled_sample_date), ctx.dest)) {
            Column(Modifier.padding(top = 8.dp)) { reasons.forEachIndexed { i, r -> if (i > 0) RowDivider(); RadioRow(r, reason == i, { reason = i }) } }
            RideButton(textTripKeep, onKeep, Modifier.padding(top = 12.dp))
            DangerButton(textTripCancelBtn, onCancelled, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun EditScheduledScreen(ctx: MoreCtx, onBack: () -> Unit, onSave: (String) -> Unit, onRoute: () -> Unit) {
    val textEditScheduledRide = stringResource(R.string.text_edit_scheduled_ride)
    val textChangeTheDetailsOfYourUpcomingTrip = stringResource(R.string.text_change_the_details_of_your_upcoming_trip)
    val textSaveChanges = stringResource(R.string.text_save_changes)
    val textDiscardChanges = stringResource(R.string.text_discard_changes)
    val textDate = stringResource(R.string.text_date)
    val textTime = stringResource(R.string.text_time)
    val textPm = stringResource(R.string.text_pm)
    val textAm = stringResource(R.string.text_am)
    val textPickup = stringResource(R.string.text_pickup)
    val textSearchCurrent = stringResource(R.string.search_current)
    val textDrop = stringResource(R.string.text_drop)
    val textVehicle = stringResource(R.string.text_vehicle)
    val textTheFareMayChangeIfYouEditTheRouteOrVehicle = stringResource(R.string.text_the_fare_may_change_if_you_edit_the_route_or_vehicle)
    val textSave = stringResource(R.string.text_save)

    val c = RideTheme.colors
    var date by rememberSaveable { mutableLongStateOf(LocalDate.now().plusDays(1).toEpochDay()) }
    var hour by rememberSaveable { mutableIntStateOf(8) }
    var minute by rememberSaveable { mutableIntStateOf(30) }
    var pm by rememberSaveable { mutableStateOf(false) }
    var veh by rememberSaveable { mutableIntStateOf(ctx.vehicle) }
    var rem by rememberSaveable { mutableIntStateOf(2) }
    var sheet by remember { mutableStateOf("") }
    val d = LocalDate.ofEpochDay(date)
    val label = pickLabel(d, hour, minute, pm, textAm, textPm)
    Box(Modifier.fillMaxSize()) {
        MoreScaffold(textEditScheduledRide, onBack, subtitle = textChangeTheDetailsOfYourUpcomingTrip, bottom = {
            RideButton(textSaveChanges, { onSave(label) }); RideButton(textDiscardChanges, onBack, style = RideButtonStyle.Text)
        }) {
            GroupCard(Modifier.padding(top = 16.dp)) {
                NavRow(RideIcons.Calendar, textDate, { sheet = "date" }, subtitle = label.substringBeforeLast(", "))
                RowDivider(); NavRow(RideIcons.Clock, textTime, { sheet = "time" }, subtitle = "%d:%02d %s".format(hour, minute, if (pm) textPm else textAm))
                RowDivider(); NavRow(RideIcons.Pin, textPickup, onRoute, subtitle = textSearchCurrent)
                RowDivider(); NavRow(RideIcons.Flag, textDrop, onRoute, subtitle = ctx.dest)
                RowDivider(); NavRow(RideIcons.Car, textVehicle, { sheet = "vehicle" }, subtitle = "${Vehicles[veh.coerceIn(0, Vehicles.lastIndex)].name} · ${money(Vehicles[veh.coerceIn(0, Vehicles.lastIndex)].fare)}")
                RowDivider(); NavRow(RideIcons.Bell, stringResource(R.string.reminder_title), { sheet = "reminder" }, subtitle = reminderOptions()[rem])
            }
            Text(textTheFareMayChangeIfYouEditTheRouteOrVehicle, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        when (sheet) {
            "date" -> CalendarSheet(d, { date = it.toEpochDay(); sheet = "" }, { sheet = "" })
            "time" -> SheetScreen({ sheet = "" }, textTime) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..12).forEach { h -> Chip("$h", h == hour, { hour = h }, Modifier.widthIn(min = 48.dp)) } }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 15, 30, 45).forEach { m -> Chip("%02d".format(m), m == minute, { minute = m }) }; Spacer(Modifier.width(8.dp))
                    Chip(textAm, !pm, { pm = false }); Chip(textPm, pm, { pm = true })
                }
                RideButton(textSave, { sheet = "" }, Modifier.padding(top = 16.dp))
            }
            "vehicle" -> ChoiceSheet(textVehicle, Vehicles.map { it.name }, veh, { veh = it; sheet = "" }, { sheet = "" }, Vehicles.map { money(it.fare) })
            "reminder" -> ChoiceSheet(stringResource(R.string.reminder_title), reminderOptions(), rem, { rem = it; sheet = "" }, { sheet = "" })
        }
    }
}
