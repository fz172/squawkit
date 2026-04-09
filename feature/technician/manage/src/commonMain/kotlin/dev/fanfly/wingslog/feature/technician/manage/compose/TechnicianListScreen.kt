package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.TechnicianListViewModel
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.add_technician
import wingslog.feature.technician.sharedassets.generated.resources.delete_technician
import wingslog.feature.technician.sharedassets.generated.resources.manage_technicians
import wingslog.feature.technician.sharedassets.generated.resources.no_technicians
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes

import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import androidx.compose.material.icons.filled.Engineering
import wingslog.feature.technician.sharedassets.generated.resources.empty_technicians_desc
import wingslog.feature.technician.sharedassets.generated.resources.empty_technicians_title

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import wingslog.feature.technician.sharedassets.generated.resources.delete_technician_confirmation
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.delete
import wingslog.core.ui.generated.resources.Res as CoreRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianListScreen(
  viewModel: TechnicianListViewModel,
  onNavigateBack: () -> Unit,
  onNavigateToEdit: (technicianId: String?) -> Unit,
  modifier: Modifier = Modifier
) {
  val technicians by viewModel.technicians.collectAsState()
  var technicianToDelete by remember { mutableStateOf<Technician?>(null) }

  if (technicianToDelete != null) {
    AlertDialog(
      onDismissRequest = { technicianToDelete = null },
      title = { Text(stringResource(TechnicianRes.string.delete_technician)) },
      text = { Text(stringResource(TechnicianRes.string.delete_technician_confirmation)) },
      confirmButton = {
        TextButton(onClick = {
          technicianToDelete?.let { viewModel.deleteTechnician(it.id) }
          technicianToDelete = null
        }) {
          Text(stringResource(CoreRes.string.delete), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { technicianToDelete = null }) {
          Text(stringResource(CoreRes.string.cancel))
        }
      }
    )
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(TechnicianRes.string.manage_technicians)) },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        }
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { onNavigateToEdit(null) }) {
        Icon(Icons.Default.Add, contentDescription = stringResource(TechnicianRes.string.add_technician))
      }
    }
  ) { paddingValues ->
    if (technicians.isEmpty()) {
      EmptyState(
        title = stringResource(TechnicianRes.string.empty_technicians_title),
        description = stringResource(TechnicianRes.string.empty_technicians_desc),
        icon = Icons.Default.Engineering,
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      )
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        items(technicians, key = { it.id }) { technician ->
          TechnicianListItem(
            technician = technician,
            onClick = { onNavigateToEdit(technician.id) },
            onDelete = { technicianToDelete = technician }
          )
          HorizontalDivider()
        }
      }
    }
  }
}

@Composable
fun TechnicianListItem(
  technician: Technician,
  onClick: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = Spacing.large, vertical = Spacing.medium)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(end = Spacing.extraLarge)
    ) {
      Text(
        text = technician.name,
        style = MaterialTheme.typography.bodyLarge
      )
      if (technician.cert_type.isNotBlank() || technician.cert_number.isNotBlank()) {
        val certText = listOf(technician.cert_type, technician.cert_number)
          .filter { it.isNotBlank() }
          .joinToString(" - ")
        Text(
          text = certText,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    IconButton(
      onClick = onDelete,
      modifier = Modifier.align(Alignment.CenterEnd)
    ) {
      Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = stringResource(TechnicianRes.string.delete_technician),
        tint = MaterialTheme.colorScheme.error
      )
    }
  }
}
