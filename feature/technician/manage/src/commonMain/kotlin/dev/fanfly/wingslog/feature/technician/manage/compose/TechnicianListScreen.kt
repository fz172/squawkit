package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedFloatingAction
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.TechnicianListViewModel
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.add_technician
import wingslog.feature.technician.sharedassets.generated.resources.empty_technicians_desc
import wingslog.feature.technician.sharedassets.generated.resources.empty_technicians_title
import wingslog.feature.technician.sharedassets.generated.resources.manage_technicians
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianListScreen(
  viewModel: TechnicianListViewModel,
  onNavigateBack: () -> Unit,
  onNavigateToEdit: (technicianId: String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.uiState.collectAsState()

  Scaffold(
    modifier = modifier,
    topBar = {
      ConstrainedTopBar {
        TopAppBar(
          title = { Text(stringResource(TechnicianRes.string.manage_technicians)) },
          navigationIcon = {
            IconButton(onClick = onNavigateBack) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null
              )
            }
          }
        )
      }
    },
    floatingActionButton = {
      ConstrainedFloatingAction(ContentWidth.Reading) {
        FloatingActionButton(onClick = { onNavigateToEdit(null) }) {
          Icon(
            Icons.Default.Add,
            contentDescription = stringResource(TechnicianRes.string.add_technician)
          )
        }
      }
    }
  ) { paddingValues ->
    if (state.technicians.isEmpty()) {
      EmptyState(
        title = stringResource(TechnicianRes.string.empty_technicians_title),
        description = stringResource(TechnicianRes.string.empty_technicians_desc),
        icon = Icons.Default.Engineering,
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      )
    } else {
      // Edge-to-edge: apply only the top/horizontal scaffold insets to the container and fold the
      // bottom system-bar inset into the list's content padding, so the list scrolls under the
      // transparent system navigation bar while the last card still clears the gesture bar.
      val layoutDirection = LocalLayoutDirection.current
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(
            top = paddingValues.calculateTopPadding(),
            start = paddingValues.calculateStartPadding(layoutDirection),
            end = paddingValues.calculateEndPadding(layoutDirection),
          ),
        contentAlignment = Alignment.TopCenter,
      ) {
        LazyColumn(
          modifier = Modifier
            .fillMaxHeight()
            .constrainedContentWidth(ContentWidth.Reading),
          contentPadding = PaddingValues(
            start = Spacing.large,
            end = Spacing.large,
            top = Spacing.large,
            bottom = Spacing.large + paddingValues.calculateBottomPadding(),
          ),
          verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
          items(state.technicians, key = { it.id }) { technician ->
            TechnicianCard(
              technician = technician,
              onClick = { onNavigateToEdit(technician.id) },
              isSelf = technician.id == state.selfId,
            )
          }
        }
      }
    }
  }
}
