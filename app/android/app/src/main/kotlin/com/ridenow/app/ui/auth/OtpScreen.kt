package com.ridenow.app.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.R
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance
import kotlinx.coroutines.delay

private const val CODE_LENGTH = 6

/** Step 2 of 3. Demo rule: any code is accepted except 000000, which shows the error state. */
@Composable
fun OtpScreen(phone: String, onBack: () -> Unit, onVerified: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val haptics = LocalHapticFeedback.current
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var errorTick by remember { mutableIntStateOf(0) }
    var verifying by remember { mutableStateOf(false) }
    var resendKey by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(24) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(Unit) { delay(400); focusRequester.requestFocus() }
    LaunchedEffect(resendKey) { seconds = 24; while (seconds > 0) { delay(1000); seconds-- } }
    LaunchedEffect(verifying) {
        if (verifying) {
            delay(900)
            if (code == "000000") { error = true; errorTick++; verifying = false; code = "" } else onVerified()
        }
    }
    LaunchedEffect(errorTick) {
        if (errorTick > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            for (v in listOf(-12f, 12f, -8f, 8f, 0f)) shake.animateTo(v, tween(50))
        }
    }
    val cursor by rememberInfiniteTransition(label = "cursor").animateFloat(1f, 0f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "blink")

    Column(
        modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp),
    ) {
        Box(
            Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) { RideIcon(RideIcons.Back, c.fg, size = 22.dp) }

        Row(Modifier.padding(top = 10.dp).fillMaxWidth().entrance(0), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) {
                    if (i < 2) Box(Modifier.fillMaxSize().background(c.accent))
                }
            }
        }

        Column(Modifier.entrance(80)) {
            Text(
                stringResource(R.string.otp_title), color = c.fg, fontSize = 32.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 32.dp).semantics { heading() },
            )
            Text(stringResource(R.string.otp_subtitle, phone), color = c.muted, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
            Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.otp_edit), color = c.accent, fontSize = 14.sp)
            }
        }

        BasicTextField(
            value = code,
            onValueChange = { v ->
                if (!verifying) {
                    code = v.filter(Char::isDigit).take(CODE_LENGTH); error = false
                    if (code.length == CODE_LENGTH) verifying = true
                }
            },
            modifier = Modifier.padding(top = 20.dp).fillMaxWidth().offset(x = shake.value.dp)
                .focusRequester(focusRequester).onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = "6-digit verification code" },
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (code.length == CODE_LENGTH) verifying = true }),
            decorationBox = { inner ->
                Box(Modifier.alpha(0f).size(1.dp)) { inner() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(CODE_LENGTH) { i ->
                        val active = focused && i == code.length
                        val border = when { error -> c.danger; active -> c.accent; else -> c.border }
                        Box(
                            Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(16.dp)).background(c.field)
                                .border(1.dp, border, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i < code.length) Text(code[i].toString(), color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light)
                            else if (active) Box(Modifier.size(width = 2.dp, height = 26.dp).alpha(cursor).background(c.accent))
                        }
                    }
                }
            },
        )

        if (error) Text(stringResource(R.string.otp_error), color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))

        Row(Modifier.padding(top = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (seconds > 0) {
                Text(stringResource(R.string.otp_resend_in, seconds / 60, seconds % 60), color = c.muted, fontSize = 14.sp)
            } else {
                Text(
                    stringResource(R.string.otp_resend), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(role = Role.Button) { code = ""; error = false; resendKey++ }.padding(vertical = 12.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))
        RideButton(
            stringResource(R.string.otp_verify), onClick = { verifying = true },
            modifier = Modifier.padding(bottom = 12.dp).entrance(200), enabled = code.length == CODE_LENGTH, loading = verifying,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OtpDarkPreview() = RideNowTheme(darkTheme = true) { OtpScreen("+1 555 012 3456", {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OtpLightPreview() = RideNowTheme(darkTheme = false) { OtpScreen("+1 555 012 3456", {}, {}) }
