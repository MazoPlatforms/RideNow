package com.ridenow.driver.ui.registration

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

private class PhotoSlot(val label: Int, val kind: VehiclePhotoKind, val optional: Boolean)
private val Slots = listOf(
    PhotoSlot(R.string.vphoto_front, VehiclePhotoKind.Front, false), PhotoSlot(R.string.vphoto_rear, VehiclePhotoKind.Rear, false),
    PhotoSlot(R.string.vphoto_left, VehiclePhotoKind.Left, false), PhotoSlot(R.string.vphoto_right, VehiclePhotoKind.Right, false),
    PhotoSlot(R.string.vphoto_interior, VehiclePhotoKind.Interior, true), PhotoSlot(R.string.vphoto_extra, VehiclePhotoKind.Extra, true),
)

/** Form section of the merged step: 6 of 9: front, rear, left and right photos are required; interior and additional are optional. */
@Composable
fun VehiclePhotosContent(onValid: (Boolean) -> Unit, onSummary: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val photos = remember { mutableStateListOf<Bitmap?>(null, null, null, null, null, null) }
    val openPicker = rememberPhotoPicker(stringResource(R.string.vphoto_sheet_title)) { i, b -> photos[i] = b }
    val canContinue = Slots.indices.all { Slots[it].optional || photos[it] != null }

    val summary = stringResource(R.string.review_photos, photos.count { it != null })
    LaunchedEffect(canContinue) { onValid(canContinue) }
    LaunchedEffect(summary) { onSummary(summary) }
    Column(modifier) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Slots.withIndex().chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (i, slot) ->
                            VehiclePhotoTile(stringResource(slot.label), slot.kind, photos[i], slot.optional, { openPicker(i, photos[i] != null) }, Modifier.weight(1f))
                        }
                    }
                }
                Text(stringResource(R.string.vphoto_tip), color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
    }
}
