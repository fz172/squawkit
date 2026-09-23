package dev.fanfly.wingslog.feature.logs.viewing.list.card

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.ui.common.compose.TimelineGapRow
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.viewing.generated.resources.log_gap_one
import wingslog.feature.logs.viewing.generated.resources.log_gap_plural
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes

/** The logs a filter removed between two entries, counted in the template's own noun. */
@Composable
fun LogGapRow(omitted: Int, modifier: Modifier = Modifier) {
  val lexicon = LocalThingLexicon.current
  TimelineGapRow(
    text = if (omitted == 1) {
      stringResource(
        MaintenanceRes.string.log_gap_one,
        lexicon.logNoun.singular
      )
    } else {
      stringResource(
        MaintenanceRes.string.log_gap_plural,
        omitted,
        lexicon.logNoun.plural
      )
    },
    modifier = modifier,
  )
}
