package com.ridenow.driver.ui.legal

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideTheme

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

/** Legal & About: legal documents, about pages and the app version. */
@Composable
fun LegalAboutScreen(onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    val version = remember {
        runCatching { val i = context.packageManager.getPackageInfo(context.packageName, 0); "${i.versionName} (${versionCodeOf(i)})" }.getOrDefault("1.0.0")
    }
    SubScreen(str(R.string.lg_title), onBack, modifier) {
        SectionTitle(str(R.string.lg_legal))
        GroupCard { LegalDocs.legal.forEachIndexed { i, d -> if (i > 0) RowDivider(); TileRow(d.icon, Tone.Neutral, str(d.title), str(d.sub), { onOpen(d.key) }) } }
        SectionTitle(str(R.string.lg_about_section))
        GroupCard { LegalDocs.about.forEachIndexed { i, d -> if (i > 0) RowDivider(); TileRow(d.icon, Tone.Neutral, str(d.title), str(d.sub), { onOpen(d.key) }) } }
        GroupCard(Modifier.padding(top = 18.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(c.fill), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Car, c.onFill, size = 32.dp) }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(str(R.string.app_name_short), color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(str(R.string.lg_version, version), color = c.muted, fontSize = 13.sp)
                    Text(str(R.string.lg_tagline), color = c.muted, fontSize = 13.sp)
                }
            }
        }
        Text(str(R.string.lg_copyright), color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}

/** Shows one legal or about document. */
@Composable
fun LegalDocScreen(doc: LegalDoc, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    SubScreen(str(doc.title), onBack, modifier) {
        str(doc.body).split("\n\n").forEachIndexed { i, p ->
            val head = p.startsWith("# ")
            Text(
                if (head) p.removePrefix("# ") else p, color = if (head) c.fg else c.muted, fontSize = if (head) 16.sp else 14.sp, lineHeight = 21.sp,
                fontWeight = if (head) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = if (i == 0) 8.dp else if (head) 20.dp else 10.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
