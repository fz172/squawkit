package dev.fanfly.wingslog.feature.squawk.update.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.DangerZone
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.sharedassets.compose.LogPickerSheet
import dev.fanfly.wingslog.feature.squawk.update.compose.SquawkBasicSection
import dev.fanfly.wingslog.feature.squawk.update.compose.SquawkDetailsSection
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.SquawkFormState
import dev.fanfly.wingslog.feature.squawk.viewing.DeleteSquawkConfirmDialog
import dev.fanfly.wingslog.thing.SquawkDismissReason
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.save_changes
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.add_squawk
import wingslog.feature.squawk.sharedassets.generated.resources.delete_this_squawk_subtitle
import wingslog.feature.squawk.sharedassets.generated.resources.delete_this_squawk_title
import wingslog.feature.squawk.sharedassets.generated.resources.edit_squawk
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SquawkFormScreen(
  state: SquawkFormState,
  onTitleChange: (String) -> Unit,
  onDescriptionChange: (String) -> Unit,
  onPriorityChange: (dev.fanfly.wingslog.thing.SquawkPriority) -> Unit,
  onSave: () -> Unit,
  onBack: () -> Unit,
  onAddLog: () -> Unit,
  onClearLog: () -> Unit,
  onSelectLog: (String) -> Unit,
  onHideLogPicker: () -> Unit,
  onDeleteClick: () -> Unit,
  onDeleteConfirm: () -> Unit,
  onDeleteDialogDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
  attachmentSection: @Composable () -> Unit = {},
  /** Attachments added or removed since load — unsaved until the form is saved. */
  hasAttachmentChanges: Boolean = false,
) {
  val isEdit = state.squawkId != null
  val isDismissed =
    state.dismissReason != SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN
  val squawk = LocalThingLexicon.current.squawkNoun
  val screenTitle =
    if (isEdit) stringResource(Res.string.edit_squawk, squawk.singular)
    else stringResource(Res.string.add_squawk, squawk.singular)

  val hasChanges = hasAttachmentChanges || if (isEdit) {
    state.title != state.initialTitle ||
      state.description != state.initialDescription ||
      state.priority != state.initialPriority ||
      state.addressedByLogId != state.initialAddressedByLogId
  } else {
    state.title.isNotEmpty() || state.description.isNotEmpty()
  }

  var showUnsavedDialog by remember { mutableStateOf(false) }

  val tryBack = {
    if (hasChanges) showUnsavedDialog = true else onBack()
  }

  BackHandler(enabled = hasChanges) { showUnsavedDialog = true }

  if (showUnsavedDialog) {
    UnsavedChangesDialog(
      onConfirm = { showUnsavedDialog = false; onBack() },
      onDismiss = { showUnsavedDialog = false },
    )
  }

  val analytics = LocalAnalytics.current
  LaunchedEffect(Unit) { analytics.logScreenView("squawk_form") }
  Scaffold(
    modifier = modifier.imePadding(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Column {
        ConstrainedTopBar(ContentWidth.Form) {
          TopAppBar(
            title = {
              Text(
                text = screenTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
            },
            navigationIcon = {
              IconButton(onClick = { tryBack() }) {
                Icon(
                  Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = null
                )
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = Color.Transparent,
              scrolledContainerColor = Color.Transparent,
            ),
          )
        }
      }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier = Modifier
        .padding(padding)
        .fillMaxSize(),
    ) {
      Box(
        modifier = Modifier.weight(1f)
          .fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
      ) {
        Column(
          modifier = Modifier
            .fillMaxHeight()
            .constrainedContentWidth(ContentWidth.Form)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenPadding),
          verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
          SquawkBasicSection(
            title = state.title,
            onTitleChange = onTitleChange,
            priority = state.priority,
            onPriorityChange = onPriorityChange,
            reportedDateFormatted = state.reportedDateFormatted,
            readOnly = state.isAddressedReadOnly,
            titleError = state.titleError,
          )

          SquawkDetailsSection(
            description = state.description,
            onDescriptionChange = onDescriptionChange,
            isEdit = isEdit,
            addressedByLogId = state.addressedByLogId,
            availableLogs = state.availableLogs,
            onAddLog = onAddLog,
            onClearLog = onClearLog,
            readOnly = state.isAddressedReadOnly,
            dismissReason = state.dismissReason,
            dismissedAtFormatted = state.dismissedAtFormatted,
            attachmentSection = attachmentSection,
          )

          // Edit only — there is nothing to delete yet.
          if (isEdit) {
            DangerZone(
              title = stringResource(
                Res.string.delete_this_squawk_title,
                squawk.singular
              ),
              subtitle = stringResource(
                Res.string.delete_this_squawk_subtitle,
                LexiconFormatter.sentenceCasePlural(LocalThingLexicon.current.logNoun),
              ),
              onDelete = onDeleteClick,
            )
          }
        }
      }

      BottomButtons(
        onPrimaryClick = onSave,
        onSecondaryClick = { tryBack() },
        // "Save Changes" on a record that does not exist yet. New forms name the creation, the way
        // the thing form already does; editing keeps the default.
        primaryLabel = if (isEdit) {
          stringResource(CoreRes.string.save_changes)
        } else {
          stringResource(Res.string.add_squawk, squawk.singular)
        },
        primaryEnabled = !state.isSaving,
        isPrimaryFunctionInProgress = state.isSaving,
      )
    }
  }

  if (state.showLogPicker) {
    LogPickerSheet(
      logs = state.availableLogs,
      onSelect = { log -> onSelectLog(log.id) },
      onDismiss = onHideLogPicker,
    )
  }

  if (state.showDeleteDialog) {
    DeleteSquawkConfirmDialog(
      onConfirm = onDeleteConfirm,
      onDismiss = onDeleteDialogDismiss,
    )
  }
}
