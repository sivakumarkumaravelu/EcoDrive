package com.ecodrive.app.ui.screens.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ecodrive.app.ui.theme.*

/**
 * Placeholder screens for tabs that will be fully implemented in later phases.
 * Each screen shows a preview of what's coming with a "Coming Soon" indicator.
 */

@Composable
fun TripsPlaceholderScreen() {
    PlaceholderContent(
        icon = Icons.Filled.Route,
        title = "Trip History",
        description = "View all your past trips with detailed breakdowns including distance, " +
                "duration, fuel consumed, eco score, and driving events.",
        features = listOf(
            "📊 Trip-by-trip eco score comparison",
            "📈 Speed and fuel consumption graphs",
            "🗺️ Route tracking with event markers",
            "📤 Export trip data as CSV",
        ),
    )
}

@Composable
fun AnalyticsPlaceholderScreen() {
    PlaceholderContent(
        icon = Icons.Filled.Analytics,
        title = "Analytics",
        description = "Deep dive into your driving patterns with weekly and monthly trends, " +
                "fuel efficiency comparisons, and behavior breakdowns.",
        features = listOf(
            "📅 Weekly/Monthly fuel efficiency trends",
            "🥧 Driving behavior breakdown (pie chart)",
            "📉 Improvement tracking over time",
            "🏆 Best vs worst trip comparisons",
        ),
    )
}

@Composable
fun CoachPlaceholderScreen() {
    PlaceholderContent(
        icon = Icons.Filled.School,
        title = "Driving Coach",
        description = "Get personalized coaching based on your driving patterns. " +
                "Real-time alerts and actionable advice to improve your fuel efficiency.",
        features = listOf(
            "🔔 Real-time audio/visual driving alerts",
            "💡 Personalized improvement suggestions",
            "🎯 Weekly challenges to improve habits",
            "📖 Eco-driving education modules",
        ),
    )
}

@Composable
fun SettingsPlaceholderScreen() {
    PlaceholderContent(
        icon = Icons.Filled.Settings,
        title = "Settings",
        description = "Configure your vehicle profile, Bluetooth connection, " +
                "alert thresholds, and app preferences.",
        features = listOf(
            "🚗 Vehicle profile (make, model, fuel type)",
            "📡 Bluetooth device management",
            "⚙️ Alert sensitivity thresholds",
            "📏 Unit preferences (metric / imperial)",
        ),
    )
}

@Composable
private fun PlaceholderContent(
    icon: ImageVector,
    title: String,
    description: String,
    features: List<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = EcoGreen.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = EcoGreen.copy(alpha = 0.15f),
        ) {
            Text(
                text = "Coming in Phase 4",
                style = MaterialTheme.typography.labelMedium,
                color = EcoGreen,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Feature list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EcoDriveTheme.colors.cardBackground)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Planned Features",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            features.forEach { feature ->
                Text(
                    text = feature,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
