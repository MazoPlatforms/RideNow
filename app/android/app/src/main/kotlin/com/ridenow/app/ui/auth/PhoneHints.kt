package com.ridenow.app.ui.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.telephony.TelephonyManager
import androidx.compose.ui.text.AnnotatedString
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import java.util.Locale

/**
 * Country from the SIM or network. Needs no permission and nothing is shown to the user.
 * Falls back to the device locale, then to the United States.
 */
fun detectCountryIso(context: Context): String {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val raw = tm?.networkCountryIso?.takeIf { it.isNotBlank() }
        ?: tm?.simCountryIso?.takeIf { it.isNotBlank() }
        ?: Locale.getDefault().country
    val iso = raw.uppercase(Locale.ROOT)
    return if (Countries.all.any { it.iso == iso }) iso else "US"
}

/** Splits "+94771234567" into its country and the national digits. Prefers the longest matching dial code. */
fun splitFullNumber(full: String): Pair<Country, String>? {
    val n = full.filter { it.isDigit() || it == '+' }
    if (!n.startsWith("+")) return null
    val match = Countries.all
        .filter { n.startsWith(it.dial) }
        .maxWithOrNull(compareBy<Country>({ it.dial.length }, { it.iso == "US" })) ?: return null
    return match to n.removePrefix(match.dial).filter { it.isDigit() }
}

/** Asks Android for the phone number on this device (system picker, no permission). */
fun requestPhoneHint(context: Context, onIntent: (IntentSender) -> Unit) {
    Identity.getSignInClient(context)
        .getPhoneNumberHintIntent(GetPhoneNumberHintIntentRequest.builder().build())
        .addOnSuccessListener { pending: PendingIntent -> onIntent(pending.intentSender) }
        .addOnFailureListener { }
}

/** Reads the number the user picked in the system picker. */
fun readPhoneFromHint(context: Context, data: Intent?, onNumber: (String) -> Unit) {
    val intent = data ?: return
    runCatching { Identity.getSignInClient(context).getPhoneNumberFromIntent(intent) }
        .onSuccess(onNumber)
}

/** Shows a stored number in the country's format, e.g. "+15550123456" -> "+1 (555) 012 3456". */
fun formatPhoneForDisplay(full: String): String {
    val (country, digits) = splitFullNumber(full) ?: return full
    if (digits.isEmpty()) return country.dial
    val formatted = PhoneTransformation(country.pattern).filter(AnnotatedString(digits)).text.text
    return "${country.dial} $formatted"
}
