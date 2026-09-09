package dev.fanfly.wingslog.feature.squawk.viewing

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionTone
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import org.junit.Test

/** The squawk half of PRD §5.2's action table. */
class SquawkQuickActionsTest {

  private var resolved = 0
  private var deleted = 0
  private val callbacks = SquawkQuickActionCallbacks(
    onResolve = { resolved++ },
    onDelete = { deleted++ },
  )

  private fun actionsFor(status: SquawkStatus) =
    squawkQuickActions(status, "Resolve", "Delete", callbacks)

  @Test
  fun openSquawk_offersResolveThenDelete() {
    val actions = actionsFor(SquawkStatus.OPEN)

    assertThat(actions.map { it.label }).containsExactly("Resolve", "Delete").inOrder()
    assertThat(actions.map { it.tone })
      .containsExactly(SwipeActionTone.POSITIVE, SwipeActionTone.DESTRUCTIVE).inOrder()
  }

  @Test
  fun closedSquawk_offersDeleteOnly() {
    // Addressed and dismissed are both closed; there is nothing left to resolve on either.
    assertThat(actionsFor(SquawkStatus.ADDRESSED).map { it.label }).containsExactly("Delete")
    assertThat(actionsFor(SquawkStatus.DISMISSED).map { it.label }).containsExactly("Delete")
  }

  @Test
  fun eachActionCallsItsOwnCallback() {
    val actions = actionsFor(SquawkStatus.OPEN)

    actions.first().onClick()
    actions.last().onClick()

    assertThat(resolved).isEqualTo(1)
    assertThat(deleted).isEqualTo(1)
  }
}
