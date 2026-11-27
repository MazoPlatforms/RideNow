package com.ridenow.driver.ui.legal

import android.content.Intent
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.entrance

/** A legal or about page: a title string and a body string whose paragraphs are separated by blank lines. */
class LegalDoc(val key: String, val icon: List<String>, val title: Int, val sub: Int, val body: Int)

object LegalDocs {
    val legal = listOf(
        LegalDoc("terms", RideIcons.Doc, R.string.lg_terms, R.string.lg_terms_sub, R.string.lg_terms_body),
        LegalDoc("privacy", RideIcons.Shield, R.string.lg_privacy, R.string.lg_privacy_sub, R.string.lg_privacy_body),
        LegalDoc("cookies", RideIcons.Doc, R.string.lg_cookies, R.string.lg_cookies_sub, R.string.lg_cookies_body),
        LegalDoc("guidelines", RideIcons.Shield, R.string.lg_guidelines, R.string.lg_guidelines_sub, R.string.lg_guidelines_body),
    )
    val about = listOf(
        LegalDoc("about", RideIcons.Info, R.string.lg_about, R.string.lg_about_sub, R.string.lg_about_body),
        LegalDoc("credits", RideIcons.Star, R.string.lg_credits, R.string.lg_credits_sub, R.string.lg_credits_body),
        LegalDoc("licenses", RideIcons.Doc, R.string.lg_licenses, R.string.lg_licenses_sub, R.string.lg_licenses_body),
        LegalDoc("company", RideIcons.Building, R.string.lg_company, R.string.lg_company_sub, R.string.lg_company_body),
    )
    fun find(key: String): LegalDoc? = (legal + about).firstOrNull { it.key == key }
}

@Suppress("DEPRECATION")
private fun versionCodeOf(i: android.content.pm.PackageInfo): Int = i.versionCode

/** Large left-aligned title with an optional line under it, as on the Rider app list screens. */
@Composable
private fun LegalTop(title: String, subtitle: String?, onBack: () -> Unit) {
    val c = RideTheme.colors
    Column {
        BackButton(onBack, Modifier.padding(top = 4.dp))
        Text(title, color = c.fg, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Legal & About: app card with the logo, legal documents and about pages. Same layout as the Rider app. */
@Composable
fun LegalAboutScreen(onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val licenseScheme = if (c.isDark) darkColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
        else lightColorScheme(primary = c.accent, background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg)
    val licenseTypography = MaterialTheme.typography
    val version = remember {
        runCatching { val i = context.packageManager.getPackageInfo(context.packageName, 0); "${i.versionName} (${versionCodeOf(i)})" }.getOrDefault("1.0.0")
    }
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        LegalTop(str(R.string.lg_title), str(R.string.lg_sub), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button) { onOpen("about") }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(if (c.isDark) Color(0xFF14181F) else Color.White).border(1.dp, c.border, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    LogoMark(44.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(str(R.string.app_name), color = c.fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(str(R.string.lg_version, version), color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
                    Text(str(R.string.lg_driver_tagline), color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
                RideIcon(RideIcons.Chevron, c.muted, size = 18.dp)
            }
            SectionTitle(str(R.string.lg_legal))
            GroupCard { LegalDocs.legal.forEachIndexed { i, d -> if (i > 0) RowDivider(); NavRow(d.icon, str(d.title), { onOpen(d.key) }, subtitle = str(d.sub)) } }
            SectionTitle(str(R.string.lg_about_section))
            GroupCard {
                LegalDocs.about.forEachIndexed { i, d ->
                    if (i > 0) RowDivider()
                    NavRow(d.icon, str(d.title), {
                        if (d.key == "licenses") {
                            OssLicensesMenuActivity.setActivityTitle(str(d.title))
                            OssLicensesMenuActivity.setTheme(licenseScheme, licenseScheme, licenseTypography)
                            context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                        } else onOpen(d.key)
                    }, subtitle = str(d.sub))
                }
            }
            Text(str(R.string.lg_copyright), color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
        }
    }
}

/** Shows one legal or about document. */
@Composable
fun LegalDocScreen(doc: LegalDoc, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    Column(modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        LegalTop(str(doc.title), str(doc.sub), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).entrance(60)) {
            str(doc.body).split("\n\n").forEachIndexed { i, p ->
                val head = p.startsWith("# ")
                Text(
                    if (head) p.removePrefix("# ") else p, color = if (head) c.fg else c.muted, fontSize = if (head) 16.sp else 14.sp, lineHeight = 21.sp,
                    fontWeight = if (head) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = if (i == 0) 20.dp else if (head) 22.dp else 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
