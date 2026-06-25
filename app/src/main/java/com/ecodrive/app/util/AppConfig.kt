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
 * Supported map visual styles.
 */
enum class MapStyle {
    DEFAULT,
    TERRAIN,
    STREETS
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
     * MapTiler API Key for vector/raster terrain map styles.
     */
    const val MAPTILER_API_KEY = "n7I0eIDG0ygnMqIFus7G"

    /**
     * Switch here to select the map engine you want to use.
     * Can be set to MapProvider.GOOGLE_MAPS, MapProvider.OPEN_STREET_MAP, or MapProvider.MAPLIBRE.
     */
    var ACTIVE_MAP_PROVIDER = MapProvider.OPEN_STREET_MAP

    /**
     * Switch here to select the map visual style (Default vs Terrain).
     */
    var ACTIVE_MAP_STYLE = MapStyle.DEFAULT

    /**
     * Read-only property for backward compatibility with other features.
     * D16: Includes runtime validation for MAPS_API_KEY to prevent crashes
     * if the user tries to use Google Maps with the placeholder key.
     */
    val USE_GOOGLE_MAPS: Boolean
        get() = ACTIVE_MAP_PROVIDER == MapProvider.GOOGLE_MAPS && 
                MAPS_API_KEY.isNotBlank() && 
                MAPS_API_KEY != "YOUR_GOOGLE_MAPS_API_KEY_HERE"
}

/**
 * Event bus for notifying the app to fallback to default map styles
 * when the MapTiler API limit is exceeded.
 */
object MapErrorNotifier {
    val fallbackEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    
    fun triggerFallback() {
        fallbackEvent.tryEmit(Unit)
    }
}
