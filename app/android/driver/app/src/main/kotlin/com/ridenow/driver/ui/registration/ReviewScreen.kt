package com.ridenow.driver.ui.registration

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

/** One line on the Review screen. [add] shows "Add" instead of "Edit", [warn] marks something still missing. */
class ReviewItem(val title: String, val summary: String, val warn: Boolean = false, val add: Boolean = false)

/** Final step: every section with its summary and an Edit link, then a confirmation and Submit. */
@Composable
fun ReviewScreen(items: List<ReviewItem>, onEdit: (Int) -> Unit, onSubmit: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var confirmed by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepHeader(6, 6, stringResource(R.string.review_tag), stringResource(R.string.review_title), stringResource(R.string.review_subtitle), onBack)
            Column(Modifier.padding(top = 18.dp).entrance(200)) {
                HorizontalDivider(color = c.line)
                items.forEachIndexed { i, item -> ReviewRow(item) { onEdit(i) } }
                RideCheckbox(confirmed, stringResource(R.string.review_confirm), { confirmed = it }, Modifier.padding(top = 14.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        RideButton(stringResource(R.string.review_submit), onClick = onSubmit, enabled = confirmed, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun ReviewRow(item: ReviewItem, onEdit: () -> Unit) {
    val c = RideTheme.colors
    val tone = if (item.warn) c.warn else c.accent
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(if (item.warn) RideIcons.Alert else RideIcons.Check, tone, size = 20.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.title, color = c.fg, fontSize = 14.sp)
                Text(item.summary, color = if (item.warn) c.warn else c.muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onEdit).padding(start = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(if (item.add) R.string.review_add else R.string.review_edit), color = c.accent, fontSize = 14.sp)
            }
        }
        HorizontalDivider(color = c.line)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ReviewDarkPreview() = RideNowTheme(darkTheme = true) {
    ReviewScreen(listOf(ReviewItem("Terms and location", "Accepted"), ReviewItem("Bank account", "Skipped", warn = true, add = true)), {}, {}, {})
}
