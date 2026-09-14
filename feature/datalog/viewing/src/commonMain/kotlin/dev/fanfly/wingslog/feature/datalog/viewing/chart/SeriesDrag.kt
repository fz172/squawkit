package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

/** Where a dragged chip can land (design §11.5). */
sealed interface DropTarget {
  data class OnPane(val pane: PaneId) : DropTarget
  data object NewPane : DropTarget
}

/** A chip in flight: what it carries and where the pointer is, in window coordinates. [from] is null for a sidebar row. */
data class SeriesDrag(val key: SeriesKey, val label: String, val from: PaneId?, val position: Offset)

/**
 * One drag at a time across the viewer. Chips report their gesture here; panes and the *New pane*
 * strip register their window bounds; the drop resolves against those bounds. Kept in-app rather
 * than through the platform drag-and-drop transfer, whose payload type differs per target.
 */
class SeriesDragState {
  var drag: SeriesDrag? by mutableStateOf(null)
    private set

  private val targets = mutableStateMapOf<DropTarget, Rect>()

  fun register(target: DropTarget, bounds: Rect) {
    targets[target] = bounds
  }

  fun unregister(target: DropTarget) {
    targets.remove(target)
  }

  fun start(key: SeriesKey, label: String, from: PaneId?, position: Offset) {
    drag = SeriesDrag(key, label, from, position)
  }

  fun move(delta: Offset) {
    drag = drag?.let { it.copy(position = it.position + delta) }
  }

  /** The target under the pointer, if any. */
  fun hovered(): DropTarget? = drag?.let { d -> targets.entries.firstOrNull { it.value.contains(d.position) }?.key }

  /** Ends the drag and returns what it landed on. */
  fun drop(): Pair<SeriesDrag, DropTarget?>? {
    val d = drag ?: return null
    val target = hovered()
    drag = null
    return d to target
  }

  fun cancel() {
    drag = null
  }
}
