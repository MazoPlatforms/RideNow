package com.ridenow.app.ui.post

import com.ridenow.app.ui.components.offsetByStartDirection

import com.ridenow.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.app.ui.components.RideButton
import com.ridenow.app.ui.components.RideButtonStyle
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance





/** Rate the driver 1-5. Low scores switch the tags to problems. [onSubmit] gets the star count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RateDriverScreen(onBack: () -> Unit, onSubmit: (Int) -> Unit, modifier: Modifier = Modifier) {
    val textSmoothDriving = stringResource(R.string.text_smooth_driving)
    val textCleanVehicle = stringResource(R.string.text_clean_vehicle)
    val textFriendly = stringResource(R.string.text_friendly)
    val textGoodNavigation = stringResource(R.string.text_good_navigation)
    val textFeltSafe = stringResource(R.string.text_felt_safe)
    val textUnsafeDriving = stringResource(R.string.text_unsafe_driving)
    val textRudeBehavior = stringResource(R.string.text_rude_behavior)
    val textDirtyVehicle = stringResource(R.string.text_dirty_vehicle)
    val textWrongRoute = stringResource(R.string.text_wrong_route)
    val textArrivedLate = stringResource(R.string.text_arrived_late)
    val textPoorNavigation = stringResource(R.string.text_poor_navigation)
    val textReasonOther = stringResource(R.string.reason_other)
    val textTerrible = stringResource(R.string.text_terrible)
    val textPoor = stringResource(R.string.text_poor)
    val textOkay = stringResource(R.string.text_okay)
    val textGreat = stringResource(R.string.text_great)
    val textExcellent = stringResource(R.string.text_excellent)
    val textSearchBack = stringResource(R.string.search_back)
    val textMiraTanaka = stringResource(R.string.text_mira_tanaka)
    val textOrbitSilverRn2100 = stringResource(R.string.text_orbit_silver_rn_2100)
    val textWeAreSorryAboutThat = stringResource(R.string.text_we_are_sorry_about_that)
    val textHowWasYourRide = stringResource(R.string.text_how_was_your_ride)
    val textValueStarValueOne = stringResource(R.string.extra_1_s_star)
    val textValueStarValueMany = stringResource(R.string.extra_1_s_stars)
    val textWhatWentWrong = stringResource(R.string.text_what_went_wrong)
    val textWhatWentWell = stringResource(R.string.text_what_went_well)
    val textAddACommentOptional = stringResource(R.string.text_add_a_comment_optional)
    val textSubmitRating = stringResource(R.string.text_submit_rating)
    val textSkip = stringResource(R.string.text_skip)

    val Good = listOf(textSmoothDriving, textCleanVehicle, textFriendly, textGoodNavigation, textFeltSafe)
    val Bad = listOf(textUnsafeDriving, textRudeBehavior, textDirtyVehicle, textWrongRoute, textArrivedLate, textPoorNavigation, textReasonOther)
    val Words = listOf("", textTerrible, textPoor, textOkay, textGreat, textExcellent)

    val c = RideTheme.colors
    var stars by rememberSaveable { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var comment by rememberSaveable { mutableStateOf("") }
    val tags = if (stars in 1..3) Bad else Good

    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Box(Modifier.offsetByStartDirection((-12).dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = textSearchBack }, contentAlignment = Alignment.Center) {
            RideIcon(RideIcons.Back, c.fg, size = 22.dp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(0), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)).border(1.5.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.driver_initials), color = c.accent, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(textMiraTanaka, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Text(textOrbitSilverRn2100, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            Text(if (stars in 1..3) textWeAreSorryAboutThat else textHowWasYourRide, color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 20.dp).semantics { heading() })
            Row(Modifier.padding(top = 10.dp)) {
                (1..5).forEach { n ->
                    Box(
                        Modifier.size(52.dp).clickable(role = Role.RadioButton) { stars = n; picked = emptySet() }
                            .semantics { contentDescription = (if (n > 1) textValueStarValueMany else textValueStarValueOne).format(n); selected = n == stars },
                        contentAlignment = Alignment.Center,
                    ) { Star(n <= stars, 40.dp) }
                }
            }
            Text(Words[stars], color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.height(22.dp))

            if (stars > 0) {
                Text(if (stars in 1..3) textWhatWentWrong else textWhatWentWell, color = c.muted, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 16.dp).align(Alignment.Start))
                FlowRow(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { t ->
                        val on = t in picked
                        Row(
                            Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                                .border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(20.dp)).clickable(role = Role.Checkbox) { picked = if (on) picked - t else picked + t }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (on) { RideIcon(RideIcons.Check, c.accent, size = 14.dp); Spacer(Modifier.width(6.dp)) }
                            Text(t, color = c.fg, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                BasicTextField(
                    comment, { comment = it.take(300) }, textStyle = TextStyle(color = c.fg, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(84.dp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(14.dp)) {
                            if (comment.isEmpty()) Text(textAddACommentOptional, color = c.muted, fontSize = 15.sp)
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        RideButton(textSubmitRating, { onSubmit(stars) }, enabled = stars > 0)
        RideButton(textSkip, onBack, style = RideButtonStyle.Text, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun Star(filled: Boolean, size: androidx.compose.ui.unit.Dp) {
    val c = RideTheme.colors
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val path = androidx.compose.ui.graphics.vector.PathParser().parsePathString(RideIcons.Star[0]).toPath()
        scale(this.size.width / 20f, Offset0) {
            if (filled) drawPath(path, c.accent)
            drawPath(path, if (filled) c.accent else c.muted, style = androidx.compose.ui.graphics.drawscope.Stroke(1.3f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

private val Offset0 = androidx.compose.ui.geometry.Offset.Zero

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RatePreview() = RideNowTheme(darkTheme = true) { RateDriverScreen({}, {}) }
