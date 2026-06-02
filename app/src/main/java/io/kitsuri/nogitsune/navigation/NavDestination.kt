package io.kitsuri.nogitsune.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : NavDestination("home", "Home", Icons.Default.Home)
    data object Settings : NavDestination("settings", "Settings", Icons.Default.Settings)
    data object Info : NavDestination("info", "Info", Icons.Default.Info)

    companion object {
        val navItems = listOf(Home, Settings, Info)
    }
}
