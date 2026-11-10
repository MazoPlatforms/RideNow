package com.ridenow.app.ui.search

import com.google.android.gms.maps.model.LatLng
import com.ridenow.app.ui.map.GoogleApi
import kotlinx.coroutines.delay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.R
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

data class Place(val name: String, val area: String, val distance: String, val placeId: String? = null)

private val Places = listOf(
    Place("Helix Garden Plaza", "Sector 9, Garden Loop", "6.2 km"),
    Place("Helix City Hall", "Sector 2, Civic Square", "3.1 km"),
    Place("Helios Medical Centre", "Sector 5, Skyline Way", "4.8 km"),
    Place("Hello Bakery & Café", "Sector 7, Meridian Walk", "0.4 km"),
    Place("Nova Terminal, Gate 4", "Sector 12, Orbit Ring", "6.2 km"),
    Place("Orbit Ring Station", "Sector 1, Ring Road", "5.5 km"),
    Place("Aurora Medical Centre", "Sector 3, Skyline Way", "4.1 km"),
    Place("Zenith Café", "Sector 7, Meridian Walk", "0.9 km"),
)

/** Destination search: trip type, route card with live search field, suggestions and shortcuts. */
@Composable
fun SearchScreen(
    onBack: () -> Unit, onPlace: (Place, Boolean) -> Unit, onFriend: () -> Unit, onEditPickup: () -> Unit,
    onSaved: () -> Unit, onMap: () -> Unit, onAdd: (String) -> Unit, modifier: Modifier = Modifier, scheduled: String? = null, pickup: String? = null, friend: String? = null, near: LatLng? = null,
) {
    val c = RideTheme.colors
    var returnTrip by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val q = query.trim()
    var remote by remember { mutableStateOf<List<Place>?>(null) }
    LaunchedEffect(q) {
        remote = null
        if (q.length >= 2) {
            delay(250)
            val r = GoogleApi.autocomplete(q, near)
            remote = r?.map { Place(it.name, it.area, "", it.placeId) } ?: Places.filter { it.name.contains(q, true) || it.area.contains(q, true) }
        }
    }
    val results = remote ?: emptyList()
    val loading = q.length >= 2 && remote == null

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp).entrance(0), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
                RideIcon(RideIcons.Back, c.fg, size = 22.dp)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(BrandYellow).clickable(role = Role.Button, onClick = onFriend).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RideIcon(RideIcons.Person, Ink, size = 18.dp); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search_friend), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f)); Spacer(Modifier.width(36.dp))
        }

        if (scheduled != null) Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RideIcon(RideIcons.Clock, c.accent, size = 18.dp); Spacer(Modifier.width(10.dp))
            Text("Scheduled · $scheduled", color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (friend != null) Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RideIcon(RideIcons.Person, c.accent, size = 18.dp); Spacer(Modifier.width(10.dp))
            Text("Booking for $friend", color = c.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        TripTypeToggle(returnTrip, { returnTrip = it }, Modifier.padding(top = 14.dp).entrance(60))

        RouteCard(
            returnTrip, query, { query = it }, focus, onEditPickup, pickup,
            onSubmit = { results.firstOrNull()?.let { onPlace(it, returnTrip) } },
            modifier = Modifier.padding(top = 14.dp).entrance(120),
        )

        when {
            q.isEmpty() -> LazyColumn(Modifier.weight(1f).padding(top = 10.dp)) {
                item { OptionRow(RideIcons.Heart, stringResource(R.string.search_saved), null, chevron = true, onClick = onSaved) }
                item { OptionRow(RideIcons.Map, stringResource(R.string.search_map), null, chevron = true, onClick = onMap) }
                item { OptionRow(RideIcons.Home, stringResource(R.string.search_add_home), null, plus = true) { onAdd("Home") } }
                item { OptionRow(RideIcons.Work, stringResource(R.string.search_add_work), null, plus = true) { onAdd("Work") } }
                item { OptionRow(RideIcons.Clock, "Nova Terminal, Gate 4", "Sector 12, Orbit Ring", chevron = true) { onPlace(Places[4], returnTrip) } }
            }
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), color = c.accent, strokeWidth = 3.dp) }
            results.isEmpty() -> EmptyResults(q, onMap, Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f).padding(top = 6.dp)) {
                items(results, key = { it.placeId ?: it.name }) { p -> SuggestionRow(p, q) { onPlace(p, returnTrip) } }
            }
        }
    }
}

@Composable
private fun TripTypeToggle(returnTrip: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(false to R.string.search_one_way, true to R.string.search_return).forEach { (isReturn, label) ->
            val on = returnTrip == isReturn
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.RadioButton) { onChange(isReturn) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).then(if (on) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.muted, CircleShape)), contentAlignment = Alignment.Center) {
                    if (on) RideIcon(RideIcons.Check, c.onAccent, size = 13.dp)
                }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(label), color = if (on) c.fg else c.muted, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun RouteCard(
    returnTrip: Boolean, query: String, onQuery: (String) -> Unit, focus: FocusRequester,
    onEditPickup: () -> Unit, pickup: String?, onSubmit: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    val rows = if (returnTrip) 3 else 2
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))
            .drawBehind {
                val x = 21.dp.toPx()
                drawLine(c.border, Offset(x, 32.dp.toPx()), Offset(x, ((rows - 1) * 64 + 32).dp.toPx()), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            },
    ) {
        RouteRow(stringResource(R.string.search_pickup), false, Marker.Pickup) {
            Text(pickup ?: stringResource(R.string.search_current), color = c.fg, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onEditPickup).semantics { contentDescription = "Edit pickup location" }, contentAlignment = Alignment.Center) {
                RideIcon(RideIcons.Pencil, c.accent, size = 18.dp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        RouteRow(stringResource(if (returnTrip) R.string.search_stop else R.string.search_drop), true, if (returnTrip) Marker.Stop else Marker.Drop) {
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true, modifier = Modifier.weight(1f).focusRequester(focus),
                textStyle = TextStyle(color = c.fg, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                decorationBox = { inner -> Box { if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = c.muted, fontSize = 16.sp); inner() } },
            )
            if (query.isNotEmpty()) Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { onQuery("") }.semantics { contentDescription = "Clear" }, contentAlignment = Alignment.Center) {
                RideIcon(RideIcons.Close, c.muted, size = 16.dp)
            } else if (!returnTrip) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Plus, c.accent) }
        }
        if (returnTrip) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            RouteRow(stringResource(R.string.search_drop), false, Marker.Drop) {
                Text(stringResource(R.string.search_same), color = c.fg, fontSize = 16.sp)
            }
        }
    }
}

private enum class Marker { Pickup, Stop, Drop }

@Composable
private fun RouteRow(label: String, active: Boolean, marker: Marker, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().height(63.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp), contentAlignment = Alignment.Center) {
            when (marker) {
                Marker.Pickup -> Box(Modifier.size(10.dp).clip(CircleShape).background(BrandYellow))
                Marker.Stop -> Box(Modifier.size(10.dp).clip(CircleShape).border(2.dp, c.accent, CircleShape))
                Marker.Drop -> Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c.fg))
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = if (active) c.accent else c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable
private fun OptionRow(icon: List<String>, title: String, subtitle: String?, chevron: Boolean = false, plus: Boolean = false, onClick: () -> Unit) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(icon, c.accent, size = 18.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.fg, fontSize = 15.sp)
                if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 12.sp)
            }
            if (chevron) RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
            if (plus) RideIcon(RideIcons.Plus, c.muted, size = 18.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

@Composable
private fun SuggestionRow(place: Place, query: String, onClick: () -> Unit) {
    val c = RideTheme.colors
    val title = buildAnnotatedString {
        val i = place.name.indexOf(query, ignoreCase = true)
        if (i < 0) append(place.name) else {
            append(place.name.substring(0, i))
            withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.SemiBold)) { append(place.name.substring(i, i + query.length)) }
            append(place.name.substring(i + query.length))
        }
    }
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Pin, c.accent) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.fg, fontSize = 15.sp)
                Text(place.area, color = c.muted, fontSize = 12.sp)
            }
            Text(place.distance, color = c.muted, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

@Composable
private fun EmptyResults(query: String, onMap: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Column(modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(100.dp).clip(CircleShape).border(1.5.dp, c.border, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) {
                RideIcon(RideIcons.Search, c.accent, size = 28.dp)
            }
        }
        Text(stringResource(R.string.search_none, query), color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(stringResource(R.string.search_none_tip), color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.width(240.dp)) { RideButton(stringResource(R.string.search_choose_map), onMap, style = RideButtonStyle.Text) }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SearchDarkPreview() = RideNowTheme(darkTheme = true) { SearchScreen({}, { _, _ -> }, {}, {}, {}, {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SearchLightPreview() = RideNowTheme(darkTheme = false) { SearchScreen({}, { _, _ -> }, {}, {}, {}, {}, {}) }
