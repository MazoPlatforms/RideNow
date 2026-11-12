package com.ridenow.driver.ui.registration

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.currentCity
import com.ridenow.driver.ui.util.entrance
import com.ridenow.driver.ui.util.hasLocationPermission
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val EmailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

/**
 * Step 3 of 11: face photo, full name, date of birth (18+), gender, optional email and detected location.
 * Continue unlocks when the photo, name, date of birth and gender are set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoScreen(onBack: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var dob by rememberSaveable { mutableStateOf<Long?>(null) }
    var gender by rememberSaveable { mutableStateOf<Int?>(null) }
    var city by rememberSaveable { mutableStateOf<String?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var emailFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (city == null) city = currentCity(context) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { if (it != null) photo = it }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) photo = decodeSampled(context, uri) }

    val emailOk = email.isBlank() || EmailRegex.matches(email.trim())
    val canContinue = photo != null && name.trim().length >= 2 && dob != null && gender != null && emailOk
    val dobText = dob?.let { SimpleDateFormat("dd / MM / yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(it)) } ?: ""

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(3, 11, stringResource(R.string.personal_tag), stringResource(R.string.personal_title), stringResource(R.string.personal_subtitle), onBack)

            Row(Modifier.padding(top = 20.dp).fillMaxWidth().entrance(200), verticalAlignment = Alignment.CenterVertically) {
                Avatar(photo) { showSheet = true }
                Column(Modifier.weight(1f).padding(start = 20.dp)) {
                    Row {
                        Text(stringResource(R.string.personal_photo), color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(" \u00b7 " + stringResource(R.string.personal_required), color = c.accent, fontSize = 14.sp)
                    }
                    Text(stringResource(R.string.personal_photo_hint), color = c.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Column(Modifier.padding(top = 18.dp).entrance(260), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RideTextField(
                    name, { name = it.take(60) }, stringResource(R.string.personal_name), RideIcons.Person,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.clearFocus() }),
                )
                RideSelectField(stringResource(R.string.personal_dob), dobText, stringResource(R.string.personal_dob_placeholder), RideIcons.Calendar, onClick = { focus.clearFocus(); showDate = true })
                Column {
                    Text(stringResource(R.string.personal_gender), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.gender_male, R.string.gender_female, R.string.gender_other).forEachIndexed { i, res ->
                            RideChip(stringResource(res), gender == i, { gender = i }, Modifier.weight(1f))
                        }
                    }
                }
                RideTextField(
                    email, { email = it.take(80) }, stringResource(R.string.personal_email), RideIcons.Mail,
                    isError = !emailOk && !emailFocused,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    onFocusChange = { emailFocused = it },
                )
                if (!emailOk && !emailFocused) Text(stringResource(R.string.personal_email_error), color = c.danger, fontSize = 13.sp)
                RideSelectField(
                    stringResource(R.string.personal_location), city.orEmpty(),
                    stringResource(if (hasLocationPermission(context)) R.string.personal_location_detecting else R.string.personal_location_unknown),
                    RideIcons.Pin,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        RideButton(stringResource(R.string.personal_continue), onClick = onDone, enabled = canContinue, modifier = Modifier.padding(vertical = 12.dp))
    }

    if (showSheet) {
        PhotoSheet(
            title = stringResource(R.string.photo_title),
            hasPhoto = photo != null,
            onCamera = { camera.launch(null) },
            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemove = { photo = null },
            onDismiss = { showSheet = false },
        )
    }
    if (showDate) DobDialog(dob, onDismiss = { showDate = false }, onConfirm = { dob = it; showDate = false })
}

/** Latest allowed birth date (today minus 18 years) as UTC millis, plus its year. */
private fun latestBirth(): Pair<Long, Int> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { add(Calendar.YEAR, -18) }
    return cal.timeInMillis to cal.get(Calendar.YEAR)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DobDialog(initial: Long?, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val c = RideTheme.colors
    val (maxMillis, maxYear) = remember { latestBirth() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial, initialDisplayedMonthMillis = initial ?: maxMillis, yearRange = 1940..maxYear,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= maxMillis
            override fun isSelectableYear(year: Int) = year <= maxYear
        },
    )
    val colors = DatePickerDefaults.colors(containerColor = c.bg)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let(onConfirm) }, enabled = state.selectedDateMillis != null) { Text(stringResource(R.string.dialog_ok), color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel), color = c.fg) } },
        colors = colors,
    ) { DatePicker(state = state, colors = colors) }
}

@Composable
private fun Avatar(photo: Bitmap?, onClick: () -> Unit) {
    val textPhotoTitle = stringResource(R.string.photo_title)
    val c = RideTheme.colors
    val accent = c.accent
    Box(Modifier.size(96.dp).semantics { contentDescription = textPhotoTitle }) {
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
            else RideIcon(RideIcons.Person, c.accent, size = 40.dp)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(36.dp).clip(CircleShape).background(c.fill).border(3.dp, c.bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) { RideIcon(RideIcons.Camera, c.onFill, size = 16.dp) }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PersonalDarkPreview() = RideNowTheme(darkTheme = true) { PersonalInfoScreen({}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PersonalLightPreview() = RideNowTheme(darkTheme = false) { PersonalInfoScreen({}, {}) }
