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
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.datamanager.meterKeyFor
import dev.fanfly.wingslog.feature.tasks.datamanager.pickerMillisToDate
import dev.fanfly.wingslog.feature.tasks.datamanager.toDueInstant
import dev.fanfly.wingslog.feature.tasks.datamanager.withForcedDueMeter
import dev.fanfly.wingslog.feature.tasks.datamanager.withoutOverrides
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.update.compose.InitialDueControls
import dev.fanfly.wingslog.feature.tasks.update.compose.ScheduleState
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskComplianceTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskFormTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskIdentityTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskScheduleTab
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskTabRow
import dev.fanfly.wingslog.feature.tasks.update.compose.spec
import dev.fanfly.wingslog.feature.tasks.update.compose.taskFormTabsFor
import dev.fanfly.wingslog.feature.tasks.update.viewmodel.TaskFormState
import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceTask
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.back
import wingslog.core.sharedassets.generated.resources.ok
import wingslog.feature.tasks.sharedassets.generated.resources.add_task
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedTaskRes

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalComposeUiApi::class
)
@Composable
fun AddTaskScreen(
  state: TaskFormState,
  availableInspections: List<MaintenanceTask>,
  onTitleChange: (String) -> Unit,
  onComponentChange: (ComponentType) -> Unit,
  onTypeChange: (ComplianceType) -> Unit,
  onScheduleChange: (ScheduleState) -> Unit,
  onRefNumberChange: (String) -> Unit,
  onComplianceAuthorityChange: (String) -> Unit,
  onComplianceNotesChange: (String) -> Unit,
  onForceOverrideEngineChange: (Boolean) -> Unit,
  onForcedEngineHoursChange: (String) -> Unit,
  onForceOverrideDateChange: (Boolean) -> Unit,
  onForcedDateMillisChange: (Long?) -> Unit,
  /** The due engine over this Thing's logs, for the banner; null until the Thing has loaded. */
  previewDue: (MaintenanceTask) -> DueMetadata?,
  currentReading: (String) -> Float,
  onSave: (MaintenanceTask) -> Unit,
  onCancel: () -> Unit,
  isSaving: Boolean = false,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
  attachmentSection: @Composable () -> Unit = {},
) {
  var showUnsavedChangesDialog by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }

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

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          // Picking a date is what sets the first due; there is no separate switch on create.
          onForcedDateMillisChange(datePickerState.selectedDateMillis)
          onForceOverrideDateChange(datePickerState.selectedDateMillis != null)
          showDatePicker = false
        }) { Text(stringResource(CoreRes.string.ok)) }
      }) {
      DatePicker(state = datePickerState)
    }
  }

  val capabilities = LocalThingCapabilities.current
  // The card as Save would write it, built once for the banner and again for Save so the two
  // can never describe different tasks.
  val buildDraft: () -> MaintenanceTask = {
    val rules = state.schedule.toRules(
      dueOnAnniversary = capabilities.month_intervals_due_on_anniversary,
    )
    // The first due is the same override the Adjustments tab writes: the first cycle is the
    // user's, and the log that clears the first cycle clears the override with it.
    val firstDueDate =
      if (state.forceOverrideDate) state.forcedDateMillis?.pickerMillisToDate()
        ?.toDueInstant()
      else null
    val firstDueReading =
      if (state.forceOverrideEngine) state.forcedEngineHours.toFloatOrNull()
        ?.takeIf { it > 0f }
      else null
    MaintenanceTask(
      id = "",
      title = state.title,
      component = state.component,
      type = state.type,
      rules = rules,
      reference_number = state.refNumber.takeIf { it.isNotBlank() } ?: "",
      compliance_authority = state.complianceAuthority.takeIf { it.isNotBlank() }
        ?: "",
      compliance_details = state.complianceNotes.takeIf { it.isNotBlank() }
        ?: "",
      is_one_time = state.schedule.isOneTime,
      force_due_date = firstDueDate,
      notes = "",
    ).withForcedDueMeter(meterKeyFor(state.component, rules), firstDueReading)
  }
  val draft = buildDraft()
  val effectiveDue = previewDue(draft)
  val naturalDue = previewDue(draft.withoutOverrides())

  val tabs = taskFormTabsFor(
    capabilities,
    includeAdjustments = false,
    // A task that does not exist yet has no id for a comment to point at.
    includeComments = false,
  )
  val pagerState = rememberPagerState(pageCount = { tabs.size })
  val coroutineScope = rememberCoroutineScope()
  val analytics = LocalAnalytics.current
  // Log tab switches (tap or swipe) as page views; drop(1) skips the initial page on open.
  // Keyed on tabs too: the capability set can resolve while the form is up and change the list.
  LaunchedEffect(pagerState, tabs) {
    snapshotFlow { pagerState.currentPage }
      .drop(1)
      .collect { page ->
        tabs.getOrNull(page)
          ?.let { analytics.logScreenView("task_form/${it.analyticsKey}") }
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
                stringResource(SharedTaskRes.string.add_task).uppercase(),
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
            tabs = tabs.map { it.spec },
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
        beyondViewportPageCount = 2,
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
            when (tabs[page]) {
              TaskFormTab.IDENTITY -> TaskIdentityTab(
                title = state.title,
                onTitleChange = onTitleChange,
                component = state.component,
                onComponentChange = onComponentChange,
                attachmentSection = attachmentSection
              )

              TaskFormTab.COMPLIANCE -> TaskComplianceTab(
                complianceType = state.type,
                onComplianceTypeChange = onTypeChange,
                refNumber = state.refNumber,
                onRefNumberChange = onRefNumberChange,
                complianceAuthority = state.complianceAuthority,
                onComplianceAuthorityChange = onComplianceAuthorityChange,
                complianceNotes = state.complianceNotes,
                onComplianceNotesChange = onComplianceNotesChange,
              )

              TaskFormTab.SCHEDULE -> TaskScheduleTab(
                state = state.schedule,
                onChange = onScheduleChange,
                component = state.component,
                availableInspections = availableInspections,
                // "Due every 12 months, first on 15 Sep" — the first cycle set here, the schedule
                // from then on. Create only; an existing task reschedules from Adjustments.
                initialDue = InitialDueControls(
                  forceOverrideDate = state.forceOverrideDate,
                  onForceOverrideDateChange = onForceOverrideDateChange,
                  forcedDateMillis = state.forcedDateMillis,
                  onForcedDateMillisChange = onForcedDateMillisChange,
                  onDateClick = { showDatePicker = true },
                  forceOverrideEngine = state.forceOverrideEngine,
                  onForceOverrideEngineChange = onForceOverrideEngineChange,
                  forcedEngineHours = state.forcedEngineHours,
                  onForcedEngineHoursChange = onForcedEngineHoursChange,
                ),
                effectiveDue = effectiveDue,
                naturalDue = naturalDue,
                overrideOn = state.forceOverrideDate || state.forceOverrideEngine,
                currentReading = currentReading,
              )

              // Unreachable: this screen passes includeAdjustments = false, so the tab is never in
              // the list. Spelled out rather than covered by an `else`, because an `else` would also
              // swallow a tab added later and render a blank page instead of failing the build.
              TaskFormTab.ADJUSTMENTS, TaskFormTab.COMMENTS -> Unit
            }
          }
        }
      }

      BottomButtons(
        onPrimaryClick = { onSave(buildDraft()) },
        onSecondaryClick = { tryCancel() },
        primaryEnabled = state.title.isNotBlank(),
        isPrimaryFunctionInProgress = isSaving
      )
    }
  }
}
