package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabRow
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabSpec
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.update.generated.resources.Res
import wingslog.feature.logs.update.generated.resources.log_tab_hours
import wingslog.feature.logs.update.generated.resources.log_tab_records
import wingslog.feature.logs.update.generated.resources.log_tab_work

data class LogTabSpec(
  val icon: ImageVector,
  val label: StringResource,
)

val LOG_WORK_TAB = LogTabSpec(Icons.Default.Build, Res.string.log_tab_work)
val LOG_HOURS_TAB = LogTabSpec(Icons.Default.Schedule, Res.string.log_tab_hours)
val LOG_RECORDS_TAB = LogTabSpec(Icons.Default.Link, Res.string.log_tab_records)

@Composable
fun LogTabRow(
  tabs: List<LogTabSpec>,
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
