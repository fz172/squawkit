package dev.fanfly.wingslog.feature.search.viewing.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.model.visibleComponentOptions
import dev.fanfly.wingslog.thing.ComponentType
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.Res
import wingslog.feature.search.sharedassets.generated.resources.clear_all
import wingslog.feature.search.sharedassets.generated.resources.filter_close
import wingslog.feature.search.sharedassets.generated.resources.filter_scope_note
import wingslog.feature.search.sharedassets.generated.resources.filter_sheet_title
import wingslog.feature.search.sharedassets.generated.resources.filter_show_all
import wingslog.feature.search.sharedassets.generated.resources.filter_show_count
import wingslog.feature.search.sharedassets.generated.resources.filter_show_none

/**
 * The sheet, in plain language: each section asks the question the pilot is actually answering,
 * says whether the options are exclusive, carries the count each one would leave, and ends in a
 * button that states its own result. Choices apply immediately; the button only closes.
 */
@Composable
internal fun RecordFilterPanelContent(
  scopeLabel: String,
  filter: RecordFilter,
  showComponentFilter: Boolean,
  componentQuestion: String,
  componentLabel: @Composable (ComponentType) -> String,
  onComponentToggle: (ComponentType) -> Unit,
  timeQuestion: String,
  onTimeWindowChange: (TimeWindow) -> Unit,
  onClear: () -> Unit,
  onDone: () -> Unit,
  resultCount: Int,
  totalCount: Int,
  nounSingular: String,
  nounPlural: String,
  componentCount: ((ComponentType) -> Int)?,
  timeCount: ((TimeWindow) -> Int)?,
  dueWithin: Boolean,
  timeNote: String?,
  facetSection: (@Composable ColumnScope.() -> Unit)?,
) {
  Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        stringResource(Res.string.filter_sheet_title),
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        stringResource(Res.string.filter_scope_note, scopeLabel),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    IconButton(onClick = onDone) {
      Icon(
        Icons.Default.Close,
        contentDescription = stringResource(Res.string.filter_close),
      )
    }
  }

  val components = if (!showComponentFilter) {
    emptyList()
  } else {
    visibleComponentOptions(
      COMPONENT_OPTIONS,
      filter.components,
      componentCount
    )
  }
  // The whole section goes when nothing is left to ask about, rather than leaving the question
  // standing over an empty row.
  if (components.isNotEmpty()) {
    FilterSection(componentQuestion, pickOne = false) {
      components.forEach { component ->
        ChoiceChip(
          label = componentLabel(component),
          selected = component in filter.components,
          count = componentCount?.invoke(component),
          onClick = { onComponentToggle(component) },
        )
      }
    }
  }

  TimeSection(
    question = timeQuestion,
    time = filter.time,
    dueWithin = dueWithin,
    note = timeNote,
    count = timeCount,
    onTimeWindowChange = onTimeWindowChange,
  )

  facetSection?.let { Column(content = it) }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Greyed rather than hidden: its absence would be one more thing to notice.
    TextButton(onClick = onClear, enabled = filter.isActive) {
      Text(stringResource(Res.string.clear_all))
    }
    Spacer(Modifier.weight(1f))
    Button(onClick = onDone) {
      Text(
        when {
          !filter.isActive -> stringResource(
            Res.string.filter_show_all,
            totalCount,
            nounPlural
          )

          resultCount == 0 -> stringResource(
            Res.string.filter_show_none,
            nounPlural
          )

          else -> stringResource(
            Res.string.filter_show_count,
            resultCount,
            if (resultCount == 1) nounSingular else nounPlural,
          )
        }
      )
    }
  }
}
