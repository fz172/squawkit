package dev.fanfly.wingslog.feature.datalog.viewing.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.datetime.formatDuration
import dev.fanfly.wingslog.core.datetime.toClockText
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_ground_run
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_series_count
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_tail_mismatch

/** One row of the list (PRD R34, mocks 1a and 2a): date and route or ground run, then time · ident · duration · product. */
@Composable
fun DataLogCard(
  row: DataLogRow,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  /** Wide layouts show the source product and series count; phones keep the row to two lines. */
  showDetails: Boolean = true,
) {
  val date = row.startLocal.date.toDisplayFormat(numberOnly = false)
  val route =
    if (row.airborne && row.startLocationIdent.isNotBlank()) row.startLocationIdent
    else if (row.airborne) "" else stringResource(Res.string.data_log_ground_run)
  val title = if (route.isEmpty()) date else "$date · $route"
  val details = buildList {
    add(row.startLocal.time.toClockText())
    if (showDetails && row.airborne && row.startLocationIdent.isNotBlank()) add(
      row.startLocationIdent
    )
    add(formatDuration(row.durationSeconds))
    if (showDetails && row.product.isNotBlank()) add(row.product)
  }

  Card(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth()
        .padding(Spacing.large),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Icon(
        Icons.Filled.ShowChart,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(Spacing.extraLarge),
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (row.identityMismatch) {
            StatusChip(
              label = stringResource(Res.string.data_log_tail_mismatch),
              tier = StatusTier.CAUTION
            )
          }
        }
        Text(
          text = details.joinToString(" · "),
          style = WingslogTypography.dataSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (showDetails) {
        Text(
          text = stringResource(
            Res.string.data_log_series_count,
            row.seriesCount
          ),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.extraSmall))
      }
      Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
