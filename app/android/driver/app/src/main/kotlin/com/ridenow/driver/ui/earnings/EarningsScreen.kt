package com.ridenow.driver.ui.earnings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.home.MainBottomBar
import com.ridenow.driver.ui.home.MainTab
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.ridenow.driver.ui.trip.CompletedTrip
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Earnings tab. There are no trips yet, so every figure is zero; the chart, totals and history are
 * laid out for the real data. [onlineMinutes] is the real time online today.
 */
@Composable
fun EarningsScreen(onlineMinutes: Int, earnings: Double, history: List<CompletedTrip>, onTab: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var period by rememberSaveable { mutableIntStateOf(0) } // 0 today, 1 this week
    val format = remember { NumberFormat.getCurrencyInstance() }
    val money = format.format(earnings)
    val today = remember { LocalDate.now().dayOfWeek.value - 1 }
    val week = remember(earnings) { List(7) { if (it == today) earnings.toFloat() else 0f } } // earnings per day, Monday first

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.earn_title), color = c.fg, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp).semantics { heading() })
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RideChip(stringResource(R.string.earn_today), period == 0, { period = 0 }, Modifier.weight(1f))
                RideChip(stringResource(R.string.earn_week), period == 1, { period = 1 }, Modifier.weight(1f))
            }
            Column(Modifier.padding(top = 20.dp).entrance(60)) {
                Text(stringResource(R.string.earn_total), color = c.muted, fontSize = 13.sp)
                Text(money, color = c.fg, fontSize = 44.sp, fontWeight = FontWeight.Light)
            }
            WeekChart(week, today, Modifier.padding(top = 16.dp).entrance(120))
            Row(Modifier.padding(top = 16.dp).entrance(180), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(stringResource(R.string.earn_trips), history.size.toString(), Modifier.weight(1f))
                StatCard(stringResource(R.string.earn_online), stringResource(R.string.home_time_fmt, onlineMinutes / 60, onlineMinutes % 60), Modifier.weight(1f))
                StatCard(stringResource(R.string.earn_cash), money, Modifier.weight(1f))
            }
            SectionLabel(stringResource(R.string.earn_history))
            if (history.isEmpty()) {
                GroupCard(Modifier.padding(bottom = 20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        RideIcon(RideIcons.Receipt, c.muted, size = 28.dp)
                        Text(stringResource(R.string.earn_empty_title), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                        Text(stringResource(R.string.earn_empty_text), color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else GroupCard(Modifier.padding(bottom = 20.dp)) {
                history.forEachIndexed { i, trip ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(trip.rider, color = c.fg, fontSize = 14.sp)
                            Text(stringResource(R.string.earn_trip_route, trip.pickup, trip.drop), color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Text(format.format(trip.fare), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        MainBottomBar(MainTab.Earnings, onTab)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    val c = RideTheme.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(value, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = c.muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Seven bars, Monday first. Zero values show a thin baseline; today is highlighted. */
@Composable
private fun WeekChart(values: List<Float>, today: Int, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val description = stringResource(R.string.earn_chart_desc)
    val labels = remember { DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) } }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).padding(16.dp).semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
            val slot = size.width / values.size
            val barW = slot * 0.46f
            values.forEachIndexed { i, v ->
                val h = (size.height * (v / max)).coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    if (i == today) c.fill else c.line, Offset(slot * i + (slot - barW) / 2f, size.height - h), Size(barW, h), CornerRadius(4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            labels.forEachIndexed { i, l ->
                Text(l, color = if (i == today) c.accent else c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun EarningsDarkPreview() = RideNowTheme(darkTheme = true) { EarningsScreen(75, 16.8, emptyList(), {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun EarningsLightPreview() = RideNowTheme(darkTheme = false) { EarningsScreen(0, 0.0, emptyList(), {}) }
