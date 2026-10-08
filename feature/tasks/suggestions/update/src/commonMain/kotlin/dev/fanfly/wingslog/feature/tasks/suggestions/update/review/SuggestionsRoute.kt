package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_TASK_DRAFT
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.slotLabel
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.template.thingSectionNoun
import dev.fanfly.wingslog.core.ui.bar.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.grouped.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.layout.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.layout.LayoutTier
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.layout.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import dev.fanfly.wingslog.feature.notifications.model.OnScreenTapTargets
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.isFromModel
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.retry
import wingslog.core.sharedassets.generated.resources.save_failed
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_ai_disclosure
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_checking
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_screen_title
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_suggest
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The recommended tasks (PRD §4.9): per-item checkboxes, and Skip as a real button.
 *
 * Reached from the Tasks tab (its empty state, or the Add Tasks sheet's *Suggest*), never after
 * creating a Thing (2026-10-03), so finishing pops straight back to the shell.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SuggestionsRoute(
  navController: NavController,
  viewModel: SuggestionsViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  // While this screen is in front, its own run's "ready" push stays quiet (PRD R20).
  LifecycleResumeEffect(viewModel.thingId) {
    val hide =
      OnScreenTapTargets.show(NotificationTapTarget.Suggestions(viewModel.thingId))
    onPauseOrDispose { hide() }
  }

  // The task form's draft mode hands an edited card back here (PRD R28).
  val scope = rememberCoroutineScope()
  val editedDraft = navController.currentBackStackEntry?.savedStateHandle
    ?.getStateFlow<String?>(CROSS_SCREEN_TASK_DRAFT, null)
    ?.collectAsStateWithLifecycle()
  LaunchedEffect(editedDraft?.value) {
    val draft = editedDraft?.value ?: return@LaunchedEffect
    viewModel.onEdited(draft)
    navController.currentBackStackEntry?.savedStateHandle?.remove<String>(
      CROSS_SCREEN_TASK_DRAFT
    )
  }

  // Back is Skip: leaving without answering is declining, and the Thing already exists.
  BackHandler(enabled = !uiState.isDone) { viewModel.onSkip() }

  // Said on the task tab, in this Thing's words, when the screen closes with nothing to show. What
  // was added, the tab says itself, with *Undo* (1f).
  val closingMessage =
    uiState.closingError?.message(uiState.lexicon.thingNoun.singular)
  LaunchedEffect(uiState.isDone) {
    if (!uiState.isDone) return@LaunchedEffect
    val message = closingMessage.takeIf { uiState.acceptedCount == 0 }
    if (message != null) {
      navController.previousBackStackEntry?.savedStateHandle?.set(
        CROSS_SCREEN_SUCCESS_MESSAGE,
        message,
      )
    }
    navController.popBackStack()
  }
  val snackbarHostState = remember { SnackbarHostState() }

  // The Thing's own words, not the shell's: on the create path the switcher may still point at a
  // different Thing, and the ambient lexicon with it.
  CompositionLocalProvider(
    LocalThingLexicon provides uiState.lexicon,
    LocalThingTemplate provides uiState.template,
    LocalThingCapabilities provides (uiState.template?.capabilities
      ?: CurrentThingTemplate.ALL_ENABLED),
  ) {
    val taskNoun = LocalThingLexicon.current.taskNoun
    val noticeMessage = uiState.notice?.message()
    var sourceShown by remember { mutableStateOf<TaskSuggestion?>(null) }
    // The row opened to change its interval: one at a time, by suggestion, so it stays open when
    // the model's answer replaces the list.
    var expandedId by remember { mutableStateOf<String?>(null) }
    sourceShown?.let { SourceSheet(it) { sourceShown = null } }
    LaunchedEffect(noticeMessage) {
      if (noticeMessage == null) return@LaunchedEffect
      snackbarHostState.showSnackbar(noticeMessage)
      viewModel.onNoticeShown()
    }
    val saveFailedMessage = stringResource(CoreRes.string.save_failed)
    LaunchedEffect(uiState.saveFailed) {
      if (!uiState.saveFailed) return@LaunchedEffect
      snackbarHostState.showSnackbar(saveFailedMessage)
      viewModel.onSaveFailedShown()
    }
    val wide = LocalLayoutTier.current != LayoutTier.COMPACT
    Scaffold(
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      // Pinned, with the liability line (PRD §4.9); no *Skip*, as back already says no.
      bottomBar = {
        // Only over rows to pick from: with none (an empty answer, a failure) its button could
        // never be enabled.
        if (!uiState.isLoading && uiState.items.isNotEmpty()) {
          ReviewFooter(
            selected = uiState.selectedCount,
            wide = wide,
            isSaving = uiState.isSaving,
            onAdd = viewModel::onAccept,
          )
        }
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        ConstrainedTopBar(ContentWidth.Form) {
          WingsLogTopAppBar(
            title = stringResource(
              Res.string.suggestions_screen_title,
              LexiconFormatter.titleCasePlural(taskNoun),
            ),
            onBackClick = { viewModel.onSkip() },
            scrollBehavior = scrollBehavior,
          )
        }
      },
    ) { innerPadding ->
      Box(
        modifier = Modifier.fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.TopCenter,
      ) {
        if (uiState.isLoading) {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
          return@Box
        }
        if (uiState.isSuggesting) {
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth()
              .align(Alignment.TopCenter)
          )
        }
        Column(
          modifier = Modifier
            .fillMaxHeight()
            .constrainedContentWidth(ContentWidth.Form)
            .verticalScroll(rememberScrollState())
            .padding(
              horizontal = Spacing.screenPadding,
              vertical = Spacing.extraLarge
            ),
          verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
          DocumentsHeader(uiState.documents)
          uiState.aiSkipped?.let { skipped ->
            // The curated cards came alone; say why, and when the model is back (PRD R9a).
            Text(
              text = skipped.text(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (uiState.notEnough) {
            // R21a: nothing confident to suggest; more about the Thing is what would help. Either
            // way out ends this run and replaces the screen.
            val leaveFor = { route: String ->
              viewModel.onAddDetails()
              navController.navigate(route) {
                popUpTo(Screen.Suggestions.route) { inclusive = true }
              }
            }
            ImproveBanner(
              onAddDetails = { leaveFor(Screen.EditThing.createRoute(viewModel.thingId)) },
              // Only where the sheet's *Suggest* can start a run; otherwise the banner says when.
              onUseManual = { leaveFor(Screen.AddTasks.createRoute(viewModel.thingId)) }
                .takeIf { uiState.canUseManual },
              blocked = uiState.manualBlocked,
            )
          }
          uiState.failure?.let { failure ->
            // The model run failed; the cards below are still there to pick from (PRD R21).
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
              Text(
                text = failure.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
              )
              TextButton(
                onClick = viewModel::onRetry,
                enabled = !uiState.isSaving
              ) {
                Text(stringResource(CoreRes.string.retry))
              }
            }
          }
          if (uiState.isCheckingAi) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(Spacing.large),
                strokeWidth = 2.dp
              )
              Text(
                text = stringResource(Res.string.suggestions_checking),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          uiState.aiUnavailable?.let { unavailable ->
            // In place of the button: AI cannot run now, so nothing here would start it.
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
              Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = unavailable.text(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (uiState.canSuggest) {
            OutlinedButton(
              onClick = viewModel::onSuggest,
              enabled = !uiState.isSaving
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  Icons.Default.AutoAwesome,
                  contentDescription = null,
                  modifier = Modifier.padding(end = Spacing.small)
                )
                Text(
                  stringResource(
                    Res.string.suggestions_suggest,
                    LexiconFormatter.plural(taskNoun)
                  )
                )
              }
            }
          }
          if (uiState.isSuggesting) SuggestingNote(
            uiState.stage,
            uiState.stageArg
          )
          // The Thing's own tasks, in its words: an airplane's are the airframe's.
          val thingSection =
            LexiconFormatter.titleCase(LocalThingLexicon.current.thingSectionNoun)
          // Regrouped only when the cards change, not for each progress line of a working run.
          val groups = remember(uiState.items) { groupsOf(uiState.items) }
          groups.forEach { group ->
            // The header sits on its card; the column's spacing separates the sections.
            Column {
              // Every section is headed: its count and *Select all* are worth having for one too.
              SuggestionGroupHeader(
                title = if (group.slotKey.isEmpty()) {
                  thingSection
                } else {
                  uiState.template.slotLabel(
                    group.slotKey,
                    ifAbsent = group.slotKey
                  )
                },
                selected = group.cards.count { it.selected },
                total = group.cards.size,
                enabled = !uiState.isSaving,
                onToggleAll = { viewModel.onToggleGroup(group.cards.map { it.id }) },
              )
              GroupedRowGroup(
                dividerStartInset = 52.dp,
                rows = group.cards.map { item ->
                  {
                    SuggestionRow(
                      item = item,
                      template = uiState.template,
                      wide = wide,
                      enabled = !uiState.isSaving,
                      // A suggestion with no id is never the open one.
                      expanded = item.id.isNotEmpty() && item.id == expandedId,
                      onToggle = { viewModel.onToggle(item.id) },
                      onExpandedChange = { open -> expandedId = item.id.takeIf { open } },
                      onSource = { sourceShown = item.suggestion },
                      onInterval = { edit ->
                        when (edit) {
                          is IntervalEdit.Meter ->
                            viewModel.onMeterIntervalChange(
                              item.id,
                              edit.interval
                            )

                          is IntervalEdit.Months -> viewModel.onMonthsChange(
                            item.id,
                            edit.months
                          )

                          is IntervalEdit.Days -> viewModel.onDaysChange(
                            item.id,
                            edit.days
                          )
                        }
                      },
                      // R28: everything else about it, in the task form.
                      onMoreOptions = {
                        scope.launch {
                          val draft = viewModel.draftFor(item.id) ?: return@launch
                          navController.navigate(
                            Screen.AddMaintenanceTask.createRoute(
                              viewModel.thingId,
                              draft
                            ),
                          )
                        }
                      },
                    )
                  }
                },
              )
            }
          }
          // The model's rows land here when the run ends; the ones above can be picked meanwhile.
          if (uiState.isSuggesting) ComingGroup(uiState.readsDocuments)
          // R31: said once, and only when the model drafted some of what is on screen.
          if (uiState.items.any { it.suggestion.isFromModel() }) {
            Text(
              text = stringResource(Res.string.suggestion_ai_disclosure),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
