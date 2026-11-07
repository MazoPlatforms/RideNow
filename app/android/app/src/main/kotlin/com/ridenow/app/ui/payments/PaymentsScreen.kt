package com.ridenow.app.ui.payments

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
    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop("Payments", onBack, subtitle = "Choose how you pay for your rides.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            SectionLabel("DEFAULT METHOD")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Cash, c.onAccent, size = 22.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cash on delivery", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text("Default", color = c.onAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                    Text("Pay your driver in cash when the ride ends.", color = c.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.size(22.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Check, c.onAccent, size = 14.dp) }
            }
            SectionLabel("COMING SOON")
            GroupCard {
                Soon(RideIcons.Card, "Credit or debit card", "Visa, Mastercard and more"); RowDivider()
                Soon(RideIcons.Wallet, "RideNow Wallet", "Top up and pay in one tap"); RowDivider()
                Soon(RideIcons.Phone, "Pay by phone", "Contactless mobile payments")
            }
            Text("More ways to pay are on the way. Until then, all rides are paid in cash.", color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun Soon(icon: List<String>, title: String, subtitle: String) {
    val c = RideTheme.colors
    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp).semantics(mergeDescendants = true) { stateDescription = "Coming soon" }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(icon, c.muted, size = 20.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(title, color = c.muted, fontSize = 15.sp); Text(subtitle, color = c.muted, fontSize = 12.sp) }
        Text("Coming soon", color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.border, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PaymentsPreview() = RideNowTheme(darkTheme = true) { PaymentsScreen({}) }
