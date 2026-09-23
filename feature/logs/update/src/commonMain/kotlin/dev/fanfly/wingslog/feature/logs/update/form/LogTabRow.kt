package dev.fanfly.wingslog.feature.logs.update.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.metersLabel
import dev.fanfly.wingslog.core.ui.bar.IconLabelTabRow
import dev.fanfly.wingslog.core.ui.bar.IconLabelTabSpec
import org.jetbrains.compose.resources.stringResource

@Composable
fun LogTabRow(
  tabs: List<LogFormTab>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  IconLabelTabRow(
    tabs = tabs.map { IconLabelTabSpec(it.spec.icon, it.label()) },
    selectedIndex = selectedIndex,
    onSelect = onSelect,
    modifier = modifier,
  )
}

/**
 * The tab's caption.
 *
 * [LogFormTab.HOURS] takes the template's own word for its meters, because the tab holds whatever
 * that template declares — "Odometer" on a car, and "Hours" only on the presets that measure in
 * them.
 */
@Composable
private fun LogFormTab.label(): String {
  val shipped = stringResource(spec.label)
  return if (this == LogFormTab.HOURS) {
    LocalThingTemplate.current.metersLabel(ifAbsent = shipped)
  } else {
    shipped
  }
}
