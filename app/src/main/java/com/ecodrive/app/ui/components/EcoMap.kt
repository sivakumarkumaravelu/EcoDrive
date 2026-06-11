package com.ecodrive.app.ui.components

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ecodrive.app.ui.theme.LocalIsDarkTheme
import com.ecodrive.app.util.AppConfig
import com.ecodrive.app.util.MapProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.ecodrive.app.util.MapStyle

private const val TAG = "EcoMap"

/**
 * A hybrid map component that switches between Google Maps, OpenStreetMap (Leaflet), and MapLibre.
 */
@Composable
fun EcoMap(
    modifier: Modifier = Modifier,
    initialCenter: LatLng,
    initialZoom: Float = 12f,
    polylines: List<EcoPolyline> = emptyList(),
    markers: List<EcoMarker> = emptyList(),
    autoFit: Boolean = false,
    onPolylineClick: ((Int) -> Unit)? = null
) {
    when (AppConfig.ACTIVE_MAP_PROVIDER) {
        MapProvider.GOOGLE_MAPS -> {
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(initialCenter, initialZoom)
            }
            
            var isMapLoaded by remember { mutableStateOf(false) }
            
            if (autoFit) {
                LaunchedEffect(isMapLoaded, polylines, markers) {
                    if (isMapLoaded) {
                        val points = polylines.flatMap { it.points } + markers.map { it.position }
                        if (points.isNotEmpty()) {
                            val builder = LatLngBounds.Builder()
                            points.forEach { builder.include(it) }
                            val bounds = builder.build()
                            val latDelta = kotlin.math.abs(bounds.northeast.latitude - bounds.southwest.latitude)
                            val lngDelta = kotlin.math.abs(bounds.northeast.longitude - bounds.southwest.longitude)
                            try {
                                if (latDelta < 0.001 && lngDelta < 0.001) {
                                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(bounds.center, 15f))
                                } else {
                                    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 50))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Map not ready for fitBounds: ${e.message}")
                            }
                        }
                    }
                }
            }
            
            val mapStyle = AppConfig.ACTIVE_MAP_STYLE
            val mapProperties = remember(mapStyle) {
                MapProperties(
                    mapType = when (mapStyle) {
                        MapStyle.TERRAIN -> com.google.maps.android.compose.MapType.TERRAIN
                        else -> com.google.maps.android.compose.MapType.NORMAL
                    }
                )
            }
            
            GoogleMap(
                modifier = modifier,
                cameraPositionState = cameraPositionState,
                onMapLoaded = { isMapLoaded = true },
                uiSettings = MapUiSettings(zoomControlsEnabled = false),
                properties = mapProperties
            ) {
                markers.forEach { marker ->
                    Marker(
                        state = rememberMarkerState(position = marker.position),
                        title = marker.title
                    )
                }
                
                polylines.forEachIndexed { index, polyline ->
                    Polyline(
                        points = polyline.points,
                        color = polyline.color,
                        width = polyline.width,
                        zIndex = polyline.zIndex,
                        onClick = { onPolylineClick?.invoke(index) }
                    )
                }
            }
        }
        MapProvider.OPEN_STREET_MAP -> {
            OsmMapView(
                modifier = modifier,
                center = initialCenter,
                zoom = initialZoom,
                polylines = polylines,
                markers = markers,
                autoFit = autoFit
            )
        }
        MapProvider.MAPLIBRE -> {
            MapLibreMapView(
                modifier = modifier,
                center = initialCenter,
                zoom = initialZoom,
                polylines = polylines,
                markers = markers,
                autoFit = autoFit
            )
        }
    }
}

data class EcoPolyline(
    val points: List<LatLng>,
    val color: androidx.compose.ui.graphics.Color,
    val width: Float = 10f,
    val zIndex: Float = 0f
)

data class EcoMarker(
    val position: LatLng,
    val title: String? = null
)

@Composable
fun OsmMapView(
    modifier: Modifier,
    center: LatLng,
    zoom: Float,
    polylines: List<EcoPolyline>,
    markers: List<EcoMarker>,
    autoFit: Boolean = false
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val mapStyle = AppConfig.ACTIVE_MAP_STYLE

    // Pick tile layer based on current app theme and style
    val tileVariant = if (isDarkTheme) "dark_all" else "light_all"
    val mapBgColor  = if (isDarkTheme) "#121212" else "#f0f0f0"
    
    val tileLayerUrl = when (mapStyle) {
        MapStyle.TERRAIN -> "https://api.maptiler.com/maps/outdoor-v2/{z}/{x}/{y}.png?key=${AppConfig.MAPTILER_API_KEY}"
        MapStyle.STREETS -> "https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=${AppConfig.MAPTILER_API_KEY}"
        else -> "https://{s}.basemaps.cartocdn.com/$tileVariant/{z}/{x}/{y}{r}.png"
    }
    
    val tileLayerAttribution = when (mapStyle) {
        MapStyle.TERRAIN, MapStyle.STREETS -> "'&copy; <a href=\"https://www.maptiler.com/copyright/\">MapTiler</a> <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>'"
        else -> "'&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors &copy; <a href=\"https://carto.com/attributions\">CARTO</a>'"
    }

    val htmlContent = remember(center, zoom, polylines, markers, autoFit, isDarkTheme, mapStyle) {
        val markersJs = markers.joinToString("\n") { 
            "L.marker([${it.position.latitude}, ${it.position.longitude}]).addTo(featureGroup);" 
        }
        
        val polylinesJs = polylines.joinToString("\n") { polyline ->
            val pts = polyline.points.joinToString(",") { "[${it.latitude}, ${it.longitude}]" }
            val colorHex = "#%02x%02x%02x".format(
                (polyline.color.red * 255).toInt(),
                (polyline.color.green * 255).toInt(),
                (polyline.color.blue * 255).toInt()
            )
            "L.polyline([$pts], {color: '$colorHex', weight: ${polyline.width / 2}}).addTo(featureGroup);"
        }

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                body { margin: 0; padding: 0; background: $mapBgColor; }
                #map { height: 100vh; width: 100vw; min-height: 300px; }
                .leaflet-container { background: $mapBgColor; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                window.onerror = function(message, source, lineno, colno, error) {
                    console.log("JS Error: " + message + " at " + source + ":" + lineno + ":" + colno);
                    return true;
                };

                try {
                    console.log("Initializing map at [${center.latitude}, ${center.longitude}] with zoom ${zoom}");
                    var map = L.map('map').setView([${center.latitude}, ${center.longitude}], ${zoom});
                    // Tile layer switches between styles based on settings.
                    L.tileLayer('$tileLayerUrl', {
                        attribution: $tileLayerAttribution,
                        subdomains: 'abcd',
                        maxZoom: 20
                    }).addTo(map);
                    
                    var featureGroup = L.featureGroup().addTo(map);
                    $markersJs
                    $polylinesJs

                    if ($autoFit && featureGroup.getLayers().length > 0) {
                        map.fitBounds(featureGroup.getBounds(), { padding: [15, 15], maxZoom: 16 });
                    }

                    // Important: invalidateSize() ensures Leaflet recalculates dimensions 
                    // after the container is ready.
                    setTimeout(function() {
                        map.invalidateSize();
                        console.log("Map size invalidated");
                    }, 500);
                } catch (e) {
                    console.log("Map Init Error: " + e.message);
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        errorResponse: android.webkit.WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        val statusCode = errorResponse?.statusCode ?: 0
                        val host = request?.url?.host ?: ""
                        if (host.contains("maptiler.com") && (statusCode == 403 || statusCode == 429)) {
                            Log.e(TAG, "MapTiler API limit reached. Triggering fallback.")
                            com.ecodrive.app.util.MapErrorNotifier.triggerFallback()
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            Log.d(TAG, "WebView Console [${it.messageLevel()}]: ${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                        }
                        return true
                    }
                }
                
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                
                // OSM Tile Usage Policy requires a descriptive User-Agent.
                // Standard mobile browsers are sometimes blocked if they don't identify the app.
                settings.userAgentString = "EcoDrive-Android/1.3.3 (com.ecodrive.app; contact: support@ecodrive.example.com)"

                @Suppress("SetJavaScriptEnabled")
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(false)
                
                tag = htmlContent
                loadDataWithBaseURL(
                    "https://openstreetmap.org",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            if (webView.tag != htmlContent) {
                webView.tag = htmlContent
                webView.loadDataWithBaseURL(
                    "https://openstreetmap.org",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

/**
 * Native Vector MapView using MapLibre SDK.
 */
@Composable
fun MapLibreMapView(
    modifier: Modifier = Modifier,
    center: LatLng,
    zoom: Float,
    polylines: List<EcoPolyline>,
    markers: List<EcoMarker>,
    autoFit: Boolean = false
) {
    val context = LocalContext.current
    val isDarkTheme = LocalIsDarkTheme.current
    val mapStyle = AppConfig.ACTIVE_MAP_STYLE
    
    // Choose style based on theme and map style settings
    val styleUrl = when (mapStyle) {
        MapStyle.TERRAIN -> "https://api.maptiler.com/maps/outdoor-v2/style.json?key=${AppConfig.MAPTILER_API_KEY}"
        MapStyle.STREETS -> "https://api.maptiler.com/maps/streets-v2/style.json?key=${AppConfig.MAPTILER_API_KEY}"
        else -> if (isDarkTheme) {
            "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
        } else {
            "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"
        }
    }

    // Initialize MapLibre before constructing MapView
    com.mapbox.mapboxsdk.Mapbox.getInstance(context)
    
    // Add custom OkHttpClient to intercept 403/429
    val okHttpClient = remember {
        okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (request.url.host.contains("maptiler.com") && (response.code == 403 || response.code == 429)) {
                    Log.e(TAG, "MapTiler vector API limit reached. Triggering fallback.")
                    com.ecodrive.app.util.MapErrorNotifier.triggerFallback()
                }
                response
            }
            .build()
    }
    com.mapbox.mapboxsdk.module.http.HttpRequestUtil.setOkHttpClient(okHttpClient)

    val mapView = remember {
        com.mapbox.mapboxsdk.maps.MapView(context)
    }

    // Handle activity-like lifecycles for the native MapView component
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_CREATE -> mapView.onCreate(android.os.Bundle())
                androidx.lifecycle.Lifecycle.Event.ON_START -> mapView.onStart()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> mapView.onStop()
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.getMapAsync { mapboxMap ->
                mapboxMap.setStyle(styleUrl) { style ->
                    // Clear legacy annotations/markers
                    mapboxMap.clear()

                    // Add markers
                    markers.forEach { marker ->
                        mapboxMap.addMarker(
                            com.mapbox.mapboxsdk.annotations.MarkerOptions()
                                .position(com.mapbox.mapboxsdk.geometry.LatLng(marker.position.latitude, marker.position.longitude))
                                .title(marker.title)
                        )
                    }

                    // Render polyline routes dynamically using high-performance vector layers (GeoJSON)
                    polylines.forEachIndexed { index, polyline ->
                        val sourceId = "polyline-source-$index"
                        val layerId = "polyline-layer-$index"
                        val glowLayerId = "polyline-glow-layer-$index"

                        // Remove existing layers/sources to prevent collision on update
                        style.getLayer(layerId)?.let { style.removeLayer(it) }
                        style.getLayer(glowLayerId)?.let { style.removeLayer(it) }
                        style.getSource(sourceId)?.let { style.removeSource(it) }

                        val pts = polyline.points.map {
                            com.mapbox.geojson.Point.fromLngLat(it.longitude, it.latitude)
                        }
                        
                        if (pts.isNotEmpty()) {
                            val lineString = com.mapbox.geojson.LineString.fromLngLats(pts)
                            val feature = com.mapbox.geojson.Feature.fromGeometry(lineString)
                            val geoJsonSource = com.mapbox.mapboxsdk.style.sources.GeoJsonSource(sourceId, feature)
                            style.addSource(geoJsonSource)

                            val colorHex = "#%02x%02x%02x".format(
                                (polyline.color.red * 255).toInt(),
                                (polyline.color.green * 255).toInt(),
                                (polyline.color.blue * 255).toInt()
                            )

                            // 1. Neon Glow Polyline Layer (wider, transparent background stroke)
                            val glowLayer = com.mapbox.mapboxsdk.style.layers.LineLayer(glowLayerId, sourceId).apply {
                                setProperties(
                                    com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor(colorHex),
                                    com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth(polyline.width + 6f),
                                    com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineOpacity(0.25f),
                                    com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineCap(com.mapbox.mapboxsdk.style.layers.Property.LINE_CAP_ROUND),
                                    com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin(com.mapbox.mapboxsdk.style.layers.Property.LINE_JOIN_ROUND)
                                )
                            }
                            style.addLayer(glowLayer)

                            // 2. Core Polyline Layer (narrower, opaque path)
                            val coreLayer = com.mapbox.mapboxsdk.style.layers.LineLayer(layerId, sourceId).apply {
                                setProperties(
                                    com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor(colorHex),
                                    com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth(polyline.width / 2.5f),
                                    com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineCap(com.mapbox.mapboxsdk.style.layers.Property.LINE_CAP_ROUND),
                                    com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin(com.mapbox.mapboxsdk.style.layers.Property.LINE_JOIN_ROUND)
                                )
                            }
                            style.addLayerBelow(coreLayer, glowLayerId)
                        }
                    }

                    // Auto-fit camera position to display all trip details
                    if (autoFit) {
                        val allPoints = polylines.flatMap { it.points } + markers.map { it.position }
                        if (allPoints.isNotEmpty()) {
                            val builder = com.mapbox.mapboxsdk.geometry.LatLngBounds.Builder()
                            allPoints.forEach {
                                builder.include(com.mapbox.mapboxsdk.geometry.LatLng(it.latitude, it.longitude))
                            }
                            try {
                                val bounds = builder.build()
                                val latDelta = kotlin.math.abs(bounds.latitudeSpan)
                                val lngDelta = kotlin.math.abs(bounds.longitudeSpan)
                                if (latDelta < 0.001 && lngDelta < 0.001) {
                                    mapboxMap.moveCamera(
                                        com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngZoom(
                                            bounds.center, 15.0
                                        )
                                    )
                                } else {
                                    mapboxMap.moveCamera(
                                        com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngBounds(
                                            bounds, 50
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "MapLibre fitBounds error: ${e.message}")
                            }
                        }
                    } else {
                        mapboxMap.moveCamera(
                            com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngZoom(
                                com.mapbox.mapboxsdk.geometry.LatLng(center.latitude, center.longitude),
                                zoom.toDouble()
                            )
                        )
                    }
                }
            }
        }
    )
}
