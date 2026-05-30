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
                color = DarkOnSurface,
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
                    CircularProgressIndicator(color = EcoGreen)
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
                        EcoGreen.copy(alpha = 0.15f),
                        EcoTeal.copy(alpha = 0.10f),
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
                    tint = EcoGreen,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "This Week",
                    style = MaterialTheme.typography.titleMedium,
                    color = EcoGreen,
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
                        avgScore >= 70 -> ScoreGood
                        avgScore >= 50 -> ScoreAverage
                        else -> ScorePoor
                    },
                )
                WeeklyStatItem(
                    value = "%.1f km".format(distance),
                    label = "Distance",
                    color = GaugeBlue,
                )
                WeeklyStatItem(
                    value = "%.1f L".format(fuel),
                    label = "Fuel Used",
                    color = GaugeOrange,
                )
                WeeklyStatItem(
                    value = "$totalTrips",
                    label = "Trips",
                    color = GaugePurple,
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
            color = DarkOnSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripCard(
    trip: Trip,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val scoreColor = when (trip.ecoScore) {
        in 90..100 -> ScoreExcellent
        in 70..89 -> ScoreGood
        in 50..69 -> ScoreAverage
        else -> ScorePoor
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
                    Text("Delete", color = ErrorRed)
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
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
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
                        color = DarkOnSurface,
                    )
                    Text(
                        text = formatDuration(trip.durationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant,
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
                    color = if (trip.hardBrakeCount > 3) ScorePoor else DarkOnSurfaceVariant,
                )
                EventBadge(
                    label = "Accels",
                    count = trip.hardAccelCount,
                    color = if (trip.hardAccelCount > 3) ScoreAverage else DarkOnSurfaceVariant,
                )
                EventBadge(
                    label = "Turns",
                    count = trip.sharpTurnCount,
                    color = if (trip.sharpTurnCount > 3) ScoreAverage else DarkOnSurfaceVariant,
                )

                // Fuel efficiency
                if (trip.distanceKm > 0) {
                    val efficiency = (trip.fuelConsumedLiters / trip.distanceKm) * 100
                    Text(
                        text = "%.1f L/100km".format(efficiency),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            efficiency < 7.0 -> ScoreExcellent
                            efficiency < 9.0 -> ScoreGood
                            efficiency < 12.0 -> ScoreAverage
                            else -> ScorePoor
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
                        tint = DarkOnSurfaceVariant,
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
                        tint = EcoTeal,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Fuel verified via Toyota API (%.0f%% → %.0f%%)".format(
                            trip.startFuelPercent, trip.endFuelPercent
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = EcoTeal,
                    )
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
            tint = DarkOnSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurface,
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
            color = DarkOnSurfaceVariant,
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
            tint = DarkOnSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No trips yet",
            style = MaterialTheme.typography.titleMedium,
            color = DarkOnSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Start recording from the Dashboard to see your driving history here.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant.copy(alpha = 0.7f),
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
