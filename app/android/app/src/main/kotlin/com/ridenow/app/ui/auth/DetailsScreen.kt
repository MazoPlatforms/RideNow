package com.ridenow.app.ui.auth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.R
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RideTextField
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.util.entrance
import kotlinx.coroutines.launch

private val EmailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

/** Step 3 of 3: photo, full name and optional email. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(onBack: () -> Unit, onDone: (name: String, email: String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var emailFocused by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { if (it != null) photo = it }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo = decodeSampled(context, uri)
    }

    val emailOk = email.isBlank() || EmailRegex.matches(email.trim())
    val canContinue = name.trim().length >= 2 && emailOk
    fun submit() { if (canContinue) onDone(name.trim(), email.trim()) }

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(
                Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }

            Row(Modifier.padding(top = 10.dp).fillMaxWidth().entrance(0), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.accent)) }
            }

            Column(Modifier.entrance(80)) {
                Text(
                    stringResource(R.string.details_title), color = c.fg, fontSize = 32.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(top = 32.dp).semantics { heading() },
                )
                Text(stringResource(R.string.details_subtitle), color = c.muted, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
            }

            Column(Modifier.fillMaxWidth().padding(top = 24.dp).entrance(140), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(photo, onClick = { showSheet = true })
                Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { showSheet = true }, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.details_upload), color = c.accent, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }

            Column(Modifier.padding(top = 12.dp).entrance(200), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RideTextField(
                    name, { name = it.take(60) }, stringResource(R.string.details_name), RideIcons.Person,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                )
                RideTextField(
                    email, { email = it.take(80) }, stringResource(R.string.details_email), RideIcons.Mail,
                    isError = !emailOk && !emailFocused,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); submit() }),
                    onFocusChange = { emailFocused = it },
                )
            }
            Text(
                if (!emailOk && !emailFocused) stringResource(R.string.details_email_error) else stringResource(R.string.details_email_hint),
                color = if (!emailOk && !emailFocused) c.danger else c.muted, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            )
        }
        RideButton(stringResource(R.string.details_continue), onClick = { submit() }, enabled = canContinue, modifier = Modifier.padding(vertical = 12.dp))
    }

    if (showSheet) {
        PhotoSheet(
            hasPhoto = photo != null,
            onCamera = { camera.launch(null) },
            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemove = { photo = null },
            onDismiss = { showSheet = false },
        )
    }
}

@Composable
private fun Avatar(photo: Bitmap?, onClick: () -> Unit) {
    val c = RideTheme.colors
    val accent = c.accent
    Box(Modifier.size(112.dp).semantics { contentDescription = "Profile photo" }) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(c.field).clickable(role = Role.Button, onClick = onClick)
                .drawBehind {
                    if (photo == null) drawCircle(
                        accent, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
                        radius = size.minDimension / 2 - 1.dp.toPx(),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) Image(photo.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else RideIcon(RideIcons.Person, c.accent, size = 44.dp)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(40.dp).clip(CircleShape).background(BrandYellow)
                .border(3.dp, c.bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) { RideIcon(RideIcons.Camera, Ink, size = 18.dp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSheet(hasPhoto: Boolean, onCamera: () -> Unit, onGallery: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val c = RideTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun pick(action: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); action() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = c.bg, contentColor = c.fg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Text(stringResource(R.string.photo_title), color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
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
private fun decodeSampled(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}.getOrNull()

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DetailsDarkPreview() = RideNowTheme(darkTheme = true) { DetailsScreen({}, { _, _ -> }) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DetailsLightPreview() = RideNowTheme(darkTheme = false) { DetailsScreen({}, { _, _ -> }) }
