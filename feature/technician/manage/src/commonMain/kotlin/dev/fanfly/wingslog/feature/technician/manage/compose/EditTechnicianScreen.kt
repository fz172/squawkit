package dev.fanfly.wingslog.feature.technician.manage.compose

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.EditTechnicianViewModel
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.CertificateInputFields
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.cancel
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes
import wingslog.feature.technician.sharedassets.generated.resources.add_technician
import wingslog.feature.technician.sharedassets.generated.resources.delete_technician
import wingslog.feature.technician.sharedassets.generated.resources.delete_technician_confirmation
import wingslog.feature.technician.sharedassets.generated.resources.edit_technician
import wingslog.feature.technician.sharedassets.generated.resources.my_profile
import wingslog.feature.technician.sharedassets.generated.resources.name_required

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTechnicianScreen(
  viewModel: EditTechnicianViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  var showDeleteDialog by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.saveSuccess) {
    if (uiState.saveSuccess) onNavigateBack()
  }

  LaunchedEffect(uiState.deleteSuccess) {
    if (uiState.deleteSuccess) onNavigateBack()
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(TechnicianRes.string.delete_technician)) },
      text = { Text(stringResource(TechnicianRes.string.delete_technician_confirmation)) },
      confirmButton = {
        TextButton(onClick = {
          showDeleteDialog = false
          viewModel.delete()
        }) {
          Text(
            stringResource(TechnicianRes.string.delete_technician),
            color = MaterialTheme.colorScheme.error
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(stringResource(CoreRes.string.cancel))
        }
      }
    )
  }

  Scaffold(
    modifier = modifier.imePadding(),
    topBar = {
      TopAppBar(
        title = {
          Text(
            when {
              uiState.id.isEmpty() -> stringResource(TechnicianRes.string.add_technician)
              uiState.isSelf -> stringResource(TechnicianRes.string.my_profile)
              else -> stringResource(TechnicianRes.string.edit_technician)
            }
          )
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        if (uiState.error != null) {
          Text(
            text = uiState.error!!,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }

        OutlinedTextField(
          value = uiState.name,
          onValueChange = viewModel::updateName,
          label = { Text(stringResource(TechnicianRes.string.name_required)) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )

        CertificateInputFields(
          certType = uiState.certType,
          onCertTypeChanged = viewModel::updateCertType,
          certNumber = uiState.certNumber,
          onCertNumberChanged = viewModel::updateCertNumber,
          expireLimit = uiState.certExpireLimit,
          onExpireLimitChanged = viewModel::updateCertExpireLimit,
          expirationDate = uiState.certExpiration,
          onExpirationDateChanged = viewModel::updateCertExpiration,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      BottomButtons(
        onPrimaryClick = viewModel::save,
        onSecondaryClick = onNavigateBack,
        onDangerClick = if (uiState.id.isNotEmpty() && !uiState.isSelf) ({ showDeleteDialog = true }) else null,
        dangerLabel = stringResource(TechnicianRes.string.delete_technician),
        primaryEnabled = !uiState.isSaving,
        isPrimaryFunctionInProgress = uiState.isSaving,
      )
    }
  }
}
