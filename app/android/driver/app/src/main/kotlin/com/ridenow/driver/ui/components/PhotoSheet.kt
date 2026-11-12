package com.ridenow.driver.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.theme.RideTheme
import kotlinx.coroutines.launch

/** Bottom sheet offering camera, gallery and (when a photo exists) remove. Shared by the photo and document pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSheet(title: String, hasPhoto: Boolean, onCamera: () -> Unit, onGallery: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val c = RideTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun pick(action: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); action() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = c.bg, contentColor = c.fg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Text(title, color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))) {
                SheetRow(RideIcons.Camera, stringResource(R.string.photo_camera), false) { pick(onCamera) }
                SheetRow(RideIcons.Image, stringResource(R.string.photo_gallery), false) { pick(onGallery) }
                if (hasPhoto) SheetRow(RideIcons.Trash, stringResource(R.string.photo_remove), true) { pick(onRemove) }
            }
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(52.dp).clickable(role = Role.Button) { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.photo_cancel), color = c.fg, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SheetRow(icon: List<String>, label: String, danger: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        RideIcon(icon, if (danger) c.danger else c.accent)
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (danger) c.danger else c.fg, fontSize = 15.sp)
    }
}

/** Decodes a picked image at roughly 1024 px so large photos do not use much memory. */
fun decodeSampled(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}.getOrNull()

