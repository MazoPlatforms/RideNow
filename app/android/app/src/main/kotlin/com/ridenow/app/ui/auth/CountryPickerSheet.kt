package com.ridenow.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.R
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.RideTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(selected: Country, onSelect: (Country) -> Unit, onDismiss: () -> Unit) {
    val textClose = stringResource(R.string.text_close)

    val c = RideTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val results = remember(q) {
        if (q.isEmpty()) Countries.all
        else Countries.all.filter { (it.name.contains(q, true) || it.displayName.contains(q, true)) || it.dial.contains(q) || it.iso.equals(q, true) }
    }
    fun close(after: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { after() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.bg,
        contentColor = c.fg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(c.border))
        },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.country_title), color = c.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { close(onDismiss) }
                        .semantics { contentDescription = textClose },
                    contentAlignment = Alignment.Center,
                ) { RideIcon(RideIcons.Close, c.fg) }
            }

            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = c.fg, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(56.dp),
                decorationBox = { inner ->
                    Row(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.field)
                            .border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RideIcon(RideIcons.Search, c.accent)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text(stringResource(R.string.country_search), color = c.muted, fontSize = 16.sp)
                            inner()
                        }
                    }
                },
            )

            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                if (q.isEmpty()) {
                    item { SectionLabel(stringResource(R.string.country_suggested)) }
                    items(Countries.suggested, key = { "s" + it.iso }) { country ->
                        CountryRow(country, country.iso == selected.iso) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); close { onSelect(country); onDismiss() } }
                    }
                    item { SectionLabel(stringResource(R.string.country_all)) }
                } else if (results.isEmpty()) {
                    item { Text(stringResource(R.string.country_none), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp)) }
                }
                items(results, key = { it.iso }) { country ->
                    CountryRow(country, country.iso == selected.iso) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); close { onSelect(country); onDismiss() } }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = RideTheme.colors.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
private fun CountryRow(country: Country, selected: Boolean, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp)
            .semantics { contentDescription = "${country.displayName} ${country.dial}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).border(1.dp, if (selected) c.accent else c.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(country.flag, fontSize = 18.sp) }
        Spacer(Modifier.width(14.dp))
        Text(country.displayName, color = c.fg, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(country.dial, color = c.muted, fontSize = 14.sp)
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(22.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                RideIcon(RideIcons.Check, c.onAccent, size = 14.dp)
            }
        }
    }
}
