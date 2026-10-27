package dev.fanfly.wingslog.feature.logs.dashboard

import kotlinx.coroutines.launch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.ui.adaptive.shell.LocalSnackbarHostState
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.datamanager.OpenState
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.dataLogIdOrNull
import dev.fanfly.wingslog.feature.logs.viewing.list.MaintenanceLogListContent
import dev.fanfly.wingslog.feature.logs.viewing.list.MaintenanceLogListEvent
import dev.fanfly.wingslog.feature.logs.viewing.list.MaintenanceLogListViewModel
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.ThingId

@Composable
fun LogsTab(
  thingId: ThingId,
  onNavigateToAddLog: (() -> Unit)?,
  onNavigateToEditLog: ((logId: MaintenanceLogId) -> Unit)?,
  onOpenDataLog: (DataLogId) -> Unit,
  onTaskClick: (taskId: String) -> Unit,
  onSquawkClick: (squawkId: String) -> Unit,
  scrollToLogId: String? = null,
  modifier: Modifier = Modifier,
) {
  // Key by thingId: in the adaptive shell the switcher swaps thing within the same
  // composition site, so an unkeyed ViewModel would be reused and keep the previous thing's logs.
  val templateId = LocalThingTemplate.current?.id.orEmpty()
  val viewModel: MaintenanceLogListViewModel =
    koinViewModel(
      key = thingId.value,
      parameters = { parametersOf(thingId, templateId) })
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val filter by viewModel.filter.collectAsStateWithLifecycle()
  // The ViewModel outlives the section: a log left open when the user switched away would still
  // be open on their return. Each entry to the section starts with the list alone.
  LaunchedEffect(viewModel) { viewModel.onDismissDetail() }
  val attachmentManager: AttachmentManager = koinInject()
  val attachmentOpener: AttachmentOpener = koinInject()
  val dataLogManager: DataLogManager = koinInject()
  val syncStates by remember(thingId, attachmentManager, attachmentOpener) {
    attachmentManager.observeThingSyncStates(thingId, attachmentOpener)
  }.collectAsStateWithLifecycle(emptyMap())
  val dataLogs by remember(thingId, dataLogManager) {
    dataLogManager.observeRowInfos(thingId)
  }.collectAsStateWithLifecycle(null)
  val coroutineScope = rememberCoroutineScope()
  var openError by remember { mutableStateOf<String?>(null) }
  // A quick action runs inside the shell entry, so the cross-screen back-stack channel is the
  // wrong shape for its snackbar; the shell provides its host here instead (design §7).
  val snackbarHostState = LocalSnackbarHostState.current
  var pendingMessage by remember { mutableStateOf<UiText?>(null) }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is MaintenanceLogListEvent.ShowMessage -> pendingMessage = event.message
        is MaintenanceLogListEvent.NavigateToCreateLog -> onNavigateToAddLog?.invoke()
        is MaintenanceLogListEvent.NavigateToEditLog -> onNavigateToEditLog?.invoke(
          event.logId
        )
      }
    }
  }
  // Resolved in composition, because a UiText needs the resource table; a host that provided no
  // snackbar (a preview) drops it silently.
  val messageText = pendingMessage?.asString()
  LaunchedEffect(messageText) {
    val text = messageText ?: return@LaunchedEffect
    pendingMessage = null
    snackbarHostState?.showSnackbar(text)
  }

  MaintenanceLogListContent(
    uiState = uiState,
    filter = filter,
    syncStates = syncStates,
    dataLogs = dataLogs,
    onSearchQueryChange = viewModel::onSearchQueryChange,
    onComponentFilterToggle = viewModel::onComponentFilterToggle,
    onTimeWindowChange = viewModel::onTimeWindowChange,
    onFacetToggle = viewModel::onFacetToggle,
    onClearFilter = viewModel::clearFilter,
    onRetry = viewModel::retryLoading,
    onLogClick = viewModel::onLogClick,
    onDismissDetail = {
      openError = null
      viewModel.onDismissDetail()
    },
    onEditLog = onNavigateToEditLog?.let { viewModel::onEditLog },
    // Same gate as edit: whoever may open the form may swipe (PRD R20).
    onDeleteLog = onNavigateToEditLog?.let { viewModel::onDeleteLogClick },
    onCancelDeleteLog = viewModel::cancelDeleteLog,
    onConfirmDeleteLog = viewModel::confirmDeleteLog,
    onAddLog = onNavigateToAddLog?.let { viewModel::onAddLog },
    onAttachmentTap = { attachment ->
      openError = null
      attachment.dataLogIdOrNull()
        ?.let { dataLogId ->
          viewModel.onDismissDetail()
          onOpenDataLog(dataLogId)
          return@MaintenanceLogListContent
        }
      // Call open() synchronously inside the click handler so AttachmentOpenerWeb can
      // reserve window.open() during the user-gesture stack. Only the flow collection
      // moves into the coroutine.
      val openFlow = attachmentOpener.open(attachment)
      coroutineScope.launch {
        openFlow.collect { state ->
          if (state is OpenState.Failed) openError = state.error.message
        }
      }
    },
    openError = openError,
    onTaskClick = onTaskClick,
    onSquawkClick = onSquawkClick,
    scrollToLogId = scrollToLogId,
    modifier = modifier,
  )
}
