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
import androidx.compose.ui.viewinterop.AndroidView
import com.ecodrive.app.util.AppConfig
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

private const val TAG = "EcoMap"

/**
 * A hybrid map component that switches between Google Maps and OpenStreetMap (Leaflet).
 */
@Composable
fun EcoMap(
    modifier: Modifier = Modifier,
    initialCenter: LatLng,
    initialZoom: Float = 12f,
    polylines: List<EcoPolyline> = emptyList(),
    markers: List<EcoMarker> = emptyList(),
    onPolylineClick: ((Int) -> Unit)? = null
) {
    if (AppConfig.USE_GOOGLE_MAPS) {
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(initialCenter, initialZoom)
        }
        
        GoogleMap(
            modifier = modifier,
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
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
    } else {
        OsmMapView(
            modifier = modifier,
            center = initialCenter,
            zoom = initialZoom,
            polylines = polylines,
            markers = markers
        )
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
    markers: List<EcoMarker>
) {
    val htmlContent = remember(center, zoom, polylines, markers) {
        val markersJs = markers.joinToString("\n") { 
            "L.marker([${it.position.latitude}, ${it.position.longitude}]).addTo(map);" 
        }
        
        val polylinesJs = polylines.joinToString("\n") { polyline ->
            val pts = polyline.points.joinToString(",") { "[${it.latitude}, ${it.longitude}]" }
            val colorHex = "#%02x%02x%02x".format(
                (polyline.color.red * 255).toInt(),
                (polyline.color.green * 255).toInt(),
                (polyline.color.blue * 255).toInt()
            )
            "L.polyline([$pts], {color: '$colorHex', weight: ${polyline.width / 2}}).addTo(map);"
        }

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                body { margin: 0; padding: 0; background: #121212; }
                #map { height: 100vh; width: 100vw; min-height: 300px; }
                .leaflet-container { background: #121212; }
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
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: '&copy; OpenStreetMap contributors'
                    }).addTo(map);
                    
                    $markersJs
                    $polylinesJs

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
                webViewClient = WebViewClient()
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
