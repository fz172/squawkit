package dev.fanfly.wingslog.feature.dashboard.host.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.specLines
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.thing_shared_badge
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverviewHero(
  state: ThingOverviewUiState.Success,
  modifier: Modifier = Modifier,
) {
  // Was make, model and tail number read by key, which only an aeroplane has: a car showed its
  // make and model trailed by a blank, and a home — no make, no model, no tail number — got an
  // empty hero. Both halves come from the template now. The accent is the field it marks
  // `title_candidate`, so it is a tail number and never a VIN: what an owner calls the thing by,
  // not merely the first identifier declared.
  val spec = LocalThingTemplate.current.specLines(state.thing)
  // A preset naming neither falls back to the name, which is the one label every Thing has.
  val headline = spec.headline.ifBlank { state.thing.name }
  Column(modifier = modifier) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      if (headline.isNotBlank()) {
        Text(
          text = headline,
          style = WingslogTypography.heroDisplay,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
      if (spec.title.isNotBlank() && spec.title != headline) {
        Text(
          text = spec.title,
          style = WingslogTypography.heroDisplay,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
    if (state.shared) {
      SharedMarker()
    }
  }
}

/**
 * Marks a thing that is part of a share (§6.3) — shown to *every* partner in it, the hosting
 * owner and co-owners included, not just the accounts it was shared into. Everyone in the share
 * needs to know that what they write here is visible to the others.
 *
 * It sits under the hero title rather than in the thing picker: the picker showed it once, in
 * passing, while the dashboard is where you actually act on the thing.
 */
@Composable
private fun SharedMarker() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    modifier = Modifier.padding(top = Spacing.extraSmall),
  ) {
    Icon(
      imageVector = Icons.Filled.FolderShared,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.secondary,
      modifier = Modifier.size(Spacing.large),
    )
    Text(
      text = stringResource(CoreRes.string.thing_shared_badge),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
