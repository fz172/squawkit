package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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

private val MaxSelectedTabWidth = 200.dp
private val TabHeight = 56.dp

@Composable
fun TaskTabRow(
  tabs: List<TaskTabSpec>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val unselectedCount = tabs.size - 1
    val calculatedIconWidth = (maxWidth - MaxSelectedTabWidth) / unselectedCount
    // On super wide screens (e.g. tablets) the icon tabs would exceed the selected tab width,
    // so fall back to dividing all tabs evenly instead.
    val evenTabWidth = maxWidth / tabs.size
    val useEvenDistribution = calculatedIconWidth > MaxSelectedTabWidth
    val iconTabWidth = if (useEvenDistribution) evenTabWidth else calculatedIconWidth
    val selectedWidth = if (useEvenDistribution) evenTabWidth else MaxSelectedTabWidth

    val animatedWidths = tabs.indices.map { index ->
      animateDpAsState(
        targetValue = if (index == selectedIndex) selectedWidth else iconTabWidth,
        animationSpec = tween(
          durationMillis = 300,
          easing = FastOutSlowInEasing
        ),
        label = "tab_width_$index",
      )
    }

    val indicatorX = (0 until selectedIndex).fold(0.dp) { acc, i -> acc + animatedWidths[i].value }
    val indicatorWidth = animatedWidths[selectedIndex].value

    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(TabHeight),
      ) {
        tabs.forEachIndexed { index, spec ->
          val selected = index == selectedIndex
          val animWidth by animatedWidths[index]
          val contentColor by animateColorAsState(
            targetValue = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(200),
            label = "tab_color_$index",
          )

          Box(
            modifier = Modifier
              .width(animWidth)
              .fillMaxHeight()
              .clickable { onSelect(index) },
            contentAlignment = Alignment.Center,
          ) {
            if (selected) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(
                  6.dp,
                  Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  spec.icon,
                  contentDescription = null,
                  tint = contentColor,
                  modifier = Modifier.size(20.dp),
                )
                Text(
                  stringResource(spec.label),
                  color = contentColor,
                  style = MaterialTheme.typography.labelLarge,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            } else {
              Icon(
                spec.icon,
                contentDescription = stringResource(spec.label),
                tint = contentColor,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }

      // Indicator track + animated underline
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(2.dp)
          .background(MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Box(
          modifier = Modifier
            .offset(x = indicatorX)
            .width(indicatorWidth)
            .height(2.dp)
            .background(MaterialTheme.colorScheme.primary),
        )
      }
    }
  }
}
