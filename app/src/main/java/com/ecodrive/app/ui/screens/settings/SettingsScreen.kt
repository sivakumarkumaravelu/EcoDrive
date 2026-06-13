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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.domain.ai.config.AiConfig
import com.ecodrive.app.domain.model.AppColorPalette
import com.ecodrive.app.domain.model.AppFontScale
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

    // Manual editing state
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditOdometerDialog by remember { mutableStateOf(false) }
    var showEditFuelDialog by remember { mutableStateOf(false) }

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
                label = "Map Style & Units",
                subLabel = (if (state.useMetric) "Metric" else "Imperial") + " • " + (if (state.mapStyle == com.ecodrive.app.util.MapStyle.TERRAIN) "Terrain Map" else "Minimal Map"),
                onClick = { showAppearanceSheet = true }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            SettingsRow(
                label = "Text Size",
                subLabel = state.appFontScale.getDisplayName(),
                onClick = { showAppearanceSheet = true }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            SettingsCheckRow(
                label = "Keep display on",
                checked = state.keepDisplayOn,
                onCheckedChange = {
                    viewModel.setKeepDisplayOn(it)
                    com.ecodrive.app.util.HapticHelper.playClick(context)
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            SettingsCheckRow(
                label = "Use Google Maps (Paid)",
                checked = state.useGoogleMaps,
                enabled = false,
                onCheckedChange = {
                    // Disabled
                }
            )
            Text(
                text = "Google Maps integration requires a paid API key.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
            )
        }

        // ── Vehicle Profile ─────────────────────────────────────
        SettingsSection(title = "Vehicle Profile", icon = Icons.Filled.DirectionsCar) {
            val isSmartcarConnected = state.smartcarApiState == SmartcarApiClient.ApiState.CONNECTED
            
            // Vehicle Name
            val displayName = if (isSmartcarConnected) {
                buildString {
                    if (state.vehicleYear != null) append("${state.vehicleYear} ")
                    if (state.vehicleMake != null) append("${state.vehicleMake} ")
                    if (state.vehicleModel != null) append(state.vehicleModel)
                }.trim().takeIf { it.isNotEmpty() } ?: "Connected Vehicle"
            } else {
                state.localVehicle?.name ?: "My Vehicle"
            }

            SettingsEditableRow(
                label = "Vehicle Name",
                value = displayName,
                isEditable = !isSmartcarConnected,
                onClick = { if (!isSmartcarConnected) showEditNameDialog = true }
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Odometer
            val odometerDisplay = state.odometerKm?.let { 
                com.ecodrive.app.util.UnitConverter.formatDistance(it, state.useMetric) 
            } ?: "--"
            
            SettingsEditableRow(
                label = "Odometer",
                value = odometerDisplay,
                isEditable = !isSmartcarConnected,
                onClick = { if (!isSmartcarConnected) showEditOdometerDialog = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Fuel Level
            val fuelDisplay = state.fuelTankPercent?.let { "%.0f%%".format(it) } ?: "--"
            
            SettingsEditableRow(
                label = "Fuel Level",
                value = fuelDisplay,
                isEditable = !isSmartcarConnected,
                onClick = { if (!isSmartcarConnected) showEditFuelDialog = true }
            )

            if (!isSmartcarConnected) {
                Text(
                    text = "Tip: Connect via Smartcar below to sync these automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    text = "Currently syncing live from your vehicle via Smartcar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = EcoDriveTheme.colors.scoreExcellent,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
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
                    SmartcarApiClient.ApiState.AUTH_FAILED -> "Auth Failed" to MaterialTheme.colorScheme.error
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
                    value = state.smartcarApplicationId,
                    onValueChange = viewModel::updateApplicationId,
                    label = { Text("Smartcar Application ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))

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
                        viewModel.clearAuthError()
                        val url = viewModel.getAuthUrl()
                        if (url != null) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.smartcarApplicationId.isNotBlank() && state.smartcarClientId.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect to Vehicle")
                }

                // Show OAuth error returned from Smartcar Connect (e.g. invalid client_id)
                if (state.smartcarAuthError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.smartcarAuthError!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
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
                onCheckedChange = {
                    viewModel.toggleAutoRecord()
                    com.ecodrive.app.util.HapticHelper.playClick(context)
                }
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
                onCheckedChange = {
                    viewModel.toggleObd()
                    com.ecodrive.app.util.HapticHelper.playClick(context)
                }
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
            SettingsCheckRow(
                label = "Live Coaching",
                checked = state.liveCoachingEnabled,
                onCheckedChange = {
                    viewModel.toggleLiveCoaching()
                    com.ecodrive.app.util.HapticHelper.playClick(context)
                }
            )
            Text(
                text = "Provides real-time spoken driving tips and safety alerts.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Coach Voice",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val voiceOptions = listOf("DEFAULT", "JARVIS", "FRIDAY")
                val voiceLabels = listOf("Default", "Jarvis", "Friday")
                voiceOptions.forEachIndexed { index, voiceOption ->
                    SegmentedButton(
                        selected = state.coachVoice == voiceOption,
                        onClick = {
                            if (state.coachVoice != voiceOption) {
                                viewModel.setCoachVoice(voiceOption)
                                com.ecodrive.app.util.HapticHelper.playClick(context)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = voiceOptions.size),
                        label = { Text(voiceLabels[index]) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "EcoDrive automatically selects the best AI provider for optimal performance and cost efficiency.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── About ───────────────────────────────────────────────
        SettingsSection(title = "About", icon = Icons.Filled.Info) {
            val appVersion = "${com.ecodrive.app.BuildConfig.VERSION_NAME} (${com.ecodrive.app.BuildConfig.VERSION_CODE})"
            val buildType = com.ecodrive.app.BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }
            
            SettingsInfoRow("App Version", appVersion)
            SettingsInfoRow("Build", buildType)
            SettingsInfoRow("Package", com.ecodrive.app.BuildConfig.APPLICATION_ID)
            SettingsInfoRow("Engine", "Physics-based Power-to-Fuel Model")
            SettingsInfoRow("Calibration", "Self-improving via Smartcar API")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showAppearanceSheet) {
        AppearanceBottomSheet(
            currentTheme = state.appTheme,
            currentPalette = state.appPalette,
            currentFontScale = state.appFontScale,
            useMetric = state.useMetric,
            mapStyle = state.mapStyle,
            onThemeChange = viewModel::setAppTheme,
            onPaletteChange = viewModel::setColorPalette,
            onFontScaleChange = viewModel::setAppFontScale,
            onToggleUnits = viewModel::toggleUnits,
            onMapStyleChange = viewModel::setMapStyle,
            onDismiss = { showAppearanceSheet = false }
        )
    }

    // ── Edit Dialogs ──────────────────────────────────────────

    if (showEditNameDialog) {
        var text by remember { mutableStateOf(state.localVehicle?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Vehicle Name") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateVehicleName(text)
                    showEditNameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditOdometerDialog) {
        var text by remember { mutableStateOf(state.odometerKm?.toInt()?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { showEditOdometerDialog = false },
            title = { Text("Update Odometer") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) text = it },
                    label = { Text(if (state.useMetric) "Kilometers" else "Miles") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toDoubleOrNull()?.let { 
                        val km = if (state.useMetric) it else it / 0.621371
                        viewModel.updateOdometer(km) 
                    }
                    showEditOdometerDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditOdometerDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditFuelDialog) {
        var sliderValue by remember { mutableStateOf((state.fuelTankPercent ?: 50.0).toFloat()) }
        AlertDialog(
            onDismissRequest = { showEditFuelDialog = false },
            title = { Text("Update Fuel Level") },
            text = {
                Column {
                    Text(
                        text = "%.0f%%".format(sliderValue),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateFuelLevel(sliderValue.toDouble())
                    showEditFuelDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditFuelDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsEditableRow(
    label: String,
    value: String,
    isEditable: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEditable, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (isEditable) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceBottomSheet(
    currentTheme: AppTheme,
    currentPalette: AppColorPalette,
    currentFontScale: AppFontScale,
    useMetric: Boolean,
    mapStyle: com.ecodrive.app.util.MapStyle,
    onThemeChange: (AppTheme) -> Unit,
    onPaletteChange: (AppColorPalette) -> Unit,
    onFontScaleChange: (AppFontScale) -> Unit,
    onToggleUnits: () -> Unit,
    onMapStyleChange: (com.ecodrive.app.util.MapStyle) -> Unit,
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
            currentFontScale = currentFontScale,
            useMetric = useMetric,
            mapStyle = mapStyle,
            onThemeChange = onThemeChange,
            onPaletteChange = onPaletteChange,
            onFontScaleChange = onFontScaleChange,
            onToggleUnits = onToggleUnits,
            onMapStyleChange = onMapStyleChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSelector(
    currentTheme: AppTheme,
    currentPalette: AppColorPalette,
    currentFontScale: AppFontScale,
    useMetric: Boolean,
    mapStyle: com.ecodrive.app.util.MapStyle,
    onThemeChange: (AppTheme) -> Unit,
    onPaletteChange: (AppColorPalette) -> Unit,
    onFontScaleChange: (AppFontScale) -> Unit,
    onToggleUnits: () -> Unit,
    onMapStyleChange: (com.ecodrive.app.util.MapStyle) -> Unit,
) {
    val context = LocalContext.current
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
                        onClick = {
                            onThemeChange(theme)
                            com.ecodrive.app.util.HapticHelper.playClick(context)
                        },
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

        // Font Scale Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Text Size",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppFontScale.entries.forEachIndexed { index, scale ->
                    SegmentedButton(
                        selected = currentFontScale == scale,
                        onClick = {
                            if (currentFontScale != scale) {
                                onFontScaleChange(scale)
                                com.ecodrive.app.util.HapticHelper.playClick(context)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = AppFontScale.entries.size),
                        label = { Text(scale.getDisplayName()) }
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
                        onClick = {
                            if (useMetric != isMetric) {
                                onToggleUnits()
                                com.ecodrive.app.util.HapticHelper.playClick(context)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        label = { Text(label) }
                    )
                }
            }
        }

        // Map Style Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Map Style",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                com.ecodrive.app.util.MapStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = mapStyle == style,
                        onClick = {
                            if (mapStyle != style) {
                                onMapStyleChange(style)
                                com.ecodrive.app.util.HapticHelper.playClick(context)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = com.ecodrive.app.util.MapStyle.entries.size),
                        label = { 
                            Text(
                                when (style) {
                                    com.ecodrive.app.util.MapStyle.DEFAULT -> "Minimal"
                                    com.ecodrive.app.util.MapStyle.TERRAIN -> "Terrain"
                                    com.ecodrive.app.util.MapStyle.STREETS -> "Streets"
                                }
                            ) 
                        }
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
                        onClick = {
                            onPaletteChange(palette)
                            com.ecodrive.app.util.HapticHelper.playClick(context)
                        }
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
