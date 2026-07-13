package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.Technician
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
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_prompt_action
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_prompt_dismiss
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_prompt_title
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_review_title
import wingslog.feature.technician.sharedassets.generated.resources.linked_technician_info_body
import wingslog.feature.technician.sharedassets.generated.resources.linked_technician_info_dismiss
import wingslog.feature.technician.sharedassets.generated.resources.linked_technician_info_title
import wingslog.feature.technician.sharedassets.generated.resources.linked_technicians_header
import wingslog.feature.technician.sharedassets.generated.resources.manage_technicians
import wingslog.feature.technician.sharedassets.generated.resources.my_technicians_header
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

  // Tapping a linked profile explains who maintains it, rather than opening an editor it can't edit.
  var infoFor by remember { mutableStateOf<Technician?>(null) }
  infoFor?.let { linked ->
    AlertDialog(
      onDismissRequest = { infoFor = null },
      title = {
        Text(stringResource(TechnicianRes.string.linked_technician_info_title, linked.name))
      },
      text = { Text(stringResource(TechnicianRes.string.linked_technician_info_body)) },
      confirmButton = {
        TextButton(onClick = { infoFor = null }) {
          Text(stringResource(TechnicianRes.string.linked_technician_info_dismiss))
        }
      },
    )
  }

  if (state.showDuplicateReview) {
    DuplicateReviewSheet(
      groups = state.duplicates,
      onApply = { viewModel.applyMerges(it) },
      onDismiss = { viewModel.hideDuplicateReview() },
    )
  }

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
          },
          actions = {
            // The prompt is dismissible, so it can't be the only way in: once dismissed, a user who
            // changes their mind would have no route back to the review while the duplicates remain.
            if (state.duplicates.isNotEmpty()) {
              IconButton(onClick = { viewModel.showDuplicateReview() }) {
                Icon(
                  Icons.Filled.Merge,
                  contentDescription = stringResource(TechnicianRes.string.duplicates_review_title),
                )
              }
            }
          },
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
    if (state.technicians.isEmpty() && state.linkedTechnicians.isEmpty()) {
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
          if (state.showDuplicatePrompt) {
            item(key = "duplicate-prompt") {
              DuplicatePrompt(
                onReview = { viewModel.showDuplicateReview() },
                onDismiss = { viewModel.dismissDuplicatePrompt() },
              )
            }
          }

          // Only headline the personal list when there's a linked section to distinguish it from.
          if (state.linkedTechnicians.isNotEmpty() && state.technicians.isNotEmpty()) {
            item(key = "own-header") {
              SectionHeader(stringResource(TechnicianRes.string.my_technicians_header))
            }
          }
          items(state.technicians, key = { it.id }) { technician ->
            TechnicianCard(
              technician = technician,
              onClick = { onNavigateToEdit(technician.id) },
              isSelf = technician.id == state.selfId,
            )
          }

          if (state.linkedTechnicians.isNotEmpty()) {
            item(key = "linked-header") {
              SectionHeader(stringResource(TechnicianRes.string.linked_technicians_header))
            }
            // Keyed by source_uid: a linked profile is identified by the account that owns it, and
            // that's what keeps it distinct from any manual entry of the same person.
            items(state.linkedTechnicians, key = { "linked-${it.source_uid}" }) { linked ->
              TechnicianCard(
                technician = linked,
                onClick = { infoFor = linked },
                isLinked = true,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionHeader(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = Spacing.small, bottom = Spacing.extraSmall),
  )
}

/**
 * Dismissible nudge that look-alike rows are worth reconciling (design §7.4). "Not duplicates" is a
 * real answer — it records that the user has looked, so the prompt does not nag again.
 */
@Composable
private fun DuplicatePrompt(
  onReview: () -> Unit,
  onDismiss: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ),
  ) {
    Column(
      modifier = Modifier.padding(Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Text(
        text = stringResource(TechnicianRes.string.duplicates_prompt_title),
        style = MaterialTheme.typography.bodyMedium,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TextButton(onClick = onReview) {
          Text(stringResource(TechnicianRes.string.duplicates_prompt_action))
        }
        TextButton(onClick = onDismiss) {
          Text(stringResource(TechnicianRes.string.duplicates_prompt_dismiss))
        }
      }
    }
  }
}
