package com.ecodrive.app.ui.screens.tripdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.ui.components.*
import com.ecodrive.app.ui.theme.*
import com.ecodrive.app.util.UnitConverter
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
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.ecodrive.app.ui.components.EcoMap
import com.ecodrive.app.ui.components.EcoMarker
import com.ecodrive.app.ui.components.EcoPolyline

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // ── Route Map ───────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    when {
                        state.isLoading -> {
                            // Skeleton placeholder while data points load from Room
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        state.routePoints.isNotEmpty() -> {
                            val markers = mutableListOf(
                                EcoMarker(state.routePoints.first(), "Start"),
                                EcoMarker(state.routePoints.last(), "End")
                            )
                            state.events
                                .filter { it.latitude != 0.0 && it.longitude != 0.0 }
                                .forEach { event ->
                                    markers.add(
                                        EcoMarker(
                                            LatLng(event.latitude, event.longitude),
                                            event.type.name.replace("_", " ")
                                        )
                                    )
                                }

                            EcoMap(
                                modifier = Modifier.fillMaxSize(),
                                initialCenter = state.routePoints.first(),
                                initialZoom = 13f,
                                markers = markers,
                                polylines = listOf(
                                    EcoPolyline(
                                        points = state.routePoints,
                                        color = MaterialTheme.colorScheme.primary,
                                        width = 12f
                                    )
                                )
                            )
                        }

                        else -> {
                            // Trip loaded but no GPS coordinates were recorded
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No GPS route data recorded for this trip",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── AI Trip Coach Insight ───────────────────────────
            item {
                AiInsightCard(
                    insight = state.aiInsight,
                    isLoading = state.isAiLoading,
                    error = state.aiError
                )
            }

            // ── Trip Summary Header ─────────────────────────────
            item {
                if (trip != null) {
                    TripSummaryHeader(
                        trip = trip,
                        useMetric = state.useMetric
                    )
                } else if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ── Eco Score Breakdown ─────────────────────────────
            if (trip != null) {
                item {
                    ScoreBreakdownCard(trip = trip)
                }
            }

            // ── Speed Chart ─────────────────────────────────────
            if (state.speedPoints.isNotEmpty()) {
                item {
                    val unit = if (state.useMetric) "km/h" else "mph"
                    DetailChartCard(title = "Speed", subtitle = "$unit over time") {
                        LineChart(
                            points = if (state.useMetric) state.speedPoints else state.speedPoints.map {
                                it.copy(y = UnitConverter.kmhToMph(it.y.toDouble()).toFloat())
                            },
                            lineColor = EcoDriveTheme.colors.gaugeBlue,
                            yAxisLabel = unit,
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
                            lineColor = EcoDriveTheme.colors.gaugeOrange,
                            yAxisLabel = "m/s²",
                            fillGradient = false,
                            showDots = false,
                        )
                    }
                }
            }

            // ── Detected Anomalies ──────────────────────────────
            if (state.anomalies.isNotEmpty()) {
                item {
                    Text(
                        text = "Detected Diagnostics",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(state.anomalies) { anomaly ->
                    AnomalyCard(anomaly)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // ── Fuel Consumption Chart ──────────────────────────
            if (state.fuelPoints.isNotEmpty()) {
                item {
                    val unit = if (state.useMetric) "L/100km" else "mpg"
                    DetailChartCard(
                        title = "Fuel Consumption",
                        subtitle = "Estimated $unit",
                    ) {
                        LineChart(
                            points = if (state.useMetric) state.fuelPoints else state.fuelPoints.map {
                                it.copy(y = UnitConverter.l100kmToMpg(it.y.toDouble()).toFloat())
                            },
                            lineColor = EcoDriveTheme.colors.gaugeOrange,
                            yAxisLabel = unit,
                            minY = 0f,
                        )
                    }
                }
            }

            // ── Altitude Profile ────────────────────────────────
            if (state.altitudePoints.size > 3) {
                item {
                    val unit = if (state.useMetric) "m" else "ft"
                    val subtitle = if (state.useMetric) "Meters above sea level" else "Feet above sea level"
                    val points = if (state.useMetric) {
                        state.altitudePoints
                    } else {
                        state.altitudePoints.map { it.copy(y = it.y * 3.28084f) }
                    }
                    DetailChartCard(
                        title = "Elevation Profile",
                        subtitle = subtitle,
                    ) {
                        LineChart(
                            points = points,
                            lineColor = MaterialTheme.colorScheme.secondary,
                            yAxisLabel = unit,
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
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                items(state.events.take(50)) { event ->
                    EventTimelineItem(event = event, useMetric = state.useMetric)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AiInsightCard(
    insight: String?,
    isLoading: Boolean,
    error: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EcoDriveTheme.colors.cardBackground)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AI Trip Coach",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Analyzing your driving style...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (insight != null) {
            val sections = remember(insight) {
                insight.split(Regex("(?=\\d\\.\\s|Summary:|Key Moments:|Improvement Plan:)"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }

            if (sections.size > 1) {
                sections.forEach { section ->
                    val isSummary = section.startsWith("Summary") || section.startsWith("1.")
                    val isKeyMoments = section.startsWith("Key Moments") || section.startsWith("2.")
                    val isImprovement = section.startsWith("Improvement Plan") || section.startsWith("3.")

                    val (headerText, bodyText) = if (section.contains(":")) {
                        section.substringBefore(":").replace(Regex("\\d\\.\\s"), "").trim() to 
                        section.substringAfter(":").trim()
                    } else if (section.matches(Regex("\\d\\.\\s.*"))) {
                        section.substringBefore(" ").trim() to section.substringAfter(" ").trim()
                    } else {
                        "" to section
                    }

                    if (headerText.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        ) {
                            val icon = when {
                                isSummary -> Icons.Filled.HistoryEdu
                                isKeyMoments -> Icons.Filled.Lightbulb
                                isImprovement -> Icons.AutoMirrored.Filled.TrendingUp
                                else -> Icons.Filled.Info
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = headerText.uppercase(),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    if (bodyText.isNotBlank()) {
                        Text(
                            text = bodyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Text(
                text = "Waiting for trip analysis...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TripSummaryHeader(
    trip: com.ecodrive.app.domain.model.Trip,
    useMetric: Boolean,
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    }
    val scoreColor = when (trip.ecoScore) {
        in 90..100 -> EcoDriveTheme.colors.scoreExcellent
        in 70..89 -> EcoDriveTheme.colors.scoreGood
        in 50..69 -> EcoDriveTheme.colors.scoreAverage
        else -> EcoDriveTheme.colors.scorePoor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EcoDriveTheme.colors.cardBackground)
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
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailStat(UnitConverter.formatDistance(trip.distanceKm, useMetric), "Distance")
                DetailStat(formatDuration(trip.durationSeconds), "Duration")
                DetailStat(UnitConverter.formatSpeed(trip.averageSpeedKmh, useMetric), "Avg Speed")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailStat(UnitConverter.formatFuelVolume(trip.fuelConsumedLiters, useMetric), "Fuel")
                DetailStat(UnitConverter.formatSpeed(trip.maxSpeedKmh, useMetric), "Max Speed")
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
            .background(EcoDriveTheme.colors.cardBackground)
            .padding(16.dp),
    ) {
        Text(
            text = "Event Summary",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            EventCounter("Hard Brakes", trip.hardBrakeCount, EcoDriveTheme.colors.scorePoor)
            EventCounter("Hard Accels", trip.hardAccelCount, EcoDriveTheme.colors.scoreAverage)
            EventCounter("Sharp Turns", trip.sharpTurnCount, EcoDriveTheme.colors.gaugePurple)
            EventCounter("Idle", "${trip.idleTimeSeconds / 60}m", EcoDriveTheme.colors.scoreAverage)
        }

        // Fuel calibration info
        if (trip.startFuelPercent != null && trip.endFuelPercent != null) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fuel verified: %.0f%% → %.0f%% (calibration: %.3f)".format(
                        trip.startFuelPercent, trip.endFuelPercent, trip.calibrationFactor
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .background(EcoDriveTheme.colors.cardBackground)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun EventTimelineItem(
    event: com.ecodrive.app.domain.model.DrivingEvent,
    useMetric: Boolean,
) {
    val (icon, color) = when (event.type) {
        DrivingEventType.HARD_BRAKE -> Icons.Filled.Warning to EcoDriveTheme.colors.scorePoor
        DrivingEventType.HARD_ACCELERATION -> Icons.Filled.Speed to EcoDriveTheme.colors.scoreAverage
        DrivingEventType.SHARP_TURN -> Icons.Filled.TurnRight to EcoDriveTheme.colors.gaugePurple
        DrivingEventType.EXCESSIVE_SPEED -> Icons.Filled.Speed to MaterialTheme.colorScheme.error
        DrivingEventType.EXCESSIVE_IDLE -> Icons.Filled.HourglassEmpty to EcoDriveTheme.colors.scoreAverage
        DrivingEventType.SPEED_INCONSISTENCY -> Icons.Filled.ShowChart to EcoDriveTheme.colors.gaugeOrange
        DrivingEventType.ECO_DRIVING -> Icons.Filled.Eco to EcoDriveTheme.colors.scoreExcellent
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(EcoDriveTheme.colors.cardBackground)
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
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "at ${com.ecodrive.app.util.UnitConverter.formatSpeed(event.speedAtEvent, useMetric)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = event.timestamp
                .atZone(ZoneId.systemDefault())
                .format(timeFormatter),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun AnomalyCard(anomaly: com.ecodrive.app.domain.model.VehicleAnomaly) {
    val (icon, color) = when (anomaly.severity) {
        com.ecodrive.app.domain.model.AnomalySeverity.HIGH -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        com.ecodrive.app.domain.model.AnomalySeverity.MEDIUM -> Icons.Filled.Warning to EcoDriveTheme.colors.gaugeOrange
        com.ecodrive.app.domain.model.AnomalySeverity.LOW -> Icons.Filled.Info to MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = anomaly.type.name.replace("_", " "),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = anomaly.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (!anomaly.aiDiagnosis.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = anomaly.aiDiagnosis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
