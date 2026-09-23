package dev.fanfly.wingslog.feature.thing.dashboard.squawks

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.thing.SquawkPriority
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.priority_high
import wingslog.feature.squawk.sharedassets.generated.resources.priority_low
import wingslog.feature.squawk.sharedassets.generated.resources.priority_medium

@Composable
internal fun priorityLabel(priority: SquawkPriority): String = when (priority) {
  SquawkPriority.SQUAWK_PRIORITY_AOG -> LexiconFormatter.titleCase(
    LocalThingLexicon.current.down_status
  )

  SquawkPriority.SQUAWK_PRIORITY_HIGH -> stringResource(Res.string.priority_high)
  SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> stringResource(Res.string.priority_medium)
  else -> stringResource(Res.string.priority_low)
}
