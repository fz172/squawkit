package dev.fanfly.wingslog.feature.tasks.update.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.sharedassets.compose.LogPickerSheet
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.update.compose.ADJUSTMENT_TAB
import dev.fanfly.wingslog.feature.tasks.update.compose.BASIC_TAB
import dev.fanfly.wingslog.feature.tasks.update.compose.COMPLIANCE_TAB
import dev.fanfly.wingslog.feature.tasks.update.compose.ResolveTaskOptionsMenu
import dev.fanfly.wingslog.feature.tasks.update.compose.SCHEDULE_TAB
import dev.fanfly.wingslog.feature.tasks.update.compose.ScheduleState
import dev.fanfly.wingslog.feature.tasks.update.compose.TASK_FORM_TAB_KEYS
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskAdjustmentsTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskComplianceTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskIdentityTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskScheduleTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskTabRow
import dev.fanfly.wingslog.feature.tasks.update.viewmodel.TaskFormState
import dev.fanfly.wingslog.feature.tasks.viewing.DeleteTaskConfirmDialog
import dev.fanfly.wingslog.feature.tasks.viewing.SkipTaskConfirmDialog
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.back
import wingslog.core.sharedassets.generated.resources.ok
import wingslog.feature.tasks.sharedassets.generated.resources.edit_task
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.resolve_task
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedTaskRes

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalComposeUiApi::class
)
@Composable
fun EditTaskScreen(
  card: MaintenanceTask,
  state: TaskFormState,
  availableInspections: List<MaintenanceTask>,
  availableLogs: List<MaintenanceLog> = emptyList(),
  currentEngineHours: Float,
  naturalDueMetadata: DueMetadata?,
  onTitleChange: (String) -> Unit,
  onScheduleChange: (ScheduleState) -> Unit,
  onRefNumberChange: (String) -> Unit,
  onComplianceAuthorityChange: (String) -> Unit,
  onComplianceNotesChange: (String) -> Unit,
  onForceOverrideEngineChange: (Boolean) -> Unit,
  onForcedEngineHoursChange: (String) -> Unit,
  onForceOverrideDateChange: (Boolean) -> Unit,
  onForcedDateMillisChange: (Long?) -> Unit,
  onSave: (MaintenanceTask) -> Unit,
  onCancel: () -> Unit,
  onDeleteRequest: (String) -> Unit,
  onResolveClick: () -> Unit,
  onResolveMenuDismiss: () -> Unit,
  onCreateWorkLogClick: () -> Unit,
  onSkipConfirm: () -> Unit,
  isSaving: Boolean = false,
  showLogPicker: Boolean = false,
  onShowLogPicker: () -> Unit = {},
  onDismissLogPicker: () -> Unit = {},
  onAddLog: (MaintenanceLog) -> Unit = {},
  onRemoveLog: (MaintenanceLog) -> Unit = {},
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
  attachmentSection: @Composable () -> Unit = {},
) {
  var showDatePicker by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }
  var showSkipConfirm by remember { mutableStateOf(false) }
  var showUnsavedChangesDialog by remember { mutableStateOf(false) }

  val hasChanges = state.hasChanges

  val tryCancel = {
    if (hasChanges) showUnsavedChangesDialog = true else onCancel()
  }

  BackHandler(enabled = hasChanges) {
    showUnsavedChangesDialog = true
  }


  if (showUnsavedChangesDialog) {
    UnsavedChangesDialog(
      onConfirm = {
        showUnsavedChangesDialog = false
        onCancel()
      },
      onDismiss = { showUnsavedChangesDialog = false },
    )
  }

  val pagerState = rememberPagerState(pageCount = { 4 })
  val coroutineScope = rememberCoroutineScope()
  val analytics = LocalAnalytics.current
  // Log tab switches (tap or swipe) as page views; drop(1) skips the initial page on open.
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }
      .drop(1)
      .collect { page ->
        analytics.logScreenView("task_form/${TASK_FORM_TAB_KEYS.getOrElse(page) { "$page" }}")
      }
  }

  Scaffold(
    modifier = Modifier.imePadding(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Column {
        ConstrainedTopBar(ContentWidth.Form) {
          TopAppBar(
            title = {
              Text(
                stringResource(SharedTaskRes.string.edit_task).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
            },
            navigationIcon = {
              IconButton(onClick = { tryCancel() }) {
                Icon(
                  Icons.AutoMirrored.Default.ArrowBack,
                  contentDescription = stringResource(CoreRes.string.back)
                )
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = Color.Transparent,
              scrolledContainerColor = Color.Transparent,
            ),
          )
        }
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.TopCenter
        ) {
          TaskTabRow(
            tabs = listOf(
              BASIC_TAB,
              COMPLIANCE_TAB,
              SCHEDULE_TAB,
              ADJUSTMENT_TAB
            ),
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
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier = Modifier.padding(padding)
        .fillMaxSize()
    ) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f),
        beyondViewportPageCount = 3,
        verticalAlignment = Alignment.Top
      ) { page ->
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
        ) {
          Column(
            modifier = Modifier.fillMaxHeight()
              .constrainedContentWidth(ContentWidth.Form)
              .verticalScroll(rememberScrollState())
              .padding(Spacing.screenPadding)
          ) {
            when (page) {
              0 -> TaskIdentityTab(
                title = state.title,
                onTitleChange = onTitleChange,
                component = state.component,
                onComponentChange = null,
                isEditing = true,
                taskId = card.id,
                availableLogs = availableLogs,
                onAddLog = onShowLogPicker,
                onRemoveLog = onRemoveLog,
                attachmentSection = attachmentSection
              )

              1 -> TaskComplianceTab(
                complianceType = state.type,
                onComplianceTypeChange = null,
                refNumber = state.refNumber,
                onRefNumberChange = onRefNumberChange,
                complianceAuthority = state.complianceAuthority,
                onComplianceAuthorityChange = onComplianceAuthorityChange,
                complianceNotes = state.complianceNotes,
                onComplianceNotesChange = onComplianceNotesChange,
              )

              2 -> TaskScheduleTab(
                state = state.schedule,
                onChange = onScheduleChange,
                availableInspections = availableInspections.filter { it.id != card.id },
              )

              3 -> TaskAdjustmentsTab(
                schedule = state.schedule,
                forceOverrideEngine = state.forceOverrideEngine,
                onForceOverrideEngineChange = onForceOverrideEngineChange,
                forcedEngineHours = state.forcedEngineHours,
                onForcedEngineHoursChange = onForcedEngineHoursChange,
                forceOverrideDate = state.forceOverrideDate,
                onForceOverrideDateChange = onForceOverrideDateChange,
                forcedDateMillis = state.forcedDateMillis,
                onDateClick = { showDatePicker = true },
                naturalDueDate = naturalDueMetadata?.nextDueDate,
                naturalDueEngine = naturalDueMetadata?.nextDueEngine,
                currentEngineHours = currentEngineHours,
                onDeleteRequest = { showDeleteConfirm = true },
              )
            }
          }
        }
      }

      BottomButtons(
        onPrimaryClick = {
          val existingTimeRuleCreationDate =
            card.rules.firstNotNullOfOrNull { it.time_rule?.creation_date }
          val ruleList = state.schedule.toRules(existingTimeRuleCreationDate)

          val updatedForceDueEngine =
            if (state.forceOverrideEngine) state.forcedEngineHours.toFloatOrNull()
              ?: 0f else 0f
          val updatedForceDueDate =
            if (state.forceOverrideDate) state.forcedDateMillis?.let {
              toWireInstant(
                it / 1000,
                0
              )
            } else null

          val updated = card.copy(
            title = state.title,
            component = state.component,
            type = state.type,
            rules = ruleList,
            is_one_time = state.schedule.isOneTime,
            reference_number = state.refNumber.takeIf { it.isNotBlank() } ?: "",
            compliance_authority = state.complianceAuthority.takeIf { it.isNotBlank() }
              ?: "",
            compliance_details = state.complianceNotes.takeIf { it.isNotBlank() }
              ?: "",
            force_due_engine_hour = updatedForceDueEngine,
            force_due_date = updatedForceDueDate,
            // Skip This Cycle is now a separate, immediately-persisted action off the Resolve
            // menu (see TaskViewModel.skipThisCycle) — this form never touches it, so it just
            // carries the card's current value forward unchanged.
            force_complied_status = card.force_complied_status
          )
          onSave(updated)
        },
        onSecondaryClick = { tryCancel() },
        onDangerClick = onResolveClick,
        dangerLabel = stringResource(Res.string.resolve_task),
        dangerMenuContent = {
          ResolveTaskOptionsMenu(
            expanded = state.showResolveMenu,
            onDismissRequest = onResolveMenuDismiss,
            onCreateWorkLog = onCreateWorkLogClick,
            onSkipThisCycle = {
              onResolveMenuDismiss()
              showSkipConfirm = true
            },
          )
        },
        primaryEnabled = state.title.isNotBlank(),
        isPrimaryFunctionInProgress = isSaving
      )
    }
  }

  if (showDeleteConfirm) {
    DeleteTaskConfirmDialog(
      title = state.title,
      onConfirm = {
        showDeleteConfirm = false
        onDeleteRequest(card.id)
      },
      onDismiss = { showDeleteConfirm = false })
  }

  if (showSkipConfirm) {
    SkipTaskConfirmDialog(
      onConfirm = {
        showSkipConfirm = false
        onSkipConfirm()
      },
      onDismiss = { showSkipConfirm = false })
  }

  if (showLogPicker) {
    val linkedIds = remember(availableLogs, card.id) {
      availableLogs.filter { card.id in it.inspection_ids }
        .map { it.id }
        .toSet()
    }
    LogPickerSheet(
      logs = availableLogs.filter { it.id !in linkedIds },
      onSelect = onAddLog,
      onDismiss = onDismissLogPicker,
    )
  }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          onForcedDateMillisChange(datePickerState.selectedDateMillis)
          showDatePicker = false
        }) { Text(stringResource(CoreRes.string.ok)) }
      }) {
      DatePicker(state = datePickerState)
    }
  }
}
