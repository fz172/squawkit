package dev.fanfly.wingslog.feature.tasks.update.starter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.meter
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.GroupedCheckboxRow
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.StarterTask
import dev.fanfly.wingslog.thing.ThingTemplate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.starter_pack_add
import wingslog.feature.tasks.update.generated.resources.starter_pack_added
import wingslog.feature.tasks.update.generated.resources.starter_pack_disclaimer
import wingslog.feature.tasks.update.generated.resources.starter_pack_screen_title
import wingslog.feature.tasks.update.generated.resources.starter_pack_skip
import wingslog.feature.tasks.update.generated.resources.starter_pack_subtitle
import wingslog.feature.tasks.update.generated.resources.starter_pack_title
import wingslog.feature.tasks.update.generated.resources.starter_rule_either
import wingslog.feature.tasks.update.generated.resources.starter_rule_every_meter
import wingslog.feature.tasks.update.generated.resources.starter_rule_every_month
import wingslog.feature.tasks.update.generated.resources.starter_rule_every_months
import wingslog.feature.tasks.update.generated.resources.starter_rule_every_year
import wingslog.feature.tasks.update.generated.resources.starter_rule_every_years

/**
 * The starter-pack step (PRD §4.9, §8.1 step 4): per-item checkboxes, and Skip as a real button.
 *
 * Reached from the create form's hand-off and from an empty Tasks tab. Either way the form that
 * opened it is gone from the stack, so finishing pops straight to the shell.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun StarterPackRoute(
  navController: NavController,
  viewModel: StarterPackViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  // Back is Skip: leaving without answering is declining, and the Thing already exists.
  BackHandler(enabled = !uiState.isDone) { viewModel.onSkip() }

  val addedMessage = stringResource(
    Res.string.starter_pack_added,
    uiState.acceptedCount,
    uiState.lexicon.taskNoun.let { if (uiState.acceptedCount == 1) it.singular else it.plural },
  )
  LaunchedEffect(uiState.isDone) {
    if (!uiState.isDone) return@LaunchedEffect
    if (uiState.acceptedCount > 0) {
      navController.previousBackStackEntry?.savedStateHandle?.set(
        CROSS_SCREEN_SUCCESS_MESSAGE,
        addedMessage,
      )
    }
    navController.popBackStack()
  }

  // The Thing's own words, not the shell's: on the create path the switcher may still point at a
  // different Thing, and the ambient lexicon with it.
  CompositionLocalProvider(
    LocalThingLexicon provides uiState.lexicon,
    LocalThingTemplate provides uiState.template,
    LocalThingCapabilities provides (uiState.template?.capabilities ?: CurrentThingTemplate.ALL_ENABLED),
  ) {
    val taskNoun = LocalThingLexicon.current.taskNoun
    Scaffold(
      modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.extraLarge),
          verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
          Text(
            text = stringResource(Res.string.starter_pack_title, taskNoun.plural),
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
          GroupedRowGroup(
            rows = uiState.items.mapIndexed { index, item ->
              {
                GroupedCheckboxRow(
                  title = item.task.title,
                  subtitle = item.task.summary(uiState.template),
                  checked = item.selected,
                  enabled = !uiState.isSaving,
                  onCheckedChange = { viewModel.onToggle(index) },
                )
              }
            },
          )
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
          primaryLabel = stringResource(
            Res.string.starter_pack_add,
            uiState.selectedCount,
            if (uiState.selectedCount == 1) taskNoun.singular else taskNoun.plural,
          ),
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

/** "Every 6 months · why" — the rule first, because it is the part worth scanning for. */
@Composable
private fun StarterTask.summary(template: ThingTemplate?): String {
  val calendar = when {
    interval_months <= 0 -> null
    interval_months == 1 -> stringResource(Res.string.starter_rule_every_month)
    interval_months == 12 -> stringResource(Res.string.starter_rule_every_year)
    interval_months % 12 == 0 ->
      stringResource(Res.string.starter_rule_every_years, interval_months / 12)
    else -> stringResource(Res.string.starter_rule_every_months, interval_months)
  }
  val meter = if (meter_key.isNotEmpty() && interval > 0f) {
    stringResource(
      Res.string.starter_rule_every_meter,
      formatInterval(interval),
      template.meter(meter_key)?.unit_label ?: meter_key,
    )
  } else null
  val rule = when {
    meter != null && calendar != null -> stringResource(Res.string.starter_rule_either, meter, calendar)
    else -> meter ?: calendar
  }
  return listOfNotNull(rule, description.takeIf { it.isNotEmpty() }).joinToString(" · ")
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
