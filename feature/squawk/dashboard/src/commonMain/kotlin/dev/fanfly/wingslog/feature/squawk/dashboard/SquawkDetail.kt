package dev.fanfly.wingslog.feature.squawk.dashboard

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
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkDetailSheet
import dev.fanfly.wingslog.id.DataLogId
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** The open squawk's detail: a pane beside the list on wide tiers, a sheet on a phone. Null while no squawk is selected. */
@Composable
internal fun squawkDetailFor(
  state: ThingOverviewUiState.Success,
  onAction: (ThingOverviewAction) -> Unit,
  onMutationAction: ((ThingOverviewAction) -> Unit)?,
  onLogClick: ((logId: String) -> Unit)?,
  onOpenDataLog: ((DataLogId) -> Unit)?,
  commentThread: CommentThreadController?,
): (@Composable () -> Unit)? {
  val attachmentOpener: AttachmentOpener = koinInject()
  val coroutineScope = rememberCoroutineScope()
  var openError by remember { mutableStateOf<String?>(null) }
  return state.selectedSquawk?.let { selected ->
    {
      SquawkDetailSheet(
        item = selected,
        addressingLog = state.logForSelectedSquawk,
        onLogClick = onLogClick,
        onDismiss = {
          openError = null
          onAction(ThingOverviewAction.DismissSquawkDetail)
        },
        onAttachmentTap = { attachment ->
          openError = null
          attachment.dataLogIdOrNull()
            ?.let { dataLogId ->
              onAction(ThingOverviewAction.DismissSquawkDetail)
              onOpenDataLog?.invoke(dataLogId)
              return@SquawkDetailSheet
            }
          val openFlow = attachmentOpener.open(attachment)
          coroutineScope.launch {
            openFlow.collect { openState ->
              if (openState is OpenState.Failed) openError =
                openState.error.message
            }
          }
        },
        syncStates = state.syncStates,
        dataLogs = state.dataLogs,
        openError = openError,
        onFixedClick = onMutationAction?.let { mutate ->
          {
            onAction(ThingOverviewAction.DismissSquawkDetail)
            mutate(ThingOverviewAction.SquawkFixedClick(selected.squawk.id))
          }
        },
        onDismissNoWorkPlanned = onMutationAction?.let { mutate ->
          {
            onAction(ThingOverviewAction.DismissSquawkDetail)
            mutate(ThingOverviewAction.SquawkDismissClick(selected.squawk.id))
          }
        },
        comments = commentThread?.let { thread -> { RecordCommentThread(thread) } },
        commentComposer = commentThread?.let { thread ->
          { RecordCommentComposer(thread, state.isAnonymous) }
        },
        onReopenClick = onMutationAction?.let { mutate ->
          {
            onAction(ThingOverviewAction.DismissSquawkDetail)
            mutate(ThingOverviewAction.SquawkReopenClick(selected.squawk.id))
          }
        },
        onEditClick = onMutationAction?.let { mutate ->
          {
            onAction(ThingOverviewAction.DismissSquawkDetail)
            mutate(
              ThingOverviewAction.EditSquawkClick(
                state.thing.id,
                selected.squawk.id
              )
            )
          }
        },
      )
    }
  }
}
