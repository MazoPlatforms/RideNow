package com.ridenow.driver.ui.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The vehicle and documents belonging to the authenticated driver. */
internal data class DriverVehicleSnapshot(val vehicle: VehicleInfo, val documents: List<VehicleDoc>) {
    companion object {
        fun from(response: JSONObject, now: Instant = Instant.now()): DriverVehicleSnapshot {
            val profile = response.optJSONObject("profile")
            val submittedVehicle = profile?.text("application_vehicle_id")
            val vehicles = response.optJSONArray("vehicles").objects()
            val selected = vehicles.firstOrNull {
                it.text("id") == submittedVehicle && it.text("status") == "APPROVED"
            } ?: vehicles.lastOrNull { it.text("status") == "APPROVED" }
                ?: vehicles.firstOrNull { it.text("id") == submittedVehicle }
                ?: vehicles.singleOrNull()

            val vehicleId = selected?.text("id").orEmpty()
            val category = VehicleCategory.entries.firstOrNull { it.name == selected?.text("category") }
            val vehicle = VehicleInfo(
                type = selected?.text("category").orEmpty(),
                number = selected?.text("registration").orEmpty(),
                color = selected?.text("color").orEmpty(),
                model = selected?.text("model").orEmpty(),
                year = selected?.optInt("model_year")?.takeIf { it > 0 }?.toString().orEmpty(),
                fuel = selected?.text("fuel").orEmpty(),
                category = category,
                make = selected?.text("make").orEmpty(),
                approved = selected?.text("status") == "APPROVED" && profile?.text("status") == "APPROVED",
            )

            val submittedIds = profile?.optJSONArray("application_document_ids")
            val selectedIds = (0 until (submittedIds?.length() ?: 0))
                .mapNotNull { submittedIds?.optString(it)?.takeIf(String::isNotBlank) }.toSet()
            val documents = response.optJSONArray("documents").objects()
                .filter { doc ->
                    val owner = doc.text("vehicle_id")
                    val driverLicense = doc.text("kind") in listOf("LICENSE", "LICENSE_FRONT", "LICENSE_BACK")
                    (if (driverLicense) owner.isEmpty() else vehicleId.isNotEmpty() && owner == vehicleId) &&
                        (selectedIds.isEmpty() || doc.text("id") in selectedIds)
                }.associateBy { it.text("kind") }
            val types = listOf(
                DocType.License to listOf("LICENSE", "LICENSE_FRONT", "LICENSE_BACK"),
                DocType.Registration to listOf("REGISTRATION"),
                DocType.Revenue to listOf("REVENUE_LICENSE"),
                DocType.Insurance to listOf("INSURANCE"),
                DocType.Emission to listOf("EMISSION"),
                DocType.Photos to listOf(
                    "VEHICLE_FRONT", "VEHICLE_REAR", "VEHICLE_LEFT", "VEHICLE_RIGHT",
                    "VEHICLE_INTERIOR", "VEHICLE_EXTRA",
                ),
            )
            val expiryFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            val display = types.mapNotNull { (type, kinds) ->
                val files = kinds.mapNotNull(documents::get)
                if (files.isEmpty()) return@mapNotNull null
                val expiry = files.mapNotNull { doc ->
                    runCatching { OffsetDateTime.parse(doc.text("expires_at")) }.getOrNull()
                }.minByOrNull { it.toInstant() }
                val status = when {
                    files.any { it.text("scan_status") in setOf("INFECTED", "FAILED") } -> DocStatus.Rejected
                    expiry != null && !expiry.toInstant().isAfter(now) -> DocStatus.Expired
                    files.all { it.text("scan_status") == "CLEAN" } -> DocStatus.Verified
                    else -> DocStatus.Pending
                }
                VehicleDoc(type, expiry?.format(expiryFormat), status, photos = if (type == DocType.Photos) files.size else 0)
            }
            return DriverVehicleSnapshot(vehicle, display)
        }
    }
}

private fun JSONObject.text(key: String) = (opt(key) as? String).orEmpty()

private fun JSONArray?.objects(): List<JSONObject> =
    (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }
