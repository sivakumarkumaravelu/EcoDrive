package com.ecodrive.app.data.remote

import com.ecodrive.app.domain.model.MapRoute
import com.google.android.gms.maps.model.LatLng

/**
 * Common interface for Directions API clients.
 */
interface DirectionsClient {
    suspend fun getRoutes(
        origin: LatLng,
        destination: LatLng,
        apiKey: String? = null
    ): Result<List<MapRoute>>
}
