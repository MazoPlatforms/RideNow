package com.ridenow.driver.ui.trip

/** A trip offered to the driver. Fare is in the local currency. */
class TripRequest(
    val id: Int, val rider: String, val pickup: String, val drop: String,
    val distanceKm: Double, val etaMin: Int, val fare: Double,
) {
    val initials: String get() = rider.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
}

enum class TripPhase { ToPickup, Arrived, InTrip }

class CompletedTrip(val rider: String, val pickup: String, val drop: String, val fare: Double, val atMillis: Long)

/** Sample requests used by the UI preview. */
object SampleTrips {
    val all = listOf(
        TripRequest(1, "Kai Moreno", "Helix Garden Plaza", "Orbit Tower", 6.4, 3, 680.0),
        TripRequest(2, "Mira Santos", "Nova Station", "Harbor Point", 4.2, 5, 520.0),
        TripRequest(3, "Leo Park", "University Quarter", "Airport Terminal", 18.0, 4, 1250.0),
        TripRequest(4, "Zoe Adams", "Central Market", "Riverside Hills", 8.1, 6, 840.0),
    )
}
