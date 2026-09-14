package dev.fanfly.wingslog.feature.datalog.model

import kotlin.jvm.JvmInline

/** A pane within one viewer session. Device-local UI state, so a value class rather than a proto (design §4.4). */
@JvmInline
value class PaneId(val value: Int)

/** A series in a pane, by its source column (`DataLogSeries.column`). */
@JvmInline
value class SeriesKey(val column: Int)

data class Pane(val id: PaneId, val series: List<SeriesKey>)

/** What the user arranged: panes and their series, and which pane new series land in (design §11.1). */
data class ChartLayout(
  val panes: List<Pane> = emptyList(),
  val targetPane: PaneId? = null,
) {
  companion object {
    val EMPTY = ChartLayout()
  }
}

/** The visible time range in elapsed seconds; null on the ViewModel means the whole log. */
data class ViewWindow(val startSeconds: Int, val endSeconds: Int) {
  val lengthSeconds: Int get() = endSeconds - startSeconds
}
