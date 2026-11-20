package com.ridenow.driver.ui.util

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationManagerCompat
import com.ridenow.driver.R

/**
 * The notification categories shown in Android's notification settings for this app (Settings > Apps > RideNow Driver >
 * Notifications). Ids match the switches in Notification Settings. Creating a channel again is harmless, and once the
 * driver changes a channel in system settings Android keeps their choice.
 */
object NotificationChannels {
    const val RIDE_REQUESTS = "ride_requests"
    const val TRIP_UPDATES = "trip_updates"
    const val PAYMENTS = "payments"
    const val PROMOTIONS = "promotions"
    const val REMINDERS = "reminders"
    const val SAFETY_ALERTS = "safety_alerts"
    const val APP_UPDATES = "app_updates"

    private const val GROUP_RIDES = "group_rides"
    private const val GROUP_MONEY = "group_money"
    private const val GROUP_ALERTS = "group_alerts"
    private const val GROUP_GENERAL = "group_general"

    fun ensure(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        fun group(id: String, name: Int) = nm.createNotificationChannelGroup(NotificationChannelGroupCompat.Builder(id).setName(context.getString(name)).build())
        group(GROUP_RIDES, R.string.ng_rides); group(GROUP_MONEY, R.string.ng_money); group(GROUP_ALERTS, R.string.ng_alerts); group(GROUP_GENERAL, R.string.ng_general)

        fun channel(id: String, groupId: String, name: Int, desc: Int, importance: Int, vibrate: Boolean = true) = nm.createNotificationChannel(
            NotificationChannelCompat.Builder(id, importance).setName(context.getString(name)).setDescription(context.getString(desc)).setGroup(groupId).setVibrationEnabled(vibrate).build(),
        )
        channel(RIDE_REQUESTS, GROUP_RIDES, R.string.ns_ride_requests, R.string.ns_ride_requests_sub, NotificationManagerCompat.IMPORTANCE_HIGH)
        channel(TRIP_UPDATES, GROUP_RIDES, R.string.ns_trip_updates, R.string.ns_trip_updates_sub, NotificationManagerCompat.IMPORTANCE_HIGH)
        channel(PAYMENTS, GROUP_MONEY, R.string.ns_payments, R.string.ns_payments_sub, NotificationManagerCompat.IMPORTANCE_DEFAULT)
        channel(REMINDERS, GROUP_ALERTS, R.string.ns_reminders, R.string.ns_reminders_sub, NotificationManagerCompat.IMPORTANCE_DEFAULT)
        channel(SAFETY_ALERTS, GROUP_ALERTS, R.string.ns_safety, R.string.ns_safety_sub, NotificationManagerCompat.IMPORTANCE_HIGH)
        channel(PROMOTIONS, GROUP_GENERAL, R.string.ns_promotions, R.string.ns_promotions_sub, NotificationManagerCompat.IMPORTANCE_LOW, vibrate = false)
        channel(APP_UPDATES, GROUP_GENERAL, R.string.ns_updates, R.string.ns_updates_sub, NotificationManagerCompat.IMPORTANCE_LOW, vibrate = false)
    }
}
