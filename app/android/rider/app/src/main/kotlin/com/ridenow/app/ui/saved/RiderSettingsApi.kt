package com.ridenow.app.ui.saved

import android.content.Context
import com.ridenow.app.ui.map.LatLng
import com.ridenow.app.ui.auth.PhoneAuthApi
import com.ridenow.app.ui.safety.Contact
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedAddress(val id: String, val kind: String, val label: String?, val address: String, val point: LatLng?)

internal data class RiderSettings(val city: String?, val addresses: List<SavedAddress>, val contacts: List<Contact>)

internal object RiderSettingsApi {
    private fun identifier(value: String): String = UUID.fromString(value).toString()

    suspend fun load(context: Context): RiderSettings =
        PhoneAuthApi.authorized(context, "GET", "/v1/me/rider-settings") { data ->
            RiderSettings(data.opt("city") as? String, data.getJSONArray("addresses").rows().map(::address),
                data.getJSONArray("contacts").rows().map(::contact))
        }

    suspend fun updateCity(context: Context, city: String?): String? =
        PhoneAuthApi.authorized(context, "PUT", "/v1/me/current-city",
            JSONObject().put("city", city ?: JSONObject.NULL)) { it.opt("city") as? String }

    suspend fun saveAddress(context: Context, id: String?, kind: String, label: String?, address: String, point: LatLng?): SavedAddress {
        val payload = JSONObject().put("kind", kind).put("address", address)
        if (label != null) payload.put("label", label)
        if (point != null) payload.put("coordinates", JSONArray().put(point.longitude).put(point.latitude))
        val path = "/v1/me/saved-addresses" + (id?.let { "/" + identifier(it) } ?: "")
        return PhoneAuthApi.authorized(context, if (id == null) "POST" else "PATCH", path, payload, transform = ::address)
    }

    suspend fun deleteAddress(context: Context, id: String) =
        PhoneAuthApi.authorized(context, "DELETE", "/v1/me/saved-addresses/" + identifier(id)) { Unit }

    suspend fun saveContact(context: Context, value: Contact): Contact =
        PhoneAuthApi.authorized(context, "POST", "/v1/me/trusted-contacts",
            JSONObject().put("name", value.name).put("phone", value.phone), transform = ::contact)

    suspend fun deleteContact(context: Context, id: String) =
        PhoneAuthApi.authorized(context, "DELETE", "/v1/me/trusted-contacts/" + identifier(id)) { Unit }

    private fun JSONArray.rows() = List(length()) { getJSONObject(it) }

    private fun address(data: JSONObject) = SavedAddress(data.getString("id"), data.getString("kind"),
        data.opt("label") as? String, data.getString("address"),
        data.optJSONArray("coordinates")?.let { LatLng(it.getDouble(1), it.getDouble(0)) })

    private fun contact(data: JSONObject) = Contact(data.getString("name"), data.getString("phone"), data.getString("id"))
}
