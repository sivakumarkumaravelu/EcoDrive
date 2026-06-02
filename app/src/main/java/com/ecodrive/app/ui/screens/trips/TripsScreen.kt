package com.ecodrive.app.ui.screens.trips

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.domain.model.EcoRating
import com.ecodrive.app.domain.model.Trip
import com.ecodrive.app.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*

/**
 * Trip History screen showing past trips with eco scores,
 * statistics, and a weekly summary.
 */
@Composable
fun TripsScreen(
    onTripClick: (Long) -> Unit = {},
    viewModel: TripsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header ──────────────────────────────────────────────
        item {
            Text(
                text = "Trip History",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        // ── Weekly Summary Card ─────────────────────────────────
        item {
            WeeklySummaryCard(
                avgScore = state.weeklyAvgScore,
                distance = state.weeklyDistance,
                fuel = state.weeklyFuel,
                totalTrips = state.totalTrips,
            )
        }

        // ── Trip List ───────────────────────────────────────────
        if (state.trips.isEmpty() && !state.isLoading) {
            item {
                EmptyTripsMessage()
            }
        }

        items(
            items = state.trips,
            key = { it.id },
        ) { trip ->
            TripCard(
                trip = trip,
                routePoints = state.tripRoutes[trip.id] ?: emptyList(),
                onClick = { onTripClick(trip.id) },
                onDelete = { viewModel.deleteTrip(trip.id) },
            )
        }

        // Loading indicator
        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Bottom spacer
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun WeeklySummaryCard(
    avgScore: Int,
    distance: Double,
    fuel: Double,
    totalTrips: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                    ),
                )
            )
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "This Week",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                WeeklyStatItem(
                    value = "$avgScore",
                    label = "Avg Score",
                    color = when {
                        avgScore >= 70 -> EcoDriveTheme.colors.scoreGood
                        avgScore >= 50 -> EcoDriveTheme.colors.scoreAverage
                        else -> EcoDriveTheme.colors.scorePoor
                    },
                )
                WeeklyStatItem(
                    value = "%.1f km".format(distance),
                    label = "Distance",
                    color = EcoDriveTheme.colors.gaugeBlue,
                )
                WeeklyStatItem(
                    value = "%.1f L".format(fuel),
                    label = "Fuel Used",
                    color = EcoDriveTheme.colors.gaugeOrange,
                )
                WeeklyStatItem(
                    value = "$totalTrips",
                    label = "Trips",
                    color = EcoDriveTheme.colors.gaugePurple,
                )
            }
        }
    }
}

@Composable
private fun WeeklyStatItem(
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripCard(
    trip: Trip,
    routePoints: List<LatLng>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val scoreColor = when (trip.ecoScore) {
        in 90..100 -> EcoDriveTheme.colors.scoreExcellent
        in 70..89 -> EcoDriveTheme.colors.scoreGood
        in 50..69 -> EcoDriveTheme.colors.scoreAverage
        else -> EcoDriveTheme.colors.scorePoor
    }

    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Trip?") },
            text = { Text("This will permanently delete this trip and its data.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EcoDriveTheme.colors.cardBackground),
    ) {
        Column {
            // ── Route Map Preview ───────────────────────────────
            if (routePoints.isNotEmpty()) {
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(routePoints.first(), 12f)
                }
                
                LaunchedEffect(routePoints) {
                    if (routePoints.size > 1) {
                        val bounds = LatLngBounds.builder().apply {
                            routePoints.forEach { include(it) }
                        }.build()
                        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 50))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = false,
                            tiltGesturesEnabled = false,
                            rotationGesturesEnabled = false,
                            myLocationButtonEnabled = false,
                            compassEnabled = false
                        ),
                        properties = MapProperties(
                            isMyLocationEnabled = false
                        )
                    ) {
                        Polyline(
                            points = routePoints,
                            color = MaterialTheme.colorScheme.primary,
                            width = 6f
                        )
                    }
                    
                    // Overlay to capture clicks and prevent map interaction
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                            .clickable { onClick() }
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // ── Header Row: Date + Score ────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = trip.startTime
                                .atZone(ZoneId.systemDefault())
                                .format(dateFormatter),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = formatDuration(trip.durationSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Eco Score Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(scoreColor.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${trip.ecoScore}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = scoreColor,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Stats Row ───────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TripDetailItem(
                        icon = Icons.Filled.Straighten,
                        value = "%.1f km".format(trip.distanceKm),
                    )
                    TripDetailItem(
                        icon = Icons.Filled.Speed,
                        value = "%.0f km/h avg".format(trip.averageSpeedKmh),
                    )
                    TripDetailItem(
                        icon = Icons.Filled.LocalGasStation,
                        value = "%.2f L".format(trip.fuelConsumedLiters),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Events Row ──────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    EventBadge(
                        label = "Brakes",
                        count = trip.hardBrakeCount,
                        color = if (trip.hardBrakeCount > 3) EcoDriveTheme.colors.scorePoor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EventBadge(
                        label = "Accels",
                        count = trip.hardAccelCount,
                        color = if (trip.hardAccelCount > 3) EcoDriveTheme.colors.scoreAverage else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EventBadge(
                        label = "Turns",
                        count = trip.sharpTurnCount,
                        color = if (trip.sharpTurnCount > 3) EcoDriveTheme.colors.scoreAverage else MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Fuel efficiency
                    if (trip.distanceKm > 0) {
                        val efficiency = (trip.fuelConsumedLiters / trip.distanceKm) * 100
                        Text(
                            text = "%.1f L/100km".format(efficiency),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                efficiency < 7.0 -> EcoDriveTheme.colors.scoreExcellent
                                efficiency < 9.0 -> EcoDriveTheme.colors.scoreGood
                                efficiency < 12.0 -> EcoDriveTheme.colors.scoreAverage
                                else -> EcoDriveTheme.colors.scorePoor
                            },
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete trip",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // ── Fuel Calibration Indicator ──────────────────────
                if (trip.startFuelPercent != null && trip.endFuelPercent != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Fuel verified via Vehicle API (%.0f%% → %.0f%%)".format(
                                trip.startFuelPercent, trip.endFuelPercent
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripDetailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EventBadge(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyTripsMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Route,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No trips yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Start recording from the Dashboard to see your driving history here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes} min"
        else -> "${seconds}s"
    }
}
