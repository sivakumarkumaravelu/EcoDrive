package com.ecodrive.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.domain.ai.config.AiConfig
import com.ecodrive.app.domain.model.AppColorPalette
import com.ecodrive.app.domain.model.AppTheme
import com.ecodrive.app.ui.theme.*
import com.ecodrive.app.util.AppConfig
import com.ecodrive.app.util.UnitConverter

/**
 * Settings screen with Vehicle API configuration, vehicle info,
 * and app preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAppearanceSheet by remember { mutableStateOf(false) }

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
            color = MaterialTheme.colorScheme.onBackground,
        )

        // ── Appearance ──────────────────────────────────────────
        SettingsSection(title = "Display", icon = Icons.Filled.Palette) {
            SettingsRow(
                label = "Theme & Appearance",
                subLabel = "${state.appTheme.getDisplayName()} • ${state.appPalette.getDisplayName()}",
                onClick = { showAppearanceSheet = true }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            SettingsRow(
                label = "Units of Measurement",
                subLabel = if (state.useMetric) "Metric (km, liters)" else "Imperial (miles, gallons)",
                onClick = { showAppearanceSheet = true }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            SettingsCheckRow(
                label = "Use Google Maps",
                checked = AppConfig.USE_GOOGLE_MAPS,
                enabled = false, // Controlled via AppConfig for now
            )
        }

        // ── Vehicle Profile ─────────────────────────────────────
        SettingsSection(title = "Vehicle", icon = Icons.Filled.DirectionsCar) {
            SettingsInfoRow("Profile", "Active Vehicle")
            SettingsInfoRow("Auto-detection", "Smartcar API")
            Text(
                text = "Add and manage multiple vehicles in the next update.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ── Smartcar Connected Services ─────────────────────────
        SettingsSection(title = "Vehicle Connected Services", icon = Icons.Filled.Cloud) {
            // Connection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val (statusText, statusColor) = when (state.smartcarApiState) {
                    SmartcarApiClient.ApiState.CONNECTED -> "Connected" to EcoDriveTheme.colors.scoreExcellent
                    SmartcarApiClient.ApiState.AUTHENTICATING -> "Authenticating…" to EcoDriveTheme.colors.scoreAverage
                    SmartcarApiClient.ApiState.ERROR -> "Error" to MaterialTheme.colorScheme.error
                    SmartcarApiClient.ApiState.NOT_CONFIGURED -> "Not configured" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = statusColor,
                )
            }

            if (state.smartcarApiState == SmartcarApiClient.ApiState.CONNECTED) {
                // Show live data from Smartcar API
                state.fuelTankPercent?.let {
                    SettingsInfoRow("Fuel Level", "%.0f%%".format(it))
                }
                state.odometerKm?.let {
                    SettingsInfoRow("Odometer", UnitConverter.formatDistance(it, state.useMetric))
                }
                SettingsInfoRow(
                    "Calibration Factor",
                    "%.3f".format(state.calibrationFactor),
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.disconnectSmartcar() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Disconnect")
                }
            } else {
                // Setup form
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Connect to your vehicle via Smartcar to enable fuel tracking " +
                            "and self-calibrating fuel estimates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.smartcarClientId,
                    onValueChange = viewModel::updateClientId,
                    label = { Text("Smartcar Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
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
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.smartcarClientId.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect to Vehicle")
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Get API keys at smartcar.com/dashboard",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.autoRecordEnabled && !state.hasBackgroundLocationPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Background location permission is required for auto-record to work reliably.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
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
                label = "🌐 Vehicle API (Fuel Calibration)",
                checked = state.smartcarApiState == SmartcarApiClient.ApiState.CONNECTED,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── AI Insights ────────────────────────────────────────
        SettingsSection(title = "AI Coaching & Insights", icon = Icons.Filled.AutoAwesome) {
            Text(
                text = "Choose an AI provider for real-time coaching and post-trip analysis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            var showProviderMenu by remember { mutableStateOf(false) }
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    onClick = { showProviderMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Selected Provider",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = state.selectedAiProvider.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                }

                DropdownMenu(
                    expanded = showProviderMenu,
                    onDismissRequest = { showProviderMenu = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    val providers = AiConfig.UI_PROVIDERS
                    providers.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                viewModel.setSelectedAiProvider(provider)
                                showProviderMenu = false
                            },
                            trailingIcon = {
                                if (state.selectedAiProvider == provider) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.selectedAiProvider == "LOCAL") {
                Text(
                    text = "Local coaching uses rule-based logic and doesn't require an internet connection or API keys.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Coaching and insights are powered by hardcoded AI configurations for optimal performance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── About ───────────────────────────────────────────────
        SettingsSection(title = "About", icon = Icons.Filled.Info) {
            SettingsInfoRow("App", "EcoDrive v1.1.0")
            SettingsInfoRow("Engine", "Physics-based Power-to-Fuel Model")
            SettingsInfoRow("Calibration", "Self-improving via Smartcar API")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showAppearanceSheet) {
        AppearanceBottomSheet(
            currentTheme = state.appTheme,
            currentPalette = state.appPalette,
            useMetric = state.useMetric,
            onThemeChange = viewModel::setAppTheme,
            onPaletteChange = viewModel::setColorPalette,
            onToggleUnits = viewModel::toggleUnits,
            onDismiss = { showAppearanceSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceBottomSheet(
    currentTheme: AppTheme,
    currentPalette: AppColorPalette,
    useMetric: Boolean,
    onThemeChange: (AppTheme) -> Unit,
    onPaletteChange: (AppColorPalette) -> Unit,
    onToggleUnits: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        AppearanceSelector(
            currentTheme = currentTheme,
            currentPalette = currentPalette,
            useMetric = useMetric,
            onThemeChange = onThemeChange,
            onPaletteChange = onPaletteChange,
            onToggleUnits = onToggleUnits
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSelector(
    currentTheme: AppTheme,
    currentPalette: AppColorPalette,
    useMetric: Boolean,
    onThemeChange: (AppTheme) -> Unit,
    onPaletteChange: (AppColorPalette) -> Unit,
    onToggleUnits: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Appearance & Units",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )

        // Theme Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Theme Mode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppTheme.entries.forEachIndexed { index, theme ->
                    SegmentedButton(
                        selected = currentTheme == theme,
                        onClick = { onThemeChange(theme) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = AppTheme.entries.size),
                        label = {
                            Text(
                                text = theme.getDisplayName(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }

        // Unit Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "System of Measurement",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf("Metric", "Imperial")
                options.forEachIndexed { index, label ->
                    val isMetric = label == "Metric"
                    SegmentedButton(
                        selected = useMetric == isMetric,
                        onClick = { if (useMetric != isMetric) onToggleUnits() },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        label = { Text(label) }
                    )
                }
            }
        }

        // Color Palette Selector
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Color Palette",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(AppColorPalette.entries) { palette ->
                    ColorSwatch(
                        palette = palette,
                        selected = currentPalette == palette,
                        onClick = { onPaletteChange(palette) }
                    )
                }
            }

            Text(
                text = currentPalette.getDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    palette: AppColorPalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = when (palette) {
        AppColorPalette.ECO_GREEN -> EcoGreen
        AppColorPalette.MIDNIGHT_BLUE -> MidnightBlue
        AppColorPalette.SOLAR_ORANGE -> SolarOrange
        AppColorPalette.DEEP_PURPLE -> DeepPurple
        AppColorPalette.OCEAN_TEAL -> OceanTeal
        AppColorPalette.CRIMSON_RED -> CrimsonRed
        AppColorPalette.DYNAMIC -> MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.1f))
            .border(
                BorderStroke(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) color else color.copy(alpha = 0.3f)
                ),
                CircleShape
            )
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}

// ── Reusable Setting Components ─────────────────────────────────

@Composable
private fun SettingsRow(
    label: String,
    subLabel: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subLabel != null) {
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

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
            .background(EcoDriveTheme.colors.cardBackground)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
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
            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            ),
        )
    }
}
