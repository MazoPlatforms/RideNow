package com.ridenow.driver.ui.trip

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.ridenow.driver.R
import com.ridenow.driver.ui.auth.PhoneAuthException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

internal object DriverGps {
    private val lock = Mutex()
    private var sent = 0L
    private var acceptedAt = 0L

    fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun publish(context: Context): Boolean = lock.withLock {
        if (!permitted(context)) return false
        val manager = context.getSystemService(LocationManager::class.java)
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return false
        if (acceptedAt > 0 && SystemClock.elapsedRealtime() - acceptedAt < 8_000L) return true
        val fix = freshFix(context, manager) ?: return false
        val age = SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos
        if (!fix.hasAccuracy() || fix.accuracy > 100f || age !in 0L..15_000_000_000L) return false
        if (fix.time <= sent) return acceptedAt > 0 && SystemClock.elapsedRealtime() - acceptedAt < 15_000L
        DriverRideApi.location(context, fix)
        sent = fix.time
        acceptedAt = SystemClock.elapsedRealtime()
        true
    }

    suspend fun reset() = lock.withLock { sent = 0L; acceptedAt = 0L }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(context: Context, manager: LocationManager): Location? = withTimeoutOrNull(11_000L) {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(manager, LocationManager.GPS_PROVIDER, signal,
                    ContextCompat.getMainExecutor(context)) { fix ->
                    if (continuation.isActive) continuation.resume(fix)
                }
            } catch (_: RuntimeException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}

class DriverTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastOffer: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!DriverGps.permitted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.live_tracking), NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(OFFERS, getString(R.string.live_offer), NotificationManager.IMPORTANCE_HIGH))
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.live_tracking))
            .setContentText(getString(R.string.live_tracking_detail))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .apply {
                if (appIntent != null) setContentIntent(PendingIntent.getActivity(this@DriverTrackingService, 0, appIntent, PendingIntent.FLAG_IMMUTABLE))
            }
            .build()
        try {
            ServiceCompat.startForeground(this, 51, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive != true) job = scope.launch {
            while (isActive) {
                try {
                    val status = DriverRideApi.current(this@DriverTrackingService)
                    if (status.availability == "OFFLINE" && status.ride?.needsLocation != true) {
                        stopSelf()
                        break
                    }
                    // Check offers before requesting GPS, which can wait for a satellite fix.
                    val offer = if (status.availability == "AVAILABLE") DriverRideApi.offer(this@DriverTrackingService) else null
                    if (offer?.id != lastOffer) {
                        lastOffer = offer?.id
                        if (offer == null) manager.cancel(52)
                        else if (manager.areNotificationsEnabled()) {
                            val remaining = offer.expires.toEpochMilli() - System.currentTimeMillis()
                            if (remaining > 0) {
                                val prompt = NotificationCompat.Builder(this@DriverTrackingService, OFFERS)
                                    .setSmallIcon(android.R.drawable.ic_menu_directions)
                                    .setContentTitle(getString(R.string.live_offer))
                                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                                    .setTimeoutAfter(remaining)
                                    .setAutoCancel(true)
                                    .apply {
                                        if (appIntent != null) setContentIntent(PendingIntent.getActivity(
                                            this@DriverTrackingService, 0, appIntent, PendingIntent.FLAG_IMMUTABLE))
                                    }
                                    .build()
                                manager.notify(52, prompt)
                            }
                        }
                    }
                    DriverGps.publish(this@DriverTrackingService)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (failure is PhoneAuthException && failure.status in setOf(401, 403)) {
                        stopSelf()
                        break
                    }
                }
                delay(3_500L)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        getSystemService(NotificationManager::class.java).cancel(52)
        requested = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "driver-location"
        private const val OFFERS = "driver-offers"
        private var requested = false

        fun start(context: Context) {
            if (requested) return
            ContextCompat.startForegroundService(context, Intent(context, DriverTrackingService::class.java))
            requested = true
        }

        fun stop(context: Context) { context.stopService(Intent(context, DriverTrackingService::class.java)) }
    }
}
