package com.ridenow.app.ui.post

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.booking.Vehicle
import com.ridenow.app.ui.booking.Vehicles
import com.ridenow.app.ui.booking.money
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

/** Arrival summary: total, receipt, rating and problem reporting. [rating] is 0 when not yet rated. */
@Composable
fun RideCompleteScreen(
    destination: String, vehicle: Vehicle, total: Double, rating: Int,
    onReceipt: () -> Unit, onRate: () -> Unit, onReport: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier,
) {
    val textYouHaveArrived = stringResource(R.string.text_you_have_arrived)
    val textTripTotal = stringResource(R.string.text_trip_total)
    val textPaid = stringResource(R.string.text_paid)
    val text84Km19MinValueRn2100 = stringResource(R.string.text_text_8_4_km_19_min_value_rn_2100)
    val textDownloadReceipt = stringResource(R.string.text_download_receipt)
    val textPdfTrip8f3k2q = stringResource(R.string.text_pdf_trip_8f3k2q)
    val textRateYourDriver = stringResource(R.string.text_rate_your_driver)
    val textYouRatedValue = stringResource(R.string.text_you_rated_value)
    val textMiraTanaka = stringResource(R.string.text_mira_tanaka)
    val textReportATripProblem = stringResource(R.string.text_report_a_trip_problem)
    val textBehaviorRoutePaymentAndMore = stringResource(R.string.text_behavior_route_payment_and_more)
    val textDone = stringResource(R.string.text_done)

    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 28.dp).size(150.dp).entrance(0), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(c.ring1, 74.dp.toPx(), style = Stroke(1.dp.toPx())); drawCircle(c.ring2, 57.dp.toPx(), style = Stroke(1.dp.toPx()))
                }
                Box(Modifier.size(80.dp).clip(CircleShape).background(BrandYellow), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Check, Ink, size = 38.dp) }
            }
            Text(textYouHaveArrived, color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 20.dp).semantics { heading() }.entrance(80))
            Text(destination, color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))

            Column(Modifier.padding(top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(16.dp).entrance(160)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(textTripTotal, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.weight(1f))
                    Text(textPaid, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp))
                }
                Text(money(total), color = c.fg, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                Text(text84Km19MinValueRn2100.format(vehicle.name), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Column(Modifier.padding(top = 14.dp).fillMaxWidth().entrance(240)) {
                HorizontalDivider(color = c.line)
                ActionRow(RideIcons.Download, textDownloadReceipt, textPdfTrip8f3k2q, onReceipt)
                ActionRow(RideIcons.Star, textRateYourDriver, if (rating > 0) textYouRatedValue.format(rating) else textMiraTanaka, onRate)
                ActionRow(RideIcons.Flag, textReportATripProblem, textBehaviorRoutePaymentAndMore, onReport)
            }
        }
        RideButton(textDone, onDone, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun ActionRow(icon: List<String>, title: String, subtitle: String, onClick: () -> Unit) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(icon, c.accent, size = 18.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, color = c.fg, fontSize = 15.sp); Text(subtitle, color = c.muted, fontSize = 12.sp) }
            RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
        }
        HorizontalDivider(color = c.line)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CompletePreview() = RideNowTheme(darkTheme = true) { RideCompleteScreen("Helix Garden Plaza, Sector 9", Vehicles[1], 16.80, 0, {}, {}, {}, {}) }
