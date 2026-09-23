package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.adaptive.thingIcon
import dev.fanfly.wingslog.core.ui.common.compose.GroupedCheckboxRow
import dev.fanfly.wingslog.core.ui.common.compose.GroupedLeadingIconChip
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.export.update.selection.ThingSelectionRow
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_thing_log_count
import wingslog.feature.export.sharedassets.generated.resources.export_thing_log_count_one

@Composable
internal fun ThingOptionRow(
  thing: ThingSelectionRow,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val logCount =
    if (thing.logCount == 1) stringResource(Res.string.export_thing_log_count_one)
    else stringResource(Res.string.export_thing_log_count, thing.logCount)
  GroupedCheckboxRow(
    // Already resolved per row by the ViewModel, which is the only place that knows each Thing's
    // own template. The label chain guarantees a line, so there is no "Untitled" case left.
    title = thing.label,
    subtitle = listOf(thing.subtitle, logCount).filter { it.isNotBlank() }
      .joinToString(" · "),
    titleStyle = WingslogTypography.dataLarge,
    checked = selected,
    onCheckedChange = { onClick() },
    leading = {
      GroupedLeadingIconChip(
        icon = thingIcon(thing.iconKey),
        contentDescription = null,
      )
    },
  )
}
