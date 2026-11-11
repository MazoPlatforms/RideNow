package com.ridenow.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.ln

/** Lightweight map coordinates and camera state used by the MapLibre renderer. */
data class LatLng(val latitude: Double, val longitude: Double)
data class CameraPosition(val target: LatLng, val zoom: Float) {
    companion object { fun fromLatLngZoom(target: LatLng, zoom: Float) = CameraPosition(target, zoom) }
}
data class CameraUpdate internal constructor(val target: LatLng, val zoom: Float)

class LatLngBounds private constructor(val southwest: LatLng, val northeast: LatLng) {
    class Builder {
        private val points = mutableListOf<LatLng>()
        fun include(point: LatLng) = apply { points += point }
        fun build(): LatLngBounds {
            require(points.isNotEmpty())
            return LatLngBounds(
                LatLng(points.minOf { it.latitude }, points.minOf { it.longitude }),
                LatLng(points.maxOf { it.latitude }, points.maxOf { it.longitude }),
            )
        }
    }
}

object CameraUpdateFactory {
    fun newLatLngZoom(target: LatLng, zoom: Float) = CameraUpdate(target, zoom)
    fun newLatLngBounds(bounds: LatLngBounds, padding: Int): CameraUpdate {
        val center = LatLng(
            (bounds.southwest.latitude + bounds.northeast.latitude) / 2,
            (bounds.southwest.longitude + bounds.northeast.longitude) / 2,
        )
        val span = maxOf(bounds.northeast.latitude - bounds.southwest.latitude, bounds.northeast.longitude - bounds.southwest.longitude)
        val zoom = if (span <= 0.00001) 16f else (ln(360.0 / span) / ln(2.0) - 1.0).toFloat().coerceIn(3f, 18f)
        return CameraUpdate(center, zoom)
    }
}

class CameraPositionState(initial: CameraPosition = CameraPosition.fromLatLngZoom(LatLng(7.72, 81.72), 13f)) {
    var position by mutableStateOf(initial)
        internal set
    var isMoving by mutableStateOf(false)
        internal set
    internal var applyUpdate: ((CameraUpdate) -> Unit)? = null

    suspend fun animate(update: CameraUpdate) {
        position = CameraPosition(update.target, update.zoom)
        applyUpdate?.invoke(update)
    }

    internal fun sync(target: LatLng, zoom: Double, moving: Boolean) {
        position = CameraPosition(target, zoom.toFloat())
        isMoving = moving
    }
}

@Composable
fun rememberCameraPositionState(init: CameraPositionState.() -> Unit = {}): CameraPositionState =
    remember { CameraPositionState().apply(init) }
