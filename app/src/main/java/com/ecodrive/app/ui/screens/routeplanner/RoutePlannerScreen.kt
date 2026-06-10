package com.ecodrive.app.ui.screens.routeplanner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.ui.components.EcoMap
import com.ecodrive.app.ui.components.EcoMarker
import com.ecodrive.app.ui.components.EcoPolyline
import com.ecodrive.app.ui.theme.EcoDriveTheme
import com.ecodrive.app.util.UnitConverter
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

/**
 * Screen for planning eco-friendly routes.
 *
 * Layout structure (map-first overlay architecture):
 *
 *  Column (full screen)
 *    ├── Top bar (back + title + location button)
 *    └── Box (weight=1, remaining space)
 *          ├── EcoMap (fillMaxSize — always behind everything)
 *          ├── Search overlay (top of box, floating above map)
 *          │     ├── Search TextField
 *          │     └── Popup → Suggestion list (window-level, always above AndroidView)
 *          └── Results panel (bottom of box, constrained to 45% max height)
 *                ├── AI Insight card (max 3 lines, expandable)
 *                └── Route option cards (scrollable)
 *
 * The Popup composable is used for the suggestion dropdown instead of a zIndex
 * overlay because Compose's zIndex cannot beat AndroidView (WebView/MapView) in
 * the rendering stack. Popup creates a separate Android window that always renders
 * on top of all views.
 */
@Composable
fun RoutePlannerScreen(
    viewModel: RoutePlannerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val userLocation = state.origin ?: LatLng(37.422, -122.084)

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
                .padding(horizontal = 4.dp, vertical = 8.dp),
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

        // ── Main content area — map fills everything, UI overlays float on top ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {

            // ── Layer 1: Map (always fills the full area, always behind) ─────
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

            // Loading spinner over map
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // ── Layer 2: Search bar — floating at the top of the map ─────────
            SearchOverlay(
                destination = state.destination,
                isSearching = state.isSearchingSuggestions,
                suggestions = state.suggestions,
                onDestinationChange = viewModel::updateDestination,
                onSearch = {
                    if (state.destination.isNotBlank()) {
                        viewModel.findRoutes(userLocation, state.destination)
                    }
                },
                onSuggestionSelected = viewModel::selectSuggestion,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // ── Layer 3: Results panel — floating at the bottom ───────────────
            // AnimatedContent is used instead of AnimatedVisibility because the
            // latter has a ColumnScope extension overload that causes a compile
            // error when called inside a Box without an explicit receiver.
            val showResults = state.routes.isNotEmpty() || state.error != null
            AnimatedContent(
                targetState = showResults,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "resultsPanel"
            ) { visible ->
                if (visible) {
                    ResultsBottomPanel(
                        aiInsight = state.aiRouteInsight,
                        routes = state.routes,
                        selectedRouteIndex = state.selectedRouteIndex,
                        error = state.error,
                        useMetric = state.useMetric,
                        onRouteSelected = viewModel::selectRoute
                    )
                }
            }
        } // end outer Box
    } // end root Column
}

// ─────────────────────────────────────────────────────────────────────────────
// Search overlay composable (search field + dropdown via Popup)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchOverlay(
    destination: String,
    isSearching: Boolean,
    suggestions: List<RoutePlannerViewModel.PlaceSuggestion>,
    onDestinationChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSuggestionSelected: (RoutePlannerViewModel.PlaceSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    // showDropdown tracks whether the popup should be shown
    val showDropdown = suggestions.isNotEmpty()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val offsetY = with(density) { 64.dp.roundToPx() }

    Box(modifier = modifier) {
        // Search text field
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                OutlinedTextField(
                    value = destination,
                    onValueChange = onDestinationChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter destination") },
                    trailingIcon = {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                if (isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .padding(horizontal = 12.dp)
                    )
                }
            }
        }

        // ── Popup dropdown — renders at window level, above AndroidView ───────
        // This is the KEY fix: Popup() creates a separate Android Window that
        // draws above native views (WebView, MapView). Using zIndex here would
        // NOT work because zIndex only orders Compose-to-Compose layers.
        if (showDropdown) {
            Popup(
                alignment = Alignment.TopStart,
                // Offset below the search field (~56dp + padding for the text field height)
                offset = androidx.compose.ui.unit.IntOffset(0, offsetY),
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                ),
                onDismissRequest = { /* handled by ViewModel when suggestion is cleared */ }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .heightIn(max = 280.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp,
                    tonalElevation = 4.dp
                ) {
                    LazyColumn {
                        items(suggestions) { suggestion ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSuggestionSelected(suggestion) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = suggestion.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = suggestion.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Results bottom panel (AI insight + route cards)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultsBottomPanel(
    aiInsight: String?,
    routes: List<RoutePlannerViewModel.RouteWithMetrics>,
    selectedRouteIndex: Int,
    error: String?,
    useMetric: Boolean,
    onRouteSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Constrain to max 45% of screen height so the map is always visible
            .fillMaxHeight(0.45f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        tonalElevation = 2.dp
    ) {
        Column {
            // Drag handle pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            // Scrollable content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Error message
                if (error != null) {
                    item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                // AI Insight card
                if (aiInsight != null) {
                    item {
                        AiInsightCard(insight = aiInsight)
                    }
                }

                // Route option cards
                itemsIndexed(routes) { index, routeWithMetrics ->
                    RouteOptionCard(
                        routeWithMetrics = routeWithMetrics,
                        isSelected = index == selectedRouteIndex,
                        isGreenest = index == 0,
                        useMetric = useMetric,
                        onClick = { onRouteSelected(index) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Insight card with expand/collapse
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AiInsightCard(insight: String) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Eco Insight",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                // Expand/collapse icon — only show if text would overflow
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Show less" else "Show more",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = insight,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                // Cap at 3 lines when collapsed — prevents full-screen takeover
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Route option card
// ─────────────────────────────────────────────────────────────────────────────

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
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isGreenest) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = EcoDriveTheme.colors.scoreExcellent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "GREENEST",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = EcoDriveTheme.colors.scoreExcellent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${UnitConverter.formatDistance(metrics.distanceKm, useMetric)} • ${metrics.durationMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = UnitConverter.formatFuelVolume(metrics.estimatedFuelLiters, useMetric),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
