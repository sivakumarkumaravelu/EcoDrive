package com.ecodrive.app.ui.screens.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.domain.model.Trip
import com.ecodrive.app.ui.components.EcoMap
import com.ecodrive.app.ui.components.EcoPolyline
import com.ecodrive.app.ui.theme.EcoDriveTheme
import com.ecodrive.app.util.UnitConverter
import com.google.android.gms.maps.model.LatLng
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
                useMetric = state.useMetric,
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
                useMetric = state.useMetric,
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
    useMetric: Boolean,
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
                    value = UnitConverter.formatDistance(distance, useMetric),
                    label = "Distance",
                    color = EcoDriveTheme.colors.gaugeBlue,
                )
                WeeklyStatItem(
                    value = UnitConverter.formatFuelVolume(fuel, useMetric),
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
    useMetric: Boolean,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    EcoMap(
                        modifier = Modifier.fillMaxSize(),
                        initialCenter = routePoints.first(),
                        initialZoom = 12f,
                        polylines = listOf(
                            EcoPolyline(
                                points = routePoints,
                                color = MaterialTheme.colorScheme.primary,
                                width = 6f
                            )
                        )
                    )
                    
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
                        value = UnitConverter.formatDistance(trip.distanceKm, useMetric),
                    )
                    TripDetailItem(
                        icon = Icons.Filled.Speed,
                        value = UnitConverter.formatSpeed(trip.averageSpeedKmh, useMetric),
                    )
                    TripDetailItem(
                        icon = Icons.Filled.LocalGasStation,
                        value = UnitConverter.formatFuelVolume(trip.fuelConsumedLiters, useMetric),
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

                    // Use the stored efficiency field (authoritative) rather than
                    // recalculating from raw fuel values which may be near-zero for older trips.
                    // Only display if the value is in a realistic range (0.5–40 L/100km).
                    val efficiency = trip.fuelEfficiencyLPer100Km
                    if (trip.distanceKm > 0 && efficiency in 0.5..40.0) {
                        Text(
                            text = UnitConverter.formatFuelEfficiency(efficiency, useMetric),
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
