package com.ridenow.app.ui.post

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.Ink
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

private class Category(val icon: List<String>, val title: String, val hint: String, val options: List<String>)

private val Categories = listOf(
    Category(RideIcons.Person, "Driver behavior", "Rude, unsafe or inappropriate conduct", listOf("Unsafe driving", "Rude or aggressive", "Made me uncomfortable", "Something else")),
    Category(RideIcons.Map, "Incorrect route", "The driver took a different route", listOf("Took a longer route", "I asked for a different route", "Route did not match the app")),
    Category(RideIcons.Card, "Payment issue", "Wrong charge or fare dispute", listOf("Paid more than the estimate", "Paid for a cancelled ride", "Wrong amount collected", "Other")),
    Category(RideIcons.Pin, "Pickup or drop-off issue", "Wrong spot or trouble meeting", listOf("Driver could not find me", "Driver stopped far away", "Dropped off in the wrong place", "The spot felt unsafe")),
    Category(listOf("M4 7h12l-1 10H5z", "M7.5 7V5.5a2.5 2.5 0 0 1 5 0V7"), "Lost item", "Left something in the vehicle", listOf("Phone", "Wallet", "Bag", "Keys", "Something else")),
    Category(RideIcons.Car, "Vehicle issue", "Cleanliness or condition", listOf("Dirty interior", "Bad smell", "Damaged seat or door", "Not the car shown in the app")),
    Category(RideIcons.Flag, "Other issue", "Something else went wrong", emptyList()),
)

/** Report a trip problem: choose a category, add details, submit. [onDone] is called after submitting or leaving. */
@Composable
fun ReportProblemScreen(onBack: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var cat by rememberSaveable { mutableIntStateOf(-1) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var details by rememberSaveable { mutableStateOf("") }
    BackHandler { if (submitted) onDone() else if (cat >= 0) { cat = -1; picked = emptySet() } else onBack() }

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        if (submitted) { Submitted(onDone); return@Column }
        Box(Modifier.offset(x = (-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button) { if (cat >= 0) { cat = -1; picked = emptySet() } else onBack() }.semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
            RideIcon(RideIcons.Back, c.fg, size = 22.dp)
        }
        if (cat < 0) {
            Text("What went wrong?", color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.semantics { heading() }.entrance(0))
            Text("Choose the closest match for your trip.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(80)) {
                Categories.forEachIndexed { i, k ->
                    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { cat = i }) {
                        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(k.icon, c.accent, size = 18.dp) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) { Text(k.title, color = c.fg, fontSize = 15.sp); Text(k.hint, color = c.muted, fontSize = 12.sp) }
                            RideIcon(listOf("M7 4l6 6-6 6"), c.muted, size = 18.dp)
                        }
                        HorizontalDivider(color = c.line)
                    }
                }
            }
            Row(Modifier.padding(vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                RideIcon(RideIcons.Shield, c.accent); Spacer(Modifier.width(12.dp))
                Text("In an emergency, open the Safety center.", color = c.muted, fontSize = 13.sp)
            }
        } else {
            val k = Categories[cat]
            Text(k.title, color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.semantics { heading() })
            Text(if (k.options.isEmpty()) "Tell us what happened." else "Select all that apply.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (k.options.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))) {
                    k.options.forEachIndexed { i, o ->
                        val on = o in picked
                        if (i > 0) HorizontalDivider(color = c.line)
                        Row(Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Checkbox) { picked = if (on) picked - o else picked + o }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(o, color = c.fg, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            Box(Modifier.size(22.dp).clip(CircleShape).then(if (on) Modifier.background(BrandYellow) else Modifier.border(1.5.dp, c.muted, CircleShape)), contentAlignment = Alignment.Center) { if (on) RideIcon(RideIcons.Check, Ink, size = 14.dp) }
                        }
                    }
                }
                BasicTextField(
                    details, { details = it.take(500) }, textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(if (k.options.isEmpty()) 160.dp else 90.dp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
                            if (details.isEmpty()) Text(if (k.options.isEmpty()) "Describe the issue" else "Add details (optional)", color = c.muted, fontSize = 15.sp)
                            inner()
                        }
                    },
                )
            }
            RideButton("Submit report", { submitted = true }, enabled = picked.isNotEmpty() || details.isNotBlank(), modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun Submitted(onDone: () -> Unit) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(88.dp).clip(CircleShape).background(BrandYellow), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Check, Ink, size = 40.dp) }
        Text("Report submitted.", color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 22.dp).semantics { heading() })
        Text("Thank you. We will review it and reply in the app.", color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.padding(top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
            Text("REPORT NUMBER", color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp)
            Text("R-48213", color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.weight(1f))
        RideButton("Done", onDone, modifier = Modifier.padding(bottom = 12.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ReportPreview() = RideNowTheme(darkTheme = true) { ReportProblemScreen({}, {}) }
