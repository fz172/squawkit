package dev.fanfly.wingslog.feature.aircraft.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.aircraft.overview.compose.AddInspectionScreen
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AddInspectionRoute(
  navController: NavController,
  viewModel: AircraftOverviewViewModel = koinViewModel()
) {
  AddInspectionScreen(
    onBackClick = { navController.popBackStack() },
    onSave = { title, type, component, rules, refNum, url, details, oneTime, notes ->
      viewModel.saveNewInspection(title, type, component, rules, refNum, url, details, oneTime, notes)
      navController.popBackStack()
    }
  )
}
