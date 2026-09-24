package dev.fanfly.wingslog.feature.search.viewing.filter

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.thing.ComponentType

/** Opens the filters: an inline panel under the bar on wide tiers, a bottom sheet on compact. */
@Composable
fun RecordFilterControls(
  expanded: Boolean,
  inline: Boolean,
  /** What the sheet reaches, in the thing's own words: "open squawks", "work logs". */
  scopeLabel: String,
  filter: RecordFilter,
  showComponentFilter: Boolean,
  /** The question this section answers; the Tasks tab asks about the thing, Logs about the work. */
  componentQuestion: String,
  componentLabel: @Composable (ComponentType) -> String,
  onComponentToggle: (ComponentType) -> Unit,
  timeQuestion: String,
  onTimeWindowChange: (TimeWindow) -> Unit,
  onClear: () -> Unit,
  onDismiss: () -> Unit,
  /** How many records the current filter leaves, and how many there are in total. */
  resultCount: Int,
  totalCount: Int,
  nounSingular: String,
  nounPlural: String,
  /** Records carrying each component, for the count beside its chip; null hides every count. */
  componentCount: ((ComponentType) -> Int)? = null,
  timeCount: ((TimeWindow) -> Int)? = null,
  dueWithin: Boolean = false,
  timeNote: String? = null,
  horizontalPadding: Dp = Spacing.screenPadding,
  facetSection: (@Composable ColumnScope.() -> Unit)? = null,
  /**
   * A sub-page shown *instead of* the sections — the people picker, reached from its own chip. It
   * lives inside the same sheet rather than a second one stacked on top, because two sheets deep
   * the dismiss gesture stops meaning anything predictable.
   */
  page: (@Composable ColumnScope.() -> Unit)? = null,
) {
  if (!expanded) return
  val content: @Composable ColumnScope.() -> Unit = page ?: {
    RecordFilterPanelContent(
      scopeLabel = scopeLabel,
      filter = filter,
      showComponentFilter = showComponentFilter,
      componentQuestion = componentQuestion,
      componentLabel = componentLabel,
      onComponentToggle = onComponentToggle,
      timeQuestion = timeQuestion,
      onTimeWindowChange = onTimeWindowChange,
      onClear = onClear,
      onDone = onDismiss,
      resultCount = resultCount,
      totalCount = totalCount,
      nounSingular = nounSingular,
      nounPlural = nounPlural,
      componentCount = componentCount,
      timeCount = timeCount,
      dueWithin = dueWithin,
      timeNote = timeNote,
      facetSection = facetSection,
    )
  }
  if (inline) RecordFilterPanel(
    horizontalPadding,
    content
  ) else RecordFilterSheet(onDismiss, content)
}
