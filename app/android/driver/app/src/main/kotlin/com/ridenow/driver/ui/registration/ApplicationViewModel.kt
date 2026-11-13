package com.ridenow.driver.ui.registration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.ridenow.driver.ui.trip.CompletedTrip
import com.ridenow.driver.ui.trip.SampleTrips
import com.ridenow.driver.ui.trip.TripPhase
import com.ridenow.driver.ui.trip.TripRequest
import kotlin.random.Random

enum class ApplicationStatus { Pending, ActionRequired, Rejected, Approved }

/**
 * Keeps a one-line summary of each finished registration step so the Review screen can show it,
 * plus the application status. Survives rotation and theme changes. Summary keys are the step numbers (1 to 11).
 */
class ApplicationViewModel : ViewModel() {
    val summaries = mutableStateMapOf<Int, String>()
    var bankSaved by mutableStateOf(false)
    /** Edited on the Edit profile screen. Blank means fall back to the registration answers. */
    var profileName by mutableStateOf("")
    var profileEmail by mutableStateOf("")
    var profileAddress by mutableStateOf("")

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
    fun goOffline() { bankedMillis += SystemClock.elapsedRealtime() - onlineSince; online = false; request = null }

    /** Minutes spent online today, including the current session. */
    fun onlineMinutes(): Int = ((bankedMillis + if (online) SystemClock.elapsedRealtime() - onlineSince else 0L) / 60_000L).toInt()

    /** True until the driver opens the Inbox; drives the unread dot on the Inbox tab. */
    var inboxUnread by mutableStateOf(true)
        private set
    fun markInboxRead() { inboxUnread = false }

    /** UI preview: treat the application as approved. */
    fun approveDemo() { checks = 3 }

    // ----- Trips -----
    var request by mutableStateOf<TripRequest?>(null)
        private set
    var trip by mutableStateOf<TripRequest?>(null)
        private set
    var phase by mutableStateOf(TripPhase.ToPickup)
        private set
    val history = mutableStateListOf<CompletedTrip>()
    val earnings: Double get() = history.sumOf { it.fare }
    private var nextSample = 0

    /** In the preview a sample request is offered; with a backend this is called when one arrives. */
    fun offerRequest() { if (online && request == null && trip == null) request = SampleTrips.all[nextSample++ % SampleTrips.all.size] }
    fun declineRequest() { request = null }
    fun acceptRequest() { trip = request; request = null; phase = TripPhase.ToPickup }
    fun advanceTrip() { phase = when (phase) { TripPhase.ToPickup -> TripPhase.Arrived; else -> TripPhase.InTrip } }
    fun cancelTrip() { trip = null }

    /** Records the finished trip (the fare was collected in cash) and closes it. */
    fun finishTrip() {
        trip?.let { history.add(0, CompletedTrip(it.rider, it.pickup, it.drop, it.fare, System.currentTimeMillis())) }
        trip = null
    }

    /** Clears the application and online state after sign out. */
    fun reset() {
        summaries.clear(); bankSaved = false; profileName = ""; profileEmail = ""; profileAddress = ""; checks = 0; online = false; onlineSince = 0L; bankedMillis = 0L
        request = null; trip = null; history.clear(); inboxUnread = true
    }
}
