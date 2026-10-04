package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_TASK_DRAFT
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.meter
import dev.fanfly.wingslog.core.template.slotLabel
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.bar.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.form.BottomButtons
import dev.fanfly.wingslog.core.ui.grouped.GroupedCheckboxRow
import dev.fanfly.wingslog.core.ui.grouped.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.layout.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.layout.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import dev.fanfly.wingslog.feature.subscription.viewing.paywall.ProUpsellSheet
import dev.fanfly.wingslog.feature.subscription.viewing.paywall.UpsellTrigger
import dev.fanfly.wingslog.feature.notifications.model.OnScreenTapTargets
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.TimeRule
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.add
import wingslog.core.sharedassets.generated.resources.edit
import wingslog.core.sharedassets.generated.resources.retry
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_add_details
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_added
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_disclaimer
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_edited
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_not_enough
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_screen_title
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_skip
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_stage_hint
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_subtitle
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_suggest
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_title
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_either
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_days
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_meter
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_month
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_months
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_year
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_rule_every_years
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_ai_disclosure
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The recommended tasks (PRD §4.9): per-item checkboxes, and Skip as a real button.
 *
 * Reached from the Tasks tab (its empty state, or *Suggest tasks*), never after creating a Thing
 * (2026-10-03), so finishing pops straight back to the shell.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun StarterPackRoute(
  navController: NavController,
  viewModel: StarterPackViewModel = koinViewModel(),
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

  val addedMessage = stringResource(
    Res.string.starter_pack_added,
    uiState.acceptedCount,
    uiState.lexicon.taskNoun.let { if (uiState.acceptedCount == 1) it.singular else it.plural },
  )
  // Said on the task tab, in this Thing's words, when the screen closes with nothing to show.
  val closingMessage =
    uiState.closingError?.message(uiState.lexicon.thingNoun.singular)
  LaunchedEffect(uiState.isDone) {
    if (!uiState.isDone) return@LaunchedEffect
    val message =
      if (uiState.acceptedCount > 0) addedMessage else closingMessage
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
    sourceShown?.let { SourceSheet(it) { sourceShown = null } }
    var upsell by remember { mutableStateOf(false) }
    val capability = koinInject<AppCapability>()
    uiState.sources?.let { sources ->
      SourcesSheet(
        state = sources,
        cameraSupported = capability.isCameraCaptureSupported,
        onAddDocuments = viewModel::onAddDocuments,
        onPickError = viewModel::onPickFailed,
        onRemove = viewModel::onRemoveDocument,
        // The promo replaces the sheet rather than stacking on it.
        onUpsell = {
          viewModel.onSourcesDismissed(closeIfEmpty = false)
          upsell = true
        },
        onSuggest = viewModel::onSuggest,
        onDismiss = { viewModel.onSourcesDismissed() },
      )
    }
    if (upsell) {
      ProUpsellSheet(
        trigger = UpsellTrigger.AI_DOCUMENTS,
        onSeePlans = {
          upsell = false
          navController.navigate(Screen.Subscription.route)
        },
        onDismiss = { upsell = false },
      )
    }
    LaunchedEffect(noticeMessage) {
      if (noticeMessage == null) return@LaunchedEffect
      snackbarHostState.showSnackbar(noticeMessage)
      viewModel.onNoticeShown()
    }
    Scaffold(
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        ConstrainedTopBar(ContentWidth.Form) {
          WingsLogTopAppBar(
            title = stringResource(
              Res.string.starter_pack_screen_title,
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
          Text(
            text = stringResource(
              Res.string.starter_pack_title,
              taskNoun.plural
            ),
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text = stringResource(
              Res.string.starter_pack_subtitle,
              LocalThingLexicon.current.thingNoun.singular,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          uiState.aiSkipped?.let { skipped ->
            // The curated cards came alone; say why, and when the model is back (PRD R9a).
            Text(
              text = skipped.text(),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (uiState.notEnough) {
            // R21a: nothing confident to suggest; more about the Thing is what would help.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
              Text(
                text = stringResource(
                  Res.string.starter_pack_not_enough,
                  LocalThingLexicon.current.thingNoun.singular,
                ),
                style = MaterialTheme.typography.bodyMedium,
              )
              OutlinedButton(
                onClick = {
                  viewModel.onAddDetails()
                  navController.navigate(Screen.EditThing.createRoute(viewModel.thingId)) {
                    popUpTo(Screen.StarterPack.route) { inclusive = true }
                  }
                },
              ) {
                Text(stringResource(Res.string.starter_pack_add_details))
              }
            }
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
          if (uiState.canSuggest) {
            OutlinedButton(
              onClick = viewModel::onOpenSources,
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
                    Res.string.starter_pack_suggest,
                    LexiconFormatter.plural(taskNoun)
                  )
                )
              }
            }
          }
          if (uiState.isSuggesting) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(Spacing.large),
                strokeWidth = 2.dp
              )
              Column {
                Text(
                  text = stageText(uiState.stage, uiState.stageArg),
                  style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                  text = stringResource(Res.string.starter_pack_stage_hint),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          val groups = groupsOf(uiState.items)
          groups.forEach { group ->
            // Headed only when there is more than one group to tell apart (PRD R25).
            if (groups.size > 1) {
              Text(
                text = if (group.slotKey.isEmpty()) {
                  LexiconFormatter.titleCase(LocalThingLexicon.current.thingNoun)
                } else {
                  uiState.template.slotLabel(
                    group.slotKey,
                    ifAbsent = group.slotKey
                  )
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            GroupedRowGroup(
              rows = group.cards.map { (index, item) ->
                {
                  val edited = item.edited
                  GroupedCheckboxRow(
                    title = edited?.title ?: item.suggestion.title,
                    subtitle = if (edited != null) {
                      edited.editedSummary(uiState.template)
                    } else {
                      item.suggestion.summary(uiState.template)
                    },
                    checked = item.selected,
                    enabled = !uiState.isSaving,
                    supporting = {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                      ) {
                        SourceChip(item.suggestion) {
                          sourceShown = item.suggestion
                        }
                        // R28: change it before adding it, in the task form.
                        if (item.starterTask == null) {
                          TextButton(
                            enabled = !uiState.isSaving,
                            onClick = {
                              scope.launch {
                                val draft =
                                  viewModel.draftFor(index) ?: return@launch
                                navController.navigate(
                                  Screen.AddMaintenanceTask.createRoute(
                                    viewModel.thingId,
                                    draft
                                  ),
                                )
                              }
                            },
                          ) { Text(stringResource(CoreRes.string.edit)) }
                        }
                      }
                    },
                    onCheckedChange = { viewModel.onToggle(index) },
                  )
                }
              },
            )
          }
          // R31: said once, and only when the model drafted some of what is on screen.
          if (uiState.items.any { it.suggestion.isFromModel() }) {
            Text(
              text = stringResource(Res.string.suggestion_ai_disclosure),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          // PRD §4.9's liability posture: recommendations, never authority.
          Text(
            text = stringResource(
              Res.string.starter_pack_disclaimer,
              LocalThingLexicon.current.thingNoun.singular,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(Spacing.buttonHeight + Spacing.huge))
        }
        BottomButtons(
          modifier = Modifier.align(Alignment.BottomCenter),
          // Just "Add": the checkboxes already say what will be added.
          primaryLabel = stringResource(CoreRes.string.add),
          primaryEnabled = uiState.selectedCount > 0 && !uiState.isSaving,
          isPrimaryFunctionInProgress = uiState.isSaving,
          onPrimaryClick = { viewModel.onAccept() },
          secondaryLabel = stringResource(Res.string.starter_pack_skip),
          secondaryEnabled = !uiState.isSaving,
          onSecondaryClick = { viewModel.onSkip() },
        )
      }
    }
  }
}

/**
 * "Every 6 months · why" — the rule first, because it is the part worth scanning for. A seasonal
 * rule says nothing here, as the starter pack never did; an on-condition rule shows its own words.
 */
@Composable
private fun TaskSuggestion.summary(template: ThingTemplate?): String =
  rulesSummary(rules, description, template)

/** An edited card's line: "Edited · Every 6 months · its notes" (PRD R28). */
@Composable
private fun MaintenanceTask.editedSummary(template: ThingTemplate?): String =
  listOf(
    stringResource(Res.string.starter_pack_edited),
    rulesSummary(rules, notes, template)
  )
    .filter { it.isNotEmpty() }
    .joinToString(" · ")

@Composable
private fun rulesSummary(
  rules: List<InspectionRule>,
  description: String,
  template: ThingTemplate?
): String {
  val calendar = rules.firstNotNullOfOrNull { it.time_rule }
    ?.let { calendarText(it) }
  val meter = rules.firstNotNullOfOrNull { it.meter_rule }
    ?.takeIf { it.meter_key.isNotEmpty() && it.interval > 0f }
    ?.let {
      stringResource(
        Res.string.starter_rule_every_meter,
        formatInterval(it.interval),
        template.meter(it.meter_key)?.unit_label ?: it.meter_key,
      )
    }
  val onCondition =
    rules.firstNotNullOfOrNull { it.on_condition_rule }?.description?.takeIf { it.isNotBlank() }
  val rule = when {
    meter != null && calendar != null -> stringResource(
      Res.string.starter_rule_either,
      meter,
      calendar
    )

    else -> meter ?: calendar ?: onCondition
  }
  return listOfNotNull(
    rule,
    description.takeIf { it.isNotEmpty() }).joinToString(" · ")
}

@Composable
private fun calendarText(rule: TimeRule): String? {
  val months = rule.interval_months + 12 * rule.interval_years
  return when {
    months == 1 -> stringResource(Res.string.starter_rule_every_month)
    months == 12 -> stringResource(Res.string.starter_rule_every_year)
    months > 0 && months % 12 == 0 -> stringResource(
      Res.string.starter_rule_every_years,
      months / 12
    )

    months > 0 -> stringResource(Res.string.starter_rule_every_months, months)
    rule.interval_days > 0 -> stringResource(
      Res.string.starter_rule_every_days,
      rule.interval_days
    )

    else -> null
  }
}

/** 5000 → "5,000"; 7.5 → "7.5". Grouping by hand because `String.format` is not common code. */
private fun formatInterval(value: Float): String {
  val whole = value.toLong()
  if (value != whole.toFloat()) return value.toString()
  return whole.toString()
    .reversed()
    .chunked(3)
    .joinToString(",")
    .reversed()
}
