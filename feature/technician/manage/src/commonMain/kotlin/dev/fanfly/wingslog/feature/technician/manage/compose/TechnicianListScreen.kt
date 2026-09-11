package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Merge
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.fanfly.wingslog.core.template.OfferedCertification
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedFloatingAction
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.common.compose.GroupedSection
import dev.fanfly.wingslog.core.ui.common.compose.SettingsHero
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.TechnicianListViewModel
import dev.fanfly.wingslog.thing.Technician
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.add_technician
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_prompt_action
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_prompt_dismiss
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_prompt_title
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_review_title
import wingslog.feature.technician.sharedassets.generated.resources.empty_technicians_desc
import wingslog.feature.technician.sharedassets.generated.resources.empty_technicians_title
import wingslog.feature.technician.sharedassets.generated.resources.linked_technician_info_body
import wingslog.feature.technician.sharedassets.generated.resources.linked_technician_info_dismiss
import wingslog.feature.technician.sharedassets.generated.resources.linked_technician_info_title
import wingslog.feature.technician.sharedassets.generated.resources.linked_technicians_header
import wingslog.feature.technician.sharedassets.generated.resources.manage_technicians
import wingslog.feature.technician.sharedassets.generated.resources.manage_technicians_description
import wingslog.feature.technician.sharedassets.generated.resources.my_technicians_header
import wingslog.feature.technician.sharedassets.generated.resources.technician_hero_title
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
        WingsLogTopAppBar(
          title = stringResource(TechnicianRes.string.manage_technicians),
          onBackClick = onNavigateBack,
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
        FloatingActionButton(
          onClick = { onNavigateToEdit(null) },
          shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        ) {
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
            start = Spacing.screenPadding,
            end = Spacing.screenPadding,
            top = Spacing.large,
            // Clears the FAB as well as the gesture bar.
            bottom = Spacing.massive + Spacing.huge + paddingValues.calculateBottomPadding(),
          ),
          verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
        ) {
          // Fixed text, no lexicon. This page aggregates technicians across the whole account —
          // they are account-scoped, not per-thing — so the selected thing's words are the wrong
          // words here even when only one template exists.
          //
          // The chrome stays neutral permanently. What arrives in Phase 3 is per-person
          // certifications (PRD §8.6): a technician holds one or more, their type is a key declared
          // by a template, and the domain is *derived* from it — an A&P means aviation, an
          // electrician's licence means home. Rows carry tags read off that. Nothing asks the user
          // which domain someone belongs to. That is where the domain speaks on this screen.
          item(key = "hero") {
            SettingsHero(
              icon = Icons.Default.Engineering,
              title = stringResource(TechnicianRes.string.technician_hero_title),
              body = stringResource(TechnicianRes.string.manage_technicians_description),
            )
          }

          if (state.showDuplicatePrompt) {
            item(key = "duplicate-prompt") {
              DuplicatePrompt(
                onReview = { viewModel.showDuplicateReview() },
                onDismiss = { viewModel.dismissDuplicatePrompt() },
              )
            }
          }

          if (state.technicians.isNotEmpty()) {
            item(key = "own") {
              TechnicianGroup(
                title = stringResource(TechnicianRes.string.my_technicians_header),
                technicians = state.technicians,
                offered = state.knownCertifications,
                selfId = state.selfId,
                onClick = { onNavigateToEdit(it.id) },
              )
            }
          }

          if (state.linkedTechnicians.isNotEmpty()) {
            item(key = "linked") {
              TechnicianGroup(
                title = stringResource(TechnicianRes.string.linked_technicians_header),
                technicians = state.linkedTechnicians,
                offered = state.knownCertifications,
                isLinked = true,
                onClick = { infoFor = it },
              )
            }
          }
        }
      }
    }
  }
}

/** One labelled card of roster rows. */
@Composable
private fun TechnicianGroup(
  title: String,
  technicians: List<Technician>,
  offered: List<OfferedCertification>,
  onClick: (Technician) -> Unit,
  selfId: String? = null,
  isLinked: Boolean = false,
) {
  GroupedSection(title) {
    GroupedRowGroup(
      rows = technicians.map { technician ->
        {
          TechnicianRow(
            technician = technician,
            offered = offered,
            onClick = { onClick(technician) },
            isSelf = technician.id == selfId,
            isLinked = isLinked,
          )
        }
      },
    )
  }
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
