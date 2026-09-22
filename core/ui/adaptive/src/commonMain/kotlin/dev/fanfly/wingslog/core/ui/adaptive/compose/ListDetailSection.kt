package dev.fanfly.wingslog.core.ui.adaptive.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A section as list and detail side by side on the tiers with a sidebar — the third column of
 * the tablet mock — and as the list alone on a phone, where [detail] presents itself as a sheet.
 *
 * [detail] is whatever the section already renders for its open record: a `DetailSheet` inside
 * the pane finds [LocalDetailPresentation] set to [DetailPresentation.Pane] and draws inline,
 * so the sheet composables need no second implementation. Null closes the pane, and the list
 * takes the whole width back — the scaffold animates both.
 *
 * The scaffold value is computed from [detail] rather than from a navigator: which record is
 * open is the section's own state, and the window is measured by the shell, not by
 * `currentWindowAdaptiveInfo`, which Kotlin/JS does not report reliably.
 *
 * With the pane open the list is a column of the tablet mock's width, so it reads as MEDIUM to
 * whatever is inside it — one card column, not two — and the shell's floating action is told how
 * far to step in, so it rides the list rather than the pane's own bottom bar.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ListDetailSection(
  detail: (@Composable () -> Unit)?,
  modifier: Modifier = Modifier,
  list: @Composable () -> Unit,
) {
  if (!LocalLayoutTier.current.hasSideNav) {
    list()
    detail?.invoke()
    return
  }
  val open = detail != null
  // Measured, not assumed: the scaffold shares the leftover width out, so the pane is wider than
  // it asked for on a wide window.
  val pane = LocalDetailPane.current
  val density = LocalDensity.current
  DisposableEffect(open) {
    pane.open = open
    if (!open) pane.width = 0.dp
    onDispose {
      pane.open = false
      pane.width = 0.dp
    }
  }
  val directive = PaneScaffoldDirective(
    maxHorizontalPartitions = if (open) 2 else 1,
    horizontalPartitionSpacerSize = 0.dp,
    maxVerticalPartitions = 1,
    verticalPartitionSpacerSize = 0.dp,
    defaultPanePreferredWidth = 360.dp,
    excludedBounds = emptyList(),
  )
  val value = calculateThreePaneScaffoldValue(
    maxHorizontalPartitions = directive.maxHorizontalPartitions,
    adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
    currentDestination = ThreePaneScaffoldDestinationItem<Nothing>(
      if (open) ListDetailPaneScaffoldRole.Detail else ListDetailPaneScaffoldRole.List,
    ),
  )
  ListDetailPaneScaffold(
    directive = directive,
    value = value,
    modifier = modifier.fillMaxSize(),
    listPane = {
      AnimatedPane(modifier = Modifier.preferredWidth(LIST_PANE_SHARE)) {
        val tier = LocalLayoutTier.current
        CompositionLocalProvider(
          LocalLayoutTier provides if (open && tier > LayoutTier.MEDIUM) LayoutTier.MEDIUM else tier,
        ) { list() }
      }
    },
    detailPane = {
      AnimatedPane(modifier = Modifier.preferredWidth(1f - LIST_PANE_SHARE)) {
        // A tone step above the list, and a rule between them, so the pane reads as its own
        // column rather than as the list continuing.
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.onSizeChanged { pane.width = with(density) { it.width.toDp() } },
        ) {
          Row {
            VerticalDivider()
            CompositionLocalProvider(LocalDetailPresentation provides DetailPresentation.Pane) {
              detail?.invoke()
            }
          }
        }
      }
    },
  )
}

/** How a record's detail sheet presents: on its own over the app, or inline as a scaffold's pane. */
enum class DetailPresentation { Sheet, Pane }

val LocalDetailPresentation = compositionLocalOf { DetailPresentation.Sheet }

/**
 * What the shell needs to know about an open detail pane: that one is open, so the content column
 * drops its width cap and the pane runs to the window's edge; and how wide it is, so the floating
 * action steps in past it and rides the list rather than the pane's bottom bar. Set by
 * [ListDetailSection], read by the shell.
 */
class DetailPaneState {
  var open: Boolean by mutableStateOf(false)
  var width: Dp by mutableStateOf(0.dp)
}

val LocalDetailPane = compositionLocalOf { DetailPaneState() }

/**
 * The list's share of the width, the detail taking the rest — the tablet mock's proportions.
 * Proportions rather than dp, so the two always sum to the scaffold and nothing is left over for
 * the scaffold to hand to one pane or scale out of the other.
 */
private const val LIST_PANE_SHARE = 0.32f
