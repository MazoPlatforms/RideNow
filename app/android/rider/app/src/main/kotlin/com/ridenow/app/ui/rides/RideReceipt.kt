package com.ridenow.app.ui.rides

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.ridenow.app.R
import com.ridenow.app.ui.auth.PhoneAuthApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class RideReceiptDownload(val busy: Boolean, val feedback: Int?, val start: () -> Unit)

@Composable
internal fun rememberRideReceiptDownload(rideId: String): RideReceiptDownload {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var picking by rememberSaveable(rideId) { mutableStateOf(false) }
    var writing by remember(rideId) { mutableStateOf(false) }
    var feedback by rememberSaveable(rideId) { mutableStateOf<Int?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        picking = false
        if (uri != null) {
            writing = true
            scope.launch {
                try {
                    val receipt = PhoneAuthApi.authorized(context, "GET", "/v1/rides/$rideId/receipt") { it }
                    check(receipt.getString("rideId") == rideId)
                    withContext(Dispatchers.IO) {
                        val output = checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                        output.use { stream ->
                            val pdf = PdfDocument()
                            try {
                                val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
                                try {
                                    drawReceipt(page, receipt)
                                } finally {
                                    pdf.finishPage(page)
                                }
                                pdf.writeTo(stream)
                            } finally {
                                pdf.close()
                            }
                        }
                    }
                    feedback = R.string.ride_history_receipt_saved
                } catch (failure: Exception) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    }
                    if (failure is CancellationException) throw failure
                    feedback = R.string.ride_history_receipt_failed
                } finally {
                    writing = false
                }
            }
        }
    }
    return RideReceiptDownload(picking || writing, feedback) {
        if (!picking && !writing) {
            feedback = null
            picking = true
            try {
                picker.launch("RideNow-receipt-$rideId.pdf")
            } catch (_: Exception) {
                picking = false
                feedback = R.string.ride_history_receipt_failed
            }
        }
    }
}

private fun drawReceipt(page: PdfDocument.Page, receipt: JSONObject) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 28f
    }
    page.canvas.drawText("RideNow", 48f, 72f, paint)
    paint.textSize = 18f
    page.canvas.drawText("Cash payment receipt", 48f, 110f, paint)
    paint.typeface = Typeface.DEFAULT
    paint.textSize = 13f
    val amount = receipt.getLong("amountMinor").toBigDecimal().movePointLeft(2)
    val fields = listOf(
        "Ride reference: ${receipt.getString("rideId")}",
        "Payment recorded: ${receipt.getString("paidAt")}",
        "Vehicle category: ${receipt.getString("category")}",
        "Payment method: Cash",
        "Paid amount: ${receipt.getString("currency")} ${amount.toPlainString()}",
    )
    fields.forEachIndexed { index, line -> page.canvas.drawText(line, 48f, 156f + index * 26f, paint) }
}
