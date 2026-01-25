package dev.fanfly.wingslog.fleet.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.R
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.aircraft
import dev.fanfly.wingslog.common.compose.BottomButtons
import dev.fanfly.wingslog.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.fleet.manage.data.ManageAircraftViewModel


@Composable
fun ManageAircraftScreen(
  viewModel: ManageAircraftViewModel = hiltViewModel(),
  aircraft: Aircraft = aircraft {},
  navController: NavController
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(aircraft) {
    if (uiState.isLoading) {
      viewModel.loadAircraft(aircraft)
    }
  }

  // This effect will run when isSaved becomes true
  LaunchedEffect(aircraft) {
    if (uiState.isSaved) {
      // Navigate back when save is successful
      navController.popBackStack()
    }
  }

  Scaffold(topBar = {
    WingsLogTopAppBar(
      title = if (uiState.aircraft.id == "") stringResource(R.string.add_aircraft) else stringResource(
        R.string.update_aircraft
      ), onBackClick = { navController.popBackStack() })
  }, bottomBar = {
    // This composable holds the buttons pinned to the bottom
    BottomButtons(
      saveEnabled = !uiState.isLoading,
      onSaveClick = {
        // TODO
      }, // Call ViewModel to save
      onCancelClick = { navController.popBackStack() })
  }) { innerPadding ->
    Column(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // --- Make Number ---
      OutlinedTextField(
        value = uiState.aircraft.make, // Read from ViewModel
        onValueChange = { viewModel.onMakeChanged(it) }, // Update ViewModel
        label = { Text(stringResource(R.string.aircraft_make)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        enabled = uiState.aircraft.id == ""
      )
      // --- Model Number ---
      OutlinedTextField(
        value = uiState.aircraft.model, // Read from ViewModel
        onValueChange = { viewModel.onModelChanged(it) }, // Update ViewModel
        label = { Text(stringResource(R.string.aircraft_model)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        enabled = uiState.aircraft.id == ""
      )
      // --- Model Number ---
      OutlinedTextField(
        value = uiState.aircraft.serial, // Read from ViewModel
        onValueChange = { viewModel.onSerialChanged(it) }, // Update ViewModel
        label = { Text(stringResource(R.string.aircraft_serial)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        enabled = uiState.aircraft.id == ""
      )
    }
  }

}