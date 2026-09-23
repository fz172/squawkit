package dev.fanfly.wingslog.feature.datalog.viewing.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.fanfly.wingslog.core.datetime.formatDuration
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheet
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheetAction
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheetActionRow
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_duration
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_rate
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_rate_value
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_samples
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_fact_series
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_ground_run
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_open_chart
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_recorded_as
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_sketch_loading
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * What opening the chart would give, before it is opened: how long the log ran and how much it
 * recorded, and a sketch of the series the chart opens with. *Open chart* is then
 * a deliberate step rather than the only way to learn anything about the file.
 *
 * A `DetailSheet`, so it is the pane beside the list on a wide tier and a sheet on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataLogPreviewSheet(
  preview: DataLogPreview,
  onDismiss: () -> Unit,
  onOpenChart: () -> Unit,
  /** Null for a guest, who browses but may not delete. */
  onDelete: (() -> Unit)?,
) {
  val row = preview.row
  DetailSheet(
    onDismiss = onDismiss,
    headerSlot = {
      Text(
        text = row.headline(stringResource(Res.string.data_log_ground_run)),
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(
        text = row.startedText(),
        style = WingslogTypography.dataSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (row.identityMismatch && row.identity.isNotBlank()) {
        Text(
          text = stringResource(Res.string.data_log_recorded_as, row.identity),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.statusColors.caution.accent,
        )
      }
    },
  ) {
    Spacer(Modifier.height(Spacing.medium))
    DetailSheetActionRow {
      DetailSheetAction(
        label = stringResource(Res.string.data_log_open_chart),
        onClick = onOpenChart,
        primary = true,
      )
      if (onDelete != null) {
        DetailSheetAction(
          label = stringResource(CoreRes.string.delete),
          onClick = onDelete,
          destructive = true,
        )
      }
    }
    Spacer(Modifier.height(Spacing.large))

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
      Fact(
        label = stringResource(Res.string.data_log_fact_duration),
        value = formatDuration(row.durationSeconds),
        modifier = Modifier.weight(1f),
      )
      Fact(
        label = stringResource(Res.string.data_log_fact_series),
        value = preview.series.size.toString(),
        modifier = Modifier.weight(1f),
      )
      Fact(
        label = stringResource(Res.string.data_log_fact_samples),
        value = preview.sampleCount.toString(),
        modifier = Modifier.weight(1f),
      )
      Fact(
        label = stringResource(Res.string.data_log_fact_rate),
        value = stringResource(
          Res.string.data_log_fact_rate_value,
          preview.sampleRateHz.toDouble()
            .formatToOneDecimalPlace(),
        ),
        modifier = Modifier.weight(1f),
      )
    }

    Spacer(Modifier.height(Spacing.large))
    SketchPane(preview.sketch)
  }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
    Text(
      text = value,
      style = WingslogTypography.dataLarge,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
    )
  }
}

/**
 * The recording's shape: two series as lines over the log's whole span, each on its own 0..1
 * scale. Labelled by name in the line's colour, no axes — it says what the log looks like, not
 * what it measured; the chart does that.
 */
@Composable
private fun SketchPane(sketch: Sketch?) {
  val color = MaterialTheme.colorScheme.primary
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    if (sketch == null) {
      Text(
        text = stringResource(Res.string.data_log_sketch_loading),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      return
    }
    val series = sketch.series ?: return
    if (series.points.size < 2) return
    Row(
      horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Canvas(Modifier.size(Spacing.small)) { drawCircle(color) }
      Text(
        text = series.name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(SKETCH_HEIGHT),
    ) {
      val path = Path()
      series.points.forEachIndexed { i, value ->
        val x = size.width * i / (series.points.size - 1)
        val y = size.height * (1f - value)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      drawPath(path, color, style = Stroke(width = Spacing.hairline.toPx() * 2))
    }
  }
}

private val SKETCH_HEIGHT = Spacing.massive * 2
