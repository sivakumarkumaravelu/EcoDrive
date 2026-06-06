package com.ecodrive.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation routes for the EcoDrive app.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector = Icons.Filled.Home,
    val unselectedIcon: ImageVector = Icons.Outlined.Home,
) {
    data object Dashboard : Screen(
        route = "dashboard",
        title = "Drive",
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed,
    )

    data object Trips : Screen(
        route = "trips",
        title = "Trips",
        selectedIcon = Icons.Filled.Route,
        unselectedIcon = Icons.Outlined.Route,
    )

    data object Analytics : Screen(
        route = "analytics",
        title = "Analytics",
        selectedIcon = Icons.Filled.Analytics,
        unselectedIcon = Icons.Outlined.Analytics,
    )

    data object Coach : Screen(
        route = "coach",
        title = "Coach",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School,
    )

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    )

    data object TripDetail : Screen(
        route = "trip_detail/{tripId}",
        title = "Trip Detail",
    ) {
        fun createRoute(tripId: Long): String = "trip_detail/$tripId"
    }

    data object RoutePlanner : Screen(
        route = "route_planner",
        title = "Route Planner",
    )

    companion object {
        val bottomNavItems = listOf(Dashboard, Trips, Analytics, Coach, Settings)
    }
}
