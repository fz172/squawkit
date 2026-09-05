package dev.fanfly.wingslog.feature.thing.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.ui.adaptive.thingIcon
import dev.fanfly.wingslog.core.ui.common.compose.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.ThingTemplate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.feature.thing.update.generated.resources.pick_type_subtitle
import wingslog.feature.thing.update.generated.resources.pick_type_title
import wingslog.feature.thing.update.generated.resources.Res as ThingRes

/**
 * Which template a new Thing is created from (#738).
 *
 * Always a bottom sheet, on every window size. The shared `DetailSheet` switches to an end drawer
 * above COMPACT, which is right for reading a record beside the list it came from; a picker is a
 * short interruption on the way to the form, and the sheet reads better wide than a drawer does.
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
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = Spacing.extraLarge)
        .padding(bottom = Spacing.extraLarge),
    ) {
      Text(
        stringResource(ThingRes.string.pick_type_title),
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        stringResource(ThingRes.string.pick_type_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
          top = Spacing.extraSmall,
          bottom = Spacing.large
        ),
      )
      // Two per row, chunked rather than a lazy grid: the column already scrolls, and a lazy grid
      // nested in it has no bounded height to measure against.
      templates.chunked(2)
        .forEach { row ->
          Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            modifier = Modifier.fillMaxWidth()
              .padding(bottom = Spacing.small),
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
    }
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
