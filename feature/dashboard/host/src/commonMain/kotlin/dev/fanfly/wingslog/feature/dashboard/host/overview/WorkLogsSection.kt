package dev.fanfly.wingslog.feature.dashboard.host.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.ui.common.compose.ListRowDivider
import dev.fanfly.wingslog.core.ui.common.compose.SectionHeader
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.logs.dashboard.LogOnboardingCard
import dev.fanfly.wingslog.feature.logs.dashboard.RecentLogRow
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.dashboard.host.generated.resources.Res
import wingslog.feature.dashboard.host.generated.resources.overview_all_logs
import wingslog.feature.dashboard.host.generated.resources.overview_title_with_count


/** The log noun and how many there are — "Work logs · 40". The count the stats strip used to hold. */
@Composable
internal fun workLogsTitle(state: ThingOverviewUiState.Success): String {
  val title =
    LexiconFormatter.titleCasePlural(LocalThingLexicon.current.logNoun)
  val total = state.logStats?.total ?: return title
  return stringResource(
    Res.string.overview_title_with_count,
    title,
    total.toInt()
  )
}

/** The newest logs under a header that links to all of them; the onboarding card when there are none. */
@Composable
internal fun WorkLogsSection(
  state: ThingOverviewUiState.Success,
  onViewLogsTab: () -> Unit,
  onMutationAction: ((ThingOverviewAction) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val total = state.logStats?.total ?: return
  if (total == 0L) {
    if (onMutationAction != null) {
      LogOnboardingCard(
        onAddLogClick = { onMutationAction(ThingOverviewAction.AddLogClick(state.thing.id)) },
        modifier = modifier,
      )
    }
    return
  }
  Column(modifier = modifier) {
    SectionHeader(
      title = LexiconFormatter.titleCasePlural(LocalThingLexicon.current.logNoun),
      count = total.toInt(),
      modifier = Modifier.clickable(onClick = onViewLogsTab),
      trailing = {
        Text(
          text = stringResource(Res.string.overview_all_logs),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(Spacing.extraSmall),
        )
      },
    )
    state.recentLogs.forEachIndexed { index, log ->
      if (index > 0) ListRowDivider()
      RecentLogRow(log = log, onClick = onViewLogsTab)
    }
  }
}
