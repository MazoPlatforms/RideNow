package com.ridenow.driver.ui.status

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.RideIcons
import com.ridenow.driver.ui.theme.RideNowTheme
import com.ridenow.driver.ui.theme.RideTheme

/** Application sent. [name] is the driver's first name, [reference] the application number. */
@Composable
fun SubmittedScreen(name: String, reference: String, onCheckStatus: () -> Unit, onSupport: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    StatusScaffold(
        StatusTone.Accent, RideIcons.Check, stringResource(R.string.sub_title),
        if (name.isBlank()) stringResource(R.string.sub_text_plain) else stringResource(R.string.sub_text, name),
        stringResource(R.string.sub_check), onCheckStatus, stringResource(R.string.status_support), onSupport, modifier,
    ) {
        StatusRow(RideIcons.Check, stringResource(R.string.sub_row_sent), stringResource(R.string.sub_reference, reference), c.accent)
        StatusRow(RideIcons.Clock, stringResource(R.string.sub_row_pending), stringResource(R.string.sub_row_pending_text), c.fg)
        StatusRow(RideIcons.Shield, stringResource(R.string.sub_row_decision), stringResource(R.string.sub_row_decision_text), c.muted)
    }
}

/** Documents that need fixing. [onFix] gets the index of the item (0 = NIC back, 1 = insurance). */
@Composable
fun ActionRequiredScreen(onFix: (Int) -> Unit, onUpdate: () -> Unit, onSupport: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    StatusScaffold(
        StatusTone.Warn, RideIcons.Alert, stringResource(R.string.act_title), stringResource(R.string.act_text),
        stringResource(R.string.act_update), onUpdate, stringResource(R.string.status_support), onSupport, modifier,
    ) {
        StatusRow(RideIcons.Card, stringResource(R.string.act_nic), stringResource(R.string.act_nic_text), c.warn, stringResource(R.string.act_fix)) { onFix(0) }
        StatusRow(RideIcons.Doc, stringResource(R.string.act_ins), stringResource(R.string.act_ins_text), c.warn, stringResource(R.string.act_fix)) { onFix(1) }
    }
}

/** Application declined, with the reasons. */
@Composable
fun RejectedScreen(onReapply: () -> Unit, onSupport: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    StatusScaffold(
        StatusTone.Danger, RideIcons.Close, stringResource(R.string.rej_title), stringResource(R.string.rej_text),
        stringResource(R.string.rej_reapply), onReapply, stringResource(R.string.status_support), onSupport, modifier,
    ) {
        StatusRow(RideIcons.Card, stringResource(R.string.rej_license), stringResource(R.string.rej_license_text), c.danger)
        StatusRow(RideIcons.Car, stringResource(R.string.rej_vehicle), stringResource(R.string.rej_vehicle_text), c.danger)
    }
}

/** Approved: the welcome screen that leads to the home screen. */
@Composable
fun ApprovedScreen(onOpenHome: () -> Unit, onExplore: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    StatusScaffold(
        StatusTone.Accent, RideIcons.Check, stringResource(R.string.app_title), stringResource(R.string.app_text),
        stringResource(R.string.app_home), onOpenHome, stringResource(R.string.app_explore), onExplore, modifier, pill = stringResource(R.string.app_pill),
    ) {
        StatusRow(RideIcons.Car, stringResource(R.string.app_online), stringResource(R.string.app_online_text), c.accent)
        StatusRow(RideIcons.Card, stringResource(R.string.app_cash), stringResource(R.string.app_cash_text), c.accent)
        StatusRow(RideIcons.Chart, stringResource(R.string.app_earn), stringResource(R.string.app_earn_text), c.accent)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SubmittedPreview() = RideNowTheme(darkTheme = true) { SubmittedScreen("Alex", "DRV-2100-0482", {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ActionRequiredPreview() = RideNowTheme(darkTheme = false) { ActionRequiredScreen({}, {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RejectedPreview() = RideNowTheme(darkTheme = true) { RejectedScreen({}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ApprovedPreview() = RideNowTheme(darkTheme = false) { ApprovedScreen({}, {}) }
