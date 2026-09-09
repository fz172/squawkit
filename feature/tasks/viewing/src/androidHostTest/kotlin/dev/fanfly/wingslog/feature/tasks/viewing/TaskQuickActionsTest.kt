package dev.fanfly.wingslog.feature.tasks.viewing

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionTone
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import org.junit.Test

/** The task half of PRD §5.2's action table. */
class TaskQuickActionsTest {

  private var resolved = 0
  private var deleted = 0
  private val callbacks = TaskQuickActionCallbacks(
    onResolve = { resolved++ },
    onDelete = { deleted++ },
  )

  private fun actionsFor(status: DueStatus) =
    taskQuickActions(status, "Resolve", "Delete", callbacks)

  @Test
  fun outstandingTask_offersResolveThenDelete() {
    // NORMAL is the on-condition card: nothing has come due, but it is still resolvable.
    listOf(DueStatus.NORMAL, DueStatus.DUE_SOON, DueStatus.OVERDUE).forEach { status ->
      val actions = actionsFor(status)
      assertThat(actions.map { it.label }).containsExactly("Resolve", "Delete").inOrder()
      assertThat(actions.map { it.tone })
        .containsExactly(SwipeActionTone.POSITIVE, SwipeActionTone.DESTRUCTIVE).inOrder()
    }
  }

  @Test
  fun compliedTask_offersDeleteOnly() {
    // Only the History filter lists these, and its cycle is already closed.
    assertThat(actionsFor(DueStatus.COMPLIED).map { it.label }).containsExactly("Delete")
  }

  @Test
  fun eachActionCallsItsOwnCallback() {
    val actions = actionsFor(DueStatus.OVERDUE)

    actions.first().onClick()
    actions.last().onClick()

    assertThat(resolved).isEqualTo(1)
    assertThat(deleted).isEqualTo(1)
  }
}
