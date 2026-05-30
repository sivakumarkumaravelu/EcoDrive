package com.ecodrive.app.ui.screens.coach

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
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.ui.theme.*

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
                text = "Eco Coach",
                style = MaterialTheme.typography.headlineMedium,
                color = DarkOnSurface,
            )
            
            // Audio toggle
            IconButton(
                onClick = { viewModel.toggleAudioCoaching() },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
            ) {
                Icon(
                    imageVector = if (state.isAudioCoachingEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = "Toggle Audio Coaching",
                    tint = if (state.isAudioCoachingEnabled) EcoGreen else DarkOnSurfaceVariant
                )
            }
        }

        Text(
            text = "Real-time audio feedback is ${if (state.isAudioCoachingEnabled) "ON" else "OFF"}",
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant
        )

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

        // ── Top Personalized Tip ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (state.topIssue != null) AccentAmber.copy(alpha = 0.15f)
                    else EcoGreen.copy(alpha = 0.15f)
                )
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = if (state.topIssue != null) AccentAmber else EcoGreen,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Focus Area for You",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (state.topIssue != null) AccentAmber else EcoGreen,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = state.personalizedTip,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DarkOnSurface,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f
                )
            }
        }

        // ── General Highlander Hybrid Tips ──────────────────────
        Text(
            text = "Highlander Hybrid Best Practices",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = DarkOnSurface,
            modifier = Modifier.padding(top = 8.dp)
        )

        HybridTipCard(
            title = "Pulse and Glide",
            description = "Accelerate briskly to your target speed using the gas engine (Pulse), then ease off the accelerator to let the electric motor maintain speed (Glide).",
            icon = Icons.Filled.Speed
        )
        
        HybridTipCard(
            title = "Maximize Regenerative Braking",
            description = "Light, steady braking recovers energy back into the hybrid battery. Hard braking activates the friction brakes, wasting that energy as heat.",
            icon = Icons.Filled.BatteryChargingFull
        )
        
        HybridTipCard(
            title = "EV Mode Sweet Spot",
            description = "The Highlander Hybrid shines in stop-and-go traffic under 40 km/h. Keep your inputs gentle to prevent the gas engine from firing up unnecessarily.",
            icon = Icons.Filled.EvStation
        )
        
        HybridTipCard(
            title = "Climate Control Impact",
            description = "Running the AC or heater heavily forces the gas engine to run just to power the compressor/heater core. Use Eco mode for climate when possible.",
            icon = Icons.Filled.AcUnit
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HybridTipCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = EcoTeal,
            modifier = Modifier.size(24.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = DarkOnSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurfaceVariant
            )
        }
    }
}
