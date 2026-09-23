package dev.fanfly.wingslog.feature.tasks.update.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabRow
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabSpec
import org.jetbrains.compose.resources.stringResource

@Composable
fun TaskTabRow(
  tabs: List<TaskTabSpec>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  IconLabelTabRow(
    tabs = tabs.map { IconLabelTabSpec(it.icon, stringResource(it.label)) },
    selectedIndex = selectedIndex,
    onSelect = onSelect,
    modifier = modifier,
  )
}
