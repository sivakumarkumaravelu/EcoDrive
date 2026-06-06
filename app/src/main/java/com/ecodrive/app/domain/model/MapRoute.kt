package com.ecodrive.app.domain.model

import com.google.android.gms.maps.model.LatLng

/**
 * Provider-agnostic representation of a route for map display and analysis.
 */
data class MapRoute(
    val polyline: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val summary: String,
    val points: List<LatLng>
)
