package dev.fanfly.wingslog.feature.search.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.Res
import wingslog.feature.search.sharedassets.generated.resources.custom_range_chip
import wingslog.feature.search.sharedassets.generated.resources.due_within_12_months
import wingslog.feature.search.sharedassets.generated.resources.due_within_3_months
import wingslog.feature.search.sharedassets.generated.resources.due_within_any
import wingslog.feature.search.sharedassets.generated.resources.period_all
import wingslog.feature.search.sharedassets.generated.resources.period_custom
import wingslog.feature.search.sharedassets.generated.resources.period_last_12_months
import wingslog.feature.search.sharedassets.generated.resources.period_last_3_months

/** The sheet’s choice label; [dueWithin] switches to the Tasks wording. */
@Composable
fun TimeWindow.optionLabel(dueWithin: Boolean): String = when (this) {
  TimeWindow.All -> stringResource(if (dueWithin) Res.string.due_within_any else Res.string.period_all)
  is TimeWindow.LastMonths -> when {
    months <= 3 -> stringResource(if (dueWithin) Res.string.due_within_3_months else Res.string.period_last_3_months)
    else -> stringResource(if (dueWithin) Res.string.due_within_12_months else Res.string.period_last_12_months)
  }
  is TimeWindow.Custom -> stringResource(Res.string.period_custom)
}

/** The active-filter chip label: the option label, or the dates for a custom range. */
@Composable
fun TimeWindow.chipLabel(dueWithin: Boolean): String = when (this) {
  is TimeWindow.Custom -> stringResource(Res.string.custom_range_chip, start.toDisplayFormat(), end.toDisplayFormat())
  else -> optionLabel(dueWithin)
}
