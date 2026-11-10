package com.ridenow.app.ui.more

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.ridenow.app.ui.components.*
import com.ridenow.app.ui.theme.RideTheme

private class Article(val title: String, val intro: String, val steps: List<String>)

private val Topics = linkedMapOf(
    "Getting started" to listOf(
        Article("Create your account", "Sign up in under a minute.", listOf("Enter your phone number.", "Type the 6-digit code we send you.", "Add your name and details.", "Allow location so we can find your pickup.")),
        Article("Book your first ride", "From the home screen to a driver.", listOf("Tap Where to? and choose a destination.", "Pick a vehicle.", "Review the fare and confirm.", "Watch your driver arrive on the map.")),
        Article("Understand fares", "What makes up your fare.", listOf("The fare is a base fare plus distance and time.", "Taxes and fees are added at the end.", "Fares can rise when demand is high.", "You always see the estimate before you confirm.")),
    ),
    "Account & profile" to listOf(
        Article("Edit your profile", "Keep your details up to date.", listOf("Open the menu and tap Profile.", "Tap Edit profile.", "Change your details.", "Tap Save.")),
        Article("Change your phone number", "Move to a new number safely.", listOf("Open Settings, then Privacy & Security.", "Tap Change phone number.", "Enter the new number and the code we send.", "Your old number stays active until you verify.")),
        Article("Delete your account", "Remove your data for good.", listOf("Finish any pending rides or payments.", "Open Privacy & Security and tap Delete Account.", "Enter the code we send you.", "Confirm the deletion.")),
    ),
    "Payments & wallet" to listOf(
        Article("Pay in cash", "RideNow rides are paid in cash.", listOf("Check the amount due at the end of your trip.", "Hand the cash to your driver.", "Wait for your driver to confirm payment.")),
        Article("Read your receipt", "Find any trip receipt.", listOf("Open Your Rides.", "Tap the trip.", "Tap Download receipt to save a PDF.")),
        Article("Report a payment problem", "Tell us about a wrong charge.", listOf("Open the trip in Your Rides.", "Tap Report a trip problem.", "Choose Payment issue and describe it.")),
    ),
    "Ride bookings" to listOf(
        Article("How to book a ride", "Get a ride now.", listOf("Tap Where to? on the home screen.", "Choose a destination.", "Pick a vehicle.", "Review the details and confirm.")),
        Article("How to schedule a ride", "Plan your pickup ahead.", listOf("Tap Later on the home screen.", "Pick a date and time for your pickup.", "Choose a destination and a vehicle.", "Review the details and confirm.")),
        Article("How to cancel a ride", "Cancel before your driver arrives.", listOf("Open your trip.", "Tap Cancel ride.", "Choose a reason and confirm.")),
        Article("Change your drop-off", "Update where you are going.", listOf("Open your trip while it is in progress.", "Tap Change drop-off.", "Choose the new place and check the new estimate.", "Confirm the change.")),
        Article("Book for a friend", "Send a ride to someone else.", listOf("Choose Book for a friend when you set your pickup.", "Pick a contact.", "They get the driver and trip details.")),
    ),
    "Safety & security" to listOf(
        Article("Share your trip", "Let people follow your ride.", listOf("Open your trip.", "Tap Share.", "Pick a trusted contact.", "They see a live tracking page.")),
        Article("Add trusted contacts", "Choose who is told in an emergency.", listOf("Open the Safety center.", "Tap Trusted contacts.", "Add a contact from your phone.")),
        Article("Contact emergency services", "Get help fast.", listOf("Tap the Safety button during a trip.", "Tap Emergency.", "Your location is shared with your trusted contacts.")),
    ),
    "Lost items" to listOf(
        Article("Report a lost item", "Left something in the vehicle?", listOf("Open the trip in Your Rides.", "Tap Report a trip problem.", "Choose Lost item and describe it.")),
        Article("Contact your driver", "Reach your driver after a trip.", listOf("Open the trip in Your Rides.", "Use Call or Message in the first hour after the trip.")),
        Article("What happens next", "How we follow up.", listOf("We contact the driver for you.", "You get a notification with the result.", "If found, you agree on a pickup spot.")),
    ),
)

private val AllArticles get() = Topics.values.flatten()

@Composable
fun HelpCenterScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val c = RideTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val hits = AllArticles.filter { it.title.contains(query.trim(), ignoreCase = true) }
    MoreScaffold("Help Center", onBack, subtitle = "Find answers to common questions.") {
        RideTextField(query, { query = it.take(40) }, "Search help", RideIcons.Search, Modifier.padding(top = 16.dp))
        if (query.isBlank()) {
            SectionLabel("BROWSE BY TOPIC")
            GroupCard { Topics.keys.forEachIndexed { i, t -> if (i > 0) RowDivider(); NavRow(RideIcons.Book, t, { open("Help topic:$t") }) } }
            SectionLabel("TOP ARTICLES")
            GroupCard { listOf("How to schedule a ride", "How to cancel a ride").forEachIndexed { i, t -> if (i > 0) RowDivider(); NavRow(RideIcons.Doc, t, { open("Help article:$t") }) } }
        } else {
            SectionLabel("RESULTS")
            if (hits.isEmpty()) Text("No articles match your search.", color = c.muted, fontSize = 14.sp)
            else GroupCard { hits.forEachIndexed { i, a -> if (i > 0) RowDivider(); NavRow(RideIcons.Doc, a.title, { open("Help article:${a.title}") }) } }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun HelpTopicScreen(topic: String, onBack: () -> Unit, open: (String) -> Unit) {
    val list = Topics[topic].orEmpty()
    val sub = when (topic) { "Ride bookings" -> "Book, schedule and cancel rides."; "Getting started" -> "Learn the basics."; "Account & profile" -> "Manage your details and security."; "Payments & wallet" -> "Cash payments and receipts."; "Safety & security" -> "Stay safe on every trip."; else -> "Find something you left behind." }
    MoreScaffold(topic, onBack, subtitle = sub) {
        SectionLabel("ARTICLES")
        GroupCard { list.forEachIndexed { i, a -> if (i > 0) RowDivider(); NavRow(RideIcons.Doc, a.title, { open("Help article:${a.title}") }) } }
    }
}

@Composable
fun HelpArticleScreen(title: String, onBack: () -> Unit, onSupport: () -> Unit) {
    val c = RideTheme.colors
    val a = AllArticles.firstOrNull { it.title == title } ?: Article(title, "", emptyList())
    var vote by rememberSaveable { mutableStateOf("") }
    MoreScaffold(a.title, onBack, subtitle = a.intro) {
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            a.steps.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) { Text("${i + 1}", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    Text(s, color = c.fg, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(start = 14.dp, top = 3.dp))
                }
            }
        }
        SectionLabel("WAS THIS HELPFUL?")
        if (vote.isEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("Yes", false, { vote = "yes" }); Chip("No", false, { vote = "no" }) }
        else Text(if (vote == "yes") "Thanks for your feedback." else "Sorry about that. Our team can help.", color = c.muted, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        RideButton("Contact support", onSupport, Modifier.padding(vertical = 20.dp), style = RideButtonStyle.Text)
    }
}

@Composable
fun ReportSafetyScreen(onBack: () -> Unit, onSafety: () -> Unit) {
    val c = RideTheme.colors
    val kinds = listOf("Unsafe driving", "Harassment", "Accident", "Vehicle or driver mismatch", "Other")
    var kind by rememberSaveable { mutableIntStateOf(-1) }
    var text by rememberSaveable { mutableStateOf("") }
    var withTrip by rememberSaveable { mutableStateOf(true) }
    var sent by rememberSaveable { mutableStateOf(false) }
    if (sent) {
        MoreScaffold("Report a safety issue", onBack, bottom = { RideButton("Done", onBack); RideButton("Open Safety center", onSafety, style = RideButtonStyle.Text) }) {
            Hero(RideIcons.Shield, "Report received.", "Our safety team will review it and contact you within 24 hours.")
        }
        return
    }
    MoreScaffold("Report a safety issue", onBack, subtitle = "Tell us what happened. In an emergency, call emergency services first.", bottom = {
        RideButton("Submit report", { sent = true }, enabled = kind >= 0 && text.isNotBlank())
    }) {
        SectionLabel("WHAT HAPPENED?")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { kinds.chunked(2).forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { r.forEach { k -> Chip(k, kinds.indexOf(k) == kind, { kind = kinds.indexOf(k) }) } } } }
        SectionLabel("DETAILS")
        BasicTextField(
            text, { text = it.take(500) }, Modifier.fillMaxWidth().heightIn(min = 120.dp), textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    if (text.isEmpty()) Text("Describe what happened", color = c.muted, fontSize = 15.sp)
                    inner()
                }
            },
        )
        GroupCard(Modifier.padding(top = 16.dp)) { ToggleRow("Include trip details", "Share the route, driver and plate with our safety team.", withTrip, { withTrip = it }) }
        Spacer(Modifier.height(12.dp))
    }
}
