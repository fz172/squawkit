package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.EditTechnicianViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Instant
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.add_technician
import wingslog.feature.technician.sharedassets.generated.resources.certificate_number
import wingslog.feature.technician.sharedassets.generated.resources.certificate_type
import wingslog.feature.technician.sharedassets.generated.resources.edit_technician
import wingslog.feature.technician.sharedassets.generated.resources.expiration_date
import wingslog.feature.technician.sharedassets.generated.resources.name_required
import wingslog.feature.technician.sharedassets.generated.resources.no_expiration
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTechnicianScreen(
  viewModel: EditTechnicianViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  var showDatePicker by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.saveSuccess) {
    if (uiState.saveSuccess) {
      onNavigateBack()
    }
  }

  Scaffold(
    modifier = modifier.imePadding(),
    topBar = {
      TopAppBar(
        title = {
          Text(
            if (uiState.id.isEmpty()) stringResource(TechnicianRes.string.add_technician)
            else stringResource(TechnicianRes.string.edit_technician)
          )
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
        actions = {
          IconButton(onClick = viewModel::save, enabled = !uiState.isSaving) {
            Icon(Icons.Default.Check, contentDescription = null)
          }
        }
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium)
    ) {
      if (uiState.error != null) {
        Text(
          text = uiState.error!!,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium
        )
      }

      OutlinedTextField(
        value = uiState.name,
        onValueChange = viewModel::updateName,
        label = { Text(stringResource(TechnicianRes.string.name_required)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
      )

      OutlinedTextField(
        value = uiState.certType,
        onValueChange = viewModel::updateCertType,
        label = { Text(stringResource(TechnicianRes.string.certificate_type)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
      )

      OutlinedTextField(
        value = uiState.certNumber,
        onValueChange = viewModel::updateCertNumber,
        label = { Text(stringResource(TechnicianRes.string.certificate_number)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
      )

      val selectedDate = uiState.certExpiration?.toLocalDateTime(TimeZone.UTC)?.date
      val dateDisplayText = selectedDate?.toString() ?: stringResource(TechnicianRes.string.no_expiration)

      OutlinedTextField(
        value = dateDisplayText,
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(TechnicianRes.string.expiration_date)) },
        leadingIcon = {
          Icon(
            Icons.Default.CalendarToday,
            contentDescription = stringResource(TechnicianRes.string.expiration_date)
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .clickable { showDatePicker = true },
        singleLine = true,
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
          disabledTextColor = MaterialTheme.colorScheme.onSurface,
          disabledBorderColor = MaterialTheme.colorScheme.outline,
          disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      )

      if (showDatePicker) {
        val initialMs = selectedDate?.let { date ->
          date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs)
        DatePickerDialog(
          onDismissRequest = { showDatePicker = false },
          confirmButton = {
            TextButton(onClick = {
              val selectedMs = datePickerState.selectedDateMillis
              if (selectedMs != null) {
                val newDateInstant = Instant.fromEpochMilliseconds(selectedMs)
                viewModel.updateCertExpiration(newDateInstant)
              } else {
                viewModel.updateCertExpiration(null)
              }
              showDatePicker = false
            }) {
              Text("OK")
            }
          },
          dismissButton = {
            TextButton(onClick = { showDatePicker = false }) {
              Text("Cancel")
            }
          }
        ) {
          DatePicker(state = datePickerState)
        }
      }
    }
  }
}
