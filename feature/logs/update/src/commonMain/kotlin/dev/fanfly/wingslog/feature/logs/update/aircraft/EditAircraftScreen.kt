package dev.fanfly.wingslog.feature.logs.update.aircraft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.DashedButton
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.update.aircraft.compose.AirframeSection
import dev.fanfly.wingslog.feature.logs.update.aircraft.compose.EngineSection
import dev.fanfly.wingslog.feature.logs.update.aircraft.viewmodel.EditAircraftViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.add_aircraft
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.component_airframe
import wingslog.core.ui.generated.resources.component_engine
import wingslog.core.ui.generated.resources.delete
import wingslog.feature.logs.sharedassets.generated.resources.delete_aircraft
import wingslog.feature.logs.sharedassets.generated.resources.this_action_cannot_be_undone
import wingslog.feature.logs.update.generated.resources.add_engine
import wingslog.feature.logs.update.generated.resources.update_aircraft
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.logs.update.generated.resources.Res as MaintenanceRes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun EditAircraftScreen(
  viewModel: EditAircraftViewModel = koinViewModel(),
  navController: NavController,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }

  val tryNavigateBack = {
    if (uiState.hasChanges) showUnsavedChangesDialog = true
    else navController.popBackStack()
  }

  BackHandler(enabled = uiState.hasChanges) {
    showUnsavedChangesDialog = true
  }

  if (showUnsavedChangesDialog) {
    UnsavedChangesDialog(
      onConfirm = {
        showUnsavedChangesDialog = false
        navController.popBackStack()
      },
      onDismiss = { showUnsavedChangesDialog = false },
    )
  }

  // This effect will run when isSaved becomes true
  LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
    if (uiState.isSaved || uiState.isDeleted) {
      // Navigate back when save or delete is successful
      if (uiState.isDeleted) {
        navController.popBackStack(Screen.Dashboard.route, inclusive = false)
      } else {
        navController.popBackStack()
      }
    }
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(SharedRes.string.delete_aircraft)) },
      text = { Text(stringResource(SharedRes.string.this_action_cannot_be_undone)) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteAircraft()
            showDeleteDialog = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
          Text(stringResource(CoreRes.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(stringResource(CoreRes.string.cancel))
        }
      })
  }

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      WingsLogTopAppBar(
        title = if (uiState.aircraft.id == "") stringResource(CoreRes.string.add_aircraft)
        else stringResource(MaintenanceRes.string.update_aircraft),
        onBackClick = { tryNavigateBack() },
        scrollBehavior = scrollBehavior,
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize()
        .padding(innerPadding)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .imePadding()
          .verticalScroll(scrollState)
          .padding(
            horizontal = Spacing.screenPadding,
            vertical = Spacing.extraLarge
          ),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
      ) {
        // AIRFRAME
        Text(
          text = stringResource(CoreRes.string.component_airframe).uppercase()
        )
        AirframeSection(
          uiState.aircraft,
          viewModel,
          uiState.showValidationErrors
        )

        // ENGINE
        Text(
          text = stringResource(CoreRes.string.component_engine).uppercase()
        )
        uiState.aircraft.engine.forEachIndexed { index, engine ->
          EngineSection(
            engineIndex = index,
            engine = engine,
            viewModel = viewModel,
            showValidationErrors = uiState.showValidationErrors
          )
        }

        DashedButton(
          label = stringResource(
            MaintenanceRes.string.add_engine
          ),
          modifier = Modifier.fillMaxWidth(),
          onClick = { viewModel.onAddEngine() },
        )

        Spacer(Modifier.height(Spacing.buttonHeight + Spacing.huge))
      }
      BottomButtons(
        modifier = Modifier.align(Alignment.BottomCenter),
        primaryEnabled = !uiState.isLoading,
        onPrimaryClick = { viewModel.saveAircraft() },
        onSecondaryClick = { tryNavigateBack() },
        onDangerClick = if (uiState.aircraft.id != "") {
          { showDeleteDialog = true }
        } else null,
        primaryLabel = if (uiState.aircraft.id == "")
          stringResource(CoreRes.string.add_aircraft)
        else
          stringResource(MaintenanceRes.string.update_aircraft)
      )
    }
  }
}
