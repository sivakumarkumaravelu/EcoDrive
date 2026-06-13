package com.ecodrive.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecodrive.app.ui.theme.*

/**
 * Data point for line/area charts.
 */
data class ChartPoint(
    val x: Float,
    val y: Float,
    val label: String = "",
)

/**
 * Animated line chart with gradient fill and optional axis labels.
 * Draws directly on Compose Canvas for full visual control.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = EcoGreen,
    fillGradient: Boolean = true,
    showDots: Boolean = true,
    yAxisLabel: String = "",
    xAxisLabels: List<String> = emptyList(),
    minY: Float? = null,
    maxY: Float? = null,
) {
    if (points.size < 2) {
        EmptyChartPlaceholder(modifier, "Not enough data")
        return
    }

    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "chartAnim",
    )

    val textMeasurer = rememberTextMeasurer()
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBg = EcoDriveTheme.colors.cardBackground

    Column(modifier = modifier) {
        if (yAxisLabel.isNotBlank()) {
            Text(
                text = yAxisLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val paddingX = 48.dp.toPx()
            val paddingBottom = 32.dp.toPx()
            val paddingTop = 16.dp.toPx()
            val chartWidth = size.width - paddingX * 2
            val chartHeight = size.height - paddingBottom - paddingTop

            val yMin = minY ?: points.minOf { it.y }
            val yMax = maxY ?: points.maxOf { it.y }
            val yRange = (yMax - yMin).coerceAtLeast(1f)

            fun mapX(index: Int): Float =
                paddingX + (index.toFloat() / (points.size - 1)) * chartWidth

            fun mapY(value: Float): Float =
                paddingTop + chartHeight - ((value - yMin) / yRange) * chartHeight

            // Draw horizontal grid lines
            val gridCount = 4
            for (i in 0..gridCount) {
                val y = paddingTop + chartHeight * i / gridCount
                drawLine(
                    color = outlineColor.copy(alpha = 0.3f),
                    start = Offset(paddingX, y),
                    end = Offset(size.width - paddingX, y),
                    strokeWidth = 1f,
                )

                // Y-axis labels
                val labelValue = yMax - (yRange * i / gridCount)
                val text = if (labelValue >= 100) "%.0f".format(labelValue)
                else "%.1f".format(labelValue)
                val textStyle = TextStyle(
                    fontSize = 9.sp,
                    color = onSurfaceVariantColor,
                )
                val measuredText = textMeasurer.measure(text, textStyle)
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset((paddingX - measuredText.size.width - 8f).coerceAtLeast(0f), y - measuredText.size.height / 2f),
                )
            }

            // Build path with animation
            val path = Path()
            val animatedCount = (points.size * animationProgress).toInt().coerceAtLeast(2)

            for (i in 0 until animatedCount) {
                val x = mapX(i)
                val y = mapY(points[i].y)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            // Draw gradient fill
            if (fillGradient) {
                val fillPath = Path().apply {
                    addPath(path)
                    val lastX = mapX(animatedCount - 1)
                    lineTo(lastX, paddingTop + chartHeight)
                    lineTo(paddingX, paddingTop + chartHeight)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.3f),
                            lineColor.copy(alpha = 0.0f),
                        ),
                    ),
                )
            }

            // Draw line
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 2.5f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // Draw dots
            if (showDots && points.size <= 30) {
                for (i in 0 until animatedCount) {
                    val x = mapX(i)
                    val y = mapY(points[i].y)
                    drawCircle(color = lineColor, radius = 3.5f, center = Offset(x, y))
                    drawCircle(color = cardBg, radius = 1.5f, center = Offset(x, y))
                }
            }

            // X-axis labels
            if (xAxisLabels.isNotEmpty()) {
                val step = (xAxisLabels.size / 5).coerceAtLeast(1)
                for (i in xAxisLabels.indices step step) {
                    val x = paddingX + (i.toFloat() / (xAxisLabels.size - 1)) * chartWidth
                    val text = xAxisLabels[i]
                    val measuredText = textMeasurer.measure(
                        text = text,
                        style = TextStyle(
                            fontSize = 8.sp,
                            color = onSurfaceVariantColor,
                        )
                    )
                    val xOffset = when (i) {
                        0 -> x
                        xAxisLabels.lastIndex -> x - measuredText.size.width
                        else -> x - (measuredText.size.width / 2f)
                    }
                    
                    drawText(
                        textLayoutResult = measuredText,
                        topLeft = Offset(xOffset, paddingTop + chartHeight + 8.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Bar chart with animated bars and color coding.
 */
@Composable
fun BarChart(
    values: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: (Float) -> Color = { EcoGreen },
    maxValue: Float? = null,
    yAxisLabel: String = "",
) {
    if (values.isEmpty()) {
        EmptyChartPlaceholder(modifier, "No data")
        return
    }

    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "barAnim",
    )

    val textMeasurer = rememberTextMeasurer()
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier) {
        if (yAxisLabel.isNotBlank()) {
            Text(
                text = yAxisLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val chartPadding = 48.dp.toPx()
            val topPadding = 16.dp.toPx()
            val bottomPadding = 32.dp.toPx()
            val chartWidth = size.width - chartPadding * 2
            val chartHeight = size.height - topPadding - bottomPadding

            val max = maxValue ?: values.maxOf { it.second }.coerceAtLeast(1f)
            val barWidth = (chartWidth / values.size) * 0.6f
            val barSpacing = chartWidth / values.size


            // Draw horizontal grid lines
            val gridCount = 4
            for (i in 0..gridCount) {
                val yLine = topPadding + chartHeight * i / gridCount
                drawLine(
                    color = outlineColor.copy(alpha = 0.3f),
                    start = Offset(chartPadding, yLine),
                    end = Offset(size.width - chartPadding, yLine),
                    strokeWidth = 1f,
                )

                // Y-axis labels
                val labelValue = max - (max * i / gridCount)
                val text = if (labelValue >= 100 || labelValue % 1f == 0f) "%.0f".format(labelValue)
                else "%.1f".format(labelValue)
                
                val textStyle = TextStyle(
                    fontSize = 9.sp,
                    color = onSurfaceVariantColor,
                )
                val measuredText = textMeasurer.measure(text, textStyle)
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset((chartPadding - measuredText.size.width - 8f).coerceAtLeast(0f), yLine - measuredText.size.height / 2f),
                )
            }

            values.forEachIndexed { index, (label, value) ->
                val safeValue = value.coerceAtLeast(0f)
                val barHeight = (safeValue / max) * chartHeight * animationProgress
                val x = chartPadding + index * barSpacing + (barSpacing - barWidth) / 2
                val y = topPadding + chartHeight - barHeight

                // Bar with rounded top corners
                drawRoundRect(
                    color = barColor(value),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4f, 4f),
                )

                // Value above bar
                val valueText = if (value >= 100f || value % 1f == 0f) "%.0f".format(value) else "%.1f".format(value)
                val measuredValText = textMeasurer.measure(
                    text = valueText,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                )
                drawText(
                    textLayoutResult = measuredValText,
                    topLeft = Offset(x + (barWidth / 2f) - (measuredValText.size.width / 2f), y - measuredValText.size.height - 2f)
                )

                // Label below bar
                val measuredText = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        fontSize = 8.sp,
                        color = onSurfaceVariantColor,
                    )
                )
                val textX = x + (barWidth / 2f) - (measuredText.size.width / 2f)
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset(textX.coerceAtLeast(chartPadding), topPadding + chartHeight + 8f)
                )
            }
        }
    }
}

/**
 * Horizontal breakdown bar showing category proportions.
 */
@Composable
fun BreakdownBar(
    segments: List<Triple<String, Float, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp,
) {
    val total = segments.sumOf { it.second.toDouble() }.toFloat()
    if (total <= 0) return

    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "breakdownAnim",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        var xOffset = 0f
        val barHeight = size.height
        val totalWidth = size.width * animationProgress

        segments.forEach { (_, value, color) ->
            val segmentWidth = (value / total) * totalWidth
            drawRoundRect(
                color = color,
                topLeft = Offset(xOffset, 0f),
                size = Size(segmentWidth, barHeight),
                cornerRadius = CornerRadius(6f, 6f),
            )
            xOffset += segmentWidth + 2f
        }
    }
}

/**
 * Donut chart showing score breakdown.
 */
@Composable
fun ScoreDonut(
    segments: List<Pair<String, Int>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
) {
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "donutAnim",
    )

    val total = segments.sumOf { it.second }.toFloat()
    if (total <= 0f) return

    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 18f
        val radius = (this.size.minDimension - strokeWidth) / 2f
        val center = Offset(this.size.width / 2, this.size.height / 2)

        var startAngle = -90f
        segments.forEachIndexed { index, (_, value) ->
            val sweep = (value / total) * 360f * animationProgress
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            startAngle += sweep + 2f
        }
    }
}

@Composable
private fun EmptyChartPlaceholder(modifier: Modifier, message: String) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
