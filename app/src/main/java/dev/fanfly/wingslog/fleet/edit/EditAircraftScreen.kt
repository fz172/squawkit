package dev.fanfly.wingslog.fleet.edit

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.R
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.aircraft.aircraft
import dev.fanfly.wingslog.common.compose.BottomButtons
import dev.fanfly.wingslog.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.fleet.edit.data.EditAircraftViewModel


@Composable
fun EditAircraftScreen(
  viewModel: EditAircraftViewModel = hiltViewModel(),
  aircraft: Aircraft = aircraft {},
  navController: NavController
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()

  LaunchedEffect(aircraft) {
    if (uiState.isLoading) {
      viewModel.loadAircraft(aircraft)
    }
  }

  // This effect will run when isSaved becomes true
  LaunchedEffect(uiState.isSaved) {
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
      saveEnabled = !uiState.isLoading && uiState.isValid, onSaveClick = {
        viewModel.saveAircraft()
      }, // Call ViewModel to save
      onCancelClick = { navController.popBackStack() })
  }) { innerPadding ->
    Column(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 20.dp)
        .verticalScroll(scrollState),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // AIRFRAME
      Text(
        text = stringResource(R.string.airframe).uppercase()
      )
      AirframeSection(uiState.aircraft, viewModel)

      // ENGINE
      Text(
        text = stringResource(R.string.powerplant).uppercase()
      )
      uiState.aircraft.engineList.forEachIndexed { index, engine ->
        EngineSection(
          engineIndex = index,
          engine = engine,
          viewModel = viewModel
        )
      }

      DashedButton(
        label = stringResource(
          R.string.add_engine
        ),
        modifier = Modifier
          .fillMaxWidth(),
        onClick = { viewModel.onAddEngine() }
      )

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}

@Composable
fun AirframeSection(aircraft: Aircraft, viewModel: EditAircraftViewModel) {
  Card(
    modifier = Modifier.padding(vertical = 8.dp)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {

      // --- Make Number ---
      InputField(
        value = aircraft.make, // Read from ViewModel
        onValueChange = { viewModel.onMakeChanged(it) }, // Update ViewModel
        label = stringResource(R.string.make), enabled = aircraft.id == ""
      )
      // --- Model Number ---
      InputField(
        value = aircraft.model, // Read from ViewModel
        onValueChange = { viewModel.onModelChanged(it) }, // Update ViewModel
        label = stringResource(R.string.model), enabled = aircraft.id == ""
      )
      Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // --- Serial Number ---
        InputField(
          value = aircraft.serial, // Read from ViewModel
          onValueChange = { viewModel.onSerialChanged(it) }, // Update ViewModel
          label = stringResource(R.string.serial), modifier = Modifier.weight(1f), // Takes up 50%
          enabled = aircraft.id == ""
        )
        // --- Tail Number ---
        InputField(
          value = aircraft.tailNumber, // Read from ViewModel
          onValueChange = { viewModel.onTailNumberChanged(it) }, // Update ViewModel
          label = stringResource(R.string.tail_number),
          modifier = Modifier.weight(1f), // Takes up 50%
          enabled = aircraft.id == ""
        )
      }
    }
  }
}

@Composable
fun EngineSection(
  engineIndex: Int,
  engine: Engine,
  viewModel: EditAircraftViewModel
) {
  Card(
    modifier = Modifier.padding(vertical = 8.dp)
  ) {

    Column(modifier = Modifier.padding(12.dp)) {
      Box(modifier = Modifier.fillMaxWidth()) {
        Text(
          stringResource(R.string.engine_with_index, engineIndex + 1),
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.align(Alignment.CenterStart)
        )
        IconButton(
          onClick = { viewModel.onRemoveEngine(engineIndex) },
          modifier = Modifier
            .align(Alignment.CenterEnd)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        ) {
          Icon(
            Icons.Default.Close,
            contentDescription = "Remove Engine",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      InputField(label = stringResource(R.string.make), value = engine.make) {
        viewModel.onEngineMakeChanged(engineIndex, it)
      }

      Row( horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InputField(
          label = stringResource(R.string.model),
          value = engine.model,
          modifier = Modifier.weight(1f)
        ) {
          viewModel.onEngineModelChanged(engineIndex, it)
        }
        InputField(
          label = stringResource(R.string.serial),
          value = engine.serial,
          modifier = Modifier.weight(1f)
        ) {
          viewModel.onEngineSerialChanged(engineIndex, it)
        }
      }

      // Propeller Section
      Text(stringResource(R.string.propeller_hub), style = MaterialTheme.typography.labelSmall)
      val hub = engine.propeller.hub
      Row( horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InputField(
          label = stringResource(R.string.make),
          value = hub.make,
          modifier = Modifier.weight(1f)
        ) {
          viewModel.onPropellerHubMakeChanged(engineIndex, it)
        }
        InputField(
          label = stringResource(R.string.model),
          value = hub.model,
          modifier = Modifier.weight(1f)
        ) {
          viewModel.onPropellerHubModelChanged(engineIndex, it)
        }
      }

      // Blade Serial Numbers - Dynamic List
      Text(stringResource(R.string.blade_serial_numbers))
      val blades = engine.propeller.bladesList
      // Chunked(2) allows us to create rows of 2 for that 50/50 look
      blades.withIndex().chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          pair.forEach { (bladeIndex, blade) ->
            InputField(
              label = stringResource(R.string.blade),
              value = blade.serial,
              modifier = Modifier.weight(1f),
              trailingIcon = {
                IconButton(onClick = { viewModel.onRemoveBlade(engineIndex, bladeIndex) }) {
                  Icon(Icons.Default.Close, contentDescription = "Remove Blade")
                }
              }
            ) {
              viewModel.onPropellerBladeSerialChanged(engineIndex, bladeIndex, it)
            }
          }
          if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DashedButton(
          label =
          stringResource(R.string.add_blade),
          modifier = Modifier
            .weight(1f)
            .padding(vertical = 8.dp),

          onClick = { viewModel.onAddBlade(engineIndex) }
        )
        Spacer(Modifier.weight(1f))
      }
    }
  }
}

@Composable
fun InputField(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  trailingIcon: @Composable (() -> Unit)? = null,
  onValueChange: (String) -> Unit
) = OutlinedTextField(
  value = value,
  onValueChange = onValueChange,
  label = { Text(label, fontSize = 10.sp) },
  modifier = modifier.padding(vertical = 4.dp),
  singleLine = true,
  shape = RoundedCornerShape(12.dp),
  enabled = enabled,
  trailingIcon = trailingIcon
)

@Composable
fun DashedButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val stroke = Stroke(
    width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
  )
  // 1. Get the standard colors for a card
  val colors = OutlinedTextFieldDefaults.colors()

  // 2. Extract the container color to use in drawing
  val containerColor = colors.unfocusedIndicatorColor
  val contentColor = colors.unfocusedLabelColor

  Box(
    modifier = modifier
      .height(56.dp)
      .drawWithContent {
        drawContent()
        drawRoundRect(
          color = containerColor,
          style = stroke,
          cornerRadius = CornerRadius(12.dp.toPx()) // Match card radius
        )
      }
      .clip(RoundedCornerShape(12.dp))
      .clickable { onClick() }, contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        Icons.Default.Add, contentDescription = null, tint = contentColor
      )
      Spacer(Modifier.width(8.dp))
      Text(
        label, color = contentColor, fontWeight = FontWeight.Bold
      )
    }
  }
}