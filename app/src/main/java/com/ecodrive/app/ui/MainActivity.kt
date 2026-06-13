package com.ecodrive.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.domain.model.AppColorPalette
import com.ecodrive.app.domain.model.AppTheme
import com.ecodrive.app.ui.navigation.Screen
import com.ecodrive.app.ui.screens.analytics.AnalyticsScreen
import com.ecodrive.app.ui.screens.coach.CoachScreen
import com.ecodrive.app.ui.screens.dashboard.DashboardScreen
import com.ecodrive.app.ui.screens.dashboard.DashboardViewModel
import com.ecodrive.app.ui.screens.routeplanner.RoutePlannerScreen
import com.ecodrive.app.ui.screens.settings.SettingsScreen
import com.ecodrive.app.ui.screens.tripdetail.TripDetailScreen
import com.ecodrive.app.ui.screens.trips.TripsScreen
import com.ecodrive.app.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main entry Activity for EcoDrive.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val appTheme by preferenceManager.appTheme.collectAsStateWithLifecycle(initialValue = AppTheme.DARK)
            val appPalette by preferenceManager.colorPalette.collectAsStateWithLifecycle(initialValue = AppColorPalette.ECO_GREEN)
            val appFontScale by preferenceManager.appFontScale.collectAsStateWithLifecycle(initialValue = com.ecodrive.app.domain.model.AppFontScale.MEDIUM)

            LaunchedEffect(preferenceManager) {
                launch {
                    preferenceManager.useGoogleMaps.collect { useGoogleMaps ->
                        com.ecodrive.app.util.AppConfig.ACTIVE_MAP_PROVIDER = if (useGoogleMaps) {
                            com.ecodrive.app.util.MapProvider.GOOGLE_MAPS
                        } else {
                            com.ecodrive.app.util.MapProvider.OPEN_STREET_MAP
                        }
                    }
                }
                launch {
                    preferenceManager.mapStyle.collect { mapStyle ->
                        com.ecodrive.app.util.AppConfig.ACTIVE_MAP_STYLE = mapStyle
                    }
                }
                launch {
                    com.ecodrive.app.util.MapErrorNotifier.fallbackEvent.collect {
                        // Switch back to free tiles
                        preferenceManager.setMapStyle(com.ecodrive.app.util.MapStyle.DEFAULT)
                        
                        kotlinx.coroutines.Dispatchers.Main.dispatch(kotlin.coroutines.EmptyCoroutineContext, Runnable {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "MapTiler API limit reached. Falling back to free map.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        })
                    }
                }
                launch {
                    preferenceManager.keepDisplayOn.collect { keepDisplayOn ->
                        if (keepDisplayOn) {
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }

            EcoDriveTheme(
                appTheme = appTheme,
                appPalette = appPalette,
                appFontScale = appFontScale
            ) {
                EcoDriveApp()
            }
        }
    }

    companion object {
        val authCodeFlow = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String?>?>(null)
        val authErrorFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            if (uri.scheme == "ecodrive" && uri.host == "callback") {
                val code = uri.getQueryParameter("code")
                val userId = uri.getQueryParameter("user_id")
                val error = uri.getQueryParameter("error")
                val errorDescription = uri.getQueryParameter("error_description")

                when {
                    code != null -> {
                        authCodeFlow.value = Pair(code, userId)
                    }
                    error != null -> {
                        // Smartcar returned an error — surface it to the UI
                        val message = when {
                            errorDescription != null -> "$error: $errorDescription"
                            else -> error
                        }
                        authErrorFlow.value = message
                        android.util.Log.e("MainActivity", "Smartcar OAuth error: $message")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcoDriveApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route?.let { route ->
        Screen.bottomNavItems.any { it.route == route }
    } ?: true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon
                                    else screen.unselectedIcon,
                                    contentDescription = screen.title,
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Dashboard.route) {
                DashboardWithPermissions(
                    onPlanRoute = { navController.navigate(Screen.RoutePlanner.route) }
                )
            }
            composable(Screen.Trips.route) {
                TripsScreen(
                    onTripClick = { tripId ->
                        navController.navigate(Screen.TripDetail.createRoute(tripId))
                    },
                )
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen()
            }
            composable(Screen.Coach.route) {
                CoachScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            composable(Screen.RoutePlanner.route) {
                RoutePlannerScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.TripDetail.route,
                arguments = listOf(
                    navArgument("tripId") { type = NavType.LongType }
                ),
            ) {
                TripDetailScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
fun DashboardWithPermissions(onPlanRoute: () -> Unit) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.onPermissionsGranted()
        }
    }

    if (state.needsPermissions) {
        PermissionRequestScreen(
            onRequestPermissions = {
                val missing = viewModel.permissionManager.getMissingPermissions()
                permissionLauncher.launch(missing.toTypedArray())
            },
        )
    } else {
        DashboardScreen(viewModel = viewModel, onPlanRoute = onPlanRoute)
    }
}

@Composable
private fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "EcoDrive needs access to your location to measure driving speed " +
                    "and distance using GPS. This data stays on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermissionItem(
                icon = Icons.Filled.MyLocation,
                title = "Location (GPS)",
                description = "Speed, distance, route tracking",
            )
            PermissionItem(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                description = "Background recording indicator",
            )
            PermissionItem(
                icon = Icons.Filled.DirectionsCar,
                title = "Activity Recognition",
                description = "Detect when you are in a vehicle",
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
        ) {
            Text(
                text = "Grant Permissions",
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Accelerometer & gyroscope don't require permissions",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
