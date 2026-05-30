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
                color = DarkOnSurface,
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
                        selectedContainerColor = EcoGreen.copy(alpha = 0.2f),
                        selectedLabelColor = EcoGreen,
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
                CircularProgressIndicator(color = EcoGreen)
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
                color = GaugeBlue,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "Avg Score",
                value = "${state.avgEcoScore}",
                icon = Icons.Filled.Stars,
                color = when {
                    state.avgEcoScore >= 70 -> ScoreGood
                    state.avgEcoScore >= 50 -> ScoreAverage
                    else -> ScorePoor
                },
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "Distance",
                value = "%.0f km".format(state.totalDistanceKm),
                icon = Icons.Filled.Straighten,
                color = GaugePurple,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Fuel Savings Highlight ──────────────────────────────
        if (state.fuelSavedEstimate > 0.5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(EcoGreen.copy(alpha = 0.1f))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Eco,
                        contentDescription = null,
                        tint = EcoGreen,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "🎉 Estimated %.1f L saved!".format(state.fuelSavedEstimate),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = EcoGreen,
                        )
                        Text(
                            text = "Compared to EPA rated 6.4 L/100km for your Highlander",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkOnSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Eco Score Trend ──────────────────────────────────────
        ChartCard(title = "Eco Score Trend") {
            LineChart(
                points = state.ecoScoreTrend,
                lineColor = EcoGreen,
                yAxisLabel = "Score",
                xAxisLabels = state.ecoScoreTrend.map { it.label },
                minY = 0f,
                maxY = 100f,
            )
        }

        // ── Fuel Efficiency Trend ───────────────────────────────
        ChartCard(title = "Fuel Efficiency") {
            LineChart(
                points = state.fuelEfficiencyTrend,
                lineColor = GaugeOrange,
                yAxisLabel = "L/100km",
                xAxisLabels = state.fuelEfficiencyTrend.map { it.label },
                fillGradient = true,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Average: %.1f L/100km (EPA: 6.4)".format(state.avgFuelEfficiency),
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant,
            )
        }

        // ── Weekly Eco Score Bar Chart ───────────────────────────
        if (state.weeklyScores.isNotEmpty()) {
            ChartCard(title = "Weekly Eco Scores") {
                BarChart(
                    values = state.weeklyScores,
                    barColor = { score ->
                        when {
                            score >= 70 -> ScoreGood
                            score >= 50 -> ScoreAverage
                            else -> ScorePoor
                        }
                    },
                    maxValue = 100f,
                    yAxisLabel = "Avg Score",
                )
            }
        }

        // ── Weekly Distance Bar Chart ───────────────────────────
        if (state.weeklyDistances.isNotEmpty()) {
            ChartCard(title = "Weekly Distance") {
                BarChart(
                    values = state.weeklyDistances,
                    barColor = { GaugeBlue },
                    yAxisLabel = "km",
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
                        Triple("Braking", brakes, ScorePoor),
                        Triple("Accel", accels, ScoreAverage),
                        Triple("Cornering", turns, GaugePurple),
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
                BehaviorStat("Hard Brakes", state.totalHardBrakes, ScorePoor)
                BehaviorStat("Hard Accels", state.totalHardAccels, ScoreAverage)
                BehaviorStat("Sharp Turns", state.totalSharpTurns, GaugePurple)
                BehaviorStat("Idle (min)", state.totalIdleMinutes.toInt(), AccentAmber)
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
                        distance = trip.distanceKm,
                        efficiency = trip.fuelEfficiencyLPer100Km,
                        color = ScoreExcellent,
                        modifier = Modifier.weight(1f),
                    )
                }
                state.worstTrip?.let { trip ->
                    ComparisonItem(
                        label = "📉 Worst Trip",
                        score = trip.ecoScore,
                        distance = trip.distanceKm,
                        efficiency = trip.fuelEfficiencyLPer100Km,
                        color = ScorePoor,
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
                FuelStat("Total Used", "%.1f L".format(state.totalFuelLiters), GaugeOrange)
                FuelStat(
                    "Avg L/100km",
                    "%.1f".format(state.avgFuelEfficiency),
                    if (state.avgFuelEfficiency < 6.4) ScoreGood else ScoreAverage,
                )
                FuelStat(
                    "vs EPA",
                    if (state.fuelSavedEstimate > 0) "-%.1f L".format(state.fuelSavedEstimate)
                    else "+%.1f L".format(-state.fuelSavedEstimate),
                    if (state.fuelSavedEstimate > 0) ScoreGood else ScorePoor,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Sub-components ──────────────────────────────────────────────

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
            .background(DarkCard)
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
            color = DarkOnSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurfaceVariant,
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
            .background(DarkCard)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = DarkOnSurface,
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
            color = DarkOnSurfaceVariant,
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
            color = DarkOnSurface,
        )
        Text(
            text = "%.1f km • %.1f L/100km".format(distance, efficiency),
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant,
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
            color = DarkOnSurfaceVariant,
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
            tint = DarkOnSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No analytics yet",
            style = MaterialTheme.typography.titleMedium,
            color = DarkOnSurfaceVariant,
        )
        Text(
            text = "Complete a few trips to see your driving trends here.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
