package com.ridenow.app.ui.legal

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
import com.ridenow.app.ui.components.LogoMark
import com.ridenow.app.ui.components.NavRow
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.components.RowDivider
import com.ridenow.app.ui.components.ScreenTop
import com.ridenow.app.ui.components.SectionLabel
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

/** Legal and About: app card plus links to each document. */
@Composable
fun LegalScreen(onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop("Legal & About", onBack, subtitle = "App information, legal documents and policies.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { onOpen("About RideNow") }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(if (c.isDark) androidx.compose.ui.graphics.Color(0xFF14181F) else androidx.compose.ui.graphics.Color.White).border(1.dp, c.border, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { LogoMark(40.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("RideNow", color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Version 2100.1 (100)", color = c.muted, fontSize = 12.sp)
                    Text("Your everyday ride companion.", color = c.muted, fontSize = 12.sp)
                }
                RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
            }
            SectionLabel("LEGAL")
            GroupCard {
                NavRow(RideIcons.Doc, "Terms of Service", { onOpen("Terms of Service") }, subtitle = "Read our terms and conditions"); RowDivider()
                NavRow(RideIcons.Shield, "Privacy Policy", { onOpen("Privacy Policy") }, subtitle = "Learn how we handle your data"); RowDivider()
                NavRow(RideIcons.Info, "Cookie Policy", { onOpen("Cookie Policy") }, subtitle = "How we use cookies"); RowDivider()
                NavRow(RideIcons.Shield, "Data Protection", { onOpen("Data Protection") }, subtitle = "Your data rights and security")
            }
            SectionLabel("ABOUT")
            GroupCard {
                NavRow(RideIcons.Info, "About RideNow", { onOpen("About RideNow") }, subtitle = "App information and features"); RowDivider()
                NavRow(RideIcons.Star, "Open Source Licenses", { onOpen("Open Source Licenses") }, subtitle = "Third-party libraries and licenses")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val Docs = mapOf(
    "Terms of Service" to listOf("Using RideNow" to "You agree to use RideNow lawfully and to give accurate account details.", "Rides and payments" to "Fares are estimates. Rides are currently paid in cash to the driver.", "Cancellations" to "You may cancel a ride at any time before pickup."),
    "Privacy Policy" to listOf("What we collect" to "Your name, phone number, location during rides and trip history.", "How we use it" to "To match you with drivers, keep trips safe and improve the service.", "Your choices" to "You can download or delete your data from Privacy & Security."),
    "Cookie Policy" to listOf("What are cookies" to "Small files that help an app or website remember your preferences.", "How we use them" to "For sign-in, language and appearance settings."),
    "Data Protection" to listOf("Your rights" to "Access, correct, export or delete the data we hold about you.", "Security" to "Data is encrypted in transit and stored with restricted access."),
    "Open Source Licenses" to listOf("Jetpack Compose" to "Apache License 2.0.", "AndroidX libraries" to "Apache License 2.0.", "Kotlin" to "Apache License 2.0."),
    "About RideNow" to listOf("Everyday rides" to "RideNow helps you book, schedule and share rides in a few taps.", "Version" to "Version 2100.1 (100)."),
)

/** Simple reader for the legal documents. The text is placeholder copy. */
@Composable
fun DocScreen(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val sections = Docs[title] ?: listOf("Coming soon" to "This document will be available here.")
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        ScreenTop(title, onBack, subtitle = "Last updated 3 Nov 2026")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            sections.forEachIndexed { i, (h, body) ->
                Text("${i + 1}. $h", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp))
                Text(body, color = c.muted, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LegalPreview() = RideNowTheme(darkTheme = true) { LegalScreen({}, {}) }
