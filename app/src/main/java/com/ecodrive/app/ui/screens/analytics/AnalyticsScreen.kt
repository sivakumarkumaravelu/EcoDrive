package com.ecodrive.app.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.ecodrive.app.ui.components.*
import com.ecodrive.app.ui.theme.*
import com.ecodrive.app.util.UnitConverter

/**
 * Analytics dashboard with trend charts, behavior breakdown,
 * and aggregated driving statistics.
 */
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Header ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Analytics",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // ── Time Range Selector ─────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnalyticsViewModel.TimeRange.entries.forEach { range ->
                FilterChip(
                    selected = state.selectedRange == range,
                    onClick = { viewModel.selectTimeRange(range) },
                    label = {
                        Text(range.label, style = MaterialTheme.typography.labelMedium)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        if (state.totalTrips == 0 && !state.isLoading) {
            EmptyAnalytics()
            return@Column
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        // ── Summary Cards ───────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(
                label = "Trips",
                value = "${state.totalTrips}",
                icon = Icons.Filled.Route,
                color = EcoDriveTheme.colors.gaugeBlue,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "Avg Score",
                value = "${state.avgEcoScore}",
                icon = Icons.Filled.Stars,
                color = when {
                    state.avgEcoScore >= 70 -> EcoDriveTheme.colors.scoreGood
                    state.avgEcoScore >= 50 -> EcoDriveTheme.colors.scoreAverage
                    else -> EcoDriveTheme.colors.scorePoor
                },
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "Distance",
                value = UnitConverter.formatDistance(state.totalDistanceKm, state.useMetric),
                icon = Icons.Filled.Straighten,
                color = EcoDriveTheme.colors.gaugePurple,
                modifier = Modifier.weight(1f),
            )
        }

        // ── AI Analytics Narrative ──────────────────────────────
        AiNarrativeCard(
            narrative = state.aiSummary,
            isLoading = state.isAiLoading
        )

        // ── Fuel Savings Highlight ──────────────────────────────
        if (state.fuelSavedEstimate > 0.5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(EcoDriveTheme.colors.scoreExcellent.copy(alpha = 0.1f))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Eco,
                        contentDescription = null,
                        tint = EcoDriveTheme.colors.scoreExcellent,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "🎉 Estimated %.1f L saved!".format(state.fuelSavedEstimate),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = EcoDriveTheme.colors.scoreExcellent,
                        )
                        Text(
                            text = "Compared to your vehicle's rated efficiency",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Eco Score Trend ──────────────────────────────────────
        ChartCard(title = "Eco Score Trend") {
            LineChart(
                points = state.ecoScoreTrend,
                lineColor = EcoDriveTheme.colors.scoreExcellent,
                yAxisLabel = "Score",
                xAxisLabels = state.ecoScoreTrend.map { it.label },
                minY = 0f,
                maxY = 100f,
            )
        }

        // ── Fuel Efficiency Trend ───────────────────────────────
        val fuelEfficiencyUnit = if (state.useMetric) "L/100km" else "mpg"
        ChartCard(title = "Fuel Efficiency") {
            LineChart(
                points = if (state.useMetric) state.fuelEfficiencyTrend else state.fuelEfficiencyTrend.map {
                    it.copy(y = UnitConverter.l100kmToMpg(it.y.toDouble()).toFloat())
                },
                lineColor = EcoDriveTheme.colors.gaugeOrange,
                yAxisLabel = fuelEfficiencyUnit,
                xAxisLabels = state.fuelEfficiencyTrend.map { it.label },
                fillGradient = true,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Average: ${UnitConverter.formatFuelEfficiency(state.avgFuelEfficiency, state.useMetric)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Weekly Eco Score Bar Chart ───────────────────────────
        if (state.weeklyScores.isNotEmpty()) {
            val goodColor = EcoDriveTheme.colors.scoreGood
            val avgColor = EcoDriveTheme.colors.scoreAverage
            val poorColor = EcoDriveTheme.colors.scorePoor
            ChartCard(title = "Weekly Eco Scores") {
                BarChart(
                    values = state.weeklyScores,
                    barColor = { score ->
                        when {
                            score >= 70 -> goodColor
                            score >= 50 -> avgColor
                            else -> poorColor
                        }
                    },
                    maxValue = 100f,
                    yAxisLabel = "Avg Score",
                )
            }
        }

        // ── Weekly Distance Bar Chart ───────────────────────────
        if (state.weeklyDistances.isNotEmpty()) {
            val gaugeBlue = EcoDriveTheme.colors.gaugeBlue
            val distanceUnit = if (state.useMetric) "km" else "mi"
            ChartCard(title = "Weekly Distance") {
                BarChart(
                    values = if (state.useMetric) state.weeklyDistances else state.weeklyDistances.map {
                        it.first to UnitConverter.kmToMiles(it.second.toDouble()).toFloat()
                    },
                    barColor = { gaugeBlue },
                    yAxisLabel = distanceUnit,
                )
            }
        }

        // ── Driving Behavior Breakdown ──────────────────────────
        ChartCard(title = "Driving Behavior") {
            val brakes = state.totalHardBrakes.toFloat()
            val accels = state.totalHardAccels.toFloat()
            val turns = state.totalSharpTurns.toFloat()
            val total = brakes + accels + turns

            if (total > 0) {
                BreakdownBar(
                    segments = listOf(
                        Triple("Braking", brakes, EcoDriveTheme.colors.scorePoor),
                        Triple("Accel", accels, EcoDriveTheme.colors.scoreAverage),
                        Triple("Cornering", turns, EcoDriveTheme.colors.gaugePurple),
                    ),
                    height = 20.dp,
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Legend with counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BehaviorStat("Hard Brakes", state.totalHardBrakes, EcoDriveTheme.colors.scorePoor)
                BehaviorStat("Hard Accels", state.totalHardAccels, EcoDriveTheme.colors.scoreAverage)
                BehaviorStat("Sharp Turns", state.totalSharpTurns, EcoDriveTheme.colors.gaugePurple)
                BehaviorStat("Idle (min)", state.totalIdleMinutes.toInt(), EcoDriveTheme.colors.scoreAverage)
            }
        }

        // ── Best & Worst Trips ──────────────────────────────────
        ChartCard(title = "Best vs Worst") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.bestTrip?.let { trip ->
                    ComparisonItem(
                        label = "🏆 Best Trip",
                        score = trip.ecoScore,
                        distance = if (state.useMetric) trip.distanceKm else UnitConverter.kmToMiles(trip.distanceKm),
                        efficiency = if (state.useMetric) trip.fuelEfficiencyLPer100Km else UnitConverter.l100kmToMpg(trip.fuelEfficiencyLPer100Km),
                        color = EcoDriveTheme.colors.scoreExcellent,
                        useMetric = state.useMetric,
                        modifier = Modifier.weight(1f),
                    )
                }
                state.worstTrip?.let { trip ->
                    ComparisonItem(
                        label = "📉 Worst Trip",
                        score = trip.ecoScore,
                        distance = if (state.useMetric) trip.distanceKm else UnitConverter.kmToMiles(trip.distanceKm),
                        efficiency = if (state.useMetric) trip.fuelEfficiencyLPer100Km else UnitConverter.l100kmToMpg(trip.fuelEfficiencyLPer100Km),
                        color = EcoDriveTheme.colors.scorePoor,
                        useMetric = state.useMetric,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── Fuel Summary ────────────────────────────────────────
        ChartCard(title = "Fuel Summary") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FuelStat(
                    label = "Total Used",
                    value = UnitConverter.formatFuelVolume(state.totalFuelLiters, state.useMetric),
                    color = EcoDriveTheme.colors.gaugeOrange
                )
                FuelStat(
                    label = if (state.useMetric) "Avg L/100km" else "Avg MPG",
                    value = if (state.useMetric) "%.1f".format(state.avgFuelEfficiency) else "%.1f".format(UnitConverter.l100kmToMpg(state.avgFuelEfficiency)),
                    color = if (state.avgFuelEfficiency < 6.4) EcoDriveTheme.colors.scoreGood else EcoDriveTheme.colors.scoreAverage,
                )
                FuelStat(
                    label = "Savings Est.",
                    value = UnitConverter.formatFuelVolume(state.fuelSavedEstimate, state.useMetric),
                    color = EcoDriveTheme.colors.scoreExcellent
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Sub-components ──────────────────────────────────────────────

@Composable
private fun AiNarrativeCard(
    narrative: String?,
    isLoading: Boolean
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
                text = "AI Trends Narrative",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (narrative != null) {
            Text(
                text = narrative,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = "Generate insights to see your driving story.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(EcoDriveTheme.colors.cardBackground)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp),
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

@Composable
private fun ChartCard(
    title: String,
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
            modifier = Modifier.padding(bottom = 12.dp),
        )
        content()
    }
}

@Composable
private fun BehaviorStat(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
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
private fun ComparisonItem(
    label: String,
    score: Int,
    distance: Double,
    efficiency: Double,
    color: Color,
    useMetric: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Score: $score",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        
        val distLabel = if (useMetric) "km" else "mi"
        val effLabel = if (useMetric) "L/100km" else "mpg"
        
        Text(
            text = "%.1f %s • %.1f %s".format(distance, distLabel, efficiency, effLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FuelStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
private fun EmptyAnalytics() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Analytics,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No analytics yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Complete a few trips to see your driving trends here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
