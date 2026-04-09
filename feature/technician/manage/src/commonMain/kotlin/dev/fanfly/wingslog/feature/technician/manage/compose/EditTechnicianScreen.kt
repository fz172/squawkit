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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.EditTechnicianViewModel
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.CertificateInputFields
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.add_technician
import wingslog.feature.technician.sharedassets.generated.resources.edit_technician
import wingslog.feature.technician.sharedassets.generated.resources.name_required
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTechnicianScreen(
  viewModel: EditTechnicianViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()

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

      CertificateInputFields(
        licenseType = uiState.certType,
        onLicenseTypeChanged = viewModel::updateCertType,
        licenseNumber = uiState.certNumber,
        onLicenseNumberChanged = viewModel::updateCertNumber,
        expireLimit = uiState.certExpireLimit,
        onExpireLimitChanged = viewModel::updateCertExpireLimit,
        expirationDate = uiState.certExpiration,
        onExpirationDateChanged = viewModel::updateCertExpiration,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}
