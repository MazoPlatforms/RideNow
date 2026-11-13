package com.ridenow.driver.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.registration.formBottomInsets
import com.ridenow.driver.ui.theme.RideTheme

/** Edit personal contact details. Phone number stays read-only here. [onSave] receives name, email and address. */
@Composable
fun EditProfileScreen(
    name: String, phone: String, email: String, address: String,
    onBack: () -> Unit, onSave: (String, String, String) -> Unit, modifier: Modifier = Modifier,
) {
    val c = RideTheme.colors
    var fullName by rememberSaveable { mutableStateOf(name) }
    var mail by rememberSaveable { mutableStateOf(email) }
    var place by rememberSaveable { mutableStateOf(address) }
    val mailOk = mail.isBlank() || Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(mail.trim())
    val canSave = fullName.isNotBlank() && mailOk

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().formBottomInsets().padding(horizontal = 20.dp)) {
        ScreenTop(stringResource(R.string.profile_edit_title), onBack, subtitle = stringResource(R.string.profile_edit_subtitle))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 12.dp)) {
            RideTextField(
                value = fullName, onValueChange = { fullName = it }, label = stringResource(R.string.profile_full_name),
                icon = RideIcons.Person, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            RideTextField(
                value = mail, onValueChange = { mail = it }, label = stringResource(R.string.profile_email),
                icon = RideIcons.Mail, isError = !mailOk,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.padding(top = 12.dp),
            )
            if (!mailOk) Text(stringResource(R.string.profile_email_error), color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            RideTextField(
                value = place, onValueChange = { place = it }, label = stringResource(R.string.profile_address),
                icon = RideIcons.Pin, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.padding(top = 12.dp),
            )

            Row(
                Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RideIcon(RideIcons.Phone, c.muted, size = 18.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.profile_phone), color = c.muted, fontSize = 12.sp)
                    Text(phone, color = c.fg, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                    Text(stringResource(R.string.profile_phone_locked), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        RideButton(
            stringResource(R.string.profile_save),
            onClick = { onSave(fullName.trim(), mail.trim(), place.trim()) },
            enabled = canSave,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}
