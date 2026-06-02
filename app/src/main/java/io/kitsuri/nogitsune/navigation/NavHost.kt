package io.kitsuri.nogitsune.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.kitsuri.nogitsune.ui.screens.HomeScreen
import io.kitsuri.nogitsune.ui.screens.InfoScreen
import io.kitsuri.nogitsune.ui.screens.SettingsScreen

@Composable
fun NavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    androidx.navigation.compose.NavHost(
        navController = navController,
        startDestination = NavDestination.Home.route,
        modifier = modifier,
    ) {
        composable(NavDestination.Home.route) { HomeScreen() }
        composable(NavDestination.Settings.route) { SettingsScreen() }
        composable(NavDestination.Info.route) { InfoScreen() }
    }
}
