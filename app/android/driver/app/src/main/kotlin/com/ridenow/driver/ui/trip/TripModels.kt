package com.ridenow.driver.ui.trip

/** A trip offered to the driver. Fare is in the local currency. */
class TripRequest(
    val id: Int, val rider: String, val rating: Double, val pickup: String, val drop: String,
    val distanceKm: Double, val etaMin: Int, val fare: Double,
) {
    val initials: String get() = rider.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
}

enum class TripPhase { ToPickup, Arrived, InTrip }

class CompletedTrip(val rider: String, val pickup: String, val drop: String, val fare: Double, val atMillis: Long)

/** Sample requests used by the UI preview. */
object SampleTrips {
    val all = listOf(
        TripRequest(1, "Kai Moreno", 4.9, "Helix Garden Plaza", "Orbit Tower", 6.4, 3, 16.80),
        TripRequest(2, "Mira Santos", 4.8, "Nova Station", "Harbor Point", 4.2, 5, 11.50),
        TripRequest(3, "Leo Park", 5.0, "University Quarter", "Airport Terminal", 18.0, 4, 38.00),
        TripRequest(4, "Zoe Adams", 4.7, "Central Market", "Riverside Hills", 8.1, 6, 21.40),
    )
}
