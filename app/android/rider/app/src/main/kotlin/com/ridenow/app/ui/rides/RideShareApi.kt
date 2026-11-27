package com.ridenow.app.ui.rides

import android.content.Context
import com.ridenow.app.ui.auth.PhoneAuthApi
import org.json.JSONObject
import java.time.Instant

internal data class RideShareLink(val url: String, val expiresAt: Instant)

internal object RideShareApi {
    suspend fun create(context: Context, rideId: String): RideShareLink =
        PhoneAuthApi.authorized(context, "POST", "/v1/rides/$rideId/share-link") {
            RideShareLink(it.getString("url"), Instant.parse(it.getString("expiresAt")))
        }

    suspend fun revoke(context: Context, rideId: String) {
        PhoneAuthApi.authorized(context, "DELETE", "/v1/rides/$rideId/share-link") { Unit }
    }
}
