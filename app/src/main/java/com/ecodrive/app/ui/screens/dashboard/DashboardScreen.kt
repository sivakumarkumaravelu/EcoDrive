package com.ecodrive.app.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.data.sensor.SensorDataManager
import com.ecodrive.app.ui.components.*
import com.ecodrive.app.ui.theme.*
import com.ecodrive.app.util.UnitConverter
import kotlin.math.abs

/**
 * Main Dashboard screen displaying real-time driving metrics
 * from phone sensors, eco score, and contextual driving tips.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onPlanRoute: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Performance Optimization: Use derivedStateOf for values that don't need to update every frame
    val connectionStatusText by remember {
        derivedStateOf {
            when {
                state.isRecording -> state.dataSource
                state.sensorState == SensorDataManager.CollectionState.ERROR ->
                    state.errorMessage ?: "Sensor Error"
                else -> "Ready to record"
            }
        }
    }
    
    val isConnected by remember {
        derivedStateOf {
            state.sensorState == SensorDataManager.CollectionState.COLLECTING
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Connection Status Bar ───────────────────────────────
        ConnectionStatusBar(
            isConnected = isConnected,
            statusText = connectionStatusText,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── App Title ───────────────────────────────────────────
        Text(
            text = "EcoDrive",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = state.vehicleName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Start / Stop Button ─────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecordButton(
                isRecording = state.isRecording,
                onStart = viewModel::startRecording,
                onStop = viewModel::stopRecording,
            )
            
            if (!state.isRecording) {
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = onPlanRoute,
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Route,
                        contentDescription = "Plan Route",
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Plan Route",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Eco Score Gauge ─────────────────────────────────────
        EcoScoreGauge(
            score = state.ecoScore.overall,
            modifier = Modifier.padding(vertical = 8.dp),
            size = 200.dp,
        )

        // ── Score Rating Label ──────────────────────────────────
        AnimatedContent(
            targetState = state.ecoScore.rating,
            label = "ratingLabel",
        ) { rating ->
            Text(
                text = "${rating.emoji} ${rating.label}",
                style = MaterialTheme.typography.titleMedium,
                color = when (rating) {
                    com.ecodrive.app.domain.model.EcoRating.EXCELLENT -> EcoDriveTheme.colors.scoreExcellent
                    com.ecodrive.app.domain.model.EcoRating.GOOD -> EcoDriveTheme.colors.scoreGood
                    com.ecodrive.app.domain.model.EcoRating.AVERAGE -> EcoDriveTheme.colors.scoreAverage
                    com.ecodrive.app.domain.model.EcoRating.POOR -> EcoDriveTheme.colors.scorePoor
                },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Live Metric Cards — Row 1 ───────────────────────────
        MetricsRow1(
            metrics = state.metrics,
            useMetric = state.useMetric,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Live Metric Cards — Row 2 ───────────────────────────
        MetricsRow2(
            metrics = state.metrics,
            useMetric = state.useMetric,
            durationSeconds = state.tripDurationSeconds,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Trip Stats Bar ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(EcoDriveTheme.colors.cardBackground)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TripStatItem(
                icon = Icons.Filled.Timer,
                label = "Duration",
                value = formatDuration(state.tripDurationSeconds),
            )
            
            TripStatItem(
                icon = Icons.Filled.Straighten,
                label = "Distance",
                value = UnitConverter.formatDistance(state.tripDistanceKm, state.useMetric),
            )
            
            TripStatItem(
                icon = Icons.Filled.LocalGasStation,
                label = "Fuel Est.",
                value = UnitConverter.formatFuelVolume(state.fuelConsumedEstimate, state.useMetric),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Event Counters ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(EcoDriveTheme.colors.cardBackground)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TripStatItem(
                icon = Icons.Filled.Warning,
                label = "Hard Brakes",
                value = "${state.hardBrakeCount}",
            )
            TripStatItem(
                icon = Icons.Filled.Speed,
                label = "Hard Accels",
                value = "${state.hardAccelCount}",
            )
            TripStatItem(
                icon = Icons.Filled.TurnRight,
                label = "Sharp Turns",
                value = "${state.sharpTurnCount}",
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Driving Tip Banner ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        ),
                    )
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Eco,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = state.drivingTip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Sub-components ──────────────────────────────────────────────

/**
 * Extracted composables for metrics to limit recomposition scope.
 */
@Composable
private fun MetricsRow1(
    metrics: com.ecodrive.app.domain.model.DrivingMetrics,
    useMetric: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val speed = if (useMetric) metrics.speedKmh else com.ecodrive.app.util.UnitConverter.kmhToMph(metrics.speedKmh)
        val speedUnit = if (useMetric) "km/h" else "mph"
        
        MetricCard(
            label = "SPEED",
            value = "%.0f".format(speed),
            unit = speedUnit,
            accentColor = EcoDriveTheme.colors.gaugeBlue,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(EcoDriveTheme.colors.cardBackground),
        )
        
        val fuelVal = if (useMetric) metrics.fuelConsumptionLPer100Km else com.ecodrive.app.util.UnitConverter.l100kmToMpg(metrics.fuelConsumptionLPer100Km)
        val fuelUnit = if (useMetric) "L/100km" else "mpg"
        
        MetricCard(
            label = "FUEL EST.",
            value = if (metrics.fuelConsumptionLPer100Km > 0) "%.1f".format(fuelVal) else "—",
            unit = fuelUnit,
            accentColor = EcoDriveTheme.colors.gaugeOrange,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(EcoDriveTheme.colors.cardBackground),
        )
        MetricCard(
            label = "G-FORCE",
            value = "%.1f".format(kotlin.math.abs(metrics.longitudinalAccelMps2) / 9.81),
            unit = "g",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(EcoDriveTheme.colors.cardBackground),
        )
    }
}

@Composable
private fun MetricsRow2(
    metrics: com.ecodrive.app.domain.model.DrivingMetrics,
    useMetric: Boolean,
    durationSeconds: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCard(
            label = "TIME",
            value = formatDuration(durationSeconds),
            unit = "",
            accentColor = EcoDriveTheme.colors.gaugePurple,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(EcoDriveTheme.colors.cardBackground),
        )
        MetricCard(
            label = "ALTITUDE",
            value = "%.0f".format(if (useMetric) metrics.altitudeM else metrics.altitudeM * 3.28084),
            unit = if (useMetric) "m" else "ft",
            accentColor = EcoDriveTheme.colors.gaugeGreen,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(EcoDriveTheme.colors.cardBackground),
        )
        MetricCard(
            label = "GRADE",
            value = "%.1f".format(metrics.roadGradePercent),
            unit = "%",
            accentColor = EcoDriveTheme.colors.gaugeOrange,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(EcoDriveTheme.colors.cardBackground),
        )
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Button(
        onClick = { if (isRecording) onStop() else onStart() },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRecording) ErrorRed else EcoGreen,
        ),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (isRecording) "Stop" else "Start",
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isRecording) "Stop Recording" else "Start Recording",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun TripStatItem(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
