package com.ecodrive.app.ui.screens.routeplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.ui.components.EcoMap
import com.ecodrive.app.ui.components.EcoMarker
import com.ecodrive.app.ui.components.EcoPolyline
import com.ecodrive.app.ui.theme.EcoDriveTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

/**
 * Screen for planning eco-friendly routes.
 */
@Composable
fun RoutePlannerScreen(
    viewModel: RoutePlannerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    // Mock user location for demo
    val userLocation = LatLng(37.422, -122.084)

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .imePadding()) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Plan Eco-Route",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Search Bar
        OutlinedTextField(
            value = state.destination,
            onValueChange = viewModel::updateDestination,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Enter destination") },
            trailingIcon = {
                IconButton(onClick = { viewModel.findRoutes(userLocation, state.destination) }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Map Section
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val markers = mutableListOf(EcoMarker(userLocation, "Your Location"))
            state.destinationLatLng?.let { markers.add(EcoMarker(it, "Destination")) }

            EcoMap(
                modifier = Modifier.fillMaxSize(),
                initialCenter = userLocation,
                initialZoom = 12f,
                markers = markers,
                polylines = state.routes.mapIndexed { index, routeWithMetrics ->
                    EcoPolyline(
                        points = routeWithMetrics.route.points,
                        color = if (index == state.selectedRouteIndex) {
                            if (index == 0) EcoDriveTheme.colors.scoreExcellent else MaterialTheme.colorScheme.primary
                        } else Color.Gray.copy(alpha = 0.5f),
                        width = 12f,
                        zIndex = if (index == state.selectedRouteIndex) 1f else 0f
                    )
                },
                onPolylineClick = { viewModel.selectRoute(it) }
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // AI Insight Overlay
            state.aiRouteInsight?.let { insight ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .padding(top = 4.dp)
                        .fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = insight,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Route Options Section
        LazyColumn(
            modifier = Modifier.height(250.dp).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.error != null) {
                item {
                    Text(text = state.error!!, color = MaterialTheme.colorScheme.error)
                }
            }

            itemsIndexed(state.routes) { index, routeWithMetrics ->
                RouteOptionCard(
                    routeWithMetrics = routeWithMetrics,
                    isSelected = index == state.selectedRouteIndex,
                    isGreenest = index == 0,
                    onClick = { viewModel.selectRoute(index) }
                )
            }
        }
    }
}

@Composable
private fun RouteOptionCard(
    routeWithMetrics: RoutePlannerViewModel.RouteWithMetrics,
    isSelected: Boolean,
    isGreenest: Boolean,
    onClick: () -> Unit
) {
    val metrics = routeWithMetrics.metrics
    val borderColor = if (isSelected) {
        if (isGreenest) EcoDriveTheme.colors.scoreExcellent else MaterialTheme.colorScheme.primary
    } else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else EcoDriveTheme.colors.cardBackground
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, borderColor) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = routeWithMetrics.route.summary,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (isGreenest) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = EcoDriveTheme.colors.scoreExcellent.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "GREENEST",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = EcoDriveTheme.colors.scoreExcellent
                            )
                        }
                    }
                }
                Text(
                    text = "${metrics.distanceKm} km • ${metrics.durationMinutes} min",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.2f L".format(metrics.estimatedFuelLiters),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isGreenest) EcoDriveTheme.colors.scoreExcellent else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "%.1f kg CO₂".format(metrics.estimatedCo2Kg),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
