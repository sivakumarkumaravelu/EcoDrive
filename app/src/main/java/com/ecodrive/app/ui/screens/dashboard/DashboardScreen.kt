package com.ecodrive.app.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.ecodrive.app.sensor.SensorDataManager
import com.ecodrive.app.ui.components.*
import com.ecodrive.app.ui.theme.*
import kotlin.math.abs

/**
 * Main Dashboard screen displaying real-time driving metrics
 * from phone sensors, eco score, and contextual driving tips.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Connection Status Bar ───────────────────────────────
        ConnectionStatusBar(
            isConnected = state.sensorState == SensorDataManager.CollectionState.COLLECTING,
            statusText = when {
                state.isRecording -> state.dataSource
                state.sensorState == SensorDataManager.CollectionState.ERROR ->
                    state.errorMessage ?: "Sensor Error"
                else -> "Ready to record"
            },
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── App Title ───────────────────────────────────────────
        Text(
            text = "EcoDrive",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Active Vehicle",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Start / Stop Button ─────────────────────────────────
        RecordButton(
            isRecording = state.isRecording,
            onStart = viewModel::startRecording,
            onStop = viewModel::stopRecording,
        )

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "SPEED",
                value = "%.0f".format(state.metrics.speedKmh),
                unit = "km/h",
                accentColor = EcoDriveTheme.colors.gaugeBlue,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(EcoDriveTheme.colors.cardBackground),
            )
            MetricCard(
                label = "FUEL EST.",
                value = "%.1f".format(state.metrics.fuelConsumptionLPer100Km),
                unit = "L/100km",
                accentColor = EcoDriveTheme.colors.gaugeOrange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(EcoDriveTheme.colors.cardBackground),
            )
            MetricCard(
                label = "G-FORCE",
                value = "%.1f".format(abs(state.metrics.longitudinalAccelMps2) / 9.81),
                unit = "g",
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(EcoDriveTheme.colors.cardBackground),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Live Metric Cards — Row 2 ───────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "LATERAL",
                value = "%.1f".format(abs(state.metrics.lateralAccelMps2)),
                unit = "m/s²",
                accentColor = EcoDriveTheme.colors.gaugePurple,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(EcoDriveTheme.colors.cardBackground),
            )
            MetricCard(
                label = "GRADE",
                value = "%.1f".format(state.metrics.roadGradePercent),
                unit = "%",
                accentColor = EcoDriveTheme.colors.scoreAverage,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(EcoDriveTheme.colors.cardBackground),
            )
            MetricCard(
                label = "FUEL TANK",
                value = state.metrics.fuelTankPercent?.let { "%.0f".format(it) } ?: "—",
                unit = if (state.metrics.fuelTankPercent != null) "%" else "N/A",
                accentColor = EcoDriveTheme.colors.gaugeBlue,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(EcoDriveTheme.colors.cardBackground),
            )
        }

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
                value = "%.1f km".format(state.tripDistanceKm),
            )
            TripStatItem(
                icon = Icons.Filled.LocalGasStation,
                label = "Fuel Est.",
                value = "%.2f L".format(state.fuelConsumedEstimate),
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
            tint = DarkOnSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
