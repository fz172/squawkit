package dev.fanfly.wingslog.feature.logs.update.logs

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.DatePickerDialog
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.viewing.AttachmentFormSection
import dev.fanfly.wingslog.feature.logs.update.logs.compose.LOG_FORM_TAB_KEYS
import dev.fanfly.wingslog.feature.logs.update.logs.compose.LogFormTab
import dev.fanfly.wingslog.feature.logs.update.logs.compose.LogRecordsTab
import dev.fanfly.wingslog.feature.logs.update.logs.compose.LogTabRow
import dev.fanfly.wingslog.feature.logs.update.logs.compose.LogTimeTab
import dev.fanfly.wingslog.feature.logs.update.logs.compose.LogWorkTab
import dev.fanfly.wingslog.feature.logs.update.logs.compose.logFormTabsFor
import dev.fanfly.wingslog.feature.logs.update.logs.viewmodel.MaintenanceLogFormEvent
import dev.fanfly.wingslog.feature.logs.update.logs.viewmodel.MaintenanceLogFormViewModel
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkPickerSheet
import dev.fanfly.wingslog.feature.tasks.update.compose.TaskPickerSheet
import dev.fanfly.wingslog.feature.technician.manage.compose.TechnicianPickerSheet
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.back
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.core.sharedassets.generated.resources.ok
import wingslog.core.sharedassets.generated.resources.save
import wingslog.feature.attachment.sharedassets.generated.resources.file_read_error
import wingslog.feature.logs.sharedassets.generated.resources.add_log
import wingslog.feature.logs.sharedassets.generated.resources.edit_log
import wingslog.feature.logs.sharedassets.generated.resources.resolve_squawk_work_description
import wingslog.feature.logs.sharedassets.generated.resources.resolve_task_work_description
import wingslog.feature.logs.sharedassets.generated.resources.this_action_cannot_be_undone
import wingslog.feature.logs.update.generated.resources.delete_log
import wingslog.feature.logs.update.generated.resources.log_deleted
import wingslog.feature.logs.update.generated.resources.log_saved
import wingslog.feature.logs.update.generated.resources.log_updated
import kotlin.time.Instant
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.logs.update.generated.resources.Res as MaintenanceRes

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalComposeUiApi::class,
)
@Composable
fun MaintenanceLogFormScreen(
  navController: NavController,
  viewModel: MaintenanceLogFormViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }
  var showUnsavedChangesDialog by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }
  val tabs = logFormTabsFor(LocalThingCapabilities.current)
  val pagerState = rememberPagerState(pageCount = { tabs.size })
  val coroutineScope = rememberCoroutineScope()
  val analytics = LocalAnalytics.current
  // Log tab switches (tap or swipe) as page views; drop(1) skips the initial page on open.
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }
      .drop(1)
      .collect { page ->
        analytics.logScreenView("log_form/${LOG_FORM_TAB_KEYS.getOrElse(page) { "$page" }}")
      }
  }

  val tryNavigateBack = {
    if (uiState.hasChanges) showUnsavedChangesDialog = true
    else navController.popBackStack()
  }
  BackHandler(enabled = uiState.hasChanges) {
    showUnsavedChangesDialog = true
  }

  val logUpdatedMessage = stringResource(MaintenanceRes.string.log_updated)
  val logSavedMessage = stringResource(MaintenanceRes.string.log_saved)
  val logDeletedMessage = stringResource(MaintenanceRes.string.log_deleted)
  val fileReadErrorMessage = stringResource(AttachRes.string.file_read_error)

  // Opened via the squawk edit screen's "Fixed" option: resolve the localized prefill once the
  // squawk's title is known, then hand the plain string to the ViewModel to prepend to
  // workDescription.
  val resolveSquawkPrefill = uiState.pendingResolveSquawkTitle?.let { title ->
    stringResource(
      SharedRes.string.resolve_squawk_work_description,
      title,
      LocalThingLexicon.current.squawkNoun.singular,
    )
  }
  LaunchedEffect(resolveSquawkPrefill) {
    resolveSquawkPrefill?.let { viewModel.consumeResolveSquawkPrefill(it) }
  }

  // Opened via the task edit screen's "Create Work Log" resolve option: mirrors the squawk
  // prefill flow above.
  val resolveTaskPrefill = uiState.pendingResolveTaskTitle?.let { title ->
    stringResource(
      SharedRes.string.resolve_task_work_description,
      title,
      LocalThingLexicon.current.taskNoun.singular,
    )
  }
  LaunchedEffect(resolveTaskPrefill) {
    resolveTaskPrefill?.let { viewModel.consumeResolveTaskPrefill(it) }
  }

  // Attachment skips (over the file cap, already attached, too large) surface here rather than in
  // uiState.error, which renders on the Work tab's description field — a tab away from the
  // attachment list that produced them.
  val attachmentErrorMessage = uiState.attachmentError?.asString()
  LaunchedEffect(attachmentErrorMessage) {
    attachmentErrorMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearAttachmentError()
    }
  }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        MaintenanceLogFormEvent.SaveSuccess -> {
          val message =
            if (viewModel.isEditMode) logUpdatedMessage else logSavedMessage
          navController.previousBackStackEntry?.savedStateHandle?.set(
            CROSS_SCREEN_SUCCESS_MESSAGE,
            message,
          )
          navController.popBackStack()
        }

        MaintenanceLogFormEvent.DeleteSuccess -> {
          navController.previousBackStackEntry?.savedStateHandle?.set(
            CROSS_SCREEN_SUCCESS_MESSAGE,
            logDeletedMessage,
          )
          navController.popBackStack()
        }

        MaintenanceLogFormEvent.PickError -> snackbarHostState.showSnackbar(
          fileReadErrorMessage
        )

        else -> Unit
      }
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
                text = if (viewModel.isEditMode)
                  stringResource(
                    SharedRes.string.edit_log,
                    LexiconFormatter.titleCase(LocalThingLexicon.current.logNoun),
                  ).uppercase()
                else
                  stringResource(
                    SharedRes.string.add_log,
                    LexiconFormatter.titleCase(LocalThingLexicon.current.logNoun),
                  ).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
            },
            navigationIcon = {
              IconButton(onClick = { tryNavigateBack() }) {
                Icon(
                  Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = stringResource(CoreRes.string.back),
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
          LogTabRow(
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
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    if (uiState.isLoading) {
      Box(
        modifier = Modifier.fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    } else {
      Column(
        modifier = Modifier.padding(innerPadding)
          .fillMaxSize()
      ) {
        HorizontalPager(
          state = pagerState,
          modifier = Modifier.weight(1f),
          beyondViewportPageCount = 2,
          verticalAlignment = Alignment.Top,
        ) { page ->
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
          ) {
            Column(
              modifier = Modifier
                .fillMaxHeight()
                .constrainedContentWidth(ContentWidth.Form)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenPadding),
            ) {
              when (tabs[page]) {
                LogFormTab.WORK -> LogWorkTab(
                  maintenanceDate = uiState.maintenanceDate,
                  onDateClick = { showDatePicker = true },
                  workDescription = uiState.workDescription,
                  onWorkDescriptionChange = viewModel::onWorkDescriptionChange,
                  thing = uiState.thing,
                  selectedComponentType = uiState.selectedComponentType,
                  onComponentTypeChange = viewModel::onComponentTypeChange,
                  selectedSubComponent = uiState.selectedSubComponent,
                  onSubComponentChange = viewModel::onSubComponentChange,
                  error = uiState.error,
                )

                LogFormTab.HOURS -> LogTimeTab(
              meterValues = uiState.meterValues,
              onMeterChange = viewModel::onMeterChanged,
                )

                LogFormTab.RECORDS -> LogRecordsTab(
                  selectedTechnician = uiState.selectedTechnician,
                  onTechnicianClick = viewModel::showTechnicianPicker,
                  selectedSquawkIds = uiState.selectedSquawkIds,
                  availableSquawks = uiState.availableSquawks,
                  onAddSquawkClick = viewModel::showSquawkPicker,
                  onRemoveSquawk = viewModel::removeSquawkId,
                  selectedInspectionIds = uiState.selectedInspectionIds,
                  availableInspectionCards = uiState.availableInspectionCards,
                  onAddTaskClick = viewModel::showInspectionPicker,
                  onRemoveTask = viewModel::removeInspectionId,
                  attachmentSection = {
                    AttachmentFormSection(
                      visibleAttachments = uiState.visibleAttachments,
                      isAnonymous = uiState.isAnonymous,
                      filesAtLimit = uiState.filesAtLimit,
                      uploadEnabled = uiState.attachmentUploadEnabled,
                      showPickerSheet = uiState.showAttachmentPicker,
                      onAddClick = viewModel::showAttachmentPicker,
                      onRemove = viewModel::removeAttachment,
                      onPickFiles = viewModel::addLocalFiles,
                      onAddLink = viewModel::addLink,
                      onDismissSheet = viewModel::hideAttachmentPicker,
                      onPickError = viewModel::onFilePickError,
                      onSeePlans = { navController.navigate(Screen.Subscription.route) },
                      modifier = Modifier,
                    )
                  },
                )
              }
            }
          }
        }

        BottomButtons(
          onPrimaryClick = viewModel::save,
          onSecondaryClick = { tryNavigateBack() },
          onDangerClick = if (viewModel.isEditMode) {
            { showDeleteDialog = true }
          } else null,
          dangerLabel = stringResource(CoreRes.string.delete),
          primaryEnabled = !uiState.isSaving,
          isPrimaryFunctionInProgress = uiState.isSaving,
          primaryLabel = stringResource(CoreRes.string.save),
        )
      }
    }
  }

  // Overlays — rendered outside Scaffold so they float above all content

  if (showUnsavedChangesDialog) {
    UnsavedChangesDialog(
      onConfirm = {
        showUnsavedChangesDialog = false
        navController.popBackStack()
      },
      onDismiss = { showUnsavedChangesDialog = false },
    )
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(MaintenanceRes.string.delete_log)) },
      text = { Text(stringResource(SharedRes.string.this_action_cannot_be_undone)) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteLog()
            showDeleteDialog = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text(stringResource(CoreRes.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(stringResource(CoreRes.string.cancel))
        }
      },
    )
  }

  if (showDatePicker) {
    val initialMs = uiState.maintenanceDate?.let { date ->
      LocalDateTime(date.year, date.month, date.day, 12, 0, 0).let { ldt ->
        Instant.fromEpochSeconds(ldt.date.toEpochDays() * 86400L)
          .toEpochMilliseconds()
      }
    }
    val datePickerState =
      rememberDatePickerState(initialSelectedDateMillis = initialMs)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          val selectedMs = datePickerState.selectedDateMillis
          if (selectedMs != null) {
            val selectedDate =
              Instant.fromEpochMilliseconds(selectedMs)
                .toLocalDateTime(TimeZone.UTC).date
            viewModel.onMaintenanceDateChange(selectedDate)
          }
          showDatePicker = false
        }) {
          Text(stringResource(CoreRes.string.ok))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text(stringResource(CoreRes.string.cancel))
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  if (uiState.showTechnicianPicker) {
    TechnicianPickerSheet(
      availableTechnicians = uiState.availableTechnicians,
      knownCertifications = uiState.knownCertifications,
      linkedTechnicians = uiState.linkedTechnicians,
      selfId = uiState.selfTechnicianId,
      selectedId = uiState.selectedTechnician?.id,
      onSelect = { viewModel.onTechnicianSelect(it) },
      onAddClick = {
        viewModel.hideTechnicianPicker()
        navController.navigate(Screen.EditTechnician.createRoute(null))
      },
      onDismiss = { viewModel.hideTechnicianPicker() },
    )
  }

  if (uiState.showSquawkPicker) {
    SquawkPickerSheet(
      openSquawks = uiState.availableSquawks,
      selectedIds = uiState.selectedSquawkIds.toSet(),
      onToggle = { id, _ -> viewModel.toggleSquawkSelection(id) },
      onDismiss = viewModel::hideSquawkPicker,
    )
  }

  if (uiState.showInspectionPicker) {
    TaskPickerSheet(
      availableCards = uiState.availableInspectionCards,
      selectedIds = uiState.selectedInspectionIds,
      onToggle = viewModel::toggleInspectionSelection,
      onDismiss = viewModel::hideInspectionPicker,
    )
  }
}
