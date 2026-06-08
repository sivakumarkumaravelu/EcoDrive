package com.ecodrive.app.ui.screens.routeplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.ui.components.EcoMap
import com.ecodrive.app.ui.components.EcoMarker
import com.ecodrive.app.ui.components.EcoPolyline
import com.ecodrive.app.ui.theme.EcoDriveTheme
import com.ecodrive.app.util.UnitConverter
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlin.math.roundToInt

/**
 * Screen for planning eco-friendly routes.
 *
 * Layout structure:
 *  Column (full screen)
 *    ├── Top bar (back + title + location button)
 *    └── Box (weight=1, remaining space)  ← outer overlay container
 *          ├── Column (fillMaxSize)        ← normal flow
 *          │     ├── Search field
 *          │     ├── Map
 *          │     ├── AI insight
 *          │     └── Route list
 *          └── Suggestion dropdown         ← floats on top via zIndex
 *
 * The dropdown is a direct child of the outer Box so it can overlap
 * the map without being clipped by the Column's bounds.
 */
@Composable
fun RoutePlannerScreen(
    viewModel: RoutePlannerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val userLocation = state.origin ?: LatLng(37.422, -122.084)
    val density = LocalDensity.current

    // Track position/height of the search field so we can anchor the dropdown.
    var searchFieldBottomY by remember { mutableIntStateOf(0) }
    var searchFieldLeft by remember { mutableIntStateOf(0) }
    var searchFieldWidth by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Plan Eco-Route",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f)
            )
            IconButton(onClick = viewModel::loadCurrentLocation) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "My Location",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ── Outer overlay Box ─────────────────────────────────────────────────
        // Everything below the top bar sits here. The suggestion dropdown is
        // rendered as a sibling of the main Column so it can float over the map.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {

            // ── Main content (normal document flow) ──────────────────────────
            Column(modifier = Modifier.fillMaxSize()) {

                // Search field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .onGloballyPositioned { coords ->
                            searchFieldBottomY = (coords.positionInParent().y + coords.size.height).roundToInt()
                            searchFieldLeft = coords.positionInParent().x.roundToInt()
                            searchFieldWidth = coords.size.width
                        }
                ) {
                    OutlinedTextField(
                        value = state.destination,
                        onValueChange = viewModel::updateDestination,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter destination") },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (state.destination.isNotBlank()) {
                                    viewModel.findRoutes(userLocation, state.destination)
                                }
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (state.isSearchingSuggestions) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp)
                                .height(2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Map
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val markers = remember(userLocation, state.destinationLatLng) {
                        val list = mutableListOf(EcoMarker(userLocation, "Your Location"))
                        state.destinationLatLng?.let { list.add(EcoMarker(it, "Destination")) }
                        list
                    }

                    EcoMap(
                        modifier = Modifier.fillMaxSize(),
                        initialCenter = userLocation,
                        initialZoom = 12f,
                        markers = markers,
                        polylines = state.routes.mapIndexed { index, routeWithMetrics ->
                            EcoPolyline(
                                points = routeWithMetrics.route.points,
                                color = if (index == state.selectedRouteIndex) {
                                    if (index == 0) EcoDriveTheme.colors.scoreExcellent
                                    else MaterialTheme.colorScheme.primary
                                } else Color.Gray.copy(alpha = 0.5f),
                                width = 12f,
                                zIndex = if (index == state.selectedRouteIndex) 1f else 0f
                            )
                        },
                        onPolylineClick = { viewModel.selectRoute(it) }
                    )

                    if (state.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                // AI Insight Section (below map, above route list)
                state.aiRouteInsight?.let { insight ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shadowElevation = 2.dp
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
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Route Options Section
                LazyColumn(
                    modifier = Modifier
                        .height(250.dp)
                        .fillMaxWidth()
                        .padding(16.dp),
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
                            useMetric = state.useMetric,
                            onClick = { viewModel.selectRoute(index) }
                        )
                    }
                }
            } // end main Column

            // ── Suggestion dropdown overlay ───────────────────────────────────
            // Rendered as a direct child of the outer Box — floats above the map.
            if (state.suggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .offset { IntOffset(searchFieldLeft, searchFieldBottomY + 4) }
                        .width(with(density) { searchFieldWidth.toDp() })
                        .heightIn(max = 260.dp)
                        .zIndex(10f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 12.dp,
                    tonalElevation = 4.dp
                ) {
                    LazyColumn {
                        items(state.suggestions) { suggestion ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectSuggestion(suggestion) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = suggestion.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = suggestion.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        } // end outer Box
    } // end root Column
}

@Composable
private fun RouteOptionCard(
    routeWithMetrics: RoutePlannerViewModel.RouteWithMetrics,
    isSelected: Boolean,
    isGreenest: Boolean,
    useMetric: Boolean,
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
                    text = "${UnitConverter.formatDistance(metrics.distanceKm, useMetric)} • ${metrics.durationMinutes} min",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = UnitConverter.formatFuelVolume(metrics.estimatedFuelLiters, useMetric),
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
