package dev.fanfly.wingslog.feature.tasks.dashboard.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.ui.adaptive.shell.LocalSnackbarHostState
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.datamanager.OpenState
import dev.fanfly.wingslog.feature.comments.dashboard.RecordCommentComposer
import dev.fanfly.wingslog.feature.comments.dashboard.RecordCommentThread
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.dataLogIdOrNull
import dev.fanfly.wingslog.feature.tasks.viewing.DeleteTaskConfirmDialog
import dev.fanfly.wingslog.feature.tasks.viewing.SkipTaskConfirmDialog
import dev.fanfly.wingslog.feature.tasks.viewing.detail.TaskDetailSheet
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The open task's detail, which a wide tier hosts as a pane beside the list and a phone as a
 * sheet. Built at section level rather than in the Tasks tab because it can be opened from the
 * Overview tab or a log's Affected Tasks as well. Null while no task is selected.
 */
@Composable
fun taskDetailFor(
  detailViewModel: TaskDetailViewModel,
  thingId: String,
  isAnonymous: Boolean,
  onNavigateToEditTask: (cardId: String) -> Unit,
  onNavigateToCreateLog: (cardId: String) -> Unit,
  onOpenDataLog: (dataLogId: DataLogId) -> Unit,
): (@Composable () -> Unit)? {
  val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
  val commentThread by detailViewModel.commentThread.collectAsStateWithLifecycle()
  val attachmentManager: AttachmentManager = koinInject()
  val attachmentOpener: AttachmentOpener = koinInject()
  val dataLogManager: DataLogManager = koinInject()
  val coroutineScope = rememberCoroutineScope()
  var taskSheetOpenError by remember(thingId) { mutableStateOf<String?>(null) }
  return detailState.selectedTask?.let { selectedTask ->
    {
      val syncStates by remember(thingId, attachmentManager, attachmentOpener) {
        attachmentManager.observeThingSyncStates(thingId, attachmentOpener)
      }.collectAsStateWithLifecycle(emptyMap())
      val dataLogs by remember(thingId, dataLogManager) {
        dataLogManager.observeRowInfos(ThingId(thingId))
      }.collectAsStateWithLifecycle(null)
      TaskDetailSheet(
        cardWithStatus = selectedTask,
        logs = detailState.logsForSelectedTask,
        onDismiss = {
          taskSheetOpenError = null
          detailViewModel.dismissDetail()
        },
        onEditClick = {
          detailViewModel.dismissDetail()
          onNavigateToEditTask(selectedTask.card.id)
        },
        onAttachmentTap = { attachment ->
          taskSheetOpenError = null
          attachment.dataLogIdOrNull()
            ?.let { dataLogId ->
              detailViewModel.dismissDetail()
              onOpenDataLog(dataLogId)
              return@TaskDetailSheet
            }
          val openFlow = attachmentOpener.open(attachment)
          coroutineScope.launch {
            openFlow.collect { openState ->
              if (openState is OpenState.Failed) taskSheetOpenError =
                openState.error.message
            }
          }
        },
        syncStates = syncStates,
        dataLogs = dataLogs,
        openError = taskSheetOpenError,
        onLogWorkClick = {
          detailViewModel.dismissDetail()
          detailViewModel.onCreateLogClick()
          onNavigateToCreateLog(selectedTask.card.id)
        },
        onSkipCycleClick = {
          detailViewModel.dismissDetail()
          detailViewModel.onSkipClick(selectedTask)
        },
        comments = commentThread?.let { thread ->
          {
            RecordCommentThread(
              thread
            )
          }
        },
        commentComposer = commentThread?.let { thread ->
          { RecordCommentComposer(thread, isAnonymous) }
        },
      )
    }
  }
}

/**
 * Renders task skip/delete confirmation dialogs and collects snackbar events from
 * [TaskDetailViewModel] at section level so neither is clipped by a swipe container.
 */
@Composable
fun TaskDetailDialogs(
  detailViewModel: TaskDetailViewModel,
  thingId: String,
) {
  val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = LocalSnackbarHostState.current
  var pendingMessage by remember(thingId) { mutableStateOf<UiText?>(null) }
  LaunchedEffect(detailViewModel) {
    detailViewModel.events.collect { pendingMessage = it }
  }
  val messageText = pendingMessage?.asString()
  LaunchedEffect(messageText) {
    val text = messageText ?: return@LaunchedEffect
    pendingMessage = null
    snackbarHostState?.showSnackbar(text)
  }

  if (detailState.skippingTaskId != null) {
    SkipTaskConfirmDialog(
      onConfirm = detailViewModel::confirmSkip,
      onDismiss = detailViewModel::cancelSkip,
    )
  }

  if (detailState.deletingTaskId != null) {
    DeleteTaskConfirmDialog(
      title = detailState.deletingTaskTitle,
      onConfirm = detailViewModel::confirmDelete,
      onDismiss = detailViewModel::cancelDelete,
    )
  }
}
