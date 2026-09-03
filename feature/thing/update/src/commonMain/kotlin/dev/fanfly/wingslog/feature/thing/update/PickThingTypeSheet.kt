package dev.fanfly.wingslog.feature.thing.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.ui.adaptive.thingIcon
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.ThingTemplate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.feature.thing.update.generated.resources.Res as ThingRes
import wingslog.feature.thing.update.generated.resources.pick_type_subtitle
import wingslog.feature.thing.update.generated.resources.pick_type_title

/**
 * Which template a new Thing is created from (#738).
 *
 * A [DetailSheet], so it is a bottom sheet on a phone and an end drawer on a wide window — the
 * same presentation as viewing a squawk. It is a step on the way to the form, not a screen of its
 * own, and a full-screen dialog said otherwise.
 *
 * Sits ahead of the form rather than inside it: the template decides which spec fields the form
 * asks for. Until this existed, "Add a new thing" always produced an airplane.
 *
 * Offers [TemplateRegistry.canonical] only — already sorted, and already filtered to what this
 * build can render, so a template naming a `min_app_version` above us never appears.
 */
@Composable
fun PickThingTypeSheet(
  onDismiss: () -> Unit,
  onPick: (String) -> Unit,
  registry: TemplateRegistry = koinInject<TemplateRegistry>(),
) {
  PickThingTypeSheetContent(
    templates = registry.canonical(),
    onDismiss = onDismiss,
    onPick = onPick,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickThingTypeSheetContent(
  templates: List<ThingTemplate>,
  onDismiss: () -> Unit,
  onPick: (String) -> Unit,
) {
  DetailSheet(
    onDismiss = onDismiss,
    headerSlot = {
      Text(
        stringResource(ThingRes.string.pick_type_title),
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        stringResource(ThingRes.string.pick_type_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
  ) {
    // Two per row, chunked rather than a lazy grid: DetailSheet already scrolls, and nesting a
    // lazy grid inside a scrolling column crashes on an unbounded height.
    Spacer(Modifier.padding(top = Spacing.small))
    templates.chunked(2).forEach { row ->
      Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.small),
      ) {
        row.forEach { template ->
          ThingTypeCard(
            template = template,
            onClick = { onPick(template.id) },
            modifier = Modifier.weight(1f),
          )
        }
        // Keeps a lone card on an odd last row at half width instead of stretching it.
        if (row.size == 1) Spacer(Modifier.weight(1f))
      }
    }
    Spacer(Modifier.padding(bottom = Spacing.extraLarge))
  }
}

@Composable
private fun ThingTypeCard(
  template: ThingTemplate,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = modifier.clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(Spacing.large),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(
        thingIcon(template.icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Text(
        // display_name, not the lexicon noun: the picker names the type, and the lexicon is the
        // vocabulary a Thing gets *after* it has one.
        template.display_name,
        style = MaterialTheme.typography.titleSmall,
      )
    }
  }
}
