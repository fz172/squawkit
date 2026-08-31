package dev.fanfly.wingslog.feature.thing.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.DashedButton
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.compose.AirframeSection
import dev.fanfly.wingslog.feature.thing.update.compose.EngineSection
import dev.fanfly.wingslog.feature.thing.update.viewmodel.EditThingViewModel
import dev.fanfly.wingslog.feature.thing.update.viewmodel.engines
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.add_thing
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.component_airframe
import wingslog.core.sharedassets.generated.resources.component_engine
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.feature.logs.sharedassets.generated.resources.this_action_cannot_be_undone
import wingslog.feature.thing.update.generated.resources.add_engine
import wingslog.feature.thing.update.generated.resources.delete_thing
import wingslog.feature.thing.update.generated.resources.delete_thing_member_plural
import wingslog.feature.thing.update.generated.resources.delete_thing_member_singular
import wingslog.feature.thing.update.generated.resources.delete_thing_shared_warning
import wingslog.feature.thing.update.generated.resources.update_thing
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.thing.update.generated.resources.Res as AircraftRes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun EditAircraftScreen(
  viewModel: EditThingViewModel = koinViewModel(),
  navController: NavController,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }

  val tryNavigateBack = {
    if (uiState.hasChanges) showUnsavedChangesDialog = true
    else navController.popBackStack()
  }

  BackHandler(enabled = uiState.hasChanges) {
    showUnsavedChangesDialog = true
  }

  if (showUnsavedChangesDialog) {
    UnsavedChangesDialog(
      onConfirm = {
        showUnsavedChangesDialog = false
        navController.popBackStack()
      },
      onDismiss = { showUnsavedChangesDialog = false },
    )
  }

  // This effect will run when isSaved becomes true
  LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
    if (uiState.isSaved || uiState.isDeleted) {
      // Navigate back when save or delete is successful
      if (uiState.isDeleted) {
        navController.popBackStack(
          Screen.AdaptiveShell.route,
          inclusive = false
        )
      } else {
        navController.popBackStack()
      }
    }
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = {
        Text(
          stringResource(
            AircraftRes.string.delete_thing,
            LexiconFormatter.titleCase(LocalThingLexicon.current.thingNoun),
          )
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
          Text(stringResource(SharedRes.string.this_action_cannot_be_undone))
          // Deleting a shared thing takes it away from everyone on the share (PRD D5). Saying
          // only "cannot be undone" hides that you are deleting other people's access too.
          val others = uiState.otherMemberCount
          if (others > 0) {
            Text(
              text = stringResource(
                AircraftRes.string.delete_thing_shared_warning,
                others,
                stringResource(
                  if (others == 1) AircraftRes.string.delete_thing_member_singular
                  else AircraftRes.string.delete_thing_member_plural
                ),
                LocalThingLexicon.current.thingNoun.singular,
              ),
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteThing()
            showDeleteDialog = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
          Text(stringResource(CoreRes.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(stringResource(CoreRes.string.cancel))
        }
      })
  }

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      ConstrainedTopBar(ContentWidth.Form) {
        WingsLogTopAppBar(
          title = if (uiState.thing.id == "") stringResource(
            CoreRes.string.add_thing,
            LexiconFormatter.titleCase(LocalThingLexicon.current.thingNoun),
          )
          else stringResource(
            AircraftRes.string.update_thing,
            LexiconFormatter.titleCase(LocalThingLexicon.current.thingNoun),
          ),
          onBackClick = { tryNavigateBack() },
          scrollBehavior = scrollBehavior,
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .fillMaxHeight()
          .constrainedContentWidth(ContentWidth.Form)
          .imePadding()
          .verticalScroll(scrollState)
          .padding(
            horizontal = Spacing.screenPadding,
            vertical = Spacing.extraLarge
          ),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
      ) {
        // AIRFRAME
        Text(
          text = stringResource(CoreRes.string.component_airframe).uppercase()
        )
        AirframeSection(
          uiState.thing,
          viewModel,
          uiState.showValidationErrors
        )

        // ENGINE
        Text(
          text = stringResource(CoreRes.string.component_engine).uppercase()
        )
        uiState.thing.engines.forEachIndexed { index, engine ->
          EngineSection(
            engineIndex = index,
            engine = engine,
            viewModel = viewModel,
            showValidationErrors = uiState.showValidationErrors
          )
        }

        DashedButton(
          label = stringResource(
            AircraftRes.string.add_engine
          ),
          modifier = Modifier.fillMaxWidth(),
          onClick = { viewModel.onAddEngine() },
        )

        Spacer(Modifier.height(Spacing.buttonHeight + Spacing.huge))
      }
      BottomButtons(
        modifier = Modifier.align(Alignment.BottomCenter),
        primaryEnabled = !uiState.isLoading,
        onPrimaryClick = { viewModel.saveAircraft() },
        onSecondaryClick = { tryNavigateBack() },
        // Delete is the hosting owner's alone — a co-owner holds the same OWNER role but deleting
        // would tear the share down for everyone, and the rules reject their tombstone anyway.
        onDangerClick = if (uiState.canDelete) {
          { showDeleteDialog = true }
        } else null,
        primaryLabel = if (uiState.thing.id == "")
          stringResource(
            CoreRes.string.add_thing,
            LexiconFormatter.titleCase(LocalThingLexicon.current.thingNoun),
          )
        else
          stringResource(
            AircraftRes.string.update_thing,
            LexiconFormatter.titleCase(LocalThingLexicon.current.thingNoun),
          )
      )
    }
  }
}
