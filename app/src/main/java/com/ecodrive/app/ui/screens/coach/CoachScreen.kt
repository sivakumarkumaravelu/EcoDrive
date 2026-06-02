package com.ecodrive.app.ui.screens.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.ui.theme.*

/**
 * Coach screen providing personalized driving tips and historical analysis.
 */
@Composable
fun CoachScreen(
    viewModel: CoachViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Driving Coach",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ── Your Progress ───────────────────────────────────────
        ProgressSection(
            score = state.recentEcoScore,
            trend = state.scoreTrend
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Personalized Coaching ──────────────────────────────
        if (state.personalizedTip.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Stars,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Coach's Tip",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.personalizedTip,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Weekly Activity Analysis ────────────────────────────
        Text(
            text = "Weekly Activity Analysis",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActivityCategoryCard(
                title = "Hard Brakes",
                count = state.issuesCount[DrivingEventType.HARD_BRAKE] ?: 0,
                trend = state.trends[DrivingEventType.HARD_BRAKE] ?: 0.0,
                icon = Icons.Filled.StopCircle,
                modifier = Modifier.weight(1f)
            )
            ActivityCategoryCard(
                title = "Hard Accels",
                count = state.issuesCount[DrivingEventType.HARD_ACCELERATION] ?: 0,
                trend = state.trends[DrivingEventType.HARD_ACCELERATION] ?: 0.0,
                icon = Icons.Filled.Speed,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActivityCategoryCard(
                title = "Sharp Turns",
                count = state.issuesCount[DrivingEventType.SHARP_TURN] ?: 0,
                trend = state.trends[DrivingEventType.SHARP_TURN] ?: 0.0,
                icon = Icons.Filled.LinearScale,
                modifier = Modifier.weight(1f)
            )
            ActivityCategoryCard(
                title = "Idle Mins",
                count = state.issuesCount[DrivingEventType.EXCESSIVE_IDLE] ?: 0,
                trend = state.trends[DrivingEventType.EXCESSIVE_IDLE] ?: 0.0,
                icon = Icons.Filled.Timer,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── General Efficiency Tips ─────────────────────────────
        Text(
            text = "Efficiency Best Practices",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )

        EfficiencyTipCard(
            title = "Anticipate Traffic",
            description = "Looking ahead allows you to coast and avoid unnecessary braking. Keeping your vehicle moving is more efficient than a full stop-and-start.",
            icon = Icons.Filled.Traffic
        )
        
        EfficiencyTipCard(
            title = "Gentle Acceleration",
            description = "Avoid 'flooring it' from a stop. Gentle acceleration keeps the engine in its most efficient range and avoids wasting energy.",
            icon = Icons.AutoMirrored.Filled.TrendingUp
        )
        
        EfficiencyTipCard(
            title = "Maintain Steady Speed",
            description = "Frequent speed changes waste fuel. Use cruise control on highways and try to maintain a consistent speed in city traffic.",
            icon = Icons.Filled.AvTimer
        )
        
        EfficiencyTipCard(
            title = "Minimize Idling",
            description = "If you're parked for more than 30 seconds, it's usually more efficient to turn off the engine. Idling yields 0 mpg.",
            icon = Icons.Filled.PauseCircle
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ActivityCategoryCard(
    title: String,
    count: Int,
    trend: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val countColor = if (count > 5) EcoDriveTheme.colors.scorePoor else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(EcoDriveTheme.colors.cardBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = countColor
        )
        
        if (trend != 0.0) {
            val isImproving = trend < 0 // Fewer issues is improvement
            val trendColor = if (isImproving) EcoDriveTheme.colors.scoreExcellent else EcoDriveTheme.colors.scorePoor
            val trendIcon = if (isImproving) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = trendIcon,
                    contentDescription = null,
                    tint = trendColor,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "${Math.abs(trend.toInt())}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = trendColor
                )
            }
        }
    }
}

@Composable
private fun ProgressSection(
    score: Int,
    trend: Double,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EcoDriveTheme.colors.cardBackground)
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Weekly Eco Score",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        score >= 80 -> EcoDriveTheme.colors.scoreExcellent
                        score >= 60 -> EcoDriveTheme.colors.scoreGood
                        else -> EcoDriveTheme.colors.scorePoor
                    }
                )
            }

            if (trend != 0.0) {
                val isImproving = trend > 0
                val trendColor = if (isImproving) EcoDriveTheme.colors.scoreExcellent else EcoDriveTheme.colors.scorePoor
                val trendIcon = if (isImproving) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown
                
                Surface(
                    color = trendColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = trendIcon,
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${if (isImproving) "+" else ""}${trend.toInt()}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = trendColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EfficiencyTipCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EcoDriveTheme.colors.cardBackground)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
