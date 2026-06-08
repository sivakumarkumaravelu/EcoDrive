package com.ecodrive.app.util

/**
 * Supported map engine providers.
 */
enum class MapProvider {
    GOOGLE_MAPS,
    OPEN_STREET_MAP, // Existing Leaflet WebView
    MAPLIBRE         // New Native Vector SDK
}

/**
 * Global application configuration and hard-coded API keys.
 */
object AppConfig {
    /**
     * Google Maps API Key for Geocoding, Directions, and Maps SDK.
     * Hardcoded here as per requirement.
     * 
     * NOTE: If you get a 403 Forbidden error, ensure that BOTH 'Directions API' 
     * and 'Elevation API' are enabled in your Google Cloud Console for this key.
     */
    const val MAPS_API_KEY = "YOUR_GOOGLE_MAPS_API_KEY_HERE"

    /**
     * Switch here to select the map engine you want to use.
     * Can be set to MapProvider.GOOGLE_MAPS, MapProvider.OPEN_STREET_MAP, or MapProvider.MAPLIBRE.
     */
    var ACTIVE_MAP_PROVIDER = MapProvider.OPEN_STREET_MAP

    /**
     * Read-only property for backward compatibility with other features.
     */
    val USE_GOOGLE_MAPS: Boolean
        get() = ACTIVE_MAP_PROVIDER == MapProvider.GOOGLE_MAPS
}
