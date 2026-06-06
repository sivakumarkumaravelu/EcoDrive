package com.ecodrive.app.data.remote

import android.util.Log
import com.ecodrive.app.domain.model.MapRoute
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for Google Maps Directions and Elevation APIs.
 * Used to calculate predicted fuel consumption for alternative routes.
 */
@Singleton
class GoogleMapsServicesClient @Inject constructor() : DirectionsClient {

    companion object {
        private const val TAG = "GoogleMapsServicesClient"
        private const val DIRECTIONS_BASE_URL = "https://maps.googleapis.com/maps/api/directions/json"
        private const val ELEVATION_BASE_URL = "https://maps.googleapis.com/maps/api/elevation/json"
    }

    /**
     * Data class representing a route option returned from Directions API.
     */
    data class RouteOption(
        val polyline: String,
        val distanceMeters: Int,
        val durationSeconds: Int,
        val summary: String,
        val points: List<LatLng>
    )

    /**
     * Fetch alternative routes between origin and destination.
     */
    override suspend fun getRoutes(
        origin: LatLng,
        destination: LatLng,
        apiKey: String?
    ): Result<List<MapRoute>> = withContext(Dispatchers.IO) {
        try {
            val key = apiKey ?: ""
            val urlString = "$DIRECTIONS_BASE_URL?origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${destination.latitude},${destination.longitude}" +
                    "&alternatives=true&key=$key"
            
            val response = makeRequest(urlString, "Directions")
            val json = JSONObject(response)
            
            if (json.getString("status") != "OK") {
                return@withContext Result.failure(Exception("Directions API error: ${json.optString("status")}"))
            }

            val routesArray = json.getJSONArray("routes")
            val routes = mutableListOf<MapRoute>()

            for (i in 0 until routesArray.length()) {
                val routeJson = routesArray.getJSONObject(i)
                val legs = routeJson.getJSONArray("legs")
                val leg = legs.getJSONObject(0) // Assuming single leg
                
                val polyline = routeJson.getJSONObject("overview_polyline").getString("points")
                val distance = leg.getJSONObject("distance").getInt("value")
                val duration = leg.getJSONObject("duration").getInt("value")
                val summary = routeJson.getString("summary")
                
                routes.add(MapRoute(
                    polyline = polyline,
                    distanceMeters = distance,
                    durationSeconds = duration,
                    summary = summary,
                    points = decodePolyline(polyline)
                ))
            }
            Result.success(routes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch routes: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetch elevation data for a list of LatLng points.
     */
    suspend fun getElevations(
        points: List<LatLng>,
        apiKey: String?
    ): Result<List<Double>> = withContext(Dispatchers.IO) {
        try {
            val key = apiKey ?: ""
            // Sampling if too many points (API has limits)
            val sampledPoints = if (points.size > 100) {
                val step = points.size / 100
                points.filterIndexed { index, _ -> index % step == 0 }
            } else points

            val pathStr = sampledPoints.joinToString("|") { "${it.latitude},${it.longitude}" }
            val urlString = "$ELEVATION_BASE_URL?locations=$pathStr&key=$key"
            
            val response = makeRequest(urlString, "Elevation")
            val json = JSONObject(response)
            
            if (json.getString("status") != "OK") {
                return@withContext Result.failure(Exception("Elevation API error: ${json.optString("status")}"))
            }

            val results = json.getJSONArray("results")
            val elevations = mutableListOf<Double>()
            for (i in 0 until results.length()) {
                elevations.add(results.getJSONObject(i).getDouble("elevation"))
            }
            Result.success(elevations)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch elevations: ${e.message}")
            Result.failure(e)
        }
    }

    private fun makeRequest(urlString: String, apiName: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        if (connection.responseCode != 200) {
            val errorBody = try {
                connection.errorStream?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                null
            }
            val message = "Google Maps $apiName API error: ${connection.responseCode}${if (errorBody != null) ", Body: $errorBody" else ""}"
            Log.e(TAG, message)
            throw Exception(message)
        }
        
        return BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
    }

    /**
     * Decodes an encoded polyline string into a list of LatLng points.
     */
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }
}
