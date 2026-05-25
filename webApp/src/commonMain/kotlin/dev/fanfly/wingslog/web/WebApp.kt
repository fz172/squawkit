package dev.fanfly.wingslog.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.aircraft.dashboard.AircraftOverviewScreen
import dev.fanfly.wingslog.feature.fleet.viewing.DashboardScreen
import dev.fanfly.wingslog.feature.login.AuthFlow

@Composable
fun WebApp() {
    WingslogTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = Screen.Login.route,
            ) {
                composable(Screen.Login.route) {
                    AuthFlow(
                        onComplete = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onOpenSettings = null,
                        onAddAircraft = null,
                        onAircraftClick = { aircraftId ->
                            navController.navigate(Screen.MaintenanceOverview.createRoute(aircraftId))
                        },
                    )
                }
                composable(
                    route = Screen.MaintenanceOverview.route,
                    arguments = listOf(navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType }),
                ) {
                    AircraftOverviewScreen(
                        navController = navController,
                        onMutationAction = null,
                        attachmentsAvailable = false,
                    )
                }
            }
        }
    }
}
