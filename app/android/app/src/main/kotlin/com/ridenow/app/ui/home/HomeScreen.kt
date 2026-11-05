package com.ridenow.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ridenow.app.R
import com.ridenow.app.ui.components.RideIcon
import com.ridenow.app.ui.components.RideIcons
import com.ridenow.app.ui.theme.BrandYellow
import com.ridenow.app.ui.theme.RideNowTheme
import com.ridenow.app.ui.theme.RideTheme
import com.ridenow.app.ui.util.entrance

/**
 * Home ("Where to?"): Mazo Maps preview, menu and notification buttons, and a bottom sheet.
 */
@Composable
fun HomeScreen(
    onMenu: () -> Unit, onNotifications: () -> Unit, onSearch: () -> Unit, onLater: () -> Unit,
    onEditPickup: () -> Unit, onSaved: (String) -> Unit, onRecent: (String) -> Unit,
    modifier: Modifier = Modifier, hasUnread: Boolean = true,
) {
    val c = RideTheme.colors
    var expanded by rememberSaveable { mutableStateOf(true) }
    Box(modifier.fillMaxSize().background(c.bg)) {
        MazoMapPreview(Modifier.fillMaxSize())

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).entrance(0), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundButton(RideIcons.Menu, stringResource(R.string.home_menu), onMenu)
            Box {
                RoundButton(RideIcons.Bell, stringResource(R.string.home_notifications), onNotifications)
                if (hasUnread) Box(Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 11.dp).size(12.dp).clip(CircleShape).background(BrandYellow).border(2.dp, c.bg, CircleShape))
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.bg)
                .border(1.dp, c.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).navigationBarsPadding()
                .padding(horizontal = 24.dp).entrance(150, 40),
        ) {
            // Drag or tap the handle to expand and collapse the sheet.
            Box(
                Modifier.fillMaxWidth().height(28.dp).clickable { expanded = !expanded }.pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -30f) expanded = true else if (total > 30f) expanded = false }) { _, dy -> total += dy }
                }.semantics { contentDescription = if (expanded) "Collapse" else "Expand" },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(c.border)) }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_title), color = c.fg, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f).semantics { heading() })
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button, onClick = onLater).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RideIcon(RideIcons.Clock, c.accent, size = 18.dp); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_later), color = c.fg, fontSize = 14.sp)
                }
            }

            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_pickup, "128 Meridian Walk"), color = c.muted, fontSize = 13.sp)
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onEditPickup).semantics { contentDescription = "Edit pickup location" }, contentAlignment = Alignment.Center) {
                    RideIcon(RideIcons.Pencil, c.accent, size = 18.dp)
                }
            }

            Row(
                Modifier.padding(top = 4.dp).fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(c.field)
                    .border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onSearch).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RideIcon(RideIcons.Search, c.accent); Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.home_destination), color = c.muted, fontSize = 17.sp)
            }

            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SavedButton(RideIcons.Home, stringResource(R.string.home_saved_home), Modifier.weight(1f)) { onSaved("Home") }
                SavedButton(RideIcons.Work, stringResource(R.string.home_saved_work), Modifier.weight(1f)) { onSaved("Work") }
            }

            AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.padding(top = 8.dp)) {
                    RecentRow("Nova Terminal, Gate 4", "6.2 km") { onRecent("Nova Terminal, Gate 4") }
                    RecentRow("Helix Garden Plaza", "3.8 km") { onRecent("Helix Garden Plaza") }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun RoundButton(icon: List<String>, label: String, onClick: () -> Unit) {
    val c = RideTheme.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(c.bg).border(1.dp, c.line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { RideIcon(icon, c.fg, size = 22.dp) }
}

@Composable
private fun SavedButton(icon: List<String>, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = RideTheme.colors
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) { RideIcon(icon, c.accent); Spacer(Modifier.width(10.dp)); Text(label, color = c.fg, fontSize = 15.sp) }
}

@Composable
private fun RecentRow(title: String, distance: String, onClick: () -> Unit) {
    val c = RideTheme.colors
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Clock, c.accent, size = 18.dp) }
            Spacer(Modifier.width(14.dp))
            Text(title, color = c.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(distance, color = c.muted, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

@Composable
private fun MazoMapPreview(modifier: Modifier = Modifier) {
    val background = RideTheme.colors.bg.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            WebView(viewContext).apply {
                setBackgroundColor(background)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.safeBrowsingEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url
                        return !(url.scheme == "https" && url.host == "mazoplatforms.github.io" && url.path?.startsWith("/newmaps") == true)
                    }
                }
                loadUrl("https://mazoplatforms.github.io/newmaps/")
            }
        },
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeDarkPreview() = RideNowTheme(darkTheme = true) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeLightPreview() = RideNowTheme(darkTheme = false) { HomeScreen({}, {}, {}, {}, {}, {}, {}) }
