package com.ecodrive.app.data.remote

import android.util.Log
import com.ecodrive.app.domain.model.WeatherContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Provides current weather context for driving efficiency calculations.
 *
 * Strategy:
 * 1. If an OpenWeatherMap API key is configured, fetch live data.
 * 2. Otherwise, derive a reasonable heuristic from time-of-year, time-of-day,
 *    and latitude to produce a plausible [WeatherContext] with no network calls.
 */
@Singleton
class WeatherApiClient @Inject constructor() {

    companion object {
        private const val TAG = "WeatherApiClient"
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"
    }

    private var cachedContext: WeatherContext? = null
    private var lastFetchMs: Long = 0L
    private val cacheValidityMs = 15 * 60 * 1000L // 15 minutes

    /**
     * Returns a [WeatherContext] for the given coordinates.
     * Falls back to heuristics if the API key is blank or the request fails.
     */
    suspend fun getWeatherContext(
        lat: Double,
        lon: Double,
        apiKey: String = "",
    ): WeatherContext = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedContext != null && now - lastFetchMs < cacheValidityMs) {
            return@withContext cachedContext!!
        }

        val result = if (apiKey.isNotBlank()) {
            fetchFromApi(lat, lon, apiKey) ?: buildHeuristicContext(lat)
        } else {
            buildHeuristicContext(lat)
        }

        cachedContext = result
        lastFetchMs = now
        result
    }

    /**
     * Live OpenWeatherMap fetch.
     */
    private fun fetchFromApi(lat: Double, lon: Double, apiKey: String): WeatherContext? {
        return try {
            val url = "$BASE_URL?lat=$lat&lon=$lon&appid=$apiKey&units=metric"
            val response = URL(url).readText()
            val json = JSONObject(response)
            val weather = json.getJSONArray("weather").getJSONObject(0)
            val main = json.getJSONObject("main")
            val wind = json.getJSONObject("wind")
            val conditionCode = weather.getInt("id")
            val conditionLabel = weather.getString("main")

            WeatherContext(
                tempC = main.getDouble("temp"),
                conditionCode = conditionCode,
                conditionLabel = conditionLabel,
                windSpeedKmh = wind.optDouble("speed", 0.0) * 3.6,
                windBearingDeg = wind.optInt("deg", 0),
                visibilityKm = json.optDouble("visibility", 10000.0) / 1000.0,
                humidity = main.getInt("humidity"),
                isRaining = conditionCode in 200..622,
                isSnowing = conditionCode in 600..622,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch weather from API: ${e.message}")
            null
        }
    }

    /**
     * Derives a plausible [WeatherContext] from hemisphere, season, and time-of-day.
     * No network calls and no randomness — results are fully deterministic and reproducible.
     * D10: Replaced Math.random() with deterministic rules based on month, hour, and latitude.
     */
    private fun buildHeuristicContext(lat: Double): WeatherContext {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1  // 1..12
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        // Determine hemisphere: northern or southern
        val isNorthern = lat >= 0

        // Season (Northern Hemisphere): Dec/Jan/Feb = winter, Jun/Jul/Aug = summer
        val adjustedMonth = if (!isNorthern) ((month + 6 - 1) % 12) + 1 else month
        val isSummer = adjustedMonth in 6..8
        val isWinter = adjustedMonth == 12 || adjustedMonth in 1..2
        val isSpringAutumn = !isSummer && !isWinter

        // Temperature heuristic
        val baseTempC = when {
            isSummer -> 25.0
            isWinter -> -2.0 + (kotlin.math.abs(lat) / 90.0) * -15.0  // Colder at higher latitudes
            else -> 12.0
        }
        // Night/day delta
        val dayDelta = if (hour in 10..16) 5.0 else if (hour in 0..5) -5.0 else 0.0
        val tempC = baseTempC + dayDelta

        // D10: Deterministic rain/snow rules — no Math.random()
        // Spring/autumn afternoons are reliably wetter; winter nights with sub-zero temps may snow.
        val isRaining = isSpringAutumn && hour in 12..18
        val isSnowing = isWinter && tempC < 0 && hour in 0..6  // Overnight freezing conditions

        return WeatherContext(
            tempC = tempC,
            conditionCode = if (isSnowing) 601 else if (isRaining) 500 else 800,
            conditionLabel = if (isSnowing) "Snow" else if (isRaining) "Rain" else "Clear",
            windSpeedKmh = if (isSpringAutumn) 18.0 else 10.0,
            visibilityKm = if (isSnowing || isRaining) 4.0 else 10.0,
            humidity = if (isRaining || isSnowing) 85 else 55,
            isRaining = isRaining,
            isSnowing = isSnowing,
        )
    }

    /** Invalidate cache — call when location changes significantly. */
    fun invalidateCache() {
        cachedContext = null
        lastFetchMs = 0L
    }
}
