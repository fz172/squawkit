package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabRow
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabSpec
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.details
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adjustments
import wingslog.feature.tasks.update.generated.resources.basics
import wingslog.feature.tasks.update.generated.resources.schedule
import wingslog.core.ui.generated.resources.Res as CoreRes

data class TaskTabSpec(
  val icon: ImageVector,
  val label: StringResource,
)

var BASIC_TAB = TaskTabSpec(Icons.Default.Edit, Res.string.basics)
var DETAILS_TAB = TaskTabSpec(Icons.Default.Info, CoreRes.string.details)
var SCHEDULE_TAB = TaskTabSpec(Icons.Default.DateRange, Res.string.schedule)
var ADJUSTMENT_TAB = TaskTabSpec(Icons.Default.Tune, Res.string.adjustments)

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
