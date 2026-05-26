package dev.fanfly.wingslog.feature.squawk.update.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.common.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabRow
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabSpec
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.common.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.squawk.update.compose.DismissSquawkDialog
import dev.fanfly.wingslog.feature.squawk.update.compose.LogPickerSheet
import dev.fanfly.wingslog.feature.squawk.update.compose.SquawkBasicTab
import dev.fanfly.wingslog.feature.squawk.update.compose.SquawkDetailsTab
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.SquawkFormState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.details
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.add_squawk
import wingslog.feature.squawk.sharedassets.generated.resources.edit_squawk
import wingslog.feature.squawk.update.generated.resources.dismiss_issue
import wingslog.feature.squawk.update.generated.resources.reopen_issue
import wingslog.feature.squawk.update.generated.resources.tab_basic
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.squawk.update.generated.resources.Res as UpdateRes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SquawkFormScreen(
  state: SquawkFormState,
  onTitleChange: (String) -> Unit,
  onDescriptionChange: (String) -> Unit,
  onPriorityChange: (dev.fanfly.wingslog.aircraft.SquawkPriority) -> Unit,
  onSave: () -> Unit,
  onBack: () -> Unit,
  onAddLog: () -> Unit,
  onClearLog: () -> Unit,
  onSelectLog: (String) -> Unit,
  onHideLogPicker: () -> Unit,
  onDismissClick: () -> Unit,
  onDismissDialogDismiss: () -> Unit,
  onDismissConfirm: (SquawkDismissReason) -> Unit,
  onReopenClick: () -> Unit,
  modifier: Modifier = Modifier,
  attachmentSection: @Composable () -> Unit = {},
) {
  val isEdit = state.squawkId != null
  val isDismissed =
    state.dismissReason != SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN
  val showDismissButton = isEdit && !state.isAddressedReadOnly && !isDismissed
  val screenTitle = if (isEdit) stringResource(Res.string.edit_squawk)
  else stringResource(Res.string.add_squawk)

  val hasChanges = if (isEdit) {
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

  val tabs = listOf(
    IconLabelTabSpec(
      Icons.Default.Edit,
      stringResource(UpdateRes.string.tab_basic)
    ),
    IconLabelTabSpec(
      Icons.Default.Info,
      stringResource(CoreRes.string.details)
    ),
  )
  val pagerState = rememberPagerState(pageCount = { tabs.size })
  val coroutineScope = rememberCoroutineScope()

  Scaffold(
    modifier = modifier,
    topBar = {
      Column {
        ConstrainedTopBar {
          TopAppBar(
            title = {
              Text(
                text = screenTitle.uppercase(),
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
          )
        }
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.TopCenter
        ) {
          IconLabelTabRow(
            tabs = tabs,
            selectedIndex = pagerState.currentPage,
            onSelect = {
              coroutineScope.launch {
                pagerState.animateScrollToPage(
                  it
                )
              }
            },
            modifier = Modifier.constrainedContentWidth(ContentWidth.Form),
          )
        }
      }
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .padding(padding)
        .fillMaxSize(),
    ) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f),
        beyondViewportPageCount = 1,
        verticalAlignment = Alignment.Top,
      ) { page ->
        Box(
          modifier = Modifier
            .fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
        ) {
          Column(
            modifier = Modifier
              .fillMaxHeight()
              .constrainedContentWidth(ContentWidth.Form)
              .verticalScroll(rememberScrollState())
              .padding(Spacing.screenPadding),
          ) {
            when (page) {
              0 -> SquawkBasicTab(
                title = state.title,
                onTitleChange = onTitleChange,
                priority = state.priority,
                onPriorityChange = onPriorityChange,
                reportedDateFormatted = state.reportedDateFormatted,
                readOnly = state.isAddressedReadOnly,
                titleError = state.titleError,
              )

              1 -> SquawkDetailsTab(
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
            }
          }
        }
      }

      BottomButtons(
        onPrimaryClick = onSave,
        onSecondaryClick = { tryBack() },
        primaryEnabled = !state.isSaving,
        isPrimaryFunctionInProgress = state.isSaving,
        onDangerClick = when {
          showDismissButton -> onDismissClick
          isDismissed -> onReopenClick
          else -> null
        },
        dangerLabel = when {
          isDismissed -> stringResource(UpdateRes.string.reopen_issue)
          else -> stringResource(UpdateRes.string.dismiss_issue)
        },
      )
    }
  }

  if (state.showLogPicker) {
    LogPickerSheet(
      logs = state.availableLogs,
      onSelect = onSelectLog,
      onDismiss = onHideLogPicker,
    )
  }

  if (state.showDismissDialog) {
    DismissSquawkDialog(
      onConfirm = onDismissConfirm,
      onDismiss = onDismissDialogDismiss,
    )
  }
}
