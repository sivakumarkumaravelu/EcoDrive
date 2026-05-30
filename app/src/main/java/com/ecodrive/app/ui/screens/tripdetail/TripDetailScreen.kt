package com.ecodrive.app.ui.screens.tripdetail

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.ui.components.*
import com.ecodrive.app.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*

/**
 * Detailed view of a single trip with speed, acceleration,
 * fuel consumption charts, and a timeline of driving events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    onBack: () -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trip = state.trip

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = DarkOnSurface,
                    navigationIconContentColor = DarkOnSurface,
                    actionIconContentColor = DarkOnSurface,
                ),
                actions = {
                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()
                    
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val path = viewModel.exportTripDataToCsv(context.cacheDir)
                            if (path != null) {
                                val file = File(path)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Export Trip Data"))
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export CSV")
                    }
                }
            )
        },
        containerColor = DarkBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // ── Trip Summary Header ─────────────────────────────
            item {
                if (trip != null) {
                    TripSummaryHeader(trip = trip)
                } else if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = EcoGreen)
                    }
                }
            }

            // ── Eco Score Breakdown ─────────────────────────────
            if (trip != null) {
                item {
                    ScoreBreakdownCard(trip = trip)
                }
            }

            // ── Route Map ───────────────────────────────────────
            if (state.routePoints.isNotEmpty()) {
                item {
                    DetailChartCard(title = "Route", subtitle = "GPS path driven") {
                        val cameraPositionState = rememberCameraPositionState {
                            val firstPoint = state.routePoints.first()
                            position = CameraPosition.fromLatLngZoom(firstPoint, 14f)
                        }

                        LaunchedEffect(state.routePoints) {
                            if (state.routePoints.size > 1) {
                                val bounds = LatLngBounds.builder().apply {
                                    state.routePoints.forEach { include(it) }
                                }.build()
                                cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                            }
                        }

                        GoogleMap(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(zoomControlsEnabled = false, scrollGesturesEnabled = false)
                        ) {
                            Polyline(
                                points = state.routePoints,
                                color = EcoGreen,
                                width = 10f
                            )
                            
                            // Start and End Markers
                            Marker(
                                state = MarkerState(position = state.routePoints.first()),
                                title = "Start"
                            )
                            Marker(
                                state = MarkerState(position = state.routePoints.last()),
                                title = "End"
                            )
                        }
                    }
                }
            }

            // ── Speed Chart ─────────────────────────────────────
            if (state.speedPoints.isNotEmpty()) {
                item {
                    DetailChartCard(title = "Speed", subtitle = "km/h over time") {
                        LineChart(
                            points = state.speedPoints,
                            lineColor = GaugeBlue,
                            yAxisLabel = "km/h",
                            minY = 0f,
                        )
                    }
                }
            }

            // ── Acceleration Chart ──────────────────────────────
            if (state.accelPoints.isNotEmpty()) {
                item {
                    DetailChartCard(
                        title = "Acceleration / Braking",
                        subtitle = "m/s² (positive = accel, negative = brake)",
                    ) {
                        LineChart(
                            points = state.accelPoints,
                            lineColor = GaugeOrange,
                            yAxisLabel = "m/s²",
                            fillGradient = false,
                            showDots = false,
                        )
                    }
                }
            }

            // ── Fuel Consumption Chart ──────────────────────────
            if (state.fuelPoints.isNotEmpty()) {
                item {
                    DetailChartCard(
                        title = "Fuel Consumption",
                        subtitle = "Estimated L/100km",
                    ) {
                        LineChart(
                            points = state.fuelPoints,
                            lineColor = GaugeOrange,
                            yAxisLabel = "L/100km",
                            minY = 0f,
                        )
                    }
                }
            }

            // ── Altitude Profile ────────────────────────────────
            if (state.altitudePoints.size > 3) {
                item {
                    DetailChartCard(
                        title = "Elevation Profile",
                        subtitle = "Meters above sea level",
                    ) {
                        LineChart(
                            points = state.altitudePoints,
                            lineColor = EcoTeal,
                            yAxisLabel = "m",
                            fillGradient = true,
                        )
                    }
                }
            }

            // ── Event Timeline ──────────────────────────────────
            if (state.events.isNotEmpty()) {
                item {
                    Text(
                        text = "Driving Events (${state.events.size})",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = DarkOnSurface,
                    )
                }

                items(state.events.take(50)) { event ->
                    EventTimelineItem(event = event)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TripSummaryHeader(trip: com.ecodrive.app.domain.model.Trip) {
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    }
    val scoreColor = when (trip.ecoScore) {
        in 90..100 -> ScoreExcellent
        in 70..89 -> ScoreGood
        in 50..69 -> ScoreAverage
        else -> ScorePoor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Score circle
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(scoreColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${trip.ecoScore}",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = scoreColor,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trip.startTime
                    .atZone(ZoneId.systemDefault())
                    .format(dateFormatter),
                style = MaterialTheme.typography.titleSmall,
                color = DarkOnSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailStat("%.1f km".format(trip.distanceKm), "Distance")
                DetailStat(formatDuration(trip.durationSeconds), "Duration")
                DetailStat("%.0f km/h".format(trip.averageSpeedKmh), "Avg Speed")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailStat("%.2f L".format(trip.fuelConsumedLiters), "Fuel")
                DetailStat("%.0f km/h".format(trip.maxSpeedKmh), "Max Speed")
            }
        }
    }
}

@Composable
private fun ScoreBreakdownCard(trip: com.ecodrive.app.domain.model.Trip) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp),
    ) {
        Text(
            text = "Event Summary",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = DarkOnSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            EventCounter("Hard Brakes", trip.hardBrakeCount, ScorePoor)
            EventCounter("Hard Accels", trip.hardAccelCount, ScoreAverage)
            EventCounter("Sharp Turns", trip.sharpTurnCount, GaugePurple)
            EventCounter("Idle", "${trip.idleTimeSeconds / 60}m", AccentAmber)
        }

        // Fuel calibration info
        if (trip.startFuelPercent != null && trip.endFuelPercent != null) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DarkCardBorder)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    tint = EcoTeal,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fuel verified: %.0f%% → %.0f%% (calibration: %.3f)".format(
                        trip.startFuelPercent, trip.endFuelPercent, trip.calibrationFactor
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = EcoTeal,
                )
            }
        }
    }
}

@Composable
private fun EventCounter(label: String, count: Int, color: Color) {
    EventCounter(label, "$count", color)
}

@Composable
private fun EventCounter(label: String, value: String, color: Color) {
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

@Composable
private fun DetailChartCard(
    title: String,
    subtitle: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = DarkOnSurface,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun EventTimelineItem(event: com.ecodrive.app.domain.model.DrivingEvent) {
    val (icon, color) = when (event.type) {
        DrivingEventType.HARD_BRAKE -> Icons.Filled.Warning to ScorePoor
        DrivingEventType.HARD_ACCELERATION -> Icons.Filled.Speed to ScoreAverage
        DrivingEventType.SHARP_TURN -> Icons.Filled.TurnRight to GaugePurple
        DrivingEventType.EXCESSIVE_SPEED -> Icons.Filled.Speed to ErrorRed
        DrivingEventType.EXCESSIVE_IDLE -> Icons.Filled.HourglassEmpty to AccentAmber
        DrivingEventType.SPEED_INCONSISTENCY -> Icons.Filled.ShowChart to GaugeOrange
        DrivingEventType.ECO_DRIVING -> Icons.Filled.Eco to EcoGreen
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurface,
            )
            Text(
                text = "at %.0f km/h".format(event.speedAtEvent),
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant,
            )
        }
        Text(
            text = event.timestamp
                .atZone(ZoneId.systemDefault())
                .format(timeFormatter),
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurfaceVariant,
        )
    }
}

@Composable
private fun DetailStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = DarkOnSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurfaceVariant,
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
