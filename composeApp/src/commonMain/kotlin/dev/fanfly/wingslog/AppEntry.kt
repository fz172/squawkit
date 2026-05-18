package dev.fanfly.wingslog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseHealth
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.aircraft.dashboard.AircraftOverviewScreen
import dev.fanfly.wingslog.feature.fleet.viewing.DashboardScreen
import dev.fanfly.wingslog.feature.logs.update.aircraft.EditAircraftScreen
import dev.fanfly.wingslog.feature.logs.update.logs.MaintenanceLogFormScreen
import dev.fanfly.wingslog.feature.settings.SettingsScreen
import dev.fanfly.wingslog.feature.settings.featurelab.FeatureLabScreen
import dev.fanfly.wingslog.feature.squawk.update.ui.AddSquawkRoute
import dev.fanfly.wingslog.feature.squawk.update.ui.EditSquawkRoute
import dev.fanfly.wingslog.feature.sync.settings.SyncSettingsScreen
import dev.fanfly.wingslog.feature.tasks.update.ui.AddTaskRoute
import dev.fanfly.wingslog.feature.tasks.update.ui.EditTaskRoute
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.feature.technician.manage.compose.EditTechnicianScreen
import dev.fanfly.wingslog.feature.technician.manage.compose.TechnicianListScreen
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.TechnicianListViewModel
import dev.fanfly.wingslog.login.LoginScreen
import dev.fanfly.wingslog.onboarding.NameEntryScreen
import dev.fanfly.wingslog.onboarding.OnboardingPreferences
import dev.fanfly.wingslog.onboarding.WelcomeScreen
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val GRAPH_AUTH = "graph_auth"
private const val GRAPH_FLEET = "graph_fleet"
private const val GRAPH_AIRCRAFT = "graph_aircraft"
private const val GRAPH_SETTINGS = "graph_settings"

@Composable
fun AppEntry() {
  val health: DatabaseHealth = koinInject()
  val checker: DatabaseIntegrityChecker = koinInject()
  val authManager: AuthManager = koinInject()
  val firebaseAuth: FirebaseAuth = koinInject()
  val dogfoodExts: DogfoodFeatureExtensions = koinInject()
  val onboardingPrefs: OnboardingPreferences = koinInject()

  if (health.isCorrupted) {
    WingslogTheme {
      IntegrityRecoveryDialog(
        onWipe = {
          checker.wipeAllData()
          authManager.logOut()
        },
      )
    }
    return
  }

  WingslogTheme {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      val navController = rememberNavController()

      LaunchedEffect(Unit) {
        firebaseAuth.authStateChanged.collect { user ->
          if (user == null) {
            navController.navigate(Screen.Login.route) {
              popUpTo(0) { inclusive = true }
            }
          }
        }
      }

      NavHost(
        navController,
        startDestination = GRAPH_AUTH
      ) {
        authGraph(navController, onboardingPrefs)
        fleetGraph(navController)
        aircraftGraph(navController)
        settingsGraph(navController, dogfoodExts)
      }
    }
  }
}

private fun NavGraphBuilder.authGraph(
  navController: NavController,
  onboardingPrefs: OnboardingPreferences,
) {
  navigation(
    startDestination = Screen.Login.route,
    route = GRAPH_AUTH
  ) {
    composable(Screen.Login.route) {
      LoginScreen(
        onLoginSuccess = { navController.navigate(Screen.NameEntry.route) },
      )
    }
    composable(Screen.NameEntry.route) {
      val technicianManager: TechnicianManager = koinInject()
      val firebaseAuth: FirebaseAuth = koinInject()
      val scope = rememberCoroutineScope()
      var nameChecked by remember { mutableStateOf(false) }
      LaunchedEffect(Unit) {
        val firebaseDisplayName =
          firebaseAuth.currentUser?.displayName.orEmpty()
        val selfName = technicianManager.observeSelf()
          .firstOrNull()?.name.orEmpty()
        if (firebaseDisplayName.isNotEmpty() || selfName.isNotBlank()) {
          navController.navigate(Screen.Welcome.route) {
            popUpTo(Screen.NameEntry.route) { inclusive = true }
          }
        } else {
          nameChecked = true
        }
      }
      if (nameChecked) {
        NameEntryScreen(
          initialName = firebaseAuth.currentUser?.displayName.orEmpty(),
          onBack = { navController.popBackStack() },
          onNext = { name ->
            scope.launch { technicianManager.saveSelfName(name) }
            navController.navigate(Screen.Welcome.route)
          },
        )
      }
    }
    composable(Screen.Welcome.route) {
      val technicianManager: TechnicianManager = koinInject()
      val selfTech by technicianManager.observeSelf()
        .collectAsStateWithLifecycle(null)
      var welcomeChecked by remember { mutableStateOf(false) }
      LaunchedEffect(Unit) {
        val seen =
          withContext(Dispatchers.IO) { onboardingPrefs.checkHasSeenWelcome() }
        if (seen) {
          navController.navigate(GRAPH_FLEET) {
            popUpTo(GRAPH_AUTH) { inclusive = true }
          }
        } else {
          welcomeChecked = true
        }
      }
      if (welcomeChecked) {
        WelcomeScreen(
          name = selfTech?.name.orEmpty(),
          onDone = {
            onboardingPrefs.setHasSeenWelcome()
            navController.navigate(GRAPH_FLEET) {
              popUpTo(GRAPH_AUTH) { inclusive = true }
            }
          },
        )
      }
    }
  }
}

private fun NavGraphBuilder.fleetGraph(navController: NavController) {
  navigation(
    startDestination = Screen.Dashboard.route,
    route = GRAPH_FLEET
  ) {
    composable(Screen.Dashboard.route) {
      DashboardScreen(
        onOpenSettings = { navController.navigate(GRAPH_SETTINGS) },
        onAddAircraft = { navController.navigate(Screen.AddAircraft.route) },
        onAircraftClick = { aircraftId ->
          navController.navigate(
            Screen.MaintenanceOverview.createRoute(
              aircraftId
            )
          )
        },
      )
    }
    composable(route = Screen.AddAircraft.route) {
      EditAircraftScreen(navController = navController)
    }
    composable(
      route = Screen.EditAircraft.route,
      arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
        type = NavType.StringType
        nullable = true
      }),
    ) {
      EditAircraftScreen(navController = navController)
    }
  }
}

private fun NavGraphBuilder.aircraftGraph(navController: NavController) {
  navigation(
    startDestination = Screen.MaintenanceOverview.route,
    route = GRAPH_AIRCRAFT
  ) {
    composable(
      route = Screen.MaintenanceOverview.route,
      arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
        type = NavType.StringType
      }),
    ) {
      AircraftOverviewScreen(navController = navController)
    }
    composable(
      route = Screen.AddMaintenanceTask.route,
      arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
        type = NavType.StringType
      }),
    ) {
      AddTaskRoute(navController = navController)
    }
    composable(
      route = Screen.EditMaintenanceTask.route,
      arguments = listOf(
        navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
        navArgument(Screen.CARD_ID) { type = NavType.StringType },
      ),
    ) {
      EditTaskRoute(navController = navController)
    }
    composable(
      route = Screen.AddMaintenanceLog.route,
      arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
        type = NavType.StringType
      }),
    ) {
      MaintenanceLogFormScreen(navController = navController)
    }
    composable(
      route = Screen.EditMaintenanceLog.route,
      arguments = listOf(
        navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
        navArgument(Screen.LOG_ID) { type = NavType.StringType },
      ),
    ) {
      MaintenanceLogFormScreen(navController = navController)
    }
    composable(
      route = Screen.AddSquawk.route,
      arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
        type = NavType.StringType
      }),
    ) {
      AddSquawkRoute(navController = navController)
    }
    composable(
      route = Screen.EditSquawk.route,
      arguments = listOf(
        navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
        navArgument(Screen.SQUAWK_ID) { type = NavType.StringType },
      ),
    ) {
      EditSquawkRoute(navController = navController)
    }
  }
}

private fun NavGraphBuilder.settingsGraph(
  navController: NavController,
  dogfoodExts: DogfoodFeatureExtensions
) {
  navigation(
    startDestination = Screen.Settings.route,
    route = GRAPH_SETTINGS
  ) {
    composable(Screen.Settings.route) {
      SettingsScreen(navController = navController)
    }
    composable(Screen.SyncSettings.route) {
      SyncSettingsScreen(navController = navController)
    }
    composable(Screen.FeatureLab.route) {
      FeatureLabScreen(
        navController = navController,
        dogfoodContent = { dogfoodExts.FeatureLabExtra(navController) },
      )
    }
    dogfoodExts.registerRoutes(this, navController)
    composable(Screen.ManageTechnicians.route) {
      val viewModel = koinViewModel<TechnicianListViewModel>()
      TechnicianListScreen(
        viewModel = viewModel,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToEdit = { id ->
          navController.navigate(
            Screen.EditTechnician.createRoute(
              id
            )
          )
        },
      )
    }
    composable(
      route = Screen.EditTechnician.route,
      arguments = listOf(navArgument(Screen.TECHNICIAN_ID) {
        type = NavType.StringType
        nullable = true
      }),
    ) {
      EditTechnicianScreen(
        viewModel = koinViewModel(),
        onNavigateBack = { navController.popBackStack() },
      )
    }
  }
}
