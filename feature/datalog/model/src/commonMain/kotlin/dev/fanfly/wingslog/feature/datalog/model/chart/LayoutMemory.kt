package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.feature.datalog.model.ChartLayout
import dev.fanfly.wingslog.feature.datalog.model.Pane
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

/** What the device remembers about one data log (PRD R31, R32): the panes and the axis mode. */
data class LayoutMemory(val layout: ChartLayout, val clockAxis: Boolean)

/**
 * A one-line text form, `v1;c=1;t=0;p=3,5|7`: version, clock axis, target pane index, then panes
 * as comma-separated columns separated by `|`. Decoding re-resolves every column against the
 * record's catalogue, so a re-parsed log that renumbered or dropped a series still opens.
 */
object LayoutMemoryCodec {
  private const val VERSION = "v1"

  fun encode(memory: LayoutMemory): String {
    val layout = memory.layout
    val target = layout.panes.indexOfFirst { it.id == layout.targetPane }
    val panes =
      layout.panes.joinToString("|") { pane -> pane.series.joinToString(",") { it.column.toString() } }
    return "$VERSION;c=${if (memory.clockAxis) 1 else 0};t=$target;p=$panes"
  }

  /** Null when [text] is not ours or names no series the log still has. */
  fun decode(text: String, catalogue: List<DataLogSeries>): LayoutMemory? {
    val parts = text.split(';')
    if (parts.firstOrNull() != VERSION) return null
    val fields = parts.drop(1)
      .associate { it.substringBefore('=') to it.substringAfter('=', "") }
    val known = catalogue.filter { it.isPlottable }
      .map { it.column }
      .toSet()
    val panes = fields["p"].orEmpty()
      .split('|')
      .map { pane ->
        pane.split(',')
          .mapNotNull { it.toIntOrNull() }
          .filter { it in known }
          .distinct()
          .map(::SeriesKey)
      }
    val targetIndex = fields["t"]?.toIntOrNull() ?: -1
    val targetKeys = panes.getOrNull(targetIndex)
    val kept = panes.filter { it.isNotEmpty() }
      .mapIndexed { index, keys -> Pane(PaneId(index), keys) }
    if (kept.isEmpty()) return null
    val target = kept.firstOrNull { it.series == targetKeys } ?: kept.first()
    return LayoutMemory(
      ChartLayout(kept, target.id),
      clockAxis = fields["c"] == "1"
    )
  }
}
