package dev.fanfly.wingslog.feature.search.viewing.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.viewing.optionLabel
import dev.fanfly.wingslog.thing.ComponentType
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.Res
import wingslog.feature.search.sharedassets.generated.resources.custom_from
import wingslog.feature.search.sharedassets.generated.resources.custom_to

internal val COMPONENT_OPTIONS = listOf(
  ComponentType.COMPONENT_AIRFRAME,
  ComponentType.COMPONENT_ENGINE,
  ComponentType.COMPONENT_PROPELLER,
  ComponentType.COMPONENT_UNKNOWN,
)

@Composable
internal fun TimeSection(
  question: String,
  time: TimeWindow,
  dueWithin: Boolean,
  note: String?,
  count: ((TimeWindow) -> Int)?,
  onTimeWindowChange: (TimeWindow) -> Unit,
) {
  val today = remember {
    Clock.System.now()
      .toLocalDateTime(TimeZone.currentSystemDefault()).date
  }
  val presets =
    listOf(TimeWindow.All, TimeWindow.LastMonths(3), TimeWindow.LastMonths(12))
  val custom = time as? TimeWindow.Custom
  FilterSection(label = question, pickOne = true, note = note) {
    presets.forEach { preset ->
      ChoiceChip(
        label = preset.optionLabel(dueWithin),
        selected = time == preset,
        count = count?.invoke(preset),
        onClick = { onTimeWindowChange(preset) },
      )
    }
    ChoiceChip(
      label = TimeWindow.Custom(today, today)
        .optionLabel(dueWithin),
      selected = custom != null,
      onClick = {
        if (custom == null) onTimeWindowChange(
          TimeWindow.Custom(
            today.minus(
              3,
              DateTimeUnit.MONTH
            ), today
          )
        )
      },
    )
  }
  if (custom != null) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      DateField(
        label = stringResource(Res.string.custom_from),
        date = custom.start,
        onDateChange = {
          onTimeWindowChange(
            custom.copy(
              start = it,
              end = maxOf(
                custom.end,
                it
              )
            )
          )
        },
        modifier = Modifier.weight(1f),
      )
      DateField(
        label = stringResource(Res.string.custom_to),
        date = custom.end,
        onDateChange = {
          onTimeWindowChange(
            custom.copy(
              start = minOf(
                custom.start,
                it
              ), end = it
            )
          )
        },
        modifier = Modifier.weight(1f),
      )
    }
  }
}
