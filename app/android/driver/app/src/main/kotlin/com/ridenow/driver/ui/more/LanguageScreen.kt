package com.ridenow.driver.ui.more

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
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
    val c = RideTheme.colors
    val context = LocalContext.current
    // Languages shown in the Language settings design come first, then the rest the app is translated into.
    val langs = listOf(
        Triple("en", R.string.language_native_english, R.string.language_native_english), Triple("si", R.string.language_native_sinhala, R.string.text_sinhala),
        Triple("ta", R.string.language_native_tamil, R.string.text_tamil), Triple("ar", R.string.language_native_arabic, R.string.text_arabic),
        Triple("es", R.string.language_native_spanish, R.string.text_spanish), Triple("fr", R.string.language_native_french, R.string.text_french),
        Triple("de", R.string.language_native_german, R.string.text_german), Triple("ja", R.string.language_native_japanese, R.string.text_japanese),
        Triple("hi", R.string.language_native_hindi, R.string.text_hindi),
    )
    var sel by rememberSaveable { mutableStateOf(getAppLanguage(context)) }
    MoreScaffold(stringResource(R.string.lang_screen_title), onBack, bottom = {
        RideButton(stringResource(R.string.lang_save), { if (sel != getAppLanguage(context)) setAppLanguage(context, sel) else onBack() })
    }) {
        GroupCard(Modifier.padding(top = 8.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(c.line), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Globe, c.fg, size = 22.dp) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.lang_choose), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.lang_choose_text), color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        Column(Modifier.padding(top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            langs.forEach { (code, nativeRes, englishRes) ->
                val on = sel == code
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(if (on) c.fill.copy(alpha = 0.14f) else c.field).border(1.dp, if (on) c.fill else c.border, shape)
                        .selectable(on, role = Role.RadioButton) { sel = code }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 14.dp)) {
                        Text(stringResource(nativeRes), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (code != "en") Text(stringResource(englishRes), color = c.muted, fontSize = 13.sp)
                    }
                    RadioDot(on)
                }
            }
        }
    }
}
