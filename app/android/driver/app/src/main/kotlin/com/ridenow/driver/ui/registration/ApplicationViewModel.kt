package com.ridenow.driver.ui.registration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import kotlin.random.Random

enum class ApplicationStatus { Pending, ActionRequired, Rejected, Approved }

/**
 * Keeps a one-line summary of each finished registration step so the Review screen can show it,
 * plus the application status. Survives rotation and theme changes. Summary keys are the step numbers (1 to 11).
 */
class ApplicationViewModel : ViewModel() {
    val summaries = mutableStateMapOf<Int, String>()
    var bankSaved by mutableStateOf(false)

    val reference: String = "DRV-2100-" + (1000 + Random.nextInt(9000))

    /** Demo only: each "Check status" tap moves to the next outcome (action required, rejected, approved) until a backend decides. */
    private var checks by mutableIntStateOf(0)
    val status: ApplicationStatus
        get() = when (checks) { 0 -> ApplicationStatus.Pending; 1 -> ApplicationStatus.ActionRequired; 2 -> ApplicationStatus.Rejected; else -> ApplicationStatus.Approved }

    fun checkStatus() { if (checks < 3) checks++ }

    /** Online state for the Home screen. Time online is real; trips and earnings stay at zero until trips exist. */
    var online by mutableStateOf(false)
        private set
    var onlineSince by mutableLongStateOf(0L)
        private set
    var bankedMillis by mutableLongStateOf(0L)
        private set

    fun goOnline() { onlineSince = SystemClock.elapsedRealtime(); online = true }
    fun goOffline() { bankedMillis += SystemClock.elapsedRealtime() - onlineSince; online = false }

    /** Minutes spent online today, including the current session. */
    fun onlineMinutes(): Int = ((bankedMillis + if (online) SystemClock.elapsedRealtime() - onlineSince else 0L) / 60_000L).toInt()

    /** Clears the application and online state after sign out. */
    fun reset() {
        summaries.clear(); bankSaved = false; checks = 0; online = false; onlineSince = 0L; bankedMillis = 0L
    }
}
