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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import dev.fanfly.wingslog.aircraft.EngineHourRule
import dev.fanfly.wingslog.aircraft.ForceCompliedStatus
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.LinkedRule
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.update.compose.DeleteTaskConfirmDialog
import dev.fanfly.wingslog.feature.tasks.update.compose.ForcedOverrideFields
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskDetailTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskIdentityTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskScheduleTab
import kotlin.time.Clock
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.back
import wingslog.core.ui.generated.resources.ok
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedInspectionRes
import wingslog.feature.tasks.sharedassets.generated.resources.edit_task
import wingslog.feature.tasks.update.generated.resources.Res as InspectionRes
import wingslog.feature.tasks.update.generated.resources.details
import wingslog.feature.tasks.update.generated.resources.identity
import wingslog.feature.tasks.update.generated.resources.overrides
import wingslog.feature.tasks.update.generated.resources.schedule

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun EditTaskScreen(
  card: MaintenanceTask,
  availableInspections: List<MaintenanceTask>,
  currentEngineHours: Float,
  onSave: (MaintenanceTask) -> Unit,
  onCancel: () -> Unit,
  onDeleteRequest: (String) -> Unit,
  isSaving: Boolean = false,
  attachmentSection: @Composable () -> Unit = {},
) {
  var title by remember { mutableStateOf(card.title) }
  val component = card.component
  val type = card.type
  var isOneTime by remember { mutableStateOf(card.is_one_time) }

  val initialIntervalMonths =
    card.rules.firstNotNullOfOrNull { it.time_rule?.interval_months }?.toString() ?: ""
  val initialIntervalHours =
    card.rules.firstNotNullOfOrNull { it.engine_hour_rule?.interval_hours }?.toString() ?: ""
  val initialLinkedId = card.rules.firstNotNullOfOrNull { it.linked_rule?.parent_inspection_id }

  var intervalMonths by remember { mutableStateOf(initialIntervalMonths) }
  var intervalHours by remember { mutableStateOf(initialIntervalHours) }
  var refNumber by remember { mutableStateOf(card.reference_number) }
  var complianceAuthority by remember { mutableStateOf(card.compliance_authority) }
  var complianceNotes by remember { mutableStateOf(card.compliance_details) }
  var linkedToId by remember { mutableStateOf(initialLinkedId) }
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
    isOneTime != card.is_one_time ||
    intervalMonths != initialIntervalMonths ||
    intervalHours != initialIntervalHours ||
    refNumber != card.reference_number ||
    complianceAuthority != card.compliance_authority ||
    complianceNotes != card.compliance_details ||
    linkedToId != initialLinkedId ||
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
        TopAppBar(title = {
          Text(
            stringResource(SharedInspectionRes.string.edit_task).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        }, navigationIcon = {
          IconButton(onClick = { tryCancel() }) {
            Icon(
              Icons.AutoMirrored.Default.ArrowBack,
              contentDescription = stringResource(CoreRes.string.back)
            )
          }
        })
        PrimaryTabRow(
          selectedTabIndex = pagerState.currentPage,
          containerColor = MaterialTheme.colorScheme.background,
        ) {
          Tab(
            selected = pagerState.currentPage == 0,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
            text = { Text(stringResource(InspectionRes.string.identity)) })
          Tab(
            selected = pagerState.currentPage == 1,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
            text = { Text(stringResource(InspectionRes.string.details)) })
          Tab(
            selected = pagerState.currentPage == 2,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
            text = { Text(stringResource(InspectionRes.string.schedule)) })
          Tab(
            selected = pagerState.currentPage == 3,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
            text = { Text(stringResource(InspectionRes.string.overrides)) })
        }
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
            0 -> {
              // --- Page 0: Identity (component & compliance type are read-only in edit) ---
              TaskIdentityTab(
                title = title,
                onTitleChange = { title = it },
                component = component,
                onComponentChange = null,
                complianceType = type,
                onComplianceTypeChange = null,
              )
            }

            1 -> {
              // --- Page 2: Details ---
              TaskDetailTab(
                refNumber = refNumber,
                onRefNumberChange = { refNumber = it },
                complianceAuthority = complianceAuthority,
                onComplianceAuthorityChange = { complianceAuthority = it },
                complianceNotes = complianceNotes,
                onComplianceNotesChange = { complianceNotes = it },
                attachmentSection = attachmentSection
              )
            }

            2 -> {
              // --- Page 1: Schedule ---
              TaskScheduleTab(
                isOneTime = isOneTime,
                onOneTimeChange = { isOneTime = it },
                intervalMonths = intervalMonths,
                onMonthsChange = { intervalMonths = it },
                intervalHours = intervalHours,
                onHoursChange = { intervalHours = it },
                linkedToId = linkedToId,
                onLinkChange = { linkedToId = it },
                availableInspections = availableInspections.filter { it.id != card.id }
              )
            }

            3 -> {
              // --- Page 3: Overrides ---
              ForcedOverrideFields(
                forceOverrideEngine = forceOverrideEngine,
                onForceOverrideEngineChange = { forceOverrideEngine = it },
                forcedEngineHours = forcedEngineHours,
                onForcedEngineHoursChange = { forcedEngineHours = it },
                forceOverrideDate = forceOverrideDate,
                onForceOverrideDateChange = { forceOverrideDate = it },
                forcedDateMillis = forcedDateMillis,
                onDateClick = { showDatePicker = true },
                isForceComplied = forceCompliedStatus != null,
                onToggleCompliedClick = {
                  forceCompliedStatus = if (forceCompliedStatus != null) null else {
                    ForceCompliedStatus(
                      complied_date = toWireInstant(Clock.System.now().epochSeconds),
                      complied_engine_hours = currentEngineHours
                    )
                  }
                }
              )
            }
          }
        }
      }

      BottomButtons(
        onPrimaryClick = {
          val ruleList = mutableListOf<InspectionRule>()
          val existingTimeRuleCreationDate =
            card.rules.firstNotNullOfOrNull { it.time_rule?.creation_date }
          val now = Clock.System.now()
          if (linkedToId != null) {
            ruleList.add(InspectionRule(linked_rule = LinkedRule(parent_inspection_id = linkedToId!!)))
          } else {
            intervalMonths.toIntOrNull()?.let {
              ruleList.add(
                InspectionRule(
                  time_rule = TimeRule(
                    interval_months = it,
                    creation_date = existingTimeRuleCreationDate
                      ?: toWireInstant(now.epochSeconds, now.nanosecondsOfSecond),
                  )
                )
              )
            }
            intervalHours.toFloatOrNull()?.let {
              ruleList.add(InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = it)))
            }
          }

          val updatedForceDueEngine =
            if (forceOverrideEngine) forcedEngineHours.toFloatOrNull() ?: 0f else 0f
          val updatedForceDueDate = if (forceOverrideDate) forcedDateMillis?.let {
            toWireInstant(
              it / 1000,
              0
            )
          } else null

          val isScheduleChanged = ruleList != card.rules ||
            isOneTime != card.is_one_time ||
            updatedForceDueEngine != card.force_due_engine_hour ||
            updatedForceDueDate != card.force_due_date

          val updated = card.copy(
            title = title,
            component = component,
            type = type,
            rules = ruleList,
            is_one_time = isOneTime,
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
    DeleteTaskConfirmDialog(title = title, onConfirm = {
      showDeleteConfirm = false
      onDeleteRequest(card.id)
    }, onDismiss = { showDeleteConfirm = false })
  }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
      TextButton(onClick = {
        forcedDateMillis = datePickerState.selectedDateMillis
        showDatePicker = false
      }) { Text(stringResource(CoreRes.string.ok)) }
    }) {
      DatePicker(state = datePickerState)
    }
  }
}
