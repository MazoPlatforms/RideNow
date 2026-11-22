package com.ridenow.driver.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.ridenow.driver.ui.map.SifricApi

private fun Context.safeStart(intent: Intent) {
    try { startActivity(intent) } catch (_: Exception) { Toast.makeText(this, "No app available", Toast.LENGTH_SHORT).show() }
}

private fun digits(phone: String) = phone.filter { it.isDigit() || it == '+' }

fun openDial(context: Context, phone: String) = context.safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + digits(phone))))
fun openSms(context: Context, phone: String) = context.safeStart(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + digits(phone))))
suspend fun openMaps(context: Context, place: String) {
    val point = SifricApi.geocode(context, place)
    val uri = Uri.parse("https://maps.sifric.workers.dev/").buildUpon()
    if (point != null) uri.fragment("16/${point.latitude}/${point.longitude}")
    context.safeStart(Intent(Intent.ACTION_VIEW, uri.build()))
}
fun openUrl(context: Context, url: String) = context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

fun shareText(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.safeStart(Intent.createChooser(send, title))
}

fun openAppSettings(context: Context) = context.safeStart(
    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
)

fun openAppNotificationSettings(context: Context) = context.safeStart(
    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
)

fun openChannelSettings(context: Context, channelId: String) = context.safeStart(
    Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId),
)
