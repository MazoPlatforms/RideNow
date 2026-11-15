package com.ridenow.driver.ui.settings

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.theme.RideTheme

private object LockSession { var unlocked = false }

/**
 * When App Lock is on, asks for the phone's screen lock (biometrics, PIN, pattern or password) once each time the app
 * process starts. If the phone no longer has a screen lock the gate opens, so the driver is never locked out.
 */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val enabled = remember { context.getSharedPreferences("ridenowdriver", 0).getBoolean("app_lock", false) }
    var unlocked by remember { mutableStateOf(!enabled || LockSession.unlocked || !deviceHasLock(context)) }
    val prompt = str(R.string.lock_prompt)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { LockSession.unlocked = true; unlocked = true }
    }
    fun ask() {
        val km = context.getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val intent = km?.createConfirmDeviceCredentialIntent(prompt, null)
        if (intent != null) launcher.launch(intent) else unlocked = true
    }
    if (unlocked) { content(); return }
    LaunchedEffect(Unit) { ask() }
    val c = RideTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        LogoMark(72.dp)
        Text(str(R.string.lock_screen_title), color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp))
        Text(str(R.string.lock_screen_text), color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
        RideButton(str(R.string.lock_unlock), { ask() })
    }
}
