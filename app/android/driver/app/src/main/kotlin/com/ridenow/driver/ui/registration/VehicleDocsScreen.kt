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

/** Form section of the merged step: 5 of 9: photo and expiry date for the registration certificate, revenue license and insurance. */
@Composable
fun VehicleDocsContent(onValid: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val photos = remember { mutableStateListOf<Bitmap?>(null, null, null) }
    var e0 by rememberSaveable { mutableStateOf<Long?>(null) }
    var e1 by rememberSaveable { mutableStateOf<Long?>(null) }
    var e2 by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by rememberSaveable { mutableStateOf(-1) }
    val today = remember { todayUtcMillis() }
    val openPicker = rememberPhotoPicker(stringResource(R.string.doc_photo_title)) { i, b -> photos[i] = b }
    val expiries = listOf(e0, e1, e2)
    val canContinue = photos.all { it != null } && expiries.all { it != null }
    val titles = listOf(R.string.vdoc_registration, R.string.vdoc_revenue, R.string.vdoc_insurance)
    val kinds = listOf(PaperKind.Certificate, PaperKind.Revenue, PaperKind.Insurance)

    LaunchedEffect(canContinue) { onValid(canContinue) }
    Column(modifier) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                titles.forEachIndexed { i, res ->
                    DocumentCard(
                        stringResource(res), kinds[i], photos[i], expiries[i]?.let { formatPickedDate(it) }.orEmpty(),
                        onUpload = { openPicker(i, photos[i] != null) }, onExpiry = { picking = i },
                    )
                }
                Text(stringResource(R.string.vdoc_tip), color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
    }

    if (picking >= 0) {
        val initial = expiries[picking]
        RideDatePickerDialog(initial, today, null, { picking = -1 }, {
            when (picking) { 0 -> e0 = it; 1 -> e1 = it; else -> e2 = it }
            picking = -1
        })
    }
}
