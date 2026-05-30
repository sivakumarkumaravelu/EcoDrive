package com.ecodrive.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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

    companion object {
        val bottomNavItems = listOf(Dashboard, Trips, Analytics, Coach, Settings)
    }
}
