package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_drag_series
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_remove_pane
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_remove_series

/** What a chip shows for one series (PRD R24): swatch, short name, live value and unit. */
data class ChipInfo(
  val key: SeriesKey,
  val shortName: String,
  val unit: String,
  val color: Color,
  /** The value under the cursor, already formatted, or null with no cursor or no sample. */
  val value: String?,
)

private val SwatchSize = Spacing.medium
private const val DRAGGING_ALPHA = 0.4f

/**
 * The legend row above a pane (PRD R24, design §11.5): one chip per series, the chip being the
 * drag handle (hold, then drag onto another pane or the *New pane* strip), a remove control per
 * chip, and a remove control for the pane.
 */
@Composable
fun PaneHeaderChips(
  pane: PaneId,
  chips: List<ChipInfo>,
  dragState: SeriesDragState,
  onRemoveSeries: (SeriesKey) -> Unit,
  onRemovePane: () -> Unit,
  onDrop: (SeriesDrag, DropTarget?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier = Modifier.weight(1f)
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      chips.forEach { chip ->
        SeriesChip(
          chip = chip,
          pane = pane,
          dragState = dragState,
          onRemove = { onRemoveSeries(chip.key) },
          onDrop = onDrop,
        )
      }
    }
    IconButton(onClick = onRemovePane) {
      Icon(
        Icons.Filled.Delete,
        contentDescription = stringResource(Res.string.data_log_remove_pane),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SeriesChip(
  chip: ChipInfo,
  pane: PaneId,
  dragState: SeriesDragState,
  onRemove: () -> Unit,
  onDrop: (SeriesDrag, DropTarget?) -> Unit,
) {
  var origin by remember { mutableStateOf(Offset.Zero) }
  val dragging =
    dragState.drag?.let { it.key == chip.key && it.from == pane } == true
  val dragDescription =
    stringResource(Res.string.data_log_drag_series, chip.shortName)
  Surface(
    shape = RoundedCornerShape(Spacing.smallCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier
      .alpha(if (dragging) DRAGGING_ALPHA else 1f)
      .onGloballyPositioned { origin = it.positionInWindow() }
      .semantics { contentDescription = dragDescription }
      .seriesDragSource(
        chip.key,
        chip.shortName,
        pane,
        { origin },
        dragState,
        onDrop
      ),
  ) {
    // Text selection would otherwise claim a mouse drag on the label before the chip sees it.
    DisableSelection {
      Row(
        modifier = Modifier.padding(
          start = Spacing.small,
          top = Spacing.extraSmall,
          bottom = Spacing.extraSmall
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        Box(
          Modifier.size(SwatchSize)
            .clip(CircleShape)
            .background(chip.color)
        )
        Text(chip.shortName, style = MaterialTheme.typography.labelMedium)
        val reading = buildString {
          if (chip.value != null) append(chip.value)
          if (chip.unit.isNotBlank() && chip.value != null) append(' ').append(
            chip.unit
          )
        }
        if (reading.isNotEmpty()) {
          Text(
            reading,
            style = WingslogTypography.dataSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(
          onClick = onRemove,
          modifier = Modifier.size(Spacing.extraLarge)
        ) {
          Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(
              Res.string.data_log_remove_series,
              chip.shortName
            ),
            modifier = Modifier.size(Spacing.large),
          )
        }
      }
    }
  }
}
