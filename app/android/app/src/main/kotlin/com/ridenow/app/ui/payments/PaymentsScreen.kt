package com.ridenow.app.ui.payments

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.tooling.preview.Preview
import com.ridenow.app.ui.components.GroupCard
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RowDivider
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

/** Payment methods: cash is the default, other methods are marked as coming soon. */
@Composable
fun PaymentsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val textMenuPayments = stringResource(R.string.menu_payments)
    val textChooseHowYouPayForYourRides = stringResource(R.string.text_choose_how_you_pay_for_your_rides)
    val textDefaultMethod = stringResource(R.string.text_default_method)
    val textCashOnDelivery = stringResource(R.string.text_cash_on_delivery)
    val textPayYourDriverInCashWhenTheRideEnds = stringResource(R.string.text_pay_your_driver_in_cash_when_the_ride_ends)
    val textComingSoon2 = stringResource(R.string.text_coming_soon_2)
    val textCreditOrDebitCard = stringResource(R.string.text_credit_or_debit_card)
    val textVisaMastercardAndMore = stringResource(R.string.text_visa_mastercard_and_more)
    val textRidenowWallet = stringResource(R.string.text_ridenow_wallet)
    val textTopUpAndPayInOneTap = stringResource(R.string.text_top_up_and_pay_in_one_tap)
    val textPayByPhone = stringResource(R.string.text_pay_by_phone)
    val textContactlessMobilePayments = stringResource(R.string.text_contactless_mobile_payments)
    val textMoreWaysToPayAreOnTheWayUntilThenAllRidesArePaidInCash = stringResource(R.string.text_more_ways_to_pay_are_on_the_way_until_then_all_rides_are_paid_in_cash)

    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(textMenuPayments, onBack, subtitle = textChooseHowYouPayForYourRides)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            SectionLabel(textDefaultMethod)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Cash, c.onAccent, size = 22.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(textCashOnDelivery, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.label_default), color = c.onAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                    Text(textPayYourDriverInCashWhenTheRideEnds, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.size(22.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Check, c.onAccent, size = 14.dp) }
            }
            SectionLabel(textComingSoon2)
            GroupCard {
                Soon(RideIcons.Card, textCreditOrDebitCard, textVisaMastercardAndMore); RowDivider()
                Soon(RideIcons.Wallet, textRidenowWallet, textTopUpAndPayInOneTap); RowDivider()
                Soon(RideIcons.Phone, textPayByPhone, textContactlessMobilePayments)
            }
            Text(textMoreWaysToPayAreOnTheWayUntilThenAllRidesArePaidInCash, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun Soon(icon: List<String>, title: String, subtitle: String) {
    val textComingSoon = stringResource(R.string.text_coming_soon)

    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp).semantics(mergeDescendants = true) { stateDescription = textComingSoon }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(icon, c.muted, size = 20.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, color = c.muted, fontSize = 15.sp); Text(subtitle, color = c.muted, fontSize = 12.sp) }
        Text(textComingSoon, color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.border, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PaymentsPreview() = RideNowTheme(darkTheme = true) { PaymentsScreen({}) }
