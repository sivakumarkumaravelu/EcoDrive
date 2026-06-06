package com.ecodrive.app.util

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
     * If true, use Google Maps SDK and Directions API.
     * If false, use OpenStreetMap (via WebView/Leaflet) and OSRM Routing.
     * Default set to false as per user request.
     */
    const val USE_GOOGLE_MAPS = false
}
