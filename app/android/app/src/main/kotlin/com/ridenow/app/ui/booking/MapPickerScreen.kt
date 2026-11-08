package com.ridenow.app.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.map.KATTANKUDY_MAP_CENTER
import com.ridenow.app.ui.map.MapCoordinate
import com.ridenow.app.ui.map.MazoMap
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import androidx.compose.foundation.clickable

/**
 * Select a real longitude/latitude from the native map, not a synthetic street address.
 * Mazo Maps does not currently publish a reverse-geocoding endpoint.
 */
@Composable
fun MapPickerScreen(
    title: String,
    confirmLabel: String,
    onBack: () -> Unit,
    onConfirm: (MapCoordinate) -> Unit,
    modifier: Modifier = Modifier,
    initialCenter: MapCoordinate? = null,
) {
    val colors = RideTheme.colors
    var coordinate by remember(initialCenter) {
        mutableStateOf(initialCenter ?: KATTANKUDY_MAP_CENTER)
    }

    Box(modifier.fillMaxSize().background(colors.bg)) {
        MazoMap(
            modifier = Modifier.fillMaxSize(),
            initialCenter = initialCenter,
            showLocate = true,
            onCenterChanged = { coordinate = it },
        )
        Box(
            Modifier.align(Alignment.Center).padding(bottom = 38.dp).size(46.dp)
                .background(BrandYellow, CircleShape)
                .border(3.dp, colors.bg, CircleShape),
            contentAlignment = Alignment.Center
        ) { RideIcon(RideIcons.Pin, com.ridenow.app.ui.theme.Ink, size = 25.dp) }

        Box(
            Modifier.statusBarsPadding().padding(start = 20.dp, top = 10.dp)
                .size(48.dp).background(colors.field, CircleShape)
                .border(1.dp, colors.border, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { RideIcon(RideIcons.Back, colors.fg, size = 22.dp) }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(colors.bg, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding().padding(20.dp)
        ) {
            Text(title, color = colors.fg, fontSize = 20.sp)
            Text(
                "Move the live map to position the pin. Map coordinates are saved exactly.",
                color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .background(colors.field, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RideIcon(RideIcons.Pin, colors.accent)
                Text(coordinate.label(), color = colors.fg, fontSize = 15.sp)
            }
            RideButton(
                confirmLabel,
                { onConfirm(coordinate) },
                enabled = coordinate.valid,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MapPickerPreview() = RideNowTheme(darkTheme = true) {
    MapPickerScreen("Set drop location", "Confirm location", {}, {})
}
