package dev.fanfly.wingslog.feature.logs.update.form

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import dev.fanfly.wingslog.core.ui.adaptive.layout.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.layout.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.compose.DangerZone
import dev.fanfly.wingslog.core.ui.common.compose.UnsavedChangesDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.model.dataLogIds
import dev.fanfly.wingslog.feature.attachment.viewing.AttachmentFormSection
import dev.fanfly.wingslog.feature.datalog.viewing.attach.rememberDataLogPickerSlot
import dev.fanfly.wingslog.feature.logs.update.form.hours.LogTimeTab
import dev.fanfly.wingslog.feature.logs.update.form.records.LogRecordsTab
import dev.fanfly.wingslog.feature.logs.update.form.work.LogWorkTab
import dev.fanfly.wingslog.feature.logs.viewing.DeleteLogConfirmDialog
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.back
import wingslog.core.sharedassets.generated.resources.save
import wingslog.feature.attachment.sharedassets.generated.resources.file_read_error
import wingslog.feature.logs.sharedassets.generated.resources.add_log
import wingslog.feature.logs.sharedassets.generated.resources.edit_log
import wingslog.feature.logs.sharedassets.generated.resources.log_deleted
import wingslog.feature.logs.sharedassets.generated.resources.resolve_squawk_work_description
import wingslog.feature.logs.sharedassets.generated.resources.resolve_task_work_description
import wingslog.feature.logs.update.generated.resources.delete_this_log_subtitle
import wingslog.feature.logs.update.generated.resources.delete_this_log_title
import wingslog.feature.logs.update.generated.resources.log_saved
import wingslog.feature.logs.update.generated.resources.log_updated
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
  val logDeletedMessage = stringResource(
    SharedRes.string.log_deleted,
    LexiconFormatter.sentenceCase(LocalThingLexicon.current.logNoun),
  )
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
                    LocalThingLexicon.current.logNoun.singular,
                  )
                else
                  stringResource(
                    SharedRes.string.add_log,
                    LocalThingLexicon.current.logNoun.singular,
                  ),
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
                  meterSuggestions = uiState.meterSuggestions,
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
                      dataLogPicker = rememberDataLogPickerSlot(
                        ThingId(
                          viewModel.thingId
                        ),
                        uiState.maintenanceDate,
                        uiState.pendingAttachments.dataLogIds()
                      ),
                      onAttachDataLogs = viewModel::attachDataLogs,
                      modifier = Modifier,
                    )
                  },
                )
              }
              // Edit only, and last on the last tab: there is nothing to delete yet on a new log.
              if (viewModel.isEditMode && tabs[page] == tabs.last()) {
                Spacer(Modifier.height(Spacing.extraLarge))
                DangerZone(
                  title = stringResource(
                    MaintenanceRes.string.delete_this_log_title,
                    LocalThingLexicon.current.logNoun.singular,
                  ),
                  subtitle = stringResource(MaintenanceRes.string.delete_this_log_subtitle),
                  onDelete = { showDeleteDialog = true },
                )
              }
            }
          }
        }

        BottomButtons(
          onPrimaryClick = viewModel::save,
          onSecondaryClick = { tryNavigateBack() },
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
    DeleteLogConfirmDialog(
      onConfirm = {
        viewModel.deleteLog()
        showDeleteDialog = false
      },
      onDismiss = { showDeleteDialog = false },
    )
  }

  if (showDatePicker) {
    LogDatePickerDialog(
      initialDate = uiState.maintenanceDate,
      onConfirm = viewModel::onMaintenanceDateChange,
      onDismiss = { showDatePicker = false },
    )
  }

  LogFormPickerSheets(uiState, viewModel, navController)
}
