package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adjustments
import wingslog.feature.tasks.update.generated.resources.basics
import wingslog.feature.tasks.update.generated.resources.details
import wingslog.feature.tasks.update.generated.resources.schedule

data class TaskTabSpec(
  val icon: ImageVector,
  val label: StringResource,
)

var BASIC_TAB = TaskTabSpec(
  Icons.Default.Edit,
  Res.string.basics
)
var DETAILS_TAB = TaskTabSpec(
  Icons.Default.Info,
  Res.string.details
)
var SCHEDULE_TAB = TaskTabSpec(
  Icons.Default.DateRange,
  Res.string.schedule
)
var ADJUSTMENT_TAB = TaskTabSpec(
  Icons.Default.Tune,
  Res.string.adjustments
)

@Composable
fun TaskTabRow(
  tabs: List<TaskTabSpec>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
) {
  PrimaryTabRow(
    selectedTabIndex = selectedIndex,
    containerColor = MaterialTheme.colorScheme.background,
  ) {
    tabs.forEachIndexed { index, spec ->
      val selected = selectedIndex == index
      Tab(
        selected = selected,
        onClick = { onSelect(index) },
        icon = {
          Icon(
            spec.icon,
            contentDescription = stringResource(spec.label)
          )
        },
        text = if (selected) ({ Text(stringResource(spec.label)) }) else null,
      )
    }
  }
}
