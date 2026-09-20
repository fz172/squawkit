package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Motion
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme

data class IconLabelTabSpec(
  val icon: ImageVector,
  val label: String,
)

private val TabHeight = Spacing.buttonHeight
private val IndicatorHeight = 2.dp

/** Where a tab landed in the row, so the indicator can travel to it. */
private data class TabBounds(val left: Float, val width: Float)

/**
 * Form tabs. Every tab shows its label, always: a row of bare glyphs has to be learned, and these
 * are not the five destinations a nav bar gets away with. Tabs share the width evenly when they
 * fit and scroll horizontally when they do not; the selected tab is brought into view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconLabelTabRow(
  tabs: List<IconLabelTabSpec>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val minTabWidth = maxWidth / tabs.size
    val bounds = remember(tabs.size) { mutableStateListOf(*arrayOfNulls<TabBounds>(tabs.size)) }
    val selectedBounds = bounds.getOrNull(selectedIndex)
    val indicatorLeft by animateFloatAsState(
      targetValue = selectedBounds?.left ?: 0f,
      animationSpec = Motion.enter(),
      label = "tab_indicator_left",
    )
    val indicatorWidth by animateFloatAsState(
      targetValue = selectedBounds?.width ?: 0f,
      animationSpec = Motion.enter(),
      label = "tab_indicator_width",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val indicatorColor = MaterialTheme.colorScheme.primary

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(TabHeight)
        .horizontalScroll(rememberScrollState())
        // Drawn inside the scroll, so the track and indicator move with the tabs.
        .drawBehind {
          val stroke = IndicatorHeight.toPx()
          val top = size.height - stroke
          drawRect(trackColor, Offset(0f, top), Size(size.width, stroke))
          drawRect(indicatorColor, Offset(indicatorLeft, top), Size(indicatorWidth, stroke))
        },
    ) {
      tabs.forEachIndexed { index, spec ->
        val selected = index == selectedIndex
        val contentColor by animateColorAsState(
          targetValue = if (selected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant,
          animationSpec = Motion.enter(),
          label = "tab_color_$index",
        )
        val bringIntoView = remember { BringIntoViewRequester() }
        LaunchedEffect(selected) { if (selected) bringIntoView.bringIntoView() }

        Row(
          modifier = Modifier
            .widthIn(min = minTabWidth)
            .fillMaxHeight()
            .bringIntoViewRequester(bringIntoView)
            .onGloballyPositioned {
              bounds[index] = TabBounds(it.positionInParent().x, it.size.width.toFloat())
            }
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
            .padding(horizontal = Spacing.medium),
          horizontalArrangement = Arrangement.spacedBy(
            Spacing.small,
            Alignment.CenterHorizontally
          ),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            spec.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(Spacing.xLarge),
          )
          Text(
            spec.label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
          )
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun IconLabelTabRowPreview() {
  WingslogTheme {
    IconLabelTabRow(
      tabs = listOf(
        IconLabelTabSpec(icon = Icons.Default.Build, label = "Maintenance"),
        IconLabelTabSpec(icon = Icons.Default.Schedule, label = "Schedule"),
        IconLabelTabSpec(icon = Icons.Default.Link, label = "Records"),
      ),
      selectedIndex = 0,
      onSelect = {}
    )
  }
}
