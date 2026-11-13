package com.ridenow.driver.ui.registration

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
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
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.ridenow.driver.ui.util.currentCity

private val Zones = listOf(R.string.zone_centre, R.string.zone_airport, R.string.zone_harbour, R.string.zone_university, R.string.zone_station, R.string.zone_suburbs)

/** Step 10 of 11: service city, radius, zones and whether trips outside the area are accepted. */
@Composable
fun ServiceAreaScreen(onBack: () -> Unit, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var city by rememberSaveable { mutableStateOf("") }
    var radius by rememberSaveable { mutableFloatStateOf(15f) }
    var zones by rememberSaveable { mutableIntStateOf(0b000011) }
    var outside by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (city.isBlank()) city = currentCity(context)?.substringBefore(",").orEmpty() }

    val km = radius.toInt()
    val canContinue = city.isNotBlank() && zones != 0

    val summary = stringResource(R.string.review_area, city.trim(), km, Integer.bitCount(zones))
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(10, 11, stringResource(R.string.area_tag), stringResource(R.string.area_title), stringResource(R.string.area_subtitle), onBack)
            Column(Modifier.padding(top = 20.dp).entrance(200), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AreaPreview(city, km)
                RideTextField(
                    city, { city = it.take(40) }, stringResource(R.string.area_city), RideIcons.Pin,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.area_radius), color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.area_radius_value, km), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = radius, onValueChange = { radius = it }, valueRange = 5f..50f, steps = 8,
                        colors = SliderDefaults.colors(thumbColor = c.fill, activeTrackColor = c.fill, inactiveTrackColor = c.line, activeTickColor = c.onFill, inactiveTickColor = c.border),
                    )
                }
                Column {
                    Text(stringResource(R.string.area_zones), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Zones.withIndex().chunked(2).forEach { pair ->
                        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (i, res) -> ZoneChip(stringResource(res), zones and (1 shl i) != 0, { zones = zones xor (1 shl i) }, Modifier.weight(1f)) }
                        }
                    }
                    if (zones == 0) Text(stringResource(R.string.area_zones_error), color = c.danger, fontSize = 13.sp)
                }
                RideSwitchRow(stringResource(R.string.area_outside), outside, { outside = it })
            }
            Spacer(Modifier.height(12.dp))
        }
        RideButton(stringResource(R.string.id_continue), onClick = { onDone(summary) }, enabled = canContinue, modifier = Modifier.padding(vertical = 12.dp))
    }
}

/** Stylised city map with the service radius drawn around the driver's city. */
@Composable
private fun AreaPreview(city: String, km: Int) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth().height(170.dp).clip(shape).background(c.bg).border(1.dp, c.border, shape)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCityStreets(c, 0.9f)
            val centre = Offset(size.width / 2f, size.height / 2f)
            val r = (28.dp + 70.dp * ((km - 5) / 45f)).toPx()
            drawCircle(c.accent.copy(alpha = 0.14f), r, centre)
            drawCircle(c.accent, r, centre, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 14f))))
            drawCircle(c.fill, 8.dp.toPx(), centre); drawCircle(c.fg, 8.dp.toPx(), centre, style = Stroke(3.dp.toPx()))
        }
        Text(
            stringResource(R.string.area_map_label, city.ifBlank { "\u2014" }, km), color = c.bg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(CircleShape).background(c.fg).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ZoneChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = RideTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.heightIn(min = 48.dp).clip(shape).background(if (selected) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.border, shape).selectable(selected, role = Role.Checkbox, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) c.accent else c.fg, fontSize = 14.sp, maxLines = 1) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ServiceAreaDarkPreview() = RideNowTheme(darkTheme = true) { ServiceAreaScreen({}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ServiceAreaLightPreview() = RideNowTheme(darkTheme = false) { ServiceAreaScreen({}, {}) }
