package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.schedule_unit_days
import wingslog.feature.tasks.update.generated.resources.schedule_unit_months
import wingslog.feature.tasks.update.generated.resources.schedule_unit_years

@Composable
internal fun UnitPillSelect(
  selected: ScheduleTimeUnit,
  onSelect: (ScheduleTimeUnit) -> Unit,
) {
  SegmentedChoice(
    options = listOf(
      ScheduleTimeUnit.DAYS to Res.string.schedule_unit_days,
      ScheduleTimeUnit.MONTHS to Res.string.schedule_unit_months,
      ScheduleTimeUnit.YEARS to Res.string.schedule_unit_years,
    ).map { (unit, res) ->
      SegmentOption(
        unit,
        stringResource(res).replaceFirstChar { it.titlecase() })
    },
    selected = selected,
    onSelect = onSelect,
  )
}
