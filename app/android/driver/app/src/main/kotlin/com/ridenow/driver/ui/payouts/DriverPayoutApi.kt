package com.ridenow.driver.ui.payouts

import android.content.Context
import com.ridenow.driver.ui.auth.PhoneAuthApi
import org.json.JSONObject
import java.util.UUID

internal data class PayoutBank(
    val holder: String, val bank: String, val branch: String, val number: String, val type: String,
)

internal object DriverPayoutApi {
    suspend fun method(context: Context): PayoutBank? = PhoneAuthApi.authorized(
        context, "GET", "/v1/driver/payout-method",
    ).takeIf { it.has("number") && !it.isNull("number") }?.let {
        PayoutBank(it.getString("holder"), it.getString("bank"), it.getString("branch"), it.getString("number"), it.optString("type", "Savings"))
    }

    suspend fun save(context: Context, value: PayoutBank): PayoutBank = PhoneAuthApi.authorized(
        context, "PUT", "/v1/driver/payout-method",
        JSONObject().put("holder", value.holder).put("bank", value.bank).put("branch", value.branch)
            .put("number", value.number).put("type", value.type),
    ).let { PayoutBank(it.getString("holder"), it.getString("bank"), it.getString("branch"), it.getString("number"), it.optString("type", "Savings")) }

    suspend fun remove(context: Context) {
        PhoneAuthApi.authorized(context, "DELETE", "/v1/driver/payout-method")
    }

    suspend fun payouts(context: Context): JSONObject = PhoneAuthApi.authorized(context, "GET", "/v1/driver/payouts")

    suspend fun request(context: Context, amountMinor: Long) {
        PhoneAuthApi.authorized(
            context, "POST", "/v1/driver/payouts", JSONObject().put("amountMinor", amountMinor),
            key = UUID.randomUUID().toString(),
        )
    }
}
