package com.ridenow.driver.ui.auth

import com.ridenow.driver.ui.components.offsetByStartDirection

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.RideButton
import com.ridenow.driver.ui.components.RideIcon
import com.ridenow.driver.ui.components.RideIcons
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance
import kotlinx.coroutines.delay

/** Step 1 of 3: ask for the rider's mobile number. [onCodeSent] receives the number in +E.164 style. */
@Composable
fun PhoneScreen(onBack: () -> Unit, onCodeSent: (String) -> Unit, modifier: Modifier = Modifier) {
    val textSearchBack = stringResource(R.string.search_back)
    val textCountryCodeValueValue = stringResource(R.string.text_country_code_value_value)

    val c = RideTheme.colors
    val context = LocalContext.current
    var iso by rememberSaveable { mutableStateOf(detectCountryIso(context)) }
    var hintAsked by rememberSaveable { mutableStateOf(false) }
    var digits by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val country = Countries.byIso(iso)
    val valid = digits.length >= country.min
    val focusRequester = remember { FocusRequester() }
    val borderColor by animateColorAsState(if (focused) c.accent else c.border, label = "border")

    // Android's own phone number prompt: picks the number and country for the user. No permission needed.
    val hintLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            readPhoneFromHint(context, result.data) { full ->
                splitFullNumber(full)?.let { (picked, rest) -> iso = picked.iso; digits = rest.take(picked.max) }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!hintAsked) {
            hintAsked = true
            requestPhoneHint(context) { sender -> hintLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
        }
    }
    LaunchedEffect(Unit) { delay(400); focusRequester.requestFocus() }
    LaunchedEffect(sending) {
        if (sending) { delay(900); onCodeSent(country.dial + digits) }
    }
    fun send() { if (valid && !sending) sending = true }

    Column(
        modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Box(
            Modifier.offsetByStartDirection((-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = textSearchBack },
            contentAlignment = Alignment.Center,
        ) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }

        Row(Modifier.padding(top = 10.dp).fillMaxWidth().entrance(0), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) {
                    if (i == 0) Box(Modifier.fillMaxSize().background(c.accent))
                }
            }
        }

        Column(Modifier.entrance(80)) {
            Text(
                stringResource(R.string.phone_title), color = c.fg, fontSize = 32.sp, fontWeight = FontWeight.Light,
                lineHeight = 38.sp, modifier = Modifier.padding(top = 32.dp).semantics { heading() },
            )
            Text(stringResource(R.string.phone_subtitle), color = c.muted, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
        }

        Column(Modifier.padding(top = 32.dp).entrance(160)) {
            Text(stringResource(R.string.phone_label), color = c.muted, fontSize = 13.sp)
            Row(Modifier.padding(top = 8.dp).height(60.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxHeight().width(96.dp).clip(RoundedCornerShape(16.dp)).background(c.field)
                        .border(1.dp, c.border, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { showPicker = true }.padding(horizontal = 14.dp)
                        .semantics { contentDescription = textCountryCodeValueValue.format(country.displayName, country.dial) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(country.dial, color = c.fg, fontSize = 17.sp)
                    Spacer(Modifier.width(6.dp))
                    RideIcon(RideIcons.ChevronDown, c.muted, size = 16.dp)
                }
                BasicTextField(
                    value = digits,
                    onValueChange = { v -> digits = v.filter(Char::isDigit).take(country.max) },
                    modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    textStyle = TextStyle(color = c.fg, fontSize = 20.sp, letterSpacing = 0.8.sp),
                    cursorBrush = SolidColor(c.accent),
                    visualTransformation = PhoneTransformation(country.pattern),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { send() }),
                    decorationBox = { inner ->
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.field)
                                .border(1.dp, borderColor, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (digits.isEmpty()) Text(country.example, color = c.muted, fontSize = 20.sp, letterSpacing = 0.8.sp)
                            inner()
                        }
                    },
                )
            }
            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.Top) {
                RideIcon(RideIcons.Lock, c.accent, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.phone_privacy), color = c.muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }

        Spacer(Modifier.weight(1f))
        Column(Modifier.entrance(240).padding(bottom = 12.dp)) {
            RideButton(stringResource(R.string.phone_send), onClick = { send() }, enabled = valid, loading = sending)
            Text(
                stringResource(R.string.phone_terms), color = c.muted, fontSize = 12.sp, lineHeight = 18.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }

    if (showPicker) {
        CountryPickerSheet(
            selected = country,
            onSelect = { picked -> iso = picked.iso; digits = digits.take(picked.max) },
            onDismiss = { showPicker = false },
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PhoneDarkPreview() = RideNowTheme(darkTheme = true) { PhoneScreen({}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PhoneLightPreview() = RideNowTheme(darkTheme = false) { PhoneScreen({}, {}) }
