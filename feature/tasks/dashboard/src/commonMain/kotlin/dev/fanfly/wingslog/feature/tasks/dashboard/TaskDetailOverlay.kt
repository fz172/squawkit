package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.datamanager.OpenState
import dev.fanfly.wingslog.feature.comments.dashboard.RecordCommentComposer
import dev.fanfly.wingslog.feature.comments.dashboard.RecordCommentThread
import dev.fanfly.wingslog.feature.comments.datamanager.CommentThreadController
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.datalog.model.dataLogIdOrNull
import dev.fanfly.wingslog.feature.tasks.viewing.detail.TaskDetailSheet
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The open task's detail, which a wide tier hosts as a pane beside the list and a phone as a
 * sheet. Built at section level rather than in the Tasks tab because it can be opened from a log's
 * Affected Tasks as well. Null while no task is selected.
 */
@Composable
fun taskDetailFor(
  state: ThingOverviewUiState.Success,
  thingId: String,
  onAction: (ThingOverviewAction) -> Unit,
  commentThread: CommentThreadController?,
): (@Composable () -> Unit)? {
  val attachmentOpener: AttachmentOpener = koinInject()
  val coroutineScope = rememberCoroutineScope()
  var taskSheetOpenError by remember(thingId) { mutableStateOf<String?>(null) }
  return state.selectedTask?.let { selectedTask ->
    {
      TaskDetailSheet(
        cardWithStatus = selectedTask,
        logs = state.logsForSelectedTask,
        onDismiss = {
          taskSheetOpenError = null
          onAction(ThingOverviewAction.DismissTaskDetail)
        },
        onEditClick = {
          onAction(
            ThingOverviewAction.EditTaskClick(
              thingId,
              selectedTask.card.id
            )
          )
        },
        onAttachmentTap = { attachment ->
          taskSheetOpenError = null
          attachment.dataLogIdOrNull()
            ?.let { dataLogId ->
              onAction(ThingOverviewAction.DismissTaskDetail)
              onAction(
                ThingOverviewAction.OpenDataLogClick(
                  thingId,
                  dataLogId
                )
              )
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
        syncStates = state.syncStates,
        dataLogs = state.dataLogs,
        openError = taskSheetOpenError,
        onLogWorkClick = {
          onAction(ThingOverviewAction.DismissTaskDetail)
          onAction(ThingOverviewAction.TaskCreateLogClick(selectedTask.card.id))
        },
        onSkipCycleClick = {
          onAction(ThingOverviewAction.DismissTaskDetail)
          onAction(ThingOverviewAction.TaskSkipClick(selectedTask))
        },
        comments = commentThread?.let { thread ->
          {
            RecordCommentThread(
              thread
            )
          }
        },
        commentComposer = commentThread?.let { thread ->
          { RecordCommentComposer(thread, state.isAnonymous) }
        },
      )
    }

  }
}
