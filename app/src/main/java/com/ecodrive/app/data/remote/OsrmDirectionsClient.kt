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
 * Client for Open Source Routing Machine (OSRM).
 * A free alternative to Google Directions API.
 */
@Singleton
class OsrmDirectionsClient @Inject constructor() : DirectionsClient {

    companion object {
        private const val TAG = "OsrmDirectionsClient"
        private const val BASE_URL = "https://router.project-osrm.org/route/v1/driving"
    }

    override suspend fun getRoutes(
        origin: LatLng,
        destination: LatLng,
        apiKey: String?
    ): Result<List<MapRoute>> = withContext(Dispatchers.IO) {
        try {
            // OSRM format: base_url/lon,lat;lon,lat?alternatives=true&overview=full
            val urlString = "$BASE_URL/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}" +
                    "?alternatives=true&overview=full&geometries=polyline"
            
            val response = makeRequest(urlString)
            val json = JSONObject(response)
            
            if (json.getString("code") != "Ok") {
                return@withContext Result.failure(Exception("OSRM API error: ${json.optString("code")}"))
            }

            val routesArray = json.getJSONArray("routes")
            val routes = mutableListOf<MapRoute>()

            for (i in 0 until routesArray.length()) {
                val routeJson = routesArray.getJSONObject(i)
                
                val polyline = routeJson.getString("geometry")
                val distance = routeJson.getDouble("distance").toInt()
                val duration = routeJson.getDouble("duration").toInt()
                
                // OSRM doesn't give a short summary like Google (e.g., "via I-95"), 
                // so we'll generate one.
                val summary = "Route ${i + 1}"
                
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
            Log.e(TAG, "Failed to fetch OSRM routes: ${e.message}")
            Result.failure(e)
        }
    }

    private fun makeRequest(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        if (connection.responseCode != 200) {
            val errorBody = BufferedReader(InputStreamReader(connection.errorStream)).use { it.readText() }
            throw Exception("API error: ${connection.responseCode}, Body: $errorBody")
        }
        
        return BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
    }

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
