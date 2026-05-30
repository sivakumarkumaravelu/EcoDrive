package com.ecodrive.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.data.remote.ToyotaApiClient
import com.ecodrive.app.ui.theme.*

/**
 * Settings screen with Toyota API configuration, vehicle info,
 * and app preferences.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = DarkOnSurface,
        )

        // ── Vehicle Profile ─────────────────────────────────────
        SettingsSection(title = "Vehicle", icon = Icons.Filled.DirectionsCar) {
            SettingsInfoRow("Make", "Toyota")
            SettingsInfoRow("Model", "Highlander Hybrid")
            SettingsInfoRow("Year", "2023")
            SettingsInfoRow("Engine", "2.5L 4-Cylinder + Electric")
            SettingsInfoRow("Tank", "65 L")
            SettingsInfoRow("Curb Weight", "2,090 kg")
        }

        // ── Toyota Connected Services ───────────────────────────
        SettingsSection(title = "Toyota Connected Services", icon = Icons.Filled.Cloud) {
            // Connection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurfaceVariant,
                )
                val (statusText, statusColor) = when (state.toyotaApiState) {
                    ToyotaApiClient.ApiState.CONNECTED -> "Connected" to ScoreExcellent
                    ToyotaApiClient.ApiState.AUTHENTICATING -> "Authenticating…" to AccentAmber
                    ToyotaApiClient.ApiState.ERROR -> "Error" to ErrorRed
                    ToyotaApiClient.ApiState.NOT_CONFIGURED -> "Not configured" to DarkOnSurfaceVariant
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = statusColor,
                )
            }

            if (state.toyotaApiState == ToyotaApiClient.ApiState.CONNECTED) {
                // Show live data from Toyota API
                state.fuelTankPercent?.let {
                    SettingsInfoRow("Fuel Level", "%.0f%%".format(it))
                }
                state.odometerKm?.let {
                    SettingsInfoRow("Odometer", "%.0f km".format(it))
                }
                SettingsInfoRow(
                    "Calibration Factor",
                    "%.3f".format(state.calibrationFactor),
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.disconnectToyota() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Disconnect")
                }
            } else {
                // Setup form
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Connect to Toyota via Smartcar to enable fuel tracking " +
                            "and self-calibrating fuel estimates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.smartcarClientId,
                    onValueChange = viewModel::updateClientId,
                    label = { Text("Smartcar Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EcoGreen,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedLabelColor = EcoGreen,
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.smartcarClientSecret,
                    onValueChange = viewModel::updateClientSecret,
                    label = { Text("Smartcar Client Secret") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EcoGreen,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedLabelColor = EcoGreen,
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val url = viewModel.getAuthUrl()
                        if (url != null) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoGreen),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.smartcarClientId.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect with Toyota")
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Get API keys at smartcar.com/dashboard",
                    style = MaterialTheme.typography.labelSmall,
                    color = EcoGreen,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://smartcar.com/dashboard"))
                        context.startActivity(intent)
                    }
                )
            }
        }

        // ── Automation ──────────────────────────────────────────
        SettingsSection(title = "Automation", icon = Icons.Filled.AutoMode) {
            SettingsCheckRow(
                label = "Auto-Record Drives",
                checked = state.autoRecordEnabled,
                onCheckedChange = { viewModel.toggleAutoRecord() }
            )
            Text(
                text = "EcoDrive will automatically start and stop recording " +
                        "when it detects you are in a moving vehicle.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant,
            )

            if (state.autoRecordEnabled && !state.hasBackgroundLocationPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ErrorRed.copy(alpha = 0.1f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Background location permission is required for auto-record to work reliably.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed
                    )
                }
            }
        }

        // ── Data Sources ────────────────────────────────────────
        SettingsSection(title = "Data Sources", icon = Icons.Filled.Sensors) {
            SettingsCheckRow(
                label = "📱 Phone Sensors (GPS + Accelerometer)",
                checked = true,
                enabled = false, // Always on
            )
            SettingsCheckRow(
                label = "🌐 Toyota API (Fuel Calibration)",
                checked = state.toyotaApiState == ToyotaApiClient.ApiState.CONNECTED,
                enabled = false,
            )
            SettingsCheckRow(
                label = "🔌 OBD-II Adapter (Pro)",
                checked = state.isObdEnabled,
                enabled = true,
                onCheckedChange = { viewModel.toggleObd() }
            )
            Text(
                text = "OBD-II support coming soon as an optional upgrade for " +
                        "RPM, throttle, and direct fuel flow data.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // ── About ───────────────────────────────────────────────
        SettingsSection(title = "About", icon = Icons.Filled.Info) {
            SettingsInfoRow("App", "EcoDrive v1.0.0")
            SettingsInfoRow("Target", "2023 Highlander Hybrid")
            SettingsInfoRow("Fuel Model", "VSP + Hybrid Efficiency Map")
            SettingsInfoRow("Calibration", "Self-improving via Toyota API")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Reusable Setting Components ─────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EcoGreen,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = DarkOnSurface,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurface,
        )
    }
}

@Composable
private fun SettingsCheckRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) DarkOnSurface else DarkOnSurfaceVariant,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = EcoGreen,
                checkedTrackColor = EcoGreen.copy(alpha = 0.3f),
            ),
        )
    }
}
