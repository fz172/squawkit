package dev.fanfly.wingslog.feature.shell

import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.adaptive.compose.AdaptiveFormDialogFrame
import dev.fanfly.wingslog.feature.aircraft.update.EditAircraftScreen
import dev.fanfly.wingslog.feature.export.update.ExportHistoryRoute
import dev.fanfly.wingslog.feature.export.update.ExportSelectionRoute
import dev.fanfly.wingslog.feature.logs.update.logs.MaintenanceLogFormScreen
import dev.fanfly.wingslog.feature.settings.developeroptions.DeveloperOptionsScreen
import dev.fanfly.wingslog.feature.sharing.update.EnterInviteCodeRoute
import dev.fanfly.wingslog.feature.sharing.update.InviteSheetRoute
import dev.fanfly.wingslog.feature.sharing.update.ManageAccessRoute
import dev.fanfly.wingslog.feature.squawk.update.ui.AddSquawkRoute
import dev.fanfly.wingslog.feature.squawk.update.ui.EditSquawkRoute
import dev.fanfly.wingslog.feature.stresstest.config.StressTestDeveloperOptionsExtra
import dev.fanfly.wingslog.feature.stresstest.config.registerStressTestRoutes
import dev.fanfly.wingslog.feature.sync.settings.SyncSettingsScreen
import dev.fanfly.wingslog.feature.tasks.update.ui.AddTaskRoute
import dev.fanfly.wingslog.feature.tasks.update.ui.EditTaskRoute
import dev.fanfly.wingslog.feature.technician.manage.compose.EditTechnicianScreen
import dev.fanfly.wingslog.feature.technician.manage.compose.TechnicianListScreen
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.TechnicianListViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The eight add/edit form dialogs shared by every host. Registered once on the root graph;
 * each renders inside [AdaptiveFormDialogFrame] so compact tiers get a full-screen sheet and
 * larger tiers a centered dialog.
 */
fun NavGraphBuilder.formDialogs(navController: NavController) {
  dialog(
    route = Screen.AddAircraft.route,
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      EditAircraftScreen(navController = navController)
    }
  }
  // A real centered dialog (not the full-screen form frame): the screen renders its own card over
  // the scrimmed fleet, so it looks the same on a phone and a wide window.
  dialog(
    route = Screen.EnterInviteCode.route,
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    EnterInviteCodeRoute(navController = navController)
  }
  dialog(
    route = Screen.EditAircraft.route,
    arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
      type = NavType.StringType
      nullable = true
    }),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      EditAircraftScreen(navController = navController)
    }
  }
  dialog(
    route = Screen.AddMaintenanceTask.route,
    arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
      type = NavType.StringType
    }),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      AddTaskRoute(navController = navController)
    }
  }
  dialog(
    route = Screen.EditMaintenanceTask.route,
    arguments = listOf(
      navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
      navArgument(Screen.CARD_ID) { type = NavType.StringType },
    ),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      EditTaskRoute(navController = navController)
    }
  }
  dialog(
    route = Screen.AddMaintenanceLog.route,
    arguments = listOf(
      navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
      navArgument(Screen.SQUAWK_ID) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
      },
    ),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      MaintenanceLogFormScreen(navController = navController)
    }
  }
  dialog(
    route = Screen.EditMaintenanceLog.route,
    arguments = listOf(
      navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
      navArgument(Screen.LOG_ID) { type = NavType.StringType },
    ),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      MaintenanceLogFormScreen(navController = navController)
    }
  }
  dialog(
    route = Screen.AddSquawk.route,
    arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
      type = NavType.StringType
    }),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      AddSquawkRoute(navController = navController)
    }
  }
  dialog(
    route = Screen.EditSquawk.route,
    arguments = listOf(
      navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
      navArgument(Screen.SQUAWK_ID) { type = NavType.StringType },
    ),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      EditSquawkRoute(navController = navController)
    }
  }
}

/**
 * The Settings detail destinations. Registered once on the outer graph (compact tiers open them
 * full-screen; EditTechnician is also reachable from the maintenance-log form) and once per
 * nested settings NavHost (so detail pages render in the content pane beside the sidebar).
 * [navController] is whichever graph the caller is wiring.
 *
 * Stress-test routes and the DeveloperOptions debug entry are gated on [isStressTestSupported]
 * (`AppCapability.isStressTestSupported`) on every host.
 */
/**
 * Per-aircraft sharing destinations, registered once on the root graph so both hosts render them.
 * Reached from an aircraft's context (the entry point + role-gated visibility land with #133).
 */
fun NavGraphBuilder.sharingRoutes(navController: NavController) {
  composable(
    route = Screen.ManageAccess.route,
    arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
      type = NavType.StringType
    }),
  ) {
    ManageAccessRoute(navController = navController)
  }
  dialog(
    route = Screen.InviteToAircraft.route,
    arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
      type = NavType.StringType
    }),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      InviteSheetRoute(navController = navController)
    }
  }
}

fun NavGraphBuilder.settingsDetailRoutes(
  navController: NavController,
  isStressTestSupported: Boolean,
) {
  composable(Screen.SyncSettings.route) {
    SyncSettingsScreen(navController = navController)
  }
  composable(Screen.ExportLogs.route) {
    ExportSelectionRoute(
      navController = navController,
      onNavigateToHistory = { navController.navigate(Screen.ExportHistory.route) },
    )
  }
  composable(Screen.ExportHistory.route) {
    ExportHistoryRoute(navController = navController)
  }
  composable(Screen.DeveloperOptions.route) {
    DeveloperOptionsScreen(
      navController = navController,
      dogfoodContent = {
        if (isStressTestSupported) StressTestDeveloperOptionsExtra(
          navController
        )
      },
    )
  }
  if (isStressTestSupported) {
    registerStressTestRoutes(this, navController)
  }
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
