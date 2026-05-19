package dev.fanfly.wingslog.feature.tasks.update.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import dev.fanfly.wingslog.aircraft.ForceCompliedStatus
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.update.compose.ADJUSTMENT_TAB
import dev.fanfly.wingslog.feature.tasks.update.compose.BASIC_TAB
import dev.fanfly.wingslog.feature.tasks.update.compose.DETAILS_TAB
import dev.fanfly.wingslog.feature.tasks.update.compose.DeleteTaskConfirmDialog
import dev.fanfly.wingslog.feature.tasks.update.compose.SCHEDULE_TAB
import dev.fanfly.wingslog.feature.tasks.update.compose.ScheduleState
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskAdjustmentsTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskDetailTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskIdentityTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskScheduleTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskTabRow
import kotlin.time.Clock
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.back
import wingslog.core.ui.generated.resources.ok
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedTaskRes
import wingslog.feature.tasks.sharedassets.generated.resources.edit_task

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalComposeUiApi::class
)
@Composable
fun EditTaskScreen(
  card: MaintenanceTask,
  availableInspections: List<MaintenanceTask>,
  currentEngineHours: Float,
  naturalDueMetadata: DueMetadata?,
  onSave: (MaintenanceTask) -> Unit,
  onCancel: () -> Unit,
  onDeleteRequest: (String) -> Unit,
  isSaving: Boolean = false,
  attachmentSection: @Composable () -> Unit = {},
) {
  var title by remember { mutableStateOf(card.title) }
  val component = card.component
  val type = card.type

  val initialSchedule = remember(card) { ScheduleState.fromTask(card) }
  var schedule by remember { mutableStateOf(initialSchedule) }
  var refNumber by remember { mutableStateOf(card.reference_number) }
  var complianceAuthority by remember { mutableStateOf(card.compliance_authority) }
  var complianceNotes by remember { mutableStateOf(card.compliance_details) }
  var forceCompliedStatus by remember { mutableStateOf(card.force_complied_status) }

  // Force Override States
  var forceOverrideEngine by remember { mutableStateOf(card.force_due_engine_hour > 0f) }
  var forcedEngineHours by remember {
    mutableStateOf(
      if (card.force_due_engine_hour > 0f) card.force_due_engine_hour.toString() else ""
    )
  }
  var forceOverrideDate by remember { mutableStateOf(card.force_due_date != null) }
  var forcedDateMillis by remember {
    mutableStateOf(card.force_due_date?.let { it.getEpochSecond() * 1000 })
  }

  var showDatePicker by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }
  var showUnsavedChangesDialog by remember { mutableStateOf(false) }

  val hasChanges = title != card.title ||
    schedule != initialSchedule ||
    refNumber != card.reference_number ||
    complianceAuthority != card.compliance_authority ||
    complianceNotes != card.compliance_details ||
    forceCompliedStatus != card.force_complied_status ||
    forceOverrideEngine != (card.force_due_engine_hour > 0f) ||
    (forceOverrideEngine && forcedEngineHours != (
      if (card.force_due_engine_hour > 0f) card.force_due_engine_hour.toString() else ""
      )) ||
    forceOverrideDate != (card.force_due_date != null) ||
    (forceOverrideDate && forcedDateMillis != card.force_due_date?.let { it.getEpochSecond() * 1000 })

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

  Scaffold(
    topBar = {
      Column {
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
          })
        TaskTabRow(
          tabs = listOf(
            BASIC_TAB,
            DETAILS_TAB,
            SCHEDULE_TAB,
            ADJUSTMENT_TAB
          ),
          selectedIndex = pagerState.currentPage,
          onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
        )
      }
    }) { padding ->
    Column(
      modifier = Modifier.padding(padding).fillMaxSize()
    ) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f),
        beyondViewportPageCount = 3,
        verticalAlignment = Alignment.Top
      ) { page ->
        Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(Spacing.screenPadding)
        ) {
          when (page) {
            0 -> TaskIdentityTab(
              title = title,
              onTitleChange = { title = it },
              component = component,
              onComponentChange = null,
              complianceType = type,
              onComplianceTypeChange = null,
            )

            1 -> TaskDetailTab(
              refNumber = refNumber,
              onRefNumberChange = { refNumber = it },
              complianceAuthority = complianceAuthority,
              onComplianceAuthorityChange = { complianceAuthority = it },
              complianceNotes = complianceNotes,
              onComplianceNotesChange = { complianceNotes = it },
              attachmentSection = attachmentSection
            )

            2 -> TaskScheduleTab(
              state = schedule,
              onChange = { schedule = it },
              availableInspections = availableInspections.filter { it.id != card.id },
            )

            3 -> TaskAdjustmentsTab(
              schedule = schedule,
              forceOverrideEngine = forceOverrideEngine,
              onForceOverrideEngineChange = { forceOverrideEngine = it },
              forcedEngineHours = forcedEngineHours,
              onForcedEngineHoursChange = { forcedEngineHours = it },
              forceOverrideDate = forceOverrideDate,
              onForceOverrideDateChange = { forceOverrideDate = it },
              forcedDateMillis = forcedDateMillis,
              onDateClick = { showDatePicker = true },
              isSkipping = forceCompliedStatus != null,
              onSkipToggle = {
                forceCompliedStatus = if (forceCompliedStatus != null) null else {
                  ForceCompliedStatus(
                    complied_date = toWireInstant(Clock.System.now().epochSeconds),
                    complied_engine_hours = currentEngineHours
                  )
                }
              },
              naturalDueDate = naturalDueMetadata?.nextDueDate,
              naturalDueEngine = naturalDueMetadata?.nextDueEngine,
              currentEngineHours = currentEngineHours,
            )
          }
        }
      }

      BottomButtons(
        onPrimaryClick = {
          val existingTimeRuleCreationDate =
            card.rules.firstNotNullOfOrNull { it.time_rule?.creation_date }
          val ruleList = schedule.toRules(existingTimeRuleCreationDate)

          val updatedForceDueEngine =
            if (forceOverrideEngine) forcedEngineHours.toFloatOrNull() ?: 0f else 0f
          val updatedForceDueDate = if (forceOverrideDate) forcedDateMillis?.let {
            toWireInstant(
              it / 1000,
              0
            )
          } else null

          val isScheduleChanged = ruleList != card.rules ||
            schedule.isOneTime != card.is_one_time ||
            updatedForceDueEngine != card.force_due_engine_hour ||
            updatedForceDueDate != card.force_due_date

          val updated = card.copy(
            title = title,
            component = component,
            type = type,
            rules = ruleList,
            is_one_time = schedule.isOneTime,
            reference_number = refNumber.takeIf { it.isNotBlank() } ?: "",
            compliance_authority = complianceAuthority.takeIf { it.isNotBlank() } ?: "",
            compliance_details = complianceNotes.takeIf { it.isNotBlank() } ?: "",
            force_due_engine_hour = updatedForceDueEngine,
            force_due_date = updatedForceDueDate,
            force_complied_status = if (isScheduleChanged) null else forceCompliedStatus
          )
          onSave(updated)
        },
        onSecondaryClick = { tryCancel() },
        onDangerClick = { showDeleteConfirm = true },
        primaryEnabled = title.isNotBlank(),
        isPrimaryFunctionInProgress = isSaving
      )
    }
  }

  if (showDeleteConfirm) {
    DeleteTaskConfirmDialog(
      title = title,
      onConfirm = {
        showDeleteConfirm = false
        onDeleteRequest(card.id)
      },
      onDismiss = { showDeleteConfirm = false })
  }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          forcedDateMillis = datePickerState.selectedDateMillis
          showDatePicker = false
        }) { Text(stringResource(CoreRes.string.ok)) }
      }) {
      DatePicker(state = datePickerState)
    }
  }
}
