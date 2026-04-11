package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.TechnicianListViewModel
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes
import wingslog.feature.technician.sharedassets.generated.resources.add_technician
import wingslog.feature.technician.sharedassets.generated.resources.empty_technicians_desc
import wingslog.feature.technician.sharedassets.generated.resources.empty_technicians_title
import wingslog.feature.technician.sharedassets.generated.resources.manage_technicians

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianListScreen(
  viewModel: TechnicianListViewModel,
  onNavigateBack: () -> Unit,
  onNavigateToEdit: (technicianId: String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val technicians by viewModel.technicians.collectAsState()

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
        Icon(
          Icons.Default.Add,
          contentDescription = stringResource(TechnicianRes.string.add_technician)
        )
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
          .padding(paddingValues),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        items(technicians, key = { it.id }) { technician ->
          TechnicianCard(
            technician = technician,
            onClick = { onNavigateToEdit(technician.id) },
          )
        }
      }
    }
  }
}
