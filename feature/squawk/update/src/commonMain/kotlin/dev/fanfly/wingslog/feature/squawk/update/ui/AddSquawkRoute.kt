package dev.fanfly.wingslog.feature.squawk.update.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.common.navigation.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.SquawkFormEvent
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.SquawkFormViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_added

@Composable
fun AddSquawkRoute(
  navController: NavController,
  viewModel: SquawkFormViewModel = koinViewModel(),
  attachmentSection: @Composable () -> Unit = {},
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val successMessage = stringResource(Res.string.squawk_added)

  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        is SquawkFormEvent.NavigateBack -> navController.popBackStack()
        is SquawkFormEvent.SaveSuccess -> {
          navController.previousBackStackEntry?.savedStateHandle
            ?.set(CROSS_SCREEN_SUCCESS_MESSAGE, event.message)
          navController.popBackStack()
        }
        is SquawkFormEvent.NavigateToLog -> {
          navController.navigate(Screen.EditMaintenanceLog.createRoute(event.aircraftId, event.logId))
        }
      }
    }
  }

  SquawkFormScreen(
    state = state,
    onTitleChange = viewModel::onTitleChange,
    onDescriptionChange = viewModel::onDescriptionChange,
    onPriorityChange = viewModel::onPriorityChange,
    onSave = { viewModel.save(successMessage) },
    onBack = viewModel::onBack,
    onViewLog = null,
    attachmentSection = attachmentSection,
  )
}
