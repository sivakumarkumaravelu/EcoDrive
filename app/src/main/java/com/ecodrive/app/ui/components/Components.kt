package com.ecodrive.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecodrive.app.ui.theme.*

/**
 * Animated circular gauge that displays the Eco Score (0-100).
 * Features a gradient arc from red through yellow to green,
 * with smooth animation when the value changes.
 */
@Composable
fun EcoScoreGauge(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    strokeWidth: Dp = 14.dp,
) {
    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "ecoScore",
    )

    val scoreColor = when {
        animatedScore >= 90 -> ScoreExcellent
        animatedScore >= 70 -> ScoreGood
        animatedScore >= 50 -> ScoreAverage
        else -> ScorePoor
    }

    // Glow effect pulsing
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sweepAngle = 240f
            val startAngle = 150f
            val padding = strokeWidth.toPx() / 2f + 8f

            val arcSize = Size(
                this.size.width - padding * 2,
                this.size.height - padding * 2,
            )
            val arcOffset = Offset(padding, padding)

            // Background arc (dark track)
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = arcOffset,
                size = arcSize,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round,
                ),
            )

            // Animated gradient progress arc
            val progressSweep = sweepAngle * (animatedScore / 100f)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        ScorePoor,
                        ScoreAverage,
                        ScoreGood,
                        ScoreExcellent,
                    ),
                ),
                startAngle = startAngle,
                sweepAngle = progressSweep,
                useCenter = false,
                topLeft = arcOffset,
                size = arcSize,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round,
                ),
            )

            // Glow behind the arc end
            drawArc(
                color = scoreColor.copy(alpha = glowAlpha),
                startAngle = startAngle,
                sweepAngle = progressSweep,
                useCenter = false,
                topLeft = arcOffset,
                size = arcSize,
                style = Stroke(
                    width = strokeWidth.toPx() + 12f,
                    cap = StrokeCap.Round,
                ),
            )
        }

        // Score text in center
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "ECO SCORE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$animatedScore",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = scoreColor,
            )
            Text(
                text = "/ 100",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A compact metric card showing a single value with label and icon/color.
 * Used on the dashboard for Speed, RPM, Fuel Rate, etc.
 */
@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val animatedBorderAlpha by rememberInfiniteTransition(label = "border")
        .animateFloat(
            initialValue = 0.3f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "borderAlpha",
        )

    val cardBgTint = accentColor.copy(alpha = 0.04f)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // Soft colored background tint
            drawRoundRect(
                color = cardBgTint,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
            )
            // Glassmorphism border glow
            drawRoundRect(
                color = accentColor.copy(alpha = animatedBorderAlpha),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                style = Stroke(width = 1.5f),
            )
        }

        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = accentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Connection status bar shown at the top of the dashboard.
 */
@Composable
fun ConnectionStatusBar(
    isConnected: Boolean,
    statusText: String,
    modifier: Modifier = Modifier,
) {
    val statusColor = if (isConnected) EcoGreen else MaterialTheme.colorScheme.onSurfaceVariant
    val dotAlpha by rememberInfiniteTransition(label = "dot")
        .animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dotPulse",
        )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pulsing status dot
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(
                color = statusColor.copy(alpha = if (isConnected) dotAlpha else 0.5f),
                radius = size.minDimension / 2f,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
        )
    }
}
