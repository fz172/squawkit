package dev.fanfly.wingslog.feature.aircraft.edit

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.feature.aircraft.edit.data.EditAircraftViewModel
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.component_airframe
import wingslog.core.ui.generated.resources.component_engine
import wingslog.core.ui.generated.resources.delete
import wingslog.core.ui.generated.resources.required
import wingslog.feature.aircraft.generated.resources.add_aircraft
import wingslog.feature.aircraft.generated.resources.add_blade
import wingslog.feature.aircraft.generated.resources.add_engine
import wingslog.feature.aircraft.generated.resources.blade_serial_numbers
import wingslog.feature.aircraft.generated.resources.blade_with_index
import wingslog.feature.aircraft.generated.resources.delete_aircraft
import wingslog.feature.aircraft.generated.resources.engine_with_index
import wingslog.feature.aircraft.generated.resources.make
import wingslog.feature.aircraft.generated.resources.model
import wingslog.feature.aircraft.generated.resources.propeller_hub
import wingslog.feature.aircraft.generated.resources.remove_blade
import wingslog.feature.aircraft.generated.resources.remove_engine
import wingslog.feature.aircraft.generated.resources.serial
import wingslog.feature.aircraft.generated.resources.tail_number
import wingslog.feature.aircraft.generated.resources.this_action_cannot_be_undone
import wingslog.feature.aircraft.generated.resources.update_aircraft
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAircraftScreen(
  viewModel: EditAircraftViewModel = koinViewModel(), navController: NavController
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

  // This effect will run when isSaved becomes true
  LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
    if (uiState.isSaved || uiState.isDeleted) {
      // Navigate back when save or delete is successful
      if (uiState.isDeleted) {
        navController.popBackStack("dashboard", inclusive = false)
      } else {
        navController.popBackStack()
      }
    }
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(cmpStringResource(AircraftRes.string.delete_aircraft)) },
      text = { Text(cmpStringResource(AircraftRes.string.this_action_cannot_be_undone)) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteAircraft()
            showDeleteDialog = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
          Text(cmpStringResource(CoreRes.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(cmpStringResource(CoreRes.string.cancel))
        }
      })
  }

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      WingsLogTopAppBar(
        title = if (uiState.aircraft.id == "") cmpStringResource(AircraftRes.string.add_aircraft)
        else cmpStringResource(AircraftRes.string.update_aircraft),
        onBackClick = { navController.popBackStack() },
        scrollBehavior = scrollBehavior,
      )
    }
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .imePadding()
          .verticalScroll(scrollState)
          .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
      ) {
        // AIRFRAME
        Text(
          text = cmpStringResource(CoreRes.string.component_airframe).uppercase()
        )
        AirframeSection(uiState.aircraft, viewModel, uiState.showValidationErrors)

        // ENGINE
        Text(
          text = cmpStringResource(CoreRes.string.component_engine).uppercase()
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
          label = cmpStringResource(
            AircraftRes.string.add_engine
          ), modifier = Modifier.fillMaxWidth(), onClick = { viewModel.onAddEngine() })

        Spacer(Modifier.height(88.dp))
      }
      BottomButtons(
        modifier = Modifier.align(Alignment.BottomCenter),
        saveEnabled = !uiState.isLoading,
        onSaveClick = { viewModel.saveAircraft() },
        onCancelClick = { navController.popBackStack() },
        onDeleteClick = if (uiState.aircraft.id != "") {
          { showDeleteDialog = true }
        } else null,
        saveLabel = if (uiState.aircraft.id == "") cmpStringResource(AircraftRes.string.add_aircraft) else cmpStringResource(
          AircraftRes.string.update_aircraft
        )
      )
    }
  }
}

@Composable
fun AirframeSection(
  aircraft: Aircraft, viewModel: EditAircraftViewModel, showValidationErrors: Boolean
) {
  Card(
    modifier = Modifier.padding(vertical = 8.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {

      // --- Make Number ---
      InputField(
        value = aircraft.make, // Read from ViewModel
        onValueChange = { viewModel.onMakeChanged(it) }, // Update ViewModel
        label = cmpStringResource(AircraftRes.string.make),
        enabled = aircraft.id == "",
        isError = showValidationErrors && aircraft.make.isBlank()
      )
      // --- Model Number ---
      InputField(
        value = aircraft.model, // Read from ViewModel
        onValueChange = { viewModel.onModelChanged(it) }, // Update ViewModel
        label = cmpStringResource(AircraftRes.string.model),
        enabled = aircraft.id == "",
        isError = showValidationErrors && aircraft.model.isBlank()
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // --- Serial Number ---
        InputField(
          value = aircraft.serial, // Read from ViewModel
          onValueChange = { viewModel.onSerialChanged(it) }, // Update ViewModel
          label = cmpStringResource(AircraftRes.string.serial),
          modifier = Modifier.weight(1f), // Takes up 50%
          enabled = aircraft.id == "",
          isError = showValidationErrors && aircraft.serial.isBlank(),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
        // --- Tail Number ---
        InputField(
          value = aircraft.tail_number, // Read from ViewModel
          onValueChange = { viewModel.onTailNumberChanged(it) }, // Update ViewModel
          label = cmpStringResource(AircraftRes.string.tail_number),
          modifier = Modifier.weight(1f), // Takes up 50%
          enabled = aircraft.id == "",
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
      }
    }
  }
}

@Composable
fun EngineSection(
  engineIndex: Int,
  engine: Engine,
  viewModel: EditAircraftViewModel,
  showValidationErrors: Boolean
) {
  Card(
    modifier = Modifier.padding(vertical = 8.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
  ) {

    Column(modifier = Modifier.padding(12.dp)) {
      Box(modifier = Modifier.fillMaxWidth()) {
        Text(
          cmpStringResource(AircraftRes.string.engine_with_index, engineIndex + 1),
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
            contentDescription = cmpStringResource(AircraftRes.string.remove_engine),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      InputField(
        label = cmpStringResource(AircraftRes.string.make),
        value = engine.make,
        isError = showValidationErrors && engine.make.isBlank()
      ) {
        viewModel.onEngineMakeChanged(engineIndex, it)
      }

      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InputField(
          label = cmpStringResource(AircraftRes.string.model),
          value = engine.model,
          modifier = Modifier.weight(1f),
          isError = showValidationErrors && engine.model.isBlank()
        ) {
          viewModel.onEngineModelChanged(engineIndex, it)
        }
        InputField(
          label = cmpStringResource(AircraftRes.string.serial),
          value = engine.serial,
          modifier = Modifier.weight(1f),
          isError = showValidationErrors && engine.serial.isBlank(),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        ) {
          viewModel.onEngineSerialChanged(engineIndex, it)
        }
      }

      // Propeller Section
      Text(
        cmpStringResource(AircraftRes.string.propeller_hub),
        style = MaterialTheme.typography.labelSmall
      )
      val hub = engine.propeller?.hub ?: dev.fanfly.wingslog.aircraft.PropellerHub()
      InputField(
        label = cmpStringResource(AircraftRes.string.make), value = hub.make,

        isError = showValidationErrors && hub.make.isBlank()
      ) {
        viewModel.onPropellerHubMakeChanged(engineIndex, it)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InputField(
          label = cmpStringResource(AircraftRes.string.model),
          value = hub.model,
          modifier = Modifier.weight(1f),
          isError = showValidationErrors && hub.model.isBlank()
        ) {
          viewModel.onPropellerHubModelChanged(engineIndex, it)
        }
        InputField(
          label = cmpStringResource(AircraftRes.string.serial, ""),
          value = hub.serial,
          modifier = Modifier.weight(1f),
          isError = showValidationErrors && hub.serial.isBlank()
        ) {
          viewModel.onPropellerHubSerialChanged(engineIndex, it)
        }
      }


      // Blade Serial Numbers - Dynamic List
      Text(cmpStringResource(AircraftRes.string.blade_serial_numbers))
      val blades = engine.propeller?.blades ?: emptyList()
      // Chunked(2) allows us to create rows of 2 for that 50/50 look
      blades.withIndex().chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          pair.forEach { (bladeIndex, blade) ->
            InputField(
              label = cmpStringResource(
                AircraftRes.string.blade_with_index,
                bladeIndex + 1
              ),
              value = blade.serial,
              modifier = Modifier.weight(1f),
              trailingIcon = {
                IconButton(onClick = {
                  viewModel.onRemoveBlade(
                    engineIndex,
                    bladeIndex
                  )
                }) {
                  Icon(
                    Icons.Default.Close,
                    contentDescription = cmpStringResource(AircraftRes.string.remove_blade)
                  )
                }
              },
              isError = showValidationErrors && blade.serial.isBlank(),
              keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
            ) {
              viewModel.onPropellerBladeSerialChanged(engineIndex, bladeIndex, it)
            }
          }
          if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DashedButton(
          label = cmpStringResource(AircraftRes.string.add_blade),
          modifier = Modifier
            .weight(1f)
            .padding(vertical = 8.dp),

          onClick = { viewModel.onAddBlade(engineIndex) })
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
  isError: Boolean = false,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
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
  trailingIcon = trailingIcon,
  isError = isError,
  supportingText = { if (isError) Text(cmpStringResource(CoreRes.string.required)) },
  keyboardOptions = keyboardOptions
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