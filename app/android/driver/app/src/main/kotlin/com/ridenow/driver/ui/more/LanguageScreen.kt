package com.ridenow.driver.ui.more

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.getAppLanguage
import com.ridenow.driver.setAppLanguage
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

/** Back button, title, optional subtitle, scrolling body and an optional pinned bottom area. */
@Composable
fun MoreScaffold(
    title: String, onBack: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(title, onBack, subtitle = subtitle)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60), content = content)
        if (bottom != null) Column(Modifier.padding(vertical = 12.dp), content = bottom)
    }
}

@Composable
fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit, sub: String? = null) {
    val c = RideTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.fg, fontSize = 16.sp)
            if (sub != null) Text(sub, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Box(Modifier.size(24.dp).clip(CircleShape).border(2.dp, if (selected) c.accent else c.border, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(c.accent))
        }
    }
}

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val textEnglish = stringResource(R.string.language_native_english)
    val textEspaOl = stringResource(R.string.language_native_spanish)
    val textSpanish = stringResource(R.string.text_spanish)
    val textFranAis = stringResource(R.string.language_native_french)
    val textFrench = stringResource(R.string.text_french)
    val textDeutsch = stringResource(R.string.language_native_german)
    val textGerman = stringResource(R.string.text_german)
    val textValue = stringResource(R.string.language_native_japanese)
    val textJapanese = stringResource(R.string.text_japanese)
    val textValue2 = stringResource(R.string.language_native_arabic)
    val textArabic = stringResource(R.string.text_arabic)
    val textValue3 = stringResource(R.string.language_native_hindi)
    val textHindi = stringResource(R.string.text_hindi)
    val textTamil = stringResource(R.string.text_tamil)
    val textSinhala = stringResource(R.string.text_sinhala)
    val nativeTamil = stringResource(R.string.language_native_tamil)
    val nativeSinhala = stringResource(R.string.language_native_sinhala)
    val textSettingsLanguage = stringResource(R.string.settings_language)
    val textChooseTheLanguageForRidenow = stringResource(R.string.text_choose_the_language_for_ridenow)
    val textSave = stringResource(R.string.text_save)

    val context = LocalContext.current
    val langs = listOf(
        Triple("en", textEnglish, textEnglish), Triple("es", textEspaOl, textSpanish), Triple("fr", textFranAis, textFrench),
        Triple("de", textDeutsch, textGerman), Triple("ja", textValue, textJapanese), Triple("ar", textValue2, textArabic),
        Triple("hi", textValue3, textHindi),
        Triple("ta", nativeTamil, textTamil), Triple("si", nativeSinhala, textSinhala),
    )
    var sel by rememberSaveable { mutableStateOf(getAppLanguage(context)) }
    MoreScaffold(textSettingsLanguage, onBack, subtitle = textChooseTheLanguageForRidenow, bottom = {
        RideButton(textSave, {
            val saved = getAppLanguage(context)
            if (sel != saved) {
                setAppLanguage(context, sel)
            } else onBack()
        })
    }) {
        GroupCard(Modifier.padding(top = 16.dp)) {
            langs.forEachIndexed { i, (code, name, english) ->
                if (i > 0) RowDivider()
                Column(Modifier.padding(horizontal = 16.dp)) { RadioRow(name, sel == code, { sel = code }, english) }
            }
        }
    }
}
